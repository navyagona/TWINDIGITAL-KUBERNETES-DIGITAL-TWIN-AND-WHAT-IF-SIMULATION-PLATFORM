package com.twindigital.clustersync.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KubernetesClientConfig {

    static final String SERVICE_ACCOUNT_TOKEN = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    private final ClusterSyncProperties properties;

    @Bean
    public ClusterConnection clusterConnection() {
        return resolve();
    }

    @Bean
    public ApiClient apiClient(ClusterConnection clusterConnection) {
        return clusterConnection.apiClient();
    }

    /**
     * Tries each credential source in order. Never throws: when nothing usable is found the
     * service still starts and reports NO_CLUSTER instead of syncing.
     */
    ClusterConnection resolve() {
        List<String> problems = new ArrayList<>();

        // 1. Base64-encoded kubeconfig (Docker Compose / CI)
        String base64Config = firstNonBlank(properties.getKubeconfigBase64(), System.getenv("KUBECONFIG_BASE64"));
        if (base64Config != null) {
            try {
                String yaml = new String(Base64.getDecoder().decode(base64Config.trim()), StandardCharsets.UTF_8);
                KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new StringReader(yaml));
                log.info("Initializing Kubernetes ApiClient from KUBECONFIG_BASE64");
                return ClusterConnection.of(ClientBuilder.kubeconfig(kubeConfig).build(), "KUBECONFIG_BASE64");
            } catch (Exception e) {
                log.warn("Failed to load kubeconfig from KUBECONFIG_BASE64: {}", e.getMessage());
                problems.add("KUBECONFIG_BASE64 is set but could not be loaded (" + e.getMessage() + ")");
            }
        }

        // 2. Explicit kubeconfig file path
        String configPath = firstNonBlank(properties.getKubeconfigPath(), System.getenv("KUBECONFIG"));
        if (configPath != null) {
            ClusterConnection fromFile = fromFile(new File(configPath), "KUBECONFIG file " + configPath, problems);
            if (fromFile != null) return fromFile;
        }

        // 3. In-cluster ServiceAccount (EKS / IRSA)
        if (new File(SERVICE_ACCOUNT_TOKEN).exists()) {
            try {
                log.info("Initializing Kubernetes ApiClient using in-cluster ServiceAccount token");
                return ClusterConnection.of(ClientBuilder.cluster().build(), "in-cluster ServiceAccount");
            } catch (Exception e) {
                log.warn("Failed to load in-cluster Kubernetes config: {}", e.getMessage());
                problems.add("in-cluster ServiceAccount config failed (" + e.getMessage() + ")");
            }
        }

        // 4. ~/.kube/config (local workstation)
        File defaultKubeConfig = new File(System.getProperty("user.home"), ".kube/config");
        if (defaultKubeConfig.exists()) {
            ClusterConnection fromFile = fromFile(defaultKubeConfig, "~/.kube/config", problems);
            if (fromFile != null) return fromFile;
        }

        String reason = problems.isEmpty()
                ? "no kubeconfig or in-cluster credentials found"
                : String.join("; ", problems);
        log.warn("No Kubernetes cluster configured ({}). Starting in NO_CLUSTER mode; set KUBECONFIG_BASE64 to enable sync.", reason);
        return ClusterConnection.none(reason);
    }

    private ClusterConnection fromFile(File file, String label, List<String> problems) {
        if (!file.canRead()) {
            problems.add(label + " is not readable");
            return null;
        }
        try (FileReader reader = new FileReader(file)) {
            log.info("Initializing Kubernetes ApiClient from {}", label);
            return ClusterConnection.of(ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(reader)).build(), label);
        } catch (Exception e) {
            log.warn("Failed to load {}: {}", label, e.getMessage());
            problems.add(label + " could not be loaded (" + e.getMessage() + ")");
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
