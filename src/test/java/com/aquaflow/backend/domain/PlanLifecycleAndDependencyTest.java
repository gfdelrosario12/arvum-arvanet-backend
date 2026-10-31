package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.PlanStatusUpdateRequest;
import com.aquaflow.backend.dto.request.TaskDependencyRequest;
import com.aquaflow.backend.dto.response.ChatSessionSummaryDto;
import com.aquaflow.backend.dto.response.PlanDetailDto;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanLifecycleAndDependencyTest {

    private ChatbotSessionRepository sessionRepository;
    private ChatbotMessageRepository messageRepository;
    private PlantingPlanRepository planRepository;
    private PlantingTaskRepository taskRepository;
    private TelemetryReadingRepository telemetryRepository;
    private EmergencyStopService emergencyStopService;
    private IrrigationAuditLogRepository auditLogRepository;
    private PlanConversationLinkRepository planConversationLinkRepository;
    private PlanStatusHistoryRepository planStatusHistoryRepository;
    private PlantingTaskDependencyRepository taskDependencyRepository;
    private ObjectMapper objectMapper;
    private FarmingChatbotServiceImpl chatbotService;

    private Authentication farmerAuth;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(ChatbotSessionRepository.class);
        messageRepository = mock(ChatbotMessageRepository.class);
        planRepository = mock(PlantingPlanRepository.class);
        taskRepository = mock(PlantingTaskRepository.class);
        telemetryRepository = mock(TelemetryReadingRepository.class);
        emergencyStopService = mock(EmergencyStopService.class);
        auditLogRepository = mock(IrrigationAuditLogRepository.class);
        planConversationLinkRepository = mock(PlanConversationLinkRepository.class);
        planStatusHistoryRepository = mock(PlanStatusHistoryRepository.class);
        taskDependencyRepository = mock(PlantingTaskDependencyRepository.class);
        objectMapper = new ObjectMapper();

        chatbotService = new FarmingChatbotServiceImpl(
                sessionRepository,
                messageRepository,
                planRepository,
                taskRepository,
                telemetryRepository,
                emergencyStopService,
                auditLogRepository,
                planConversationLinkRepository,
                planStatusHistoryRepository,
                taskDependencyRepository,
                objectMapper
        );

        farmerAuth = new UsernamePasswordAuthenticationToken("MrFarmer", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void testPlanLifecycleTransition_ContinuedAndCancelled() {
        PlantingPlan plan = PlantingPlan.builder()
                .id(100L)
                .planCode("PLAN-100")
                .cropName("Lowland Rice (NSIC Rc 222)")
                .lifecycleStatus("IN_PROGRESS")
                .progressPercent(50)
                .readinessScore(50)
                .readinessStatus("PREPARING")
                .tasks(new ArrayList<>())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(PlantingPlan.class))).thenAnswer(i -> i.getArgument(0));

        // 1. Transition to CONTINUED (pause)
        PlanStatusUpdateRequest pauseReq = PlanStatusUpdateRequest.builder()
                .status("CONTINUED")
                .reason("Awaiting soil desiccation window")
                .build();

        PlanDetailDto pausedDto = chatbotService.updatePlanLifecycleStatus(100L, pauseReq, farmerAuth);
        assertEquals("CONTINUED", pausedDto.getLifecycleStatus());
        assertEquals("Awaiting soil desiccation window", pausedDto.getStatusReason());
        verify(planStatusHistoryRepository, atLeastOnce()).save(any(PlanStatusHistory.class));

        // 2. Transition to CANCELLED requires reason
        PlanStatusUpdateRequest cancelReqNoReason = PlanStatusUpdateRequest.builder()
                .status("CANCELLED")
                .reason("")
                .build();
        assertThrows(ValidationException.class, () -> chatbotService.updatePlanLifecycleStatus(100L, cancelReqNoReason, farmerAuth));

        PlanStatusUpdateRequest cancelReq = PlanStatusUpdateRequest.builder()
                .status("CANCELLED")
                .reason("Farmer changed field allocation to corn")
                .build();
        PlanDetailDto cancelledDto = chatbotService.updatePlanLifecycleStatus(100L, cancelReq, farmerAuth);
        assertEquals("CANCELLED", cancelledDto.getLifecycleStatus());
    }

    @Test
    void testTaskDependency_EnforcePrerequisiteBeforeCompletion() {
        PlantingTask taskA = PlantingTask.builder()
                .id(1L)
                .title("Task A: Land Plowing")
                .status("PENDING")
                .category("FARMER_MANUAL")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        PlantingTask taskB = PlantingTask.builder()
                .id(2L)
                .title("Task B: Harrowing & Leveling")
                .status("PENDING")
                .category("FARMER_MANUAL")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        PlantingPlan plan = PlantingPlan.builder()
                .id(101L)
                .planCode("PLAN-101")
                .cropName("Lowland Rice (NSIC Rc 222)")
                .tasks(new ArrayList<>(List.of(taskA, taskB)))
                .build();

        when(planRepository.findById(101L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(PlantingPlan.class))).thenAnswer(i -> i.getArgument(0));
        when(taskRepository.save(any(PlantingTask.class))).thenAnswer(i -> i.getArgument(0));

        // Task B depends on Task A
        PlantingTaskDependency dep = PlantingTaskDependency.builder()
                .id(10L)
                .taskId(2L)
                .dependsOnTaskId(1L)
                .dependencyType("REQUIRED")
                .createdAt(OffsetDateTime.now())
                .build();
        when(taskDependencyRepository.findByTaskId(2L)).thenReturn(List.of(dep));

        // Attempting to toggle or complete Task B while Task A is PENDING should fail
        assertThrows(ValidationException.class, () -> chatbotService.toggleTask(101L, 2L, farmerAuth));

        // Now complete Task A
        chatbotService.toggleTask(101L, 1L, farmerAuth);
        assertEquals("COMPLETED", taskA.getStatus());

        // Now Task B can be toggled to COMPLETED
        chatbotService.toggleTask(101L, 2L, farmerAuth);
        assertEquals("COMPLETED", taskB.getStatus());
    }

    @Test
    void testTaskDependency_CycleDetection() {
        PlantingTask task1 = PlantingTask.builder().id(11L).title("T1").status("PENDING").category("FARMER_MANUAL").requiresConfirmation(false).build();
        PlantingTask task2 = PlantingTask.builder().id(12L).title("T2").status("PENDING").category("FARMER_MANUAL").requiresConfirmation(false).build();

        PlantingPlan plan = PlantingPlan.builder()
                .id(102L)
                .planCode("PLAN-102")
                .cropName("Lowland Rice")
                .tasks(new ArrayList<>(List.of(task1, task2)))
                .build();

        when(planRepository.findById(102L)).thenReturn(Optional.of(plan));

        // T1 depends on T2
        PlantingTaskDependency dep1 = PlantingTaskDependency.builder().id(101L).taskId(11L).dependsOnTaskId(12L).dependencyType("REQUIRED").build();
        when(taskDependencyRepository.findByTaskId(11L)).thenReturn(List.of(dep1));

        // Attempting to make T2 depend on T1 should be rejected as circular
        TaskDependencyRequest req = TaskDependencyRequest.builder().dependsOnTaskId(11L).build();
        assertThrows(ValidationException.class, () -> chatbotService.addTaskDependency(102L, 12L, req, farmerAuth));
    }
}

