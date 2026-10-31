package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.FarmingChatMessageRequest;
import com.aquaflow.backend.dto.request.PlanStatusUpdateRequest;
import com.aquaflow.backend.dto.request.TaskDependencyRequest;
import com.aquaflow.backend.dto.response.ChatSessionSummaryDto;
import com.aquaflow.backend.dto.response.FarmingChatResponse;
import com.aquaflow.backend.dto.response.PlanDetailDto;
import com.aquaflow.backend.entity.PlantingPlan;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface FarmingChatbotService {
    FarmingChatResponse processMessage(FarmingChatMessageRequest request, Authentication authentication);
    FarmingChatResponse getSessionState(String sessionId, Authentication authentication);
    FarmingChatResponse resetSession(String sessionId, Long fieldId, Authentication authentication);
    PlantingPlan confirmPlan(Long planId, Authentication authentication);
    FarmingChatResponse toggleTask(Long planId, Long taskId, Authentication authentication);
    List<PlantingPlan> getPlansForField(Long fieldId, Authentication authentication);

    // Multi-session history & context retrieval
    List<ChatSessionSummaryDto> listUserSessions(Long fieldId, boolean includeArchived, Authentication authentication);
    ChatSessionSummaryDto archiveSession(String sessionId, boolean archive, Authentication authentication);

    // Implementation plan lifecycle & details
    PlanDetailDto getPlanDetails(Long planId, Authentication authentication);
    PlanDetailDto updatePlanLifecycleStatus(Long planId, PlanStatusUpdateRequest request, Authentication authentication);
    FarmingChatResponse linkPlanToSession(Long planId, String sessionId, String linkType, Authentication authentication);

    // Task dependencies & execution
    PlanDetailDto addTaskDependency(Long planId, Long taskId, TaskDependencyRequest request, Authentication authentication);
    PlanDetailDto removeTaskDependency(Long planId, Long taskId, Long dependsOnTaskId, Authentication authentication);
    PlanDetailDto updateTaskStatus(Long planId, Long taskId, String status, String completionEvidence, Authentication authentication);
}
