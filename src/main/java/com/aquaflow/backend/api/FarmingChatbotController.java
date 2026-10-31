package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.FarmingChatbotService;
import com.aquaflow.backend.dto.request.FarmingChatMessageRequest;
import com.aquaflow.backend.dto.request.PlanStatusUpdateRequest;
import com.aquaflow.backend.dto.request.TaskDependencyRequest;
import com.aquaflow.backend.dto.response.ChatSessionSummaryDto;
import com.aquaflow.backend.dto.response.FarmingChatResponse;
import com.aquaflow.backend.dto.response.PlanDetailDto;
import com.aquaflow.backend.entity.PlantingPlan;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/farming-chat")
@RequiredArgsConstructor
@Slf4j
public class FarmingChatbotController {

    private final FarmingChatbotService chatbotService;

    @PostMapping("/message")
    public ResponseEntity<FarmingChatResponse> sendMessage(
            @Valid @RequestBody FarmingChatMessageRequest request,
            Authentication authentication) {
        log.info("POST /api/v1/farming-chat/message by authenticated user {}", authentication != null ? authentication.getName() : "ANONYMOUS");
        return ResponseEntity.ok(chatbotService.processMessage(request, authentication));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionSummaryDto>> listSessions(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.listUserSessions(fieldId, includeArchived, authentication));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<FarmingChatResponse> getSession(
            @PathVariable String sessionId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.getSessionState(sessionId, authentication));
    }

    @PostMapping("/sessions/{sessionId}/archive")
    public ResponseEntity<ChatSessionSummaryDto> archiveSession(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "true") boolean archive,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.archiveSession(sessionId, archive, authentication));
    }

    @PostMapping("/sessions/{sessionId}/reset")
    public ResponseEntity<FarmingChatResponse> resetSession(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "1") Long fieldId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.resetSession(sessionId, fieldId, authentication));
    }

    @PostMapping("/plans/{planId}/confirm")
    public ResponseEntity<PlantingPlan> confirmPlan(
            @PathVariable Long planId,
            Authentication authentication) {
        log.info("POST /api/v1/farming-chat/plans/{}/confirm by {}", planId, authentication != null ? authentication.getName() : "ANONYMOUS");
        return ResponseEntity.ok(chatbotService.confirmPlan(planId, authentication));
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<PlanDetailDto> getPlanDetails(
            @PathVariable Long planId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.getPlanDetails(planId, authentication));
    }

    @PatchMapping("/plans/{planId}/status")
    public ResponseEntity<PlanDetailDto> updatePlanLifecycleStatus(
            @PathVariable Long planId,
            @Valid @RequestBody PlanStatusUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.updatePlanLifecycleStatus(planId, request, authentication));
    }

    @PostMapping("/plans/{planId}/sessions/{sessionId}/link")
    public ResponseEntity<FarmingChatResponse> linkPlanToSession(
            @PathVariable Long planId,
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "RESUMED") String linkType,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.linkPlanToSession(planId, sessionId, linkType, authentication));
    }

    @PostMapping("/plans/{planId}/tasks/{taskId}/toggle")
    public ResponseEntity<FarmingChatResponse> toggleTask(
            @PathVariable Long planId,
            @PathVariable Long taskId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.toggleTask(planId, taskId, authentication));
    }

    @PutMapping("/plans/{planId}/tasks/{taskId}/status")
    public ResponseEntity<PlanDetailDto> updateTaskStatus(
            @PathVariable Long planId,
            @PathVariable Long taskId,
            @RequestParam String status,
            @RequestParam(required = false) String completionEvidence,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.updateTaskStatus(planId, taskId, status, completionEvidence, authentication));
    }

    @PostMapping("/plans/{planId}/tasks/{taskId}/dependencies")
    public ResponseEntity<PlanDetailDto> addTaskDependency(
            @PathVariable Long planId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskDependencyRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.addTaskDependency(planId, taskId, request, authentication));
    }

    @DeleteMapping("/plans/{planId}/tasks/{taskId}/dependencies/{dependsOnTaskId}")
    public ResponseEntity<PlanDetailDto> removeTaskDependency(
            @PathVariable Long planId,
            @PathVariable Long taskId,
            @PathVariable Long dependsOnTaskId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.removeTaskDependency(planId, taskId, dependsOnTaskId, authentication));
    }

    @GetMapping("/fields/{fieldId}/plans")
    public ResponseEntity<List<PlantingPlan>> getPlansForField(
            @PathVariable Long fieldId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.getPlansForField(fieldId, authentication));
    }
}
