package com.twindigital.clustersync.service;

import com.twindigital.clustersync.config.ClusterConnection;
import com.twindigital.clustersync.config.ClusterSyncProperties;
import com.twindigital.clustersync.metrics.ClusterSyncMetrics;
import com.twindigital.clustersync.model.mapper.KubernetesResourceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClusterSyncInformerManagerTest {

    @Mock private KubernetesResourceMapper mapper;
    @Mock private KubernetesResourcePersister persister;
    @Mock private SyncHealthTracker healthTracker;
    @Mock private ClusterSyncMetrics metrics;

    @Test
    @DisplayName("Without a cluster, start() reports NO_CLUSTER and does not start informers or reconnects")
    void startWithoutClusterMarksNoCluster() {
        ClusterConnection none = ClusterConnection.none("no kubeconfig or in-cluster credentials found");
        ClusterSyncInformerManager manager = new ClusterSyncInformerManager(
                none.apiClient(), none, new ClusterSyncProperties(), mapper, persister, healthTracker, metrics);

        manager.start();

        verify(healthTracker).markNoCluster("no kubeconfig or in-cluster credentials found");
        verify(healthTracker).persistSyncState();
        verify(healthTracker, never()).markConnected();
        verify(healthTracker, never()).incrementReconnectAttempts();
        assertFalse(manager.isRunning());
    }

    @Test
    @DisplayName("A failed state write does not stop startup in NO_CLUSTER mode")
    void persistFailureDoesNotCrash() {
        ClusterConnection none = ClusterConnection.none("reason");
        doThrow(new RuntimeException("db down")).when(healthTracker).persistSyncState();
        ClusterSyncInformerManager manager = new ClusterSyncInformerManager(
                none.apiClient(), none, new ClusterSyncProperties(), mapper, persister, healthTracker, metrics);

        manager.start(); // must not throw

        verify(healthTracker).markNoCluster("reason");
    }
}
