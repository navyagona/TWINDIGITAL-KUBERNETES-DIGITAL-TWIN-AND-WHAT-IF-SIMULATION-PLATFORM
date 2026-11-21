package com.twindigital.clustersync.config;

import io.kubernetes.client.openapi.ApiClient;

/**
 * Result of resolving Kubernetes credentials at startup.
 *
 * @param apiClient  client to use; an unauthenticated placeholder when {@code configured} is false
 * @param configured true when a real credential source (kubeconfig or in-cluster token) was found
 * @param detail     where the credentials came from, or why none were usable
 */
public record ClusterConnection(ApiClient apiClient, boolean configured, String detail) {

    public static ClusterConnection of(ApiClient apiClient, String source) {
        return new ClusterConnection(apiClient, true, source);
    }

    public static ClusterConnection none(String reason) {
        return new ClusterConnection(new ApiClient(), false, reason);
    }
}
