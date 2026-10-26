package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "post_irrigation_outcomes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostIrrigationOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "irrigation_command_id", nullable = false, length = 64)
    private String irrigationCommandId;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "controller_status", nullable = false, length = 50)
    private String controllerStatus;

    @Column(name = "water_level_delta_cm", nullable = false)
    private Double waterLevelDeltaCm;

    @Column(name = "soil_moisture_delta_pct", nullable = false)
    private Double soilMoistureDeltaPct;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "farmer_feedback", columnDefinition = "TEXT")
    private String farmerFeedback;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;
}

