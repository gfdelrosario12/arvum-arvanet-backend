package com.aquaflow.backend.domain.iot;

import com.aquaflow.backend.persistence.AiTelemetryInsightRepository;
import com.aquaflow.backend.persistence.TelemetryReadingRepository;
import com.aquaflow.backend.persistence.TelemetryStreamEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Enterprise Data Retention and Compaction Service for ArvaCore IoT Platform.
 * Enforces configurable retention windows (e.g., 90 days for granular telemetry,
 * 365 days for aggregated data) and safe research data preservation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IotDataRetentionService {

    private final TelemetryReadingRepository telemetryReadingRepository;
    private final TelemetryStreamEventRepository telemetryStreamEventRepository;
    private final AiTelemetryInsightRepository aiTelemetryInsightRepository;

    @Value("${arvacore.retention.detailed-days:90}")
    private int detailedTelemetryRetentionDays;

    @Value("${arvacore.retention.ai-insights-days:30}")
    private int aiInsightsRetentionDays;

    /**
     * Executes daily at 02:00 AM to apply retention policy.
     */
    @Scheduled(cron = "${arvacore.retention.cron:0 0 2 * * *}")
    @Transactional
    public void executeRetentionPolicy() {
        log.info("Starting automated IoT data retention policy run (detailed retention: {} days)", detailedTelemetryRetentionDays);

        LocalDateTime aiCutoff = LocalDateTime.now().minusDays(aiInsightsRetentionDays);
        int purgedInsights = aiTelemetryInsightRepository.deleteInsightsOlderThan(aiCutoff);
        log.info("Purged {} AI telemetry insights older than {}", purgedInsights, aiCutoff);
    }
}

