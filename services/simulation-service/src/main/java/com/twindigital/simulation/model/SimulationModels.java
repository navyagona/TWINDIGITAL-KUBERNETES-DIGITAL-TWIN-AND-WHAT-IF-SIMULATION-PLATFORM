package com.twindigital.simulation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

public class SimulationModels {

    public enum ChangeKind {
        RESOURCE_CHANGE,
        SCALE,
        NODE_REMOVAL,
        NEW_DEPLOYMENT
    }

    public enum FindingSeverity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /** Per-check verdict: FAIL = a CRITICAL finding, WARN = LOW..HIGH findings, PASS = none or INFO only. */
    public enum CheckStatus {
        PASS,
        WARN,
        FAIL;

        public static CheckStatus from(List<SimulationFinding> findings) {
            boolean critical = findings.stream().anyMatch(f -> f.getSeverity() == FindingSeverity.CRITICAL);
            if (critical) return FAIL;
            boolean warn = findings.stream().anyMatch(f -> f.getSeverity() != FindingSeverity.INFO);
            return warn ? WARN : PASS;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckResult {
        private String name;
        private CheckStatus status;
        private FindingSeverity highestSeverity; // null when the check produced no findings
        private int findingCount;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeSet {
        private ChangeKind kind;
        private String target;          // Deployment, Node, or Service name
        @Builder.Default
        private String namespace = "twindigital";
        private String cpuRequest;      // e.g. "200m", "1", "1.5"
        private String memRequest;      // e.g. "256Mi", "1Gi"
        private String cpuLimit;        // e.g. "500m", "2"
        private String memLimit;        // e.g. "512Mi", "2Gi"
        private Integer replicas;       // Desired replica count
        @Builder.Default
        private Map<String, String> nodeSelectors = new HashMap<>();
        @Builder.Default
        private List<String> tolerations = new ArrayList<>();
        @Builder.Default
        private Map<String, String> labels = new HashMap<>();
        @Builder.Default
        private Boolean dryRunKubeApi = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationRequest {
        @Builder.Default
        private String clusterName = "twindigital-cluster";
        @Builder.Default
        private String user = "anonymous";
        private ChangeSet changeSet;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationFinding {
        private String check;
        private FindingSeverity severity;
        private String explanation;
        private String suggestedFix;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterCapacitySummary {
        private int totalNodes;
        private int readyNodes;
        private long totalCpuAllocatableMillis;
        private long totalCpuRequestedMillis;
        private long availableCpuMillis;
        private long totalMemAllocatableBytes;
        private long totalMemRequestedBytes;
        private long availableMemBytes;
        private int totalPods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationResult {
        private String simulationId;
        private RiskLevel riskLevel;
        private int score;
        @Builder.Default
        private List<CheckResult> checks = new ArrayList<>();
        @Builder.Default
        private List<SimulationFinding> findings = new ArrayList<>();
        private String predictedOutcome;
        private ClusterCapacitySummary capacitySummary;
        private Double costDelta;
        private boolean seedSnapshotUsed;
        private Instant executedAt;
    }
}

