package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_telemetry_insights")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTelemetryInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "insight_id", nullable = false, unique = true, length = 64)
    private String insightId;

    @Column(name = "field_id", nullable = false)
    @Builder.Default
    private Long fieldId = 1L;

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "insight_type", nullable = false, length = 64)
    private String insightType;

    @Column(name = "severity", nullable = false, length = 32)
    @Builder.Default
    private String severity = "INFO";

    @Column(name = "confidence", nullable = false)
    @Builder.Default
    private Double confidence = 1.0;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "window_start_time", nullable = false)
    private LocalDateTime windowStartTime;

    @Column(name = "window_end_time", nullable = false)
    private LocalDateTime windowEndTime;

    @Column(name = "model_version", nullable = false, length = 64)
    @Builder.Default
    private String modelVersion = "RULES-HEURISTIC-V1";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

