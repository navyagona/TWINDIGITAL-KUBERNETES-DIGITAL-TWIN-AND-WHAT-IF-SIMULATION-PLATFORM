package com.twindigital.costrisk.service;

import com.twindigital.costrisk.client.AwsCostExplorerClient;
import com.twindigital.costrisk.client.AwsCostExplorerClient.AwsCostData;
import com.twindigital.costrisk.client.PrometheusClient;
import com.twindigital.costrisk.client.PrometheusClient.UsageSnapshot;
import com.twindigital.costrisk.client.PrometheusClient.UsageWindow;
import com.twindigital.costrisk.entity.CostRiskEntities.*;
import com.twindigital.costrisk.model.CostRiskDTOs.*;
import com.twindigital.costrisk.repository.CostRiskRepositories.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CostRiskServiceTest {

    @Mock
    private PrometheusClient prometheusClient;

    @Mock
    private AwsCostExplorerClient awsCostExplorerClient;

    @Mock
    private ClusterNodeRepository nodeRepository;

    @Mock
    private ClusterPodRepository podRepository;

    @Mock
    private ClusterDeploymentRepository deploymentRepository;

    @Mock
    private InstancePricingRepository pricingRepository;

    private SimpleMeterRegistry meterRegistry;
    private CostRiskService costRiskService;

    private static final String CLUSTER = "twindigital-cluster";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        costRiskService = new CostRiskService(
                prometheusClient,
                awsCostExplorerClient,
                nodeRepository,
                podRepository,
                deploymentRepository,
                pricingRepository,
                meterRegistry
        );
        ReflectionTestUtils.setField(costRiskService, "awsRegion", "ap-south-1");
        ReflectionTestUtils.setField(costRiskService, "defaultHourlyNodeCost", 0.0416);
        ReflectionTestUtils.setField(costRiskService, "monthlyHours", 730.0);
        ReflectionTestUtils.setField(costRiskService, "eksControlPlaneHourlyCost", 0.10);
        costRiskService.initMetrics();
        // Default: Prometheus reachable but empty. Tests that need usage data override this.
        lenient().when(prometheusClient.queryUsage())
                .thenReturn(new UsageSnapshot(Map.of(), Map.of(), UsageWindow.NO_DATA));
    }

    @Test
    @DisplayName("Should detect over-provisioned pods by comparing PromQL p95 usage with requests and right-size them")
    void shouldDetectOverprovisionedPodsAndRecommendRightSizing() {
        // Given pod requesting 500m CPU and 1024Mi RAM
        ClusterPodEntity pod = ClusterPodEntity.builder()
                .clusterName(CLUSTER)
                .namespace("twindigital")
                .name("billing-service-pod-1")
                .ownerName("billing-service")
                .cpuRequests("500m")
                .memoryRequests("1024Mi")
                .build();

        when(podRepository.findByClusterName(CLUSTER)).thenReturn(List.of(pod));

        // Mock Prometheus p95 usage: actual CPU is only 45m (9% util), memory is only 180Mi (17% util)
        when(prometheusClient.queryUsage()).thenReturn(new UsageSnapshot(
                Map.of("billing-service-pod-1", 45.0),
                Map.of("billing-service-pod-1", 180L * 1024L * 1024L),
                UsageWindow.P95_7D));

        // When
        OverprovisionedReport report = costRiskService.getOverprovisionedReport(CLUSTER);

        // Then
        assertThat(report).isNotNull();
        assertThat(report.isDataAvailable()).isTrue();
        assertThat(report.getTotalOverprovisionedPods()).isEqualTo(1);
        assertThat(report.getPotentialMonthlySavingsUsd()).isGreaterThan(0.0);

        OverprovisionedPod item = report.getOverprovisionedPods().get(0);
        assertThat(item.getPodName()).isEqualTo("billing-service-pod-1");
        assertThat(item.getCurrentCpuRequest()).isEqualTo("500m");
        assertThat(item.getP95CpuUsage()).isEqualTo("45m");
        // Recommended CPU should be ceil(45 * 1.25) = 57m
        assertThat(item.getRecommendedCpuRequest()).isEqualTo("57m");
        assertThat(item.getRecommendedMemoryRequest()).contains("234Mi");
        assertThat(item.getCpuWastePercent()).isGreaterThan(85.0);

        // Verify Micrometer gauge updated
        double gaugeValue = meterRegistry.get("twin_overprovisioned_pods").gauge().value();
        assertThat(gaugeValue).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should return dataAvailable=false and no fake numbers when Prometheus has no metrics")
    void shouldReturnDataAvailableFalseWhenPrometheusHasNoMetrics() {
        ClusterPodEntity pod = ClusterPodEntity.builder()
                .clusterName(CLUSTER)
                .namespace("twindigital")
                .name("billing-service-pod-1")
                .ownerName("billing-service")
                .cpuRequests("500m")
                .memoryRequests("1024Mi")
                .build();

        when(podRepository.findByClusterName(CLUSTER)).thenReturn(List.of(pod));
        when(prometheusClient.queryUsage()).thenReturn(new UsageSnapshot(Map.of(), Map.of(), UsageWindow.NO_DATA));

        OverprovisionedReport report = costRiskService.getOverprovisionedReport(CLUSTER);

        assertThat(report).isNotNull();
        assertThat(report.isDataAvailable()).isFalse();
        assertThat(report.getTotalOverprovisionedPods()).isEqualTo(0);
        assertThat(report.getPotentialMonthlySavingsUsd()).isEqualTo(0.0);
        assertThat(report.getOverprovisionedPods()).isEmpty();

        // Verify Micrometer gauge updated to 0
        double gaugeValue = meterRegistry.get("twin_overprovisioned_pods").gauge().value();
        assertThat(gaugeValue).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should fetch cost summary from AWS Cost Explorer when available")
    void shouldFetchCostSummaryFromAwsCostExplorer() {
        // Given live AWS Cost Explorer data
        AwsCostData awsData = AwsCostData.builder()
                .totalMonthlyCostUsd(145.80)
                .costByService(Map.of("Amazon Elastic Compute Cloud - Compute", 120.0, "Amazon Elastic Kubernetes Service", 25.80))
                .build();

        when(awsCostExplorerClient.getMonthlyCost(CLUSTER)).thenReturn(Optional.of(awsData));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        // When
        CostSummaryResponse summary = costRiskService.getCostSummary(CLUSTER);

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getDataSource()).isEqualTo("AWS_COST_EXPLORER");
        assertThat(summary.getTotalMonthlyCostUsd()).isEqualTo(145.80);
        assertThat(summary.getTotalAnnualCostUsd()).isCloseTo(145.80 * 12.0, within(0.01));

        // Verify Micrometer gauge updated
        double gaugeValue = meterRegistry.get("twin_cost_estimated_usd").gauge().value();
        assertThat(gaugeValue).isEqualTo(145.80);
    }

    @Test
    @DisplayName("Should fallback to pricing catalog and include EKS control plane cost ($73/mo)")
    void shouldFallbackToPricingCatalogWhenAwsOffline() {
        // Given AWS Cost Explorer returns empty (offline mode)
        when(awsCostExplorerClient.getMonthlyCost(CLUSTER)).thenReturn(Optional.empty());

        ClusterNodeEntity node1 = ClusterNodeEntity.builder().clusterName(CLUSTER).name("node-1").build();
        ClusterNodeEntity node2 = ClusterNodeEntity.builder().clusterName(CLUSTER).name("node-2").build();
        when(nodeRepository.findByClusterName(CLUSTER)).thenReturn(List.of(node1, node2));

        // Instance pricing: 2 nodes * $0.0416/hr * 730 hrs = $60.74/mo
        // Plus Amazon EKS control plane: $0.10/hr * 730 hrs = $73.00/mo
        // Total = $133.74/mo
        when(pricingRepository.findByRegionAndInstanceType("ap-south-1", "t3.medium"))
                .thenReturn(Optional.of(InstancePricingEntity.builder().onDemandPrice(0.0416).build()));
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        // When
        CostSummaryResponse summary = costRiskService.getCostSummary(CLUSTER);

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getDataSource()).isEqualTo("INSTANCE_PRICING_CATALOG");
        assertThat(summary.getTotalMonthlyCostUsd()).isEqualTo(133.74);
        assertThat(summary.getControlPlaneCostUsd()).isEqualTo(73.00);
    }

    @Test
    @DisplayName("Should estimate monthly and annual cost delta for a proposed change set")
    void shouldEstimateCostDeltaForChange() {
        EstimateChangeRequest request = EstimateChangeRequest.builder()
                .kind("SCALE")
                .target("billing-service")
                .currentReplicas(2)
                .replicas(4) // scale from 2 to 4 pods
                .cpuRequest("200m")
                .memRequest("512Mi")
                .build();

        EstimateChangeResponse response = costRiskService.estimateChangeCost(request);

        assertThat(response).isNotNull();
        assertThat(response.getTarget()).isEqualTo("billing-service");
        assertThat(response.getDeltaMonthlyUsd()).isGreaterThan(0.0);
        assertThat(response.getDeltaAnnualUsd()).isCloseTo(response.getDeltaMonthlyUsd() * 12.0, within(0.1));
        assertThat(response.getExplanation()).contains("billing-service");
    }

    private void stubPriceTable() {
        when(pricingRepository.findByRegionAndInstanceType("ap-south-1", "t3.medium")).thenReturn(Optional.of(
                InstancePricingEntity.builder().region("ap-south-1").instanceType("t3.medium")
                        .onDemandPrice(0.0416).vcpu(2).memoryGib(4.0).build()));
    }

    @Test
    @DisplayName("No Prometheus data: real fields stay empty and labelled sample suggestions are returned")
    void shouldReturnLabelledSampleSuggestionsWithoutPrometheus() {
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());
        when(prometheusClient.queryUsage()).thenReturn(new UsageSnapshot(Map.of(), Map.of(), UsageWindow.NOT_CONFIGURED));

        OverprovisionedReport report = costRiskService.getOverprovisionedReport(CLUSTER);

        assertThat(report.isDataAvailable()).isFalse();
        assertThat(report.getDataSource()).isEqualTo("SAMPLE");
        assertThat(report.getEvaluatedWindow()).startsWith("SAMPLE DATA").contains("PROMETHEUS_URL");
        assertThat(report.getOverprovisionedPods()).isEmpty();
        assertThat(report.getPotentialMonthlySavingsUsd()).isEqualTo(0.0);
        assertThat(report.getSampleSuggestions()).hasSize(2)
                .allMatch(p -> p.getExplanation().contains("SAMPLE usage data"));
        assertThat(meterRegistry.get("twin_overprovisioned_pods").gauge().value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Instant-value fallback is labelled as such, not as a 7-day p95")
    void shouldLabelInstantFallbackWindow() {
        ClusterPodEntity pod = ClusterPodEntity.builder().clusterName(CLUSTER).namespace("twindigital")
                .name("web-1").ownerName("web").cpuRequests("500m").memoryRequests("1024Mi").build();
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(List.of(pod));
        when(prometheusClient.queryUsage()).thenReturn(new UsageSnapshot(
                Map.of("web-1", 40.0), Map.of(), UsageWindow.INSTANT_FALLBACK));

        OverprovisionedReport report = costRiskService.getOverprovisionedReport(CLUSTER);

        assertThat(report.getDataSource()).isEqualTo("PROMETHEUS");
        assertThat(report.getEvaluatedWindow()).startsWith("Instant PromQL");
        assertThat(report.getOverprovisionedPods().get(0).getExplanation()).contains("current instant usage");
    }

    @Test
    @DisplayName("estimate-change prices from the ap-south-1 table and takes the baseline from the cluster snapshot")
    void shouldPriceFromTableWithSnapshotBaseline() {
        stubPriceTable();
        ClusterPodEntity pod = ClusterPodEntity.builder().clusterName(CLUSTER).namespace("twindigital")
                .name("api-5d9f-abc").ownerName("api").cpuRequests("1000m").memoryRequests("2048Mi").build();
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(List.of(pod));
        when(deploymentRepository.findByClusterNameAndNamespaceAndName(CLUSTER, "twindigital", "api"))
                .thenReturn(Optional.of(ClusterDeploymentEntity.builder().name("api").replicas(2).build()));

        // halve CPU on 2 replicas; memory unchanged
        EstimateChangeResponse r = costRiskService.estimateChangeCost(EstimateChangeRequest.builder()
                .kind("RESOURCE_CHANGE").target("api").cpuRequest("500m").build());

        // t3.medium $0.0416/hr -> CPU share $0.0104 per core-hour; 2 pods x 0.5 core x 730h = -$7.59
        assertThat(r.getDeltaMonthlyUsd()).isCloseTo(-7.59, within(0.01));
        assertThat(r.getPricingSource()).contains("td_instance_pricing ap-south-1 t3.medium");
        assertThat(r.getBaselineSource()).startsWith("CLUSTER_SNAPSHOT").contains("replicas").contains("cpuRequest");
    }

    @Test
    @DisplayName("estimate-change for NODE_REMOVAL saves one node's monthly price")
    void shouldEstimateNodeRemovalSavings() {
        stubPriceTable();

        EstimateChangeResponse r = costRiskService.estimateChangeCost(EstimateChangeRequest.builder()
                .kind("NODE_REMOVAL").target("ip-10-0-2-20.ec2.internal").build());

        assertThat(r.getDeltaMonthlyUsd()).isCloseTo(-30.37, within(0.01)); // 0.0416 x 730
        assertThat(r.getProposedMonthlyCostUsd()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("estimate-change labels made-up inputs when neither the request nor the snapshot has them")
    void shouldLabelAssumedDefaults() {
        when(podRepository.findByClusterName(CLUSTER)).thenReturn(Collections.emptyList());

        EstimateChangeResponse r = costRiskService.estimateChangeCost(EstimateChangeRequest.builder()
                .kind("RESOURCE_CHANGE").target("unknown-app").cpuRequest("300m").build());

        assertThat(r.getBaselineSource()).startsWith("ASSUMED_DEFAULTS").contains("replicas=2");
        assertThat(r.getPricingSource()).startsWith("DEFAULT_RATE");
    }
}
