package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pre_irrigation_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreIrrigationPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_code", nullable = false, unique = true, length = 64)
    private String planCode;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "crop_variety", nullable = false, length = 100)
    private String cropVariety;

    @Column(name = "crop_stage", nullable = false, length = 100)
    private String cropStage;

    @Column(name = "irrigation_objective", nullable = false)
    private String irrigationObjective;

    @Column(name = "proposed_window_start", nullable = false)
    private OffsetDateTime proposedWindowStart;

    @Column(name = "proposed_window_end", nullable = false)
    private OffsetDateTime proposedWindowEnd;

    @Column(name = "overall_status", nullable = false, length = 50)
    private String overallStatus; // PROPOSED, SCHEDULED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED, ABANDONED, OVERDUE, BLOCKED, RESCHEDULED

    @Column(name = "prerequisites_met", nullable = false)
    private Boolean prerequisitesMet;

    @Column(name = "readiness_score", nullable = false)
    private Integer readinessScore;

    @Column(name = "associated_irrigation_id", length = 64)
    private String associatedIrrigationId;

    @Column(name = "risk_factors_json", columnDefinition = "TEXT")
    private String riskFactorsJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "preIrrigationPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<PlantingTask> tasks = new ArrayList<>();
}

