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
public class ChatSessionSummaryDto {
    private String sessionId;
    private String title;
    private String userId;
    private Long fieldId;
    private String currentStep;
    private String selectedCrop;
    private Boolean isArchived;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastActivityAt;
    private Integer messageCount;
    private Integer planCount;
    private List<PlanSnippetDto> associatedPlans;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanSnippetDto {
        private Long planId;
        private String planCode;
        private String title;
        private String lifecycleStatus;
        private Integer progressPercent;
    }
}

