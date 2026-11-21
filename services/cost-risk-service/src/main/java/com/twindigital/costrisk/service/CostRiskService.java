package com.twindigital.costrisk.service;

import com.twindigital.costrisk.client.AwsCostExplorerClient;
import com.twindigital.costrisk.client.AwsCostExplorerClient.AwsCostData;
import com.twindigital.costrisk.client.PrometheusClient;
import com.twindigital.costrisk.client.PrometheusClient.UsageSnapshot;
import com.twindigital.costrisk.client.PrometheusClient.UsageWindow;
import com.twindigital.costrisk.entity.CostRiskEntities.*;
import com.twindigital.costrisk.model.CostRiskDTOs.*;
import com.twindigital.costrisk.repository.CostRiskRepositories.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostRiskService {

    private final PrometheusClient prometheusClient;
    private final AwsCostExplorerClient awsCostExplorerClient;
    private final ClusterNodeRepository nodeRepository;
    private final ClusterPodRepository podRepository;
    private final ClusterDeploymentRepository deploymentRepository;
    private final InstancePricingRepository pricingRepository;
    private final MeterRegistry meterRegistry;

    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    @Value("${cost.default-hourly-node-cost:0.0416}")
    private double defaultHourlyNodeCost;

    @Value("${cost.monthly-hours:730.0}")
    private double monthlyHours;

    @Value("${cost.eks-control-plane-hourly:0.10}")
    private double eksControlPlaneHourlyCost;

    private static final String DEFAULT_CLUSTER = "twindigital-cluster";

    // Micrometer Gauges: 2 nodes @ $0.0416 * 730h ($60.74) + EKS control plane ($73.00) = $133.74
    private final AtomicReference<Double> costEstimatedGauge = new AtomicReference<>(133.74);
    private final AtomicInteger overprovisionedPodsGauge = new AtomicInteger(0);

    @PostConstruct
    public void initMetrics() {
        Gauge.builder("twin_cost_estimated_usd", costEstimatedGauge, AtomicReference::get)
                .description("Estimated monthly cluster infrastructure cost in USD")
                .baseUnit("USD")
                .register(meterRegistry);

        Gauge.builder("twin_overprovisioned_pods", overprovisionedPodsGauge, AtomicInteger::get)
                .description("Count of detected over-provisioned Kubernetes pods")
                .baseUnit("pods")
                .register(meterRegistry);

        log.info("Registered Micrometer gauges: twin_cost_estimated_usd and twin_overprovisioned_pods");
    }

    // Fixed usage for the sample workload (the seed pods), used only to build sampleSuggestions.
    private static final Map<String, Double> SAMPLE_CPU_MILLIS = Map.of(
            "auth-service-7bc89d-abc12", 60.0,
            "billing-service-59bc6d-pl92x", 140.0);
    private static final Map<String, Long> SAMPLE_MEM_BYTES = Map.of(
            "auth-service-7bc89d-abc12", 300L * 1024L * 1024L,
            "billing-service-59bc6d-pl92x", 600L * 1024L * 1024L);

    private record Analysis(List<OverprovisionedPod> pods, double monthlySavingsUsd) {}

    /** Hourly rates derived from the price table: one node, and per vCPU / per GiB shares of it. */
    record Rates(double nodeHourly, double cpuCoreHourly, double memGibHourly, String source) {}

    /**
     * Identifies over-provisioned pods by comparing PromQL usage (7-day p95) with requests,
     * recommending right-sized resource values and potential cost savings.
     * Without Prometheus data, real fields stay empty (dataAvailable=false) and clearly labelled
     * sample suggestions are returned in sampleSuggestions with dataSource="SAMPLE".
     */
    public OverprovisionedReport getOverprovisionedReport(String cluster) {
        String clusterName = (cluster != null && !cluster.isBlank()) ? cluster : DEFAULT_CLUSTER;

        List<ClusterPodEntity> pods = podRepository.findByClusterName(clusterName);
        if (pods.isEmpty()) {
            pods = createDefaultSeedPods(clusterName);
        }

        UsageSnapshot usage = prometheusClient.queryUsage();
        Rates rates = resolveRates();

        if (!usage.hasData()) {
            String reason = usage.window() == UsageWindow.NOT_CONFIGURED
                    ? "Prometheus not configured (set PROMETHEUS_URL)"
                    : "Prometheus returned no usage metrics";
            log.info("{} for cluster '{}' - returning labelled sample suggestions", reason, clusterName);
            overprovisionedPodsGauge.set(0);
            Analysis sample = analyze(createDefaultSeedPods(clusterName), SAMPLE_CPU_MILLIS, SAMPLE_MEM_BYTES,
                    "SAMPLE usage data", rates);
            return OverprovisionedReport.builder()
                    .totalPodsAnalyzed(pods.size())
                    .totalOverprovisionedPods(0)
                    .potentialMonthlySavingsUsd(0.0)
                    .potentialAnnualSavingsUsd(0.0)
                    .overprovisionedPods(Collections.emptyList())
                    .dataAvailable(false)
                    .dataSource("SAMPLE")
                    .sampleSuggestions(sample.pods())
                    .evaluatedWindow("SAMPLE DATA: " + reason + ". sampleSuggestions are illustrative, not measured.")
                    .evaluatedAt(Instant.now())
                    .build();
        }

        boolean p95 = usage.window() == UsageWindow.P95_7D;
        Analysis real = analyze(pods, usage.cpuMillis(), usage.memoryBytes(),
                p95 ? "7-day p95" : "current instant usage", rates);

        overprovisionedPodsGauge.set(real.pods().size());

        return OverprovisionedReport.builder()
                .totalPodsAnalyzed(pods.size())
                .totalOverprovisionedPods(real.pods().size())
                .potentialMonthlySavingsUsd(Math.round(real.monthlySavingsUsd() * 100.0) / 100.0)
                .potentialAnnualSavingsUsd(Math.round(real.monthlySavingsUsd() * 12.0 * 100.0) / 100.0)
                .overprovisionedPods(real.pods())
                .dataAvailable(true)
                .dataSource("PROMETHEUS")
                .evaluatedWindow(p95 ? "7-day p95 PromQL" : "Instant PromQL (7-day p95 query returned no data)")
                .evaluatedAt(Instant.now())
                .build();
    }

    private Analysis analyze(List<ClusterPodEntity> pods, Map<String, Double> cpuUsageMap,
                             Map<String, Long> memUsageMap, String windowLabel, Rates rates) {
        List<OverprovisionedPod> overprovisionedList = new ArrayList<>();
        double totalMonthlySavings = 0.0;

        for (ClusterPodEntity pod : pods) {
            long reqCpuMillis = parseCpuMillis(pod.getCpuRequests());
            long reqMemBytes = parseMemoryBytes(pod.getMemoryRequests());

            if (reqCpuMillis <= 0 || reqMemBytes <= 0) continue;

            Double actualCpuMillis = cpuUsageMap.get(pod.getName());
            Long actualMemBytes = memUsageMap.get(pod.getName());

            // No usage data for this specific pod: skip it rather than guess
            if (actualCpuMillis == null && actualMemBytes == null) {
                continue;
            }

            double cpuUtilRatio = (actualCpuMillis != null) ? (actualCpuMillis / (double) reqCpuMillis) : 1.0;
            double memUtilRatio = (actualMemBytes != null) ? ((double) actualMemBytes / (double) reqMemBytes) : 1.0;

            // Flag as over-provisioned if CPU util < 35% OR Memory util < 40%
            boolean isCpuOver = (actualCpuMillis != null) && (cpuUtilRatio < 0.35);
            boolean isMemOver = (actualMemBytes != null) && (memUtilRatio < 0.40);

            if (isCpuOver || isMemOver) {
                double effectiveCpu = (actualCpuMillis != null) ? actualCpuMillis : reqCpuMillis;
                long effectiveMem = (actualMemBytes != null) ? actualMemBytes : reqMemBytes;

                // Right-size with safety headroom: +25% on CPU (min 50m), +30% on Memory (min 64Mi)
                long recCpuMillis = Math.max(50L, (long) Math.ceil(effectiveCpu * 1.25));
                long recMemBytes = Math.max(64L * 1024L * 1024L, (long) Math.ceil(effectiveMem * 1.30));

                double cpuWastePct = (actualCpuMillis != null) ? Math.max(0.0, (1.0 - cpuUtilRatio) * 100.0) : 0.0;
                double memWastePct = (actualMemBytes != null) ? Math.max(0.0, (1.0 - memUtilRatio) * 100.0) : 0.0;

                // Dollar waste priced with the same price-table rates as estimate-change
                double cpuWasteHourly = (actualCpuMillis != null && reqCpuMillis > recCpuMillis)
                        ? ((reqCpuMillis - recCpuMillis) / 1000.0) * rates.cpuCoreHourly() : 0.0;
                double memWasteHourly = (actualMemBytes != null && reqMemBytes > recMemBytes)
                        ? ((reqMemBytes - recMemBytes) / (1024.0 * 1024.0 * 1024.0)) * rates.memGibHourly() : 0.0;
                double monthlyWasteUsd = (cpuWasteHourly + memWasteHourly) * monthlyHours;

                totalMonthlySavings += monthlyWasteUsd;

                String serviceName = pod.getOwnerName() != null ? pod.getOwnerName() :
                        (pod.getLabels() != null ? pod.getLabels().getOrDefault("app", pod.getName()) : pod.getName());

                overprovisionedList.add(OverprovisionedPod.builder()
                        .podName(pod.getName())
                        .namespace(pod.getNamespace())
                        .serviceName(serviceName)
                        .nodeName(pod.getNodeName())
                        .currentCpuRequest(formatCpu(reqCpuMillis))
                        .p95CpuUsage(actualCpuMillis != null ? formatCpu((long) actualCpuMillis.doubleValue()) : "N/A")
                        .recommendedCpuRequest(formatCpu(recCpuMillis))
                        .currentMemoryRequest(formatMemory(reqMemBytes))
                        .p95MemoryUsage(actualMemBytes != null ? formatMemory(actualMemBytes) : "N/A")
                        .recommendedMemoryRequest(formatMemory(recMemBytes))
                        .cpuUtilizationRatio(Math.round(cpuUtilRatio * 100.0) / 100.0)
                        .memoryUtilizationRatio(Math.round(memUtilRatio * 100.0) / 100.0)
                        .cpuWastePercent(Math.round(cpuWastePct * 10.0) / 10.0)
                        .memoryWastePercent(Math.round(memWastePct * 10.0) / 10.0)
                        .estimatedMonthlyWasteUsd(Math.round(monthlyWasteUsd * 100.0) / 100.0)
                        .explanation(String.format(Locale.ROOT,
                                "Pod uses %.0f%% of requested CPU and %.0f%% of requested memory (%s). Right-sizing saves ~$%.2f/month.",
                                cpuUtilRatio * 100.0, memUtilRatio * 100.0, windowLabel, monthlyWasteUsd))
                        .build());
            }
        }
        return new Analysis(overprovisionedList, totalMonthlySavings);
    }

    /**
     * Returns cluster monthly cost summary, attributing costs by namespace and service,
     * integrating with AWS Cost Explorer with fallback to instance pricing catalog.
     * Note: Cost Explorer is account-wide unless filtered by a cluster tag.
     * Also includes Amazon EKS control plane cost ($0.10/hr = ~$73.00/month).
     */
    public CostSummaryResponse getCostSummary(String cluster) {
        String clusterName = (cluster != null && !cluster.isBlank()) ? cluster : DEFAULT_CLUSTER;

        // 1. Fetch AWS Cost Explorer data (or fallback)
        Optional<AwsCostData> awsCostOpt = awsCostExplorerClient.getMonthlyCost(clusterName);

        double totalMonthlyCost;
        String dataSource;

        List<ClusterNodeEntity> nodes = nodeRepository.findByClusterName(clusterName);
        int nodeCount = Math.max(2, nodes.size());

        // Amazon EKS control plane is billed at $0.10 per cluster-hour
        double eksControlPlaneMonthlyCost = eksControlPlaneHourlyCost * monthlyHours;

        if (awsCostOpt.isPresent() && awsCostOpt.get().getTotalMonthlyCostUsd() > 0.0) {
            totalMonthlyCost = awsCostOpt.get().getTotalMonthlyCostUsd();
            // If AWS Cost Explorer is filtered by EC2 cluster tags, EKS control plane might not carry the tag.
            // Check if EKS service cost is included; if not, add control plane cost.
            boolean includesEks = awsCostOpt.get().getCostByService().keySet().stream()
                    .anyMatch(k -> k.toLowerCase().contains("kubernetes") || k.toLowerCase().contains("eks"));
            if (!includesEks) {
                totalMonthlyCost += eksControlPlaneMonthlyCost;
            }
            dataSource = "AWS_COST_EXPLORER";
        } else {
            // Compute from instance pricing catalog (e.g. t3.medium in ap-south-1 @ $0.0416/hr)
            // plus Amazon EKS control plane ($0.10/hr * 730h = $73.00/mo)
            double hourlyRate = pricingRepository.findByRegionAndInstanceType(awsRegion, "t3.medium")
                    .map(InstancePricingEntity::getOnDemandPrice)
                    .orElse(defaultHourlyNodeCost);

            double nodeCost = nodeCount * hourlyRate * monthlyHours;
            totalMonthlyCost = nodeCost + eksControlPlaneMonthlyCost;
            dataSource = "INSTANCE_PRICING_CATALOG";
        }

        // 2. Fetch overprovisioned report
        OverprovisionedReport overReport = getOverprovisionedReport(clusterName);

        // 3. Attribute costs by Namespace and Service based on pod resource share
        List<ClusterPodEntity> pods = podRepository.findByClusterName(clusterName);
        if (pods.isEmpty()) pods = createDefaultSeedPods(clusterName);

        Map<String, Integer> nsPodCounts = new HashMap<>();
        Map<String, Integer> svcPodCounts = new HashMap<>();
        Map<String, String> svcNamespaceMap = new HashMap<>();

        for (ClusterPodEntity p : pods) {
            nsPodCounts.put(p.getNamespace(), nsPodCounts.getOrDefault(p.getNamespace(), 0) + 1);
            String svc = p.getOwnerName() != null ? p.getOwnerName() : p.getName();
            svcPodCounts.put(svc, svcPodCounts.getOrDefault(svc, 0) + 1);
            svcNamespaceMap.put(svc, p.getNamespace());
        }

        int totalPods = Math.max(1, pods.size());
        List<NamespaceCost> byNamespace = new ArrayList<>();
        for (Map.Entry<String, Integer> e : nsPodCounts.entrySet()) {
            double share = (double) e.getValue() / (double) totalPods;
            double cost = totalMonthlyCost * share;
            byNamespace.add(NamespaceCost.builder()
                    .namespace(e.getKey())
                    .monthlyCostUsd(Math.round(cost * 100.0) / 100.0)
                    .percentOfTotal(Math.round(share * 1000.0) / 10.0)
                    .podCount(e.getValue())
                    .build());
        }

        List<ServiceCost> byService = new ArrayList<>();
        for (Map.Entry<String, Integer> e : svcPodCounts.entrySet()) {
            double share = (double) e.getValue() / (double) totalPods;
            double cost = totalMonthlyCost * share;
            byService.add(ServiceCost.builder()
                    .serviceName(e.getKey())
                    .namespace(svcNamespaceMap.getOrDefault(e.getKey(), "twindigital"))
                    .monthlyCostUsd(Math.round(cost * 100.0) / 100.0)
                    .percentOfTotal(Math.round(share * 1000.0) / 10.0)
                    .build());
        }

        // Update Micrometer gauge
        costEstimatedGauge.set(Math.round(totalMonthlyCost * 100.0) / 100.0);

        return CostSummaryResponse.builder()
                .totalMonthlyCostUsd(Math.round(totalMonthlyCost * 100.0) / 100.0)
                .totalAnnualCostUsd(Math.round(totalMonthlyCost * 12.0 * 100.0) / 100.0)
                .currency("USD")
                .dataSource(dataSource)
                .potentialMonthlySavingsUsd(overReport.getPotentialMonthlySavingsUsd())
                .overprovisionedPodCount(overReport.getTotalOverprovisionedPods())
                .dataAvailable(overReport.isDataAvailable())
                .controlPlaneCostUsd(Math.round(eksControlPlaneMonthlyCost * 100.0) / 100.0)
                .byNamespace(byNamespace)
                .byService(byService)
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Estimates the monthly cost delta of a proposed change set (scale, resize, new workload, node removal),
     * priced from the td_instance_pricing table. Current replicas/requests not given in the request are
     * taken from the cluster snapshot; the response says where each input came from.
     */
    public EstimateChangeResponse estimateChangeCost(EstimateChangeRequest request) {
        String target = request.getTarget() != null ? request.getTarget() : "workload";
        String kind = request.getKind() != null ? request.getKind().toUpperCase(Locale.ROOT) : "CHANGE";
        String clusterName = (request.getClusterName() != null && !request.getClusterName().isBlank())
                ? request.getClusterName() : DEFAULT_CLUSTER;
        String namespace = (request.getNamespace() != null && !request.getNamespace().isBlank())
                ? request.getNamespace() : "twindigital";
        Rates rates = resolveRates();

        if ("NODE_REMOVAL".equals(kind)) {
            double nodeMonthly = rates.nodeHourly() * monthlyHours;
            return EstimateChangeResponse.builder()
                    .target(target)
                    .baselineMonthlyCostUsd(round2(nodeMonthly))
                    .proposedMonthlyCostUsd(0.0)
                    .deltaMonthlyUsd(round2(-nodeMonthly))
                    .deltaAnnualUsd(round2(-nodeMonthly * 12.0))
                    .explanation(String.format(Locale.ROOT,
                            "Removing node '%s' saves its instance cost: -$%.2f/month (-$%.2f/year).",
                            target, nodeMonthly, nodeMonthly * 12.0))
                    .pricingSource(rates.source())
                    .baselineSource("PRICE_TABLE_NODE")
                    .build();
        }

        // ── Current (baseline) replicas and per-pod requests ──
        List<String> fromSnapshot = new ArrayList<>();
        List<String> assumed = new ArrayList<>();
        boolean sampleSnapshot = false;

        List<ClusterPodEntity> clusterPods = podRepository.findByClusterName(clusterName);
        if (clusterPods.isEmpty()) {
            clusterPods = createDefaultSeedPods(clusterName);
            sampleSnapshot = true;
        }
        List<ClusterPodEntity> targetPods = clusterPods.stream()
                .filter(p -> target.equalsIgnoreCase(p.getOwnerName()) || p.getName().startsWith(target + "-"))
                .toList();
        Optional<ClusterPodEntity> samplePod = targetPods.stream().findFirst();

        Integer currentReplicas = request.getCurrentReplicas();
        if ("NEW_DEPLOYMENT".equals(kind)) {
            currentReplicas = 0;
        } else if (currentReplicas == null) {
            Optional<ClusterDeploymentEntity> dep =
                    deploymentRepository.findByClusterNameAndNamespaceAndName(clusterName, namespace, target);
            if (dep.isPresent()) {
                currentReplicas = dep.get().getReplicas();
                fromSnapshot.add("replicas");
            } else if (!targetPods.isEmpty()) {
                currentReplicas = targetPods.size();
                fromSnapshot.add("replicas");
            } else {
                currentReplicas = 2;
                assumed.add("replicas=2");
            }
        }

        // A SCALE changes replica count only, so current per-pod requests equal the proposed ones when given
        boolean scale = "SCALE".equals(kind);
        long currentCpu = parseCpuMillis(request.getCurrentCpuRequest());
        if (currentCpu == 0L && scale) currentCpu = parseCpuMillis(request.getCpuRequest());
        if (currentCpu == 0L && samplePod.isPresent()) {
            currentCpu = parseCpuMillis(samplePod.get().getCpuRequests());
            if (currentCpu > 0L) fromSnapshot.add("cpuRequest");
        }
        if (currentCpu == 0L) { currentCpu = 200L; assumed.add("cpuRequest=200m"); }

        long currentMem = parseMemoryBytes(request.getCurrentMemRequest());
        if (currentMem == 0L && scale) currentMem = parseMemoryBytes(request.getMemRequest());
        if (currentMem == 0L && samplePod.isPresent()) {
            currentMem = parseMemoryBytes(samplePod.get().getMemoryRequests());
            if (currentMem > 0L) fromSnapshot.add("memRequest");
        }
        if (currentMem == 0L) { currentMem = 256L * 1024L * 1024L; assumed.add("memRequest=256Mi"); }

        // ── Proposed values default to current ──
        int proposedReplicas = request.getReplicas() != null ? request.getReplicas() : Math.max(1, currentReplicas);
        long proposedCpu = parseCpuMillis(request.getCpuRequest());
        if (proposedCpu == 0L) proposedCpu = currentCpu;
        long proposedMem = parseMemoryBytes(request.getMemRequest());
        if (proposedMem == 0L) proposedMem = currentMem;

        double baselineMonthly = currentReplicas * podHourly(currentCpu, currentMem, rates) * monthlyHours;
        double proposedMonthly = proposedReplicas * podHourly(proposedCpu, proposedMem, rates) * monthlyHours;
        double deltaMonthly = proposedMonthly - baselineMonthly;

        String baselineSource;
        if (!assumed.isEmpty()) {
            baselineSource = "ASSUMED_DEFAULTS (" + String.join(", ", assumed) + ")";
        } else if (!fromSnapshot.isEmpty()) {
            baselineSource = (sampleSnapshot ? "SAMPLE_SNAPSHOT" : "CLUSTER_SNAPSHOT")
                    + " (" + String.join(", ", fromSnapshot) + ")";
        } else {
            baselineSource = "NEW_DEPLOYMENT".equals(kind) ? "NEW_WORKLOAD" : "REQUEST";
        }

        String explanation = String.format(Locale.ROOT,
                "Proposed change for '%s' (%s: %d -> %d replicas, %s -> %s CPU, %s -> %s RAM per pod) changes cost by %s$%.2f/month (%s$%.2f/year).",
                target, kind, currentReplicas, proposedReplicas,
                formatCpu(currentCpu), formatCpu(proposedCpu), formatMemory(currentMem), formatMemory(proposedMem),
                deltaMonthly >= 0 ? "+" : "-", Math.abs(deltaMonthly),
                deltaMonthly >= 0 ? "+" : "-", Math.abs(deltaMonthly * 12.0));

        return EstimateChangeResponse.builder()
                .target(target)
                .baselineMonthlyCostUsd(round2(baselineMonthly))
                .proposedMonthlyCostUsd(round2(proposedMonthly))
                .deltaMonthlyUsd(round2(deltaMonthly))
                .deltaAnnualUsd(round2(deltaMonthly * 12.0))
                .explanation(explanation)
                .pricingSource(rates.source())
                .baselineSource(baselineSource)
                .build();
    }

    /**
     * Rates from the region's t3.medium row (the cluster's node type): the node price is split 50/50
     * between CPU and memory, then divided by vCPUs and GiB. Falls back to cost.default-hourly-node-cost
     * on a 2 vCPU / 4 GiB shape when the row is missing.
     */
    Rates resolveRates() {
        Optional<InstancePricingEntity> row = pricingRepository.findByRegionAndInstanceType(awsRegion, "t3.medium");
        double price = row.map(InstancePricingEntity::getOnDemandPrice).orElse(defaultHourlyNodeCost);
        int vcpu = row.map(InstancePricingEntity::getVcpu).filter(v -> v > 0).orElse(2);
        double memGib = row.map(InstancePricingEntity::getMemoryGib).filter(m -> m > 0).orElse(4.0);
        String source = row.isPresent()
                ? String.format(Locale.ROOT, "td_instance_pricing %s t3.medium $%.4f/hr", awsRegion, price)
                : String.format(Locale.ROOT, "DEFAULT_RATE cost.default-hourly-node-cost $%.4f/hr (t3.medium shape)", price);
        return new Rates(price, price * 0.5 / vcpu, price * 0.5 / memGib, source);
    }

    private static double podHourly(long cpuMillis, long memBytes, Rates rates) {
        return (cpuMillis / 1000.0) * rates.cpuCoreHourly()
                + (memBytes / (1024.0 * 1024.0 * 1024.0)) * rates.memGibHourly();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private long parseCpuMillis(String cpu) {
        if (cpu == null || cpu.isBlank()) return 0L;
        String clean = cpu.trim().toLowerCase();
        try {
            if (clean.endsWith("m")) {
                return Long.parseLong(clean.substring(0, clean.length() - 1));
            }
            return (long) (Double.parseDouble(clean) * 1000.0);
        } catch (Exception e) {
            return 0L;
        }
    }

    private long parseMemoryBytes(String mem) {
        if (mem == null || mem.isBlank()) return 0L;
        String clean = mem.trim();
        try {
            if (clean.endsWith("Mi")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L * 1024L);
            } else if (clean.endsWith("Gi")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L * 1024L * 1024L);
            } else if (clean.endsWith("Ki")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 2)) * 1024L);
            }
            return (long) Double.parseDouble(clean);
        } catch (Exception e) {
            return 0L;
        }
    }

    private String formatCpu(long millis) {
        return millis + "m";
    }

    private String formatMemory(long bytes) {
        double mi = bytes / (1024.0 * 1024.0);
        return String.format(Locale.ROOT, "%.0fMi", mi);
    }

    private List<ClusterPodEntity> createDefaultSeedPods(String cluster) {
        return List.of(
                ClusterPodEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("auth-service-7bc89d-abc12")
                        .ownerName("auth-service")
                        .nodeName("ip-10-0-1-10.ec2.internal")
                        .cpuRequests("500m")
                        .memoryRequests("1024Mi")
                        .build(),
                ClusterPodEntity.builder()
                        .clusterName(cluster)
                        .namespace("twindigital")
                        .name("billing-service-59bc6d-pl92x")
                        .ownerName("billing-service")
                        .nodeName("ip-10-0-2-20.ec2.internal")
                        .cpuRequests("1000m")
                        .memoryRequests("2048Mi")
                        .build()
        );
    }
}
