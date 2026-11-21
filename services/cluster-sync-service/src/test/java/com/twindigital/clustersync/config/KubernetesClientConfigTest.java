package com.twindigital.clustersync.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KubernetesClientConfigTest {

    private static final String KUBECONFIG_YAML = """
            apiVersion: v1
            kind: Config
            clusters:
              - name: test
                cluster:
                  server: https://127.0.0.1:6443
            users:
              - name: test
                user:
                  token: dummy-token
            contexts:
              - name: test
                context:
                  cluster: test
                  user: test
            current-context: test
            """;

    @TempDir
    Path fakeHome;

    private String originalHome;
    private ClusterSyncProperties properties;

    @BeforeEach
    void setUp() {
        // Point ~ at an empty dir so a developer's real ~/.kube/config cannot leak into the tests.
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", fakeHome.toString());
        properties = new ClusterSyncProperties();

        assumeTrue(System.getenv("KUBECONFIG_BASE64") == null || System.getenv("KUBECONFIG_BASE64").isBlank(),
                "KUBECONFIG_BASE64 is set in the test environment");
        assumeTrue(System.getenv("KUBECONFIG") == null || System.getenv("KUBECONFIG").isBlank(),
                "KUBECONFIG is set in the test environment");
        assumeTrue(!new File(KubernetesClientConfig.SERVICE_ACCOUNT_TOKEN).exists(),
                "running inside a Kubernetes pod");
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", originalHome);
    }

    @Test
    @DisplayName("Empty KUBECONFIG_BASE64 and no other credentials resolves to NO_CLUSTER without throwing")
    void emptyConfigResolvesToNoCluster() {
        properties.setKubeconfigBase64("");

        ClusterConnection connection = new KubernetesClientConfig(properties).resolve();

        assertFalse(connection.configured());
        assertNotNull(connection.apiClient());
        assertTrue(connection.detail().contains("no kubeconfig"));
    }

    @Test
    @DisplayName("Invalid KUBECONFIG_BASE64 resolves to NO_CLUSTER and explains why")
    void invalidBase64ResolvesToNoClusterWithReason() {
        properties.setKubeconfigBase64("not-base64-!!!");

        ClusterConnection connection = new KubernetesClientConfig(properties).resolve();

        assertFalse(connection.configured());
        assertTrue(connection.detail().contains("KUBECONFIG_BASE64 is set but could not be loaded"));
    }

    @Test
    @DisplayName("Valid KUBECONFIG_BASE64 resolves to a configured connection")
    void validBase64ResolvesToConfigured() {
        properties.setKubeconfigBase64(
                Base64.getEncoder().encodeToString(KUBECONFIG_YAML.getBytes(StandardCharsets.UTF_8)));

        ClusterConnection connection = new KubernetesClientConfig(properties).resolve();

        assertTrue(connection.configured());
        assertEquals("KUBECONFIG_BASE64", connection.detail());
        assertEquals("https://127.0.0.1:6443", connection.apiClient().getBasePath());
    }
}
