package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmingChatResponse {
    private String sessionId;
    private String currentStep;
    private String replyText;
    private Long planId;
    private String planCode;
    private Integer readinessScore;
    private String readinessStatus;
    private String promptQuestion;
    private Boolean requiresConfirmation;
    private List<PlantingTaskDto> tasks;
    private List<String> agronomicReferences;
    private List<String> warnings;
    private String disclaimer;
    private List<String> suggestedQuestions;
    private ContextualFormDto contextualForm;

    // RBAC Context dynamically communicated to the frontend client
    private String userRole;
    private Boolean canControlIrrigation;
    private Boolean canManageUsers;
    private Boolean canViewAuditLogs;
    private List<String> authorizedCapabilities;

    // Workflow Engine & Task Lifecycle Context (S01-S13)
    private String taskLifecycleStatus;
    private List<WorkflowStepDto> workflowSteps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStepDto {
        private String stepCode;
        private String name;
        private String status; // PENDING, ELIGIBLE, WAITING, IN_PROGRESS, COMPLETED, FAILED, BLOCKED, SKIPPED, INVALIDATED
        private List<String> dependsOn;
        private String blockingReason;
        private String skipReason;
        private Boolean requiresUserDecision;
        private Boolean isRemediation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlantingTaskDto {
        private Long id;
        private String category;
        private String title;
        private String description;
        private String status;
        private String blockingReason;
        private String priority;
        private String deadline;
        private String evidenceSource;
        private Boolean requiresConfirmation;
    }
}
