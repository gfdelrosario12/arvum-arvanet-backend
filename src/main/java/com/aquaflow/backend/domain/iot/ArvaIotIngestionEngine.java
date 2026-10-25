package com.aquaflow.backend.domain.iot;

import com.aquaflow.backend.domain.AlertService;
import com.aquaflow.backend.dto.iot.ArvaIotTelemetryPayload;
import com.aquaflow.backend.dto.response.TelemetryUplinkResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.observability.IngestionMetricsService;
import com.aquaflow.backend.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise ArvaCore IoT Ingestion Engine.
 * Handles MQTT per-device topic authorization, idempotent deduplication,
 * time-series storage, fine-grained device state transitions, and outbox staging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArvaIotIngestionEngine {

    private final EdgeNodeRepository edgeNodeRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final TelemetryStreamEventRepository telemetryStreamEventRepository;
    private final TelemetryEventOutboxRepository outboxRepository;
    private final DeviceMqttCredentialRepository deviceMqttCredentialRepository;
    private final MonitoringPointRepository monitoringPointRepository;
    private final IngestionMetricsService metricsService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AlertService alertService;

    // In-memory bounded LRU / TTL cache for messageId deduplication (10,000 messages)
    private final Map<String, Long> processedMessageCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(1000, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > 10000;
                }
            }
    );

    // Track sequence numbers per node to detect retransmissions or reboots
    private final Map<String, Long> lastNodeSequenceNumber = new ConcurrentHashMap<>();

    /**
     * Ingests a telemetry message received over MQTT or REST with strict topic and device verification.
     *
     * @param topicDeviceId Device identifier parsed from authorized topic (e.g., "ARVANODE-001")
     * @param rawPayload JSON string payload
     * @return TelemetryUplinkResponse summarizing ingestion status
     */
    @Transactional
    public TelemetryUplinkResponse ingestMqttTelemetry(String topicDeviceId, String rawPayload) {
        metricsService.recordReceived();
        Timer.Sample timer = metricsService.startTimer();

        try {
            if (rawPayload == null || rawPayload.isBlank()) {
                metricsService.recordMalformed();
                return TelemetryUplinkResponse.builder()
                        .success(false)
                        .message("Empty or null telemetry payload")
                        .build();
            }

            ArvaIotTelemetryPayload payload;
            try {
                payload = objectMapper.readValue(rawPayload, ArvaIotTelemetryPayload.class);
            } catch (Exception e) {
                log.warn("Malformed MQTT payload on device topic {}: {}", topicDeviceId, e.getMessage());
                metricsService.recordMalformed();
                return TelemetryUplinkResponse.builder()
                        .success(false)
                        .message("JSON parse failure: " + e.getMessage())
                        .build();
            }

            String payloadDeviceId = payload.resolveEffectiveDeviceId();

            // 1. Per-device Topic Authorization Verification
            // Prevent Node A from publishing under Node B's namespace
            if (topicDeviceId != null && payloadDeviceId != null && !topicDeviceId.equalsIgnoreCase(payloadDeviceId)) {
                log.error("SECURITY ALERT: Topic deviceId '{}' mismatch with payload deviceId '{}'. Rejecting spoofed telemetry.",
                        topicDeviceId, payloadDeviceId);
                metricsService.recordUnauthorizedTopic();
                if (alertService != null) {
                    alertService.createNotification(
                            "SECURITY_SPOOF_DETECTED",
                            "Topic Mismatch Security Alert: " + topicDeviceId,
                            String.format("Device on topic %s attempted to report as %s.", topicDeviceId, payloadDeviceId),
                            "CRITICAL",
                            "security",
                            topicDeviceId,
                            null, null, null,
                            "MQTT_BROKER",
                            "Ingestion Engine",
                            topicDeviceId,
                            "Inspect network and MQTT credentials for possible compromise.",
                            "SPOOF:" + topicDeviceId + ":" + payloadDeviceId,
                            300,
                            rawPayload
                    );
                }
                return TelemetryUplinkResponse.builder()
                        .success(false)
                        .nodeId(topicDeviceId)
                        .message("FORBIDDEN: Topic deviceId does not match payload deviceId")
                        .build();
            }

            String effectiveNodeId = topicDeviceId != null ? topicDeviceId : payloadDeviceId;
            if (effectiveNodeId == null || effectiveNodeId.isBlank()) {
                metricsService.recordMalformed();
                return TelemetryUplinkResponse.builder()
                        .success(false)
                        .message("Missing node/device identifier")
                        .build();
            }

            // 2. Message Deduplication (Idempotent Delivery)
            String messageId = payload.getMessageId();
            if (messageId != null && !messageId.isBlank()) {
                String dedupKey = effectiveNodeId + ":" + messageId;
                if (processedMessageCache.containsKey(dedupKey)) {
                    log.info("Duplicate telemetry detected for {} with messageId {}. Dropping safely.", effectiveNodeId, messageId);
                    metricsService.recordDuplicate();
                    return TelemetryUplinkResponse.builder()
                            .success(true)
                            .duplicate(true)
                            .nodeId(effectiveNodeId)
                            .message("Duplicate message acknowledged and ignored")
                            .timestamp(LocalDateTime.now())
                            .build();
                }
                processedMessageCache.put(dedupKey, System.currentTimeMillis());
            }

            // Sequence Number Tracking (informational & out-of-order detection)
            Long seq = payload.resolveSequenceNumber();
            if (seq != null) {
                Long prevSeq = lastNodeSequenceNumber.put(effectiveNodeId, seq);
                if (prevSeq != null && seq < prevSeq) {
                    log.warn("Sequence regression detected for {}: previous={}, current={} (possible node reboot)",
                            effectiveNodeId, prevSeq, seq);
                }
            }

            // 3. Resolve Node and Active Credential Status
            Optional<EdgeNode> nodeOpt = edgeNodeRepository.findByNodeId(effectiveNodeId);
            if (nodeOpt.isEmpty()) {
                log.warn("Telemetry received from unregistered node: {}", effectiveNodeId);
                return TelemetryUplinkResponse.builder()
                        .success(false)
                        .nodeId(effectiveNodeId)
                        .message("Node not registered in ArvaCore registry")
                        .build();
            }

            EdgeNode node = nodeOpt.get();

            // Check Revocation Status
            if (node.getLifecycleState() == NodeLifecycleState.DECOMMISSIONED) {
                log.warn("Telemetry rejected from decommissioned node: {}", effectiveNodeId);
                return TelemetryUplinkResponse.builder()
                        .success(false)
                        .nodeId(effectiveNodeId)
                        .message("Device is decommissioned")
                        .build();
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime deviceTime = now; // Default server receipt time if device timestamp unavailable

            // 4. Persist Stream Event (Raw durable audit)
            TelemetryStreamEvent streamEvent = TelemetryStreamEvent.builder()
                    .eventId(messageId != null ? messageId : UUID.randomUUID().toString())
                    .nodeId(effectiveNodeId)
                    .eventType("TELEMETRY_UPLINK")
                    .status("PROCESSED")
                    .receivedAt(now)
                    .deviceTimestamp(deviceTime)
                    .sequenceNumber(seq)
                    .fieldId(node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null
                            ? node.getMonitoringZone().getField().getId() : 1L)
                    .zoneId(node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null)
                    .zoneCode(node.getMonitoringZone() != null ? node.getMonitoringZone().getCode() : null)
                    .soilMoisturePercent(payload.resolveSoilMoisture())
                    .waterLevelCm(payload.resolveWaterLevel())
                    .waterLevelDistanceCm(payload.resolveWaterDistance())
                    .batteryVoltage(payload.resolveBatteryVoltage())
                    .rssiDbm(payload.resolveRssi())
                    .soilSensorHealth(payload.getSensorStatus() != null ? payload.getSensorStatus().get("soilMoisture") : "OK")
                    .ultrasonicSensorHealth(payload.getSensorStatus() != null ? payload.getSensorStatus().get("waterLevel") : "OK")
                    .telemetryStatus(payload.getTelemetryStatus() != null ? payload.getTelemetryStatus() : "HEALTHY")
                    .rawPayload(rawPayload)
                    .readingsCount(2)
                    .build();

            telemetryStreamEventRepository.save(streamEvent);

            // 5. Persist Normalized Sensor Readings (Soil Moisture & Water Level Distance)
            MonitoringPoint point = resolveMonitoringPoint(node);
            Field nodeField = node.getMonitoringZone() != null ? node.getMonitoringZone().getField() : null;
            int readingsCount = 0;

            Double soilMoisture = payload.resolveSoilMoisture();
            if (soilMoisture != null && !soilMoisture.isNaN()) {
                TelemetryReading r = TelemetryReading.builder()
                        .monitoringPoint(point)
                        .edgeNode(node)
                        .field(nodeField)
                        .sensorType(SensorType.SOIL_MOISTURE)
                        .valueNum(soilMoisture)
                        .unit("%")
                        .timestamp(now)
                        .build();
                telemetryReadingRepository.save(r);
                readingsCount++;
            }

            Double waterDist = payload.resolveWaterDistance();
            if (waterDist != null && !waterDist.isNaN()) {
                TelemetryReading r = TelemetryReading.builder()
                        .monitoringPoint(point)
                        .edgeNode(node)
                        .field(nodeField)
                        .sensorType(SensorType.WATER_LEVEL_DISTANCE)
                        .valueNum(waterDist)
                        .unit("cm")
                        .timestamp(now)
                        .build();
                telemetryReadingRepository.save(r);
                readingsCount++;
            }

            // 6. Update Edge Node Device State (Shadow & Fine-Grained Connectivity)
            if (node.getHealthMetrics() == null) {
                node.setHealthMetrics(new NodeHealthMetrics());
            }
            node.getHealthMetrics().setLastHeartbeat(now);
            node.getHealthMetrics().setLastTelemetryAt(now);
            node.getHealthMetrics().setIsTelemetryStale(false);

            if (payload.resolveBatteryVoltage() != null) {
                node.getHealthMetrics().setBatteryLevel(payload.resolveBatteryVoltage());
            }
            if (payload.resolveRssi() != null) {
                node.getHealthMetrics().setSignalDbm(payload.resolveRssi());
            }
            node.setHealthState(HealthState.HEALTHY);

            edgeNodeRepository.save(node);
            metricsService.setNodeConnectionState(effectiveNodeId, true);

            // 7. Transactional Outbox Staging for Event Bus & AI Dispatch
            TelemetryEventOutbox outboxEntry = TelemetryEventOutbox.builder()
                    .eventId(streamEvent.getEventId())
                    .eventType("TELEMETRY_RECEIVED")
                    .nodeId(effectiveNodeId)
                    .fieldId(streamEvent.getFieldId())
                    .zoneId(streamEvent.getZoneId())
                    .payloadJson(rawPayload)
                    .status("PENDING")
                    .build();
            outboxRepository.save(outboxEntry);

            metricsService.recordProcessed();
            return TelemetryUplinkResponse.builder()
                    .success(true)
                    .duplicate(false)
                    .nodeId(effectiveNodeId)
                    .readingsIngested(readingsCount)
                    .timestamp(now)
                    .message("Telemetry ingested and queued for event processing")
                    .build();

        } catch (Exception e) {
            metricsService.recordFailedWrite();
            log.error("Critical error persisting telemetry for {}: {}", topicDeviceId, e.getMessage(), e);
            throw e;
        } finally {
            metricsService.stopTimer(timer);
        }
    }

    private MonitoringPoint resolveMonitoringPoint(EdgeNode node) {
        if (node.getMonitoringZone() == null) return null;
        List<MonitoringPoint> points = monitoringPointRepository.findByMonitoringZoneId(node.getMonitoringZone().getId());
        if (!points.isEmpty()) {
            return points.get(0);
        }
        return null;
    }
}

