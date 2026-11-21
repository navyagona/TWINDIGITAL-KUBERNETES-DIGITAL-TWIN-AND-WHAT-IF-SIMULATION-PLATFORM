package com.twindigital.simulation.check;

import com.twindigital.simulation.context.SimulationContext;
import com.twindigital.simulation.context.SimulationContext.BlastRadiusInfo;
import com.twindigital.simulation.entity.SimulationEntities.*;
import com.twindigital.simulation.model.SimulationModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationChecksUnitTest {

    private SchedulingFitCheck schedulingFitCheck;
    private LimitSanityCheck limitSanityCheck;
    private NodeRemovalImpactCheck nodeRemovalImpactCheck;
    private HpaImpactCheck hpaImpactCheck;
    private BlastRadiusCheck blastRadiusCheck;
    private ServerDryRunCheck serverDryRunCheck;

    @BeforeEach
    void setUp() {
        schedulingFitCheck = new SchedulingFitCheck();
        limitSanityCheck = new LimitSanityCheck();
        nodeRemovalImpactCheck = new NodeRemovalImpactCheck();
        hpaImpactCheck = new HpaImpactCheck();
        blastRadiusCheck = new BlastRadiusCheck();
        serverDryRunCheck = new ServerDryRunCheck();
    }

    @Test
    @DisplayName("SchedulingFit: bin-packing should detect when pods cannot fit due to CPU exhaustion")
    void testSchedulingFitCpuExhaustion() {
        ClusterNodeEntity node1 = ClusterNodeEntity.builder()
                .name("node-1")
                .allocatableCpu("1000m")
                .allocatableMemory("2Gi")
                .ready(true)
                .build();

        // 1 existing pod using 800m CPU
        ClusterPodEntity pod1 = ClusterPodEntity.builder()
                .name("p1")
                .nodeName("node-1")
                .cpuRequests("800m")
                .memoryRequests("500Mi")
                .build();

        // Proposed new deployment needing 500m CPU
        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.NEW_DEPLOYMENT)
                .target("new-app")
                .cpuRequest("500m")
                .memRequest("500Mi")
                .replicas(1)
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(node1))
                .pods(List.of(pod1))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = schedulingFitCheck.evaluate(ctx);

        assertThat(findings).isNotEmpty();
        assertThat(findings.get(0).getSeverity()).isEqualTo(FindingSeverity.CRITICAL);
        assertThat(findings.get(0).getExplanation()).contains("Bin-packing check failed");
        assertThat(findings.get(0).getExplanation()).contains("1 pod(s) would stay 'Pending'");
    }

    @Test
    @DisplayName("LimitSanity: should flag critical when limit < request")
    void testLimitSanityLimitLessThanRequest() {
        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.RESOURCE_CHANGE)
                .target("auth-service")
                .cpuRequest("500m")
                .cpuLimit("200m") // invalid!
                .memRequest("1Gi")
                .memLimit("512Mi") // invalid!
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = limitSanityCheck.evaluate(ctx);

        assertThat(findings).hasSize(2);
        assertThat(findings).allMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL);
        assertThat(findings.get(0).getExplanation()).contains("CPU limit");
        assertThat(findings.get(1).getExplanation()).contains("Memory limit");
    }

    @Test
    @DisplayName("LimitSanity: should flag when request > largest node allocatable")
    void testLimitSanityRequestExceedsLargestNode() {
        ClusterNodeEntity node1 = ClusterNodeEntity.builder()
                .name("node-1")
                .allocatableCpu("2000m")
                .allocatableMemory("4Gi")
                .ready(true)
                .build();

        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.NEW_DEPLOYMENT)
                .target("heavy-ml")
                .cpuRequest("4000m") // > 2000m
                .memRequest("8Gi")   // > 4Gi
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(node1))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = limitSanityCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL &&
                f.getExplanation().contains("exceeds the allocatable capacity of the largest worker node"));
    }

    @Test
    @DisplayName("NodeRemovalImpact: should flag critical when PDB has 0 disruptions allowed")
    void testNodeRemovalViolatesPdb() {
        ClusterNodeEntity node1 = ClusterNodeEntity.builder()
                .name("node-1")
                .allocatableCpu("2000m")
                .allocatableMemory("4Gi")
                .ready(true)
                .build();

        ClusterPodEntity authPod = ClusterPodEntity.builder()
                .name("auth-service-pod-1")
                .ownerName("auth-service")
                .nodeName("node-1")
                .cpuRequests("200m")
                .memoryRequests("256Mi")
                .build();

        ClusterPdbEntity authPdb = ClusterPdbEntity.builder()
                .name("auth-pdb")
                .minAvailable("1")
                .currentHealthy(1)
                .desiredHealthy(1)
                .disruptionsAllowed(0) // 0 disruptions allowed!
                .selector(Map.of("app", "auth-service"))
                .build();

        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.NODE_REMOVAL)
                .target("node-1")
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(node1))
                .pods(List.of(authPod))
                .pdbs(List.of(authPdb))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = nodeRemovalImpactCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL &&
                f.getExplanation().contains("violates PodDisruptionBudget 'auth-pdb'"));
    }

    @Test
    @DisplayName("HpaImpact: should flag high risk when maxReplicas capacity exceeds cluster allocatable")
    void testHpaMaxReplicasExceedsCapacity() {
        ClusterNodeEntity node = ClusterNodeEntity.builder()
                .name("node-1")
                .allocatableCpu("2000m")
                .allocatableMemory("4Gi")
                .ready(true)
                .build();

        ClusterHpaEntity hpa = ClusterHpaEntity.builder()
                .name("billing-hpa")
                .targetName("billing-service")
                .minReplicas(2)
                .maxReplicas(8) // at 8x 500m = 4000m CPU needed, but cluster only has 2000m
                .build();

        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.RESOURCE_CHANGE)
                .target("billing-service")
                .cpuRequest("500m")
                .memRequest("512Mi")
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(node))
                .hpas(List.of(hpa))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = hpaImpactCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.HIGH &&
                f.getExplanation().contains("HPA maxReplicas capacity shortfall"));
    }

    @Test
    @DisplayName("BlastRadius: should flag broad blast radius for core auth service")
    void testBlastRadiusForAuthService() {
        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.RESOURCE_CHANGE)
                .target("auth-service")
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .changeSet(cs)
                .blastRadiusResolver(target -> BlastRadiusInfo.builder()
                        .target(target)
                        .upstreamCallers(List.of("gateway", "billing-service", "simulation-service", "topology-service"))
                        .downstreamDependencies(List.of("postgres", "redis"))
                        .totalImpacted(6)
                        .riskScore("CRITICAL")
                        .build())
                .build();

        List<SimulationFinding> findings = blastRadiusCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.HIGH &&
                f.getExplanation().contains("Broad blast radius: Target service 'auth-service' has 4 upstream caller(s)"));
    }

    @Test
    @DisplayName("ServerDryRun: should flag RFC 1123 naming violations")
    void testServerDryRunInvalidName() {
        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.NEW_DEPLOYMENT)
                .target("INVALID_NAME_WITH_CAPS_AND_UNDERSCORES")
                .dryRunKubeApi(true)
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = serverDryRunCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL &&
                f.getExplanation().contains("invalid Kubernetes name"));
    }

    @Test
    @DisplayName("ServerDryRun: when no ApiClient is available, should return INFO finding with dry-run skipped")
    void testServerDryRunNoApiClient() {
        ChangeSet cs = ChangeSet.builder()
                .kind(ChangeKind.NEW_DEPLOYMENT)
                .target("billing-service")
                .dryRunKubeApi(true)
                .build();

        SimulationContext ctx = SimulationContext.builder()
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = serverDryRunCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.INFO &&
                f.getExplanation().contains("dry-run skipped") &&
                !f.getExplanation().contains("passed"));
    }

    private static ClusterNodeEntity readyNode(String name, String cpu, String mem) {
        return ClusterNodeEntity.builder().name(name).allocatableCpu(cpu).allocatableMemory(mem).ready(true)
                .labels(Map.of("kubernetes.io/hostname", name)).build();
    }

    private static ClusterPodEntity labeledPod(String name, String owner, String node, String cpu, String mem) {
        return ClusterPodEntity.builder().name(name).ownerKind("ReplicaSet").ownerName(owner).nodeName(node)
                .cpuRequests(cpu).memoryRequests(mem).labels(Map.of("app", owner)).build();
    }

    @Test
    @DisplayName("NodeRemovalImpact: labeled pods reschedule when remaining nodes have capacity (pod labels are not node selectors)")
    void testNodeRemovalLabeledPodsReschedule() {
        ChangeSet cs = ChangeSet.builder().kind(ChangeKind.NODE_REMOVAL).target("node-1").build();
        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(readyNode("node-1", "2000m", "4Gi"), readyNode("node-2", "2000m", "4Gi")))
                .pods(List.of(labeledPod("web-abc-1", "web", "node-1", "300m", "256Mi")))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = nodeRemovalImpactCheck.evaluate(ctx);

        assertThat(findings).noneMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL);
        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.INFO
                && f.getExplanation().contains("can be safely rescheduled"));
    }

    @Test
    @DisplayName("NodeRemovalImpact: more PDB-covered pods on the node than disruptionsAllowed is a MEDIUM warning")
    void testNodeRemovalPdbThrottlesDrain() {
        ClusterPdbEntity pdb = ClusterPdbEntity.builder().name("web-pdb").minAvailable("2")
                .currentHealthy(3).desiredHealthy(2).disruptionsAllowed(1)
                .selector(Map.of("app", "web")).build();
        ChangeSet cs = ChangeSet.builder().kind(ChangeKind.NODE_REMOVAL).target("node-1").build();
        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(readyNode("node-1", "2000m", "4Gi"), readyNode("node-2", "2000m", "4Gi")))
                .pods(List.of(
                        labeledPod("web-abc-1", "web", "node-1", "100m", "128Mi"),
                        labeledPod("web-abc-2", "web", "node-1", "100m", "128Mi"),
                        labeledPod("web-abc-3", "web", "node-2", "100m", "128Mi")))
                .pdbs(List.of(pdb))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = nodeRemovalImpactCheck.evaluate(ctx);

        assertThat(findings).noneMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL);
        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.MEDIUM
                && f.getExplanation().contains("allows only 1 disruption"));
    }

    @Test
    @DisplayName("SchedulingFit: a memory-only RESOURCE_CHANGE keeps the pods' existing CPU request")
    void testResourceChangeKeepsExistingCpuRequest() {
        // node-1 has 1930m; a 500m neighbour leaves 1430m, less than big-app's existing 1500m request
        ChangeSet cs = ChangeSet.builder().kind(ChangeKind.RESOURCE_CHANGE).target("big-app")
                .memRequest("256Mi").memLimit("512Mi").replicas(1).build();
        SimulationContext ctx = SimulationContext.builder()
                .nodes(List.of(readyNode("node-1", "1930m", "4Gi")))
                .pods(List.of(
                        labeledPod("big-app-abc-1", "big-app", "node-1", "1500m", "128Mi"),
                        labeledPod("other-abc-1", "other", "node-1", "500m", "128Mi")))
                .changeSet(cs)
                .build();

        List<SimulationFinding> findings = schedulingFitCheck.evaluate(ctx);

        assertThat(findings).anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL
                && f.getExplanation().contains("1500m"));
    }

    @Test
    @DisplayName("CheckStatus: CRITICAL -> FAIL, LOW..HIGH -> WARN, none or INFO -> PASS")
    void testCheckStatusMapping() {
        java.util.function.Function<FindingSeverity, SimulationFinding> f =
                sev -> SimulationFinding.builder().check("X").severity(sev).explanation(sev.name()).build();

        assertThat(CheckStatus.from(List.of())).isEqualTo(CheckStatus.PASS);
        assertThat(CheckStatus.from(List.of(f.apply(FindingSeverity.INFO)))).isEqualTo(CheckStatus.PASS);
        assertThat(CheckStatus.from(List.of(f.apply(FindingSeverity.LOW)))).isEqualTo(CheckStatus.WARN);
        assertThat(CheckStatus.from(List.of(f.apply(FindingSeverity.HIGH), f.apply(FindingSeverity.INFO)))).isEqualTo(CheckStatus.WARN);
        assertThat(CheckStatus.from(List.of(f.apply(FindingSeverity.MEDIUM), f.apply(FindingSeverity.CRITICAL)))).isEqualTo(CheckStatus.FAIL);
    }
}
