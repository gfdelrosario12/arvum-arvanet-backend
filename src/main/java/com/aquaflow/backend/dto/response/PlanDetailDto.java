package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanDetailDto {
    private Long id;
    private String planCode;
    private String title;
    private String description;
    private String sessionId;
    private Long fieldId;
    private String cropName;
    private String plantingMethod;
    private String intendedSchedule;
    private String lifecycleStatus;
    private Integer progressPercent;
    private String statusReason;
    private String completionEvidence;
    private Integer readinessScore;
    private String readinessStatus;
    private String summaryText;
    private String irrigationSafetyStatus;
    private Boolean farmerConfirmed;
    private OffsetDateTime farmerConfirmedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<TaskDetailDto> tasks;
    private List<StatusHistoryDto> statusHistory;
    private List<String> associatedSessionIds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDetailDto {
        private Long id;
        private Long planId;
        private String category;
        private String title;
        private String description;
        private String status;
        private String blockingReason;
        private String priority;
        private String evidenceSource;
        private String completionEvidence;
        private OffsetDateTime deadline;
        private Boolean requiresConfirmation;
        private OffsetDateTime executedAt;
        private List<Long> dependsOnTaskIds;
        private Boolean isBlocked;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryDto {
        private Long id;
        private String fromStatus;
        private String toStatus;
        private String changedBy;
        private String reason;
        private OffsetDateTime createdAt;
    }
}

