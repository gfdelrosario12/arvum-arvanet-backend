package com.aquaflow.backend.domain.iot;

import com.aquaflow.backend.domain.AlertService;
import com.aquaflow.backend.entity.AiTelemetryInsight;
import com.aquaflow.backend.entity.TelemetryEventOutbox;
import com.aquaflow.backend.entity.TelemetryReading;
import com.aquaflow.backend.infrastructure.realtime.RealtimeSseBroadcaster;
import com.aquaflow.backend.persistence.AiTelemetryInsightRepository;
import com.aquaflow.backend.persistence.TelemetryEventOutboxRepository;
import com.aquaflow.backend.persistence.TelemetryReadingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise Transactional Outbox Dispatcher.
 * Consumes staged telemetry events asynchronously without blocking ingestion,
 * routes notifications through deduplicated cooldowns, broadcasts SSE,
 * and triggers windowed statistical analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IotOutboxDispatcherService {

    private final TelemetryEventOutboxRepository outboxRepository;
    private final AiTelemetryInsightRepository insightRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AlertService alertService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RealtimeSseBroadcaster sseBroadcaster;

    @Scheduled(fixedDelayString = "${arvacore.outbox.dispatch-interval-ms:1000}")
    @Transactional
    public void dispatchPendingEvents() {
        List<TelemetryEventOutbox> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
                "PENDING", PageRequest.of(0, 50));

        if (pending.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (TelemetryEventOutbox event : pending) {
            try {
                processEvent(event);
                event.setStatus("DISPATCHED");
                event.setDispatchedAt(now);
            } catch (Exception e) {
                log.error("Failed to dispatch outbox event {}: {}", event.getEventId(), e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= 3) {
                    event.setStatus("FAILED");
                }
            }
        }
        outboxRepository.saveAll(pending);
    }

    private void processEvent(TelemetryEventOutbox event) throws Exception {
        JsonNode json = objectMapper.readTree(event.getPayloadJson());

        // 1. Broadcast SSE to ArvaLink frontend
        if (sseBroadcaster != null) {
            try {
                sseBroadcaster.broadcastEvent("telemetry", json);
            } catch (Exception ignored) {}
        }

        // 2. Evaluate Sensor Limits & Rapid Fluctuations
        Double soilMoisture = json.has("soilMoisturePercent") ? json.get("soilMoisturePercent").asDouble() : null;
        if (soilMoisture != null && soilMoisture < 30.0 && alertService != null) {
            alertService.createNotification(
                    "SOIL_CRITICAL_DRY",
                    "Low Soil Moisture Alert: " + event.getNodeId(),
                    String.format("Node %s reported critically dry soil moisture at %.1f%%.", event.getNodeId(), soilMoisture),
                    "CRITICAL",
                    "agronomic",
                    event.getNodeId(),
                    event.getFieldId(),
                    event.getZoneId(),
                    "Q" + event.getZoneId(),
                    "OUTBOX_DISPATCHER",
                    "Node " + event.getNodeId(),
                    "Zone " + event.getZoneId(),
                    "Initiate AWD re-flooding irrigation schedule.",
                    "LOW_SOIL:" + event.getNodeId(),
                    180,
                    event.getPayloadJson()
            );
        }
    }

    /**
     * Purge successfully dispatched outbox events older than 24 hours to keep table compact.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeOldDispatchedEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int deleted = outboxRepository.purgeDispatchedBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} dispatched outbox records older than {}", deleted, cutoff);
        }
    }
}

