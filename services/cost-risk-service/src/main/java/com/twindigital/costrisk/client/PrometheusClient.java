package com.twindigital.costrisk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class PrometheusClient {

    public static final String CPU_QUERY =
            "quantile_over_time(0.95, rate(container_cpu_usage_seconds_total{container!=\"\",container!=\"POD\"}[5m])[7d:5m])";
    public static final String CPU_FALLBACK_QUERY =
            "rate(container_cpu_usage_seconds_total{container!=\"\",container!=\"POD\"}[5m])";
    public static final String MEMORY_QUERY =
            "quantile_over_time(0.95, container_memory_working_set_bytes{container!=\"\",container!=\"POD\"}[7d])";
    public static final String MEMORY_FALLBACK_QUERY =
            "container_memory_working_set_bytes{container!=\"\",container!=\"POD\"}";

    /** Which query produced the data in a {@link UsageSnapshot}. */
    public enum UsageWindow {
        /** PROMETHEUS_URL is unset; no query was attempted. */
        NOT_CONFIGURED,
        /** Queries ran but returned nothing (unreachable, no cAdvisor metrics, or an error). */
        NO_DATA,
        /** 7-day p95 via quantile_over_time. */
        P95_7D,
        /** The 7-day query returned nothing, so at least one metric fell back to the current instant value. */
        INSTANT_FALLBACK
    }

    /** Per-pod usage: CPU in millicores, memory in bytes, plus the window that produced it. */
    public record UsageSnapshot(Map<String, Double> cpuMillis, Map<String, Long> memoryBytes, UsageWindow window) {
        public boolean hasData() {
            return !cpuMillis.isEmpty() || !memoryBytes.isEmpty();
        }
    }

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final boolean configured;

    @Autowired
    public PrometheusClient(
            @Value("${services.prometheus.url:}") String prometheusUrl,
            ObjectMapper objectMapper) {
        this(prometheusUrl == null || prometheusUrl.isBlank()
                        ? null : WebClient.builder().baseUrl(prometheusUrl.trim()).build(),
                objectMapper);
    }

    /** A null webClient means Prometheus is not configured. */
    public PrometheusClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.configured = webClient != null;
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Fetches per-pod CPU and memory usage, preferring the 7-day p95 and recording when a metric had
     * to fall back to an instant value, so callers can label the result honestly.
     */
    public UsageSnapshot queryUsage() {
        if (!configured) {
            return new UsageSnapshot(Map.of(), Map.of(), UsageWindow.NOT_CONFIGURED);
        }
        boolean fellBack = false;

        Map<String, Double> cpuCores = executeVectorQuery(CPU_QUERY);
        if (cpuCores.isEmpty()) {
            cpuCores = executeVectorQuery(CPU_FALLBACK_QUERY);
            fellBack |= !cpuCores.isEmpty();
        }
        Map<String, Double> memRaw = executeVectorQuery(MEMORY_QUERY);
        if (memRaw.isEmpty()) {
            memRaw = executeVectorQuery(MEMORY_FALLBACK_QUERY);
            fellBack |= !memRaw.isEmpty();
        }

        Map<String, Double> cpuMillis = new HashMap<>();
        cpuCores.forEach((pod, cores) -> cpuMillis.put(pod, cores * 1000.0));
        Map<String, Long> memBytes = new HashMap<>();
        memRaw.forEach((pod, bytes) -> memBytes.put(pod, bytes.longValue()));

        UsageWindow window;
        if (cpuMillis.isEmpty() && memBytes.isEmpty()) window = UsageWindow.NO_DATA;
        else window = fellBack ? UsageWindow.INSTANT_FALLBACK : UsageWindow.P95_7D;
        return new UsageSnapshot(cpuMillis, memBytes, window);
    }

    private Map<String, Double> executeVectorQuery(String promQl) {
        if (!configured) {
            return Collections.emptyMap();
        }
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/query")
                            .queryParam("query", promQl)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(4))
                    .block();

            return parsePrometheusResponse(response);
        } catch (Exception e) {
            log.debug("Prometheus query '{}' unavailable: {}", promQl, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Parses Prometheus vector JSON response and aggregates values per pod across containers.
     */
    public Map<String, Double> parsePrometheusResponse(String response) {
        Map<String, Double> metricMap = new HashMap<>();
        if (response == null || response.isBlank()) {
            return metricMap;
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            if ("success".equalsIgnoreCase(root.path("status").asText())) {
                JsonNode results = root.path("data").path("result");
                if (results.isArray()) {
                    for (JsonNode item : results) {
                        String pod = item.path("metric").path("pod").asText();
                        if (pod == null || pod.isBlank()) {
                            pod = item.path("metric").path("pod_name").asText();
                        }
                        JsonNode valueNode = item.path("value");
                        if (!pod.isBlank() && valueNode.isArray() && valueNode.size() >= 2) {
                            try {
                                double val = Double.parseDouble(valueNode.get(1).asText());
                                if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                                    // Sum per pod across containers
                                    metricMap.merge(pod, val, Double::sum);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse Prometheus response: {}", e.getMessage());
        }
        return metricMap;
    }
}
