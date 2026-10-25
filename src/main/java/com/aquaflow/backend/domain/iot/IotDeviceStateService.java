package com.aquaflow.backend.domain.iot;

import com.aquaflow.backend.domain.AlertService;
import com.aquaflow.backend.entity.EdgeNode;
import com.aquaflow.backend.entity.HealthState;
import com.aquaflow.backend.entity.NodeHealthMetrics;
import com.aquaflow.backend.entity.NodeLifecycleState;
import com.aquaflow.backend.infrastructure.observability.IngestionMetricsService;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Enterprise IoT Device State Service.
 * Tracks fine-grained connection status ("ONLINE", "STALE", "OFFLINE", "REVOKED", "NEVER_CONNECTED")
 * with configurable sub-minute thresholds for a 3-second continuous telemetry interval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IotDeviceStateService {

    private final EdgeNodeRepository edgeNodeRepository;
    private final IngestionMetricsService metricsService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AlertService alertService;

    // Thresholds: For 3s telemetry, 15 seconds without data = STALE, 60 seconds = OFFLINE
    @Value("${arvacore.telemetry.stale-threshold-seconds:15}")
    private long staleThresholdSeconds;

    @Value("${arvacore.telemetry.offline-threshold-seconds:60}")
    private long offlineThresholdSeconds;

    /**
     * Periodic sweep running every 5 seconds to evaluate device freshness against high-frequency intervals.
     */
    @Scheduled(fixedDelayString = "${arvacore.telemetry.state-sweep-ms:5000}")
    @Transactional
    public void evaluateDeviceStates() {
        List<EdgeNode> nodes = edgeNodeRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (EdgeNode node : nodes) {
            try {
                if (node.getLifecycleState() == NodeLifecycleState.DECOMMISSIONED) {
                    metricsService.setNodeConnectionState(node.getNodeId(), false);
                    continue;
                }

                NodeHealthMetrics metrics = node.getHealthMetrics();
                if (metrics == null) {
                    metrics = new NodeHealthMetrics();
                    node.setHealthMetrics(metrics);
                }

                LocalDateTime lastSeen = metrics.getLastTelemetryAt() != null
                        ? metrics.getLastTelemetryAt()
                        : metrics.getLastHeartbeat();

                if (lastSeen == null) {
                    node.setHealthState(HealthState.UNKNOWN);
                    metricsService.setNodeConnectionState(node.getNodeId(), false);
                    continue;
                }

                long secondsSinceLastSeen = Duration.between(lastSeen, now).getSeconds();
                HealthState previousState = node.getHealthState();
                HealthState newState;

                if (secondsSinceLastSeen < staleThresholdSeconds) {
                    newState = HealthState.HEALTHY;
                    metrics.setIsTelemetryStale(false);
                    metricsService.setNodeConnectionState(node.getNodeId(), true);
                } else if (secondsSinceLastSeen < offlineThresholdSeconds) {
                    newState = HealthState.DEGRADED;
                    metrics.setIsTelemetryStale(true);
                    metricsService.setNodeConnectionState(node.getNodeId(), true);
                } else {
                    newState = HealthState.OFFLINE;
                    metrics.setIsTelemetryStale(true);
                    metricsService.setNodeConnectionState(node.getNodeId(), false);
                }

                if (newState != previousState) {
                    log.info("Device state transition for {}: {} -> {} (last seen {}s ago)",
                            node.getNodeId(), previousState, newState, secondsSinceLastSeen);
                    node.setHealthState(newState);
                    edgeNodeRepository.save(node);

                    // Generate notification on transition to OFFLINE
                    if (newState == HealthState.OFFLINE && alertService != null) {
                        Long fieldId = (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null)
                                ? node.getMonitoringZone().getField().getId() : 1L;
                        Long zoneId = node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null;
                        String zoneCode = node.getMonitoringZone() != null ? node.getMonitoringZone().getCode() : null;

                        alertService.createNotification(
                                "DEVICE_OFFLINE",
                                "Device Offline: " + node.getNodeId(),
                                String.format("Node %s has not transmitted telemetry for %d seconds. Last reported state: %s.",
                                        node.getNodeId(), secondsSinceLastSeen, previousState),
                                "WARNING",
                                "hardware",
                                node.getNodeId(),
                                fieldId,
                                zoneId,
                                zoneCode,
                                "IOT_STATE_SERVICE",
                                "Node " + node.getNodeId(),
                                zoneCode != null ? zoneCode : node.getNodeId(),
                                "Check solar power, battery voltage, and cellular signal.",
                                "DISCONNECT:" + node.getNodeId(),
                                180,
                                null
                        );
                    }
                }
            } catch (Exception e) {
                log.error("Error evaluating connection state for node {}: {}", node.getNodeId(), e.getMessage());
            }
        }
    }
}

