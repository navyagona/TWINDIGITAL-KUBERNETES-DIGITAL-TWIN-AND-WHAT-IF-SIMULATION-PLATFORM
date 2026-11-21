package com.twindigital.costrisk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

public class CostRiskDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverprovisionedPod {
        private String podName;
        private String namespace;
        private String serviceName;
        private String nodeName;
        private String currentCpuRequest;       // e.g. "500m"
        private String p95CpuUsage;              // e.g. "45m"
        private String recommendedCpuRequest;    // e.g. "60m"
        private String currentMemoryRequest;    // e.g. "1024Mi"
        private String p95MemoryUsage;           // e.g. "180Mi"
        private String recommendedMemoryRequest; // e.g. "234Mi"
        private double cpuUtilizationRatio;      // e.g. 0.09 (9%)
        private double memoryUtilizationRatio;   // e.g. 0.17 (17%)
        private double cpuWastePercent;          // e.g. 91.0%
        private double memoryWastePercent;       // e.g. 82.5%
        private double estimatedMonthlyWasteUsd; // e.g. 14.20
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverprovisionedReport {
        private int totalPodsAnalyzed;
        private int totalOverprovisionedPods;
        private double potentialMonthlySavingsUsd;
        private double potentialAnnualSavingsUsd;
        @Builder.Default
        private List<OverprovisionedPod> overprovisionedPods = new ArrayList<>();
        private String evaluatedWindow;          // e.g. "7-day p95 PromQL"
        private Instant evaluatedAt;
        @Builder.Default
        private boolean dataAvailable = true;
        /** "PROMETHEUS" when real metrics were used; "SAMPLE" when Prometheus is unset or returned nothing. */
        @Builder.Default
        private String dataSource = "PROMETHEUS";
        /**
         * Illustrative suggestions computed from a fixed sample workload, present only when
         * dataSource is "SAMPLE". Kept apart from overprovisionedPods so sample numbers never
         * reach real totals, savings, or the twin_overprovisioned_pods gauge.
         */
        @Builder.Default
        private List<OverprovisionedPod> sampleSuggestions = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NamespaceCost {
        private String namespace;
        private double monthlyCostUsd;
        private double percentOfTotal;
        private int podCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceCost {
        private String serviceName;
        private String namespace;
        private double monthlyCostUsd;
        private double percentOfTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostSummaryResponse {
        private double totalMonthlyCostUsd;
        private double totalAnnualCostUsd;
        @Builder.Default
        private String currency = "USD";
        private String dataSource;               // "AWS_COST_EXPLORER" | "INSTANCE_PRICING_CATALOG"
        private double potentialMonthlySavingsUsd;
        private int overprovisionedPodCount;
        @Builder.Default
        private List<NamespaceCost> byNamespace = new ArrayList<>();
        @Builder.Default
        private List<ServiceCost> byService = new ArrayList<>();
        private Instant lastUpdated;
        @Builder.Default
        private boolean dataAvailable = true;
        @Builder.Default
        private double controlPlaneCostUsd = 73.00;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstimateChangeRequest {
        private String kind;                     // "RESOURCE_CHANGE", "SCALE", "NEW_DEPLOYMENT", "NODE_REMOVAL"
        private String target;                   // Deployment or Node name
        private String clusterName;              // optional; defaults to twindigital-cluster
        @Builder.Default
        private String namespace = "twindigital";
        private Integer replicas;
        private String cpuRequest;
        private String memRequest;
        private Integer currentReplicas;
        private String currentCpuRequest;
        private String currentMemRequest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstimateChangeResponse {
        private String target;
        private double baselineMonthlyCostUsd;
        private double proposedMonthlyCostUsd;
        private double deltaMonthlyUsd;
        private double deltaAnnualUsd;
        private String explanation;
        /** Where hourly rates came from, e.g. "td_instance_pricing ap-south-1 t3.medium" or "DEFAULT_RATE". */
        private String pricingSource;
        /** Where current replicas/requests came from: "REQUEST", "CLUSTER_SNAPSHOT", or "ASSUMED_DEFAULTS". */
        private String baselineSource;
    }
}
