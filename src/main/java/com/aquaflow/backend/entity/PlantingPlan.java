package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "planting_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_code", nullable = false, unique = true, length = 64)
    private String planCode;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    @Column(name = "crop_name", nullable = false, length = 100)
    private String cropName;

    @Column(name = "planting_method", length = 100)
    private String plantingMethod;

    @Column(name = "intended_schedule", length = 255)
    private String intendedSchedule;

    @Builder.Default
    @Column(name = "lifecycle_status", nullable = false, length = 50)
    private String lifecycleStatus = "NOT_STARTED"; // NOT_STARTED, IN_PROGRESS, CONTINUED, COMPLETED, CANCELLED, FAILED

    @Column(name = "objectives_json", columnDefinition = "TEXT")
    private String objectivesJson;

    @Builder.Default
    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0; // 0 to 100

    @Column(name = "status_reason", columnDefinition = "TEXT")
    private String statusReason;

    @Column(name = "completion_evidence", columnDefinition = "TEXT")
    private String completionEvidence;

    @Column(name = "readiness_score", nullable = false)
    private Integer readinessScore; // 0 to 100

    @Column(name = "readiness_status", nullable = false, length = 50)
    private String readinessStatus; // ASSESSING, READY, BLOCKED, PREPARING, COMPLETED

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "irrigation_safety_status", nullable = false, length = 50)
    private String irrigationSafetyStatus; // VERIFIED_SAFE, BLOCKED_EMERGENCY_STOP, BLOCKED_DRY_RUN, UNKNOWN

    @Column(name = "farmer_confirmed", nullable = false)
    private Boolean farmerConfirmed;

    @Column(name = "farmer_confirmed_at")
    private OffsetDateTime farmerConfirmedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "plantingPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<PlantingTask> tasks = new ArrayList<>();
}
