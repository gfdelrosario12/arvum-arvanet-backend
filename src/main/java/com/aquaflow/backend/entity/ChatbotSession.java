package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chatbot_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "title", length = 255)
    private String title;

    @Builder.Default
    @Column(name = "is_archived", nullable = false)
    private Boolean isArchived = false;

    @Column(name = "last_activity_at", nullable = false)
    private OffsetDateTime lastActivityAt;

    @Column(name = "field_id")
    private Long fieldId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "current_step", nullable = false, length = 64)
    private String currentStep; // CROP_SELECTION, FIELD_AND_METHOD, READINESS_ASSESSMENT, CONFIRMATION_PENDING, PREPARATION_ACTIVE, COMPLETED

    @Column(name = "selected_crop", length = 100)
    private String selectedCrop;

    @Column(name = "planting_method", length = 100)
    private String plantingMethod;

    @Column(name = "intended_schedule", length = 255)
    private String intendedSchedule;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "session_working_config_json", columnDefinition = "TEXT")
    private String sessionWorkingConfigJson;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE"; // ACTIVE, COMPLETED, ARCHIVED, TERMINATED

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "workflow_id", length = 64)
    private String workflowId;

    @Builder.Default
    @Column(name = "config_confirmed_by_farmer")
    private Boolean configConfirmedByFarmer = false;
}
