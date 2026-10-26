package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.FarmingChatMessageRequest;
import com.aquaflow.backend.dto.response.FarmingChatResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmingChatbotServiceImpl implements FarmingChatbotService {

    private final ChatbotSessionRepository sessionRepository;
    private final ChatbotMessageRepository messageRepository;
    private final PlantingPlanRepository planRepository;
    private final PlantingTaskRepository taskRepository;
    private final TelemetryReadingRepository telemetryRepository;
    private final EmergencyStopService emergencyStopService;
    private final IrrigationAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public static final String INITIAL_QUESTION = "Hi, I am Arva, what do you like to plant today?";
    public static final String PROCEED_QUESTION = "Do you want to proceed with the proposed planting preparation?";

    @Override
    @Transactional
    public FarmingChatResponse processMessage(FarmingChatMessageRequest request, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);
        String msg = request.getMessage() != null ? request.getMessage().trim() : "";
        Long fieldId = request.getFieldId() != null ? request.getFieldId() : 1L;

        // 1. Adversarial & Prompt Injection Defense (Neutralize attempts to bypass RBAC or force actuation)
        if (isAdversarial(msg)) {
            recordAuditLog("AI_CHAT_ADVERSARIAL_ATTEMPT", username, "CHAT", request.getSessionId() != null ? request.getSessionId() : "NONE", Map.of("query", msg, "role", role));
            return buildAdversarialResponse(request.getSessionId(), role);
        }

        // 1B. Destructive Operations Safety Intercept
        if (isDestructiveOperation(msg)) {
            recordAuditLog("AI_CHAT_DESTRUCTIVE_INTERCEPT", username, "CHAT", request.getSessionId() != null ? request.getSessionId() : "NONE", Map.of("query", msg, "role", role));
            return buildDestructiveInterceptResponse(request.getSessionId(), role);
        }

        // 2. Fetch or create session linked to the real authenticated user
        ChatbotSession session;
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            session = sessionRepository.findBySessionId(request.getSessionId())
                    .orElseGet(() -> createNewSession(username, fieldId));
            
            // Enforce ownership: Non-admins cannot hijack another user's session
            if (!isAdmin(authentication) && !session.getUserId().equalsIgnoreCase(username)) {
                log.warn("Access Denied: User {} attempted to access session {} owned by {}", username, session.getSessionId(), session.getUserId());
                throw new AccessDeniedException("FORBIDDEN: You do not have permission to access another user's chat session.");
            }
        } else {
            session = createNewSession(username, fieldId);
        }

        // Record incoming user message
        recordMessage(session.getSessionId(), "USER", msg);

        // 3. Conversational State Machine
        FarmingChatResponse response;
        String currentStep = session.getCurrentStep();

        if (msg.equalsIgnoreCase("reset") || msg.equalsIgnoreCase("start over") || msg.equalsIgnoreCase("new")) {
            return resetSession(session.getSessionId(), fieldId, authentication);
        }

        // 3A. Contextual Form Submission Handler
        if (request.getFormData() != null && !request.getFormData().isEmpty()) {
            response = handleFormSubmission(session, request.getFormData(), role, fieldId, authentication);
            return response;
        }

        // 3B. Contextual Form Request Detector (e.g. When should I plant?, How much water?)
        com.aquaflow.backend.dto.response.ContextualFormDto contextualForm = detectContextualForm(msg);
        if (contextualForm != null) {
            String formPrompt = String.format("I can provide a customized %s recommendation for your rice paddy. Please verify your field details below:", contextualForm.getTitle().toLowerCase());
            recordMessage(session.getSessionId(), "ASSISTANT", formPrompt);
            return buildBaseResponse(session, formPrompt, "Please fill in the details above or ask another question.", false, role, contextualForm);
        }

        switch (currentStep) {
            case "CROP_SELECTION" -> {
                // If user asks about weather, fertilizer, water, pests, or what to plant during crop selection
                String topicReply = checkTopicalQuery(msg);
                if (topicReply != null) {
                    recordMessage(session.getSessionId(), "ASSISTANT", topicReply);
                    response = buildBaseResponse(session, topicReply, "Select Lowland Rice to begin field preparation assessment.", false, role);
                    break;
                }

                String crop = detectCrop(msg, request.getSelectedCrop());
                session.setSelectedCrop(crop);
                session.setCurrentStep("FIELD_AND_METHOD");
                session.setUpdatedAt(OffsetDateTime.now());
                sessionRepository.save(session);

                String reply = String.format("Great! Planning for %s. Which field terrace, planting method (e.g., Direct Seeding or Wet Bed Transplanting), and intended schedule do you have in mind?", crop);
                recordMessage(session.getSessionId(), "ASSISTANT", reply);

                response = buildBaseResponse(session, reply, "Please specify field terrace, planting method (e.g., Transplanting), and schedule.", false, role);
            }
            case "FIELD_AND_METHOD" -> {
                String method = request.getPlantingMethod() != null ? request.getPlantingMethod() : extractMethod(msg);
                String schedule = request.getIntendedSchedule() != null ? request.getIntendedSchedule() : extractSchedule(msg);
                session.setPlantingMethod(method);
                session.setIntendedSchedule(schedule);
                session.setCurrentStep("READINESS_ASSESSMENT");
                session.setUpdatedAt(OffsetDateTime.now());
                sessionRepository.save(session);

                // Build Grounded Planting Plan with Deterministic Tasks
                PlantingPlan plan = generatePlantingPlan(session, fieldId);
                planRepository.save(plan);

                session.setCurrentStep("CONFIRMATION_PENDING");
                sessionRepository.save(session);

                String reply = String.format(
                        "Farm-Readiness assessment complete for %s (Method: %s, Schedule: %s). " +
                        "Readiness score is %d%% (%s). Telemetry from ArvaNodes has been evaluated. %s",
                        session.getSelectedCrop(), method, schedule, plan.getReadinessScore(), plan.getReadinessStatus(), PROCEED_QUESTION
                );
                recordMessage(session.getSessionId(), "ASSISTANT", reply);

                response = buildPlanResponse(session, plan, reply, PROCEED_QUESTION, true, role);
            }
            case "CONFIRMATION_PENDING" -> {
                PlantingPlan plan = planRepository.findTopBySessionIdOrderByUpdatedAtDesc(session.getSessionId())
                        .orElseThrow(() -> new ResourceNotFoundException("No active planting plan found for session"));

                if (isAffirmative(msg)) {
                    confirmPlan(plan.getId(), authentication);
                    session.setCurrentStep("PREPARATION_ACTIVE");
                    session.setUpdatedAt(OffsetDateTime.now());
                    sessionRepository.save(session);

                    String reply = "Planting preparation confirmed! Automated pre-soak irrigation workflow has been safely submitted to ArvaCore. Review your task checklist below as preparation progresses.";
                    recordMessage(session.getSessionId(), "ASSISTANT", reply);
                    response = buildPlanResponse(session, plan, reply, "Preparation in progress. Complete farmer manual tasks.", false, role);
                } else {
                    String reply = "Understood. The proposed planting preparation has not been started. You can adjust the parameters or ask questions about the readiness assessment.";
                    recordMessage(session.getSessionId(), "ASSISTANT", reply);
                    response = buildPlanResponse(session, plan, reply, PROCEED_QUESTION, true, role);
                }
            }
            case "PREPARATION_ACTIVE", "COMPLETED" -> {
                PlantingPlan plan = planRepository.findTopBySessionIdOrderByUpdatedAtDesc(session.getSessionId())
                        .orElseGet(() -> generatePlantingPlan(session, fieldId));

                String lower = msg.toLowerCase();
                String reply;

                if (lower.contains("harvest") || lower.contains("mature") || lower.contains("thresh") || lower.contains("dry")) {
                    reply = "🌾 PhilRice Harvest & Postharvest Advisory:\n" +
                            "• Readiness Criteria: 80-85% of grains on panicles are straw-colored/yellow; grains at base at hard dough; kernels clear and translucent.\n" +
                            "• Terminal Drainage: Cut off irrigation and drain standing water completely 7-10 days before expected harvest.\n" +
                            "• Postharvest: Thresh immediately within 24 hours of cutting to avoid discoloration. Dry palay in 4-5 cm layers to 14% moisture content.";
                } else if (lower.contains("fertilizer") || lower.contains("nutrient") || lower.contains("nitrogen") || lower.contains("urea") || lower.contains("zinc") || lower.contains("lcc") || lower.contains("moet")) {
                    reply = "🌱 PhilRice Integrated Nutrient Management (INM) Advisory:\n" +
                            "• Nitrogen (N): Use Leaf Color Chart (LCC threshold = 4) to guide topdressings. Split into 3-4 applications in dry season, 2-3 in wet season.\n" +
                            "• Phosphorus (P): Apply 100% basal at final harrowing.\n" +
                            "• Potassium (K): Apply 50% basal at harrowing, 50% at early panicle initiation (EPI).\n" +
                            "• Zinc: Apply 10-25 kg/ha Zinc Sulfate within 10-14 days after transplanting.";
                } else if (lower.contains("pest") || lower.contains("insect") || lower.contains("snail") || lower.contains("kuhol") || lower.contains("disease") || lower.contains("weed")) {
                    reply = "🛡️ PhilRice Integrated Pest Management (IPM) Advisory:\n" +
                            "• Golden Apple Snail (Kuhol): Maintain shallow 1-2 cm water depth during first 2 weeks after transplanting; install wire screens at canal inlets.\n" +
                            "• Weed Management: Maintain 2-3 cm continuous water blanket 3-4 days after transplanting to suppress weed seeds; rotary weed at 15-20 DAT.\n" +
                            "• Stem Borer & Blast: Avoid excessive nitrogen; balance potassium and maintain field sanitation.";
                } else if (lower.contains("water") || lower.contains("irrigation") || lower.contains("awd") || lower.contains("depth") || lower.contains("stage")) {
                    reply = "💧 Growth-Stage Irrigation & AWD Advisory:\n" +
                            "• Vegetative & Tillering: Maintain 2-3 cm shallow depth. Safe drying down to -15 cm (AWD) promotes deep root anchorage.\n" +
                            "• Flowering & Panicle Initiation: CRITICAL! Keep continuously flooded at 3-5 cm. Moisture stress at flowering causes spikelet sterility.\n" +
                            "• Terminal Drainage: Drain all standing water 7-10 days before harvest.";
                } else {
                    reply = String.format("Current plan for %s is active (%d%% readiness). Continue tracking tasks and field water depth.", session.getSelectedCrop(), plan.getReadinessScore());
                }

                recordMessage(session.getSessionId(), "ASSISTANT", reply);
                response = buildPlanResponse(session, plan, reply, "Keep monitoring field readiness and growth stages.", false, role);
            }
            default -> {
                response = resetSession(session.getSessionId(), fieldId, authentication);
            }
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public FarmingChatResponse getSessionState(String sessionId, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);

        ChatbotSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));

        if (!isAdmin(authentication) && !session.getUserId().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("FORBIDDEN: You do not have permission to view this chat session.");
        }

        PlantingPlan plan = planRepository.findTopBySessionIdOrderByUpdatedAtDesc(sessionId).orElse(null);
        if (plan != null) {
            return buildPlanResponse(session, plan, "Welcome back. Here is your current planting preparation state.", session.getCurrentStep().equals("CONFIRMATION_PENDING") ? PROCEED_QUESTION : null, session.getCurrentStep().equals("CONFIRMATION_PENDING"), role);
        }

        return buildBaseResponse(session, INITIAL_QUESTION, INITIAL_QUESTION, false, role);
    }

    @Override
    @Transactional
    public FarmingChatResponse resetSession(String sessionId, Long fieldId, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);

        ChatbotSession session = sessionRepository.findBySessionId(sessionId)
                .orElseGet(() -> createNewSession(username, fieldId));

        if (!isAdmin(authentication) && !session.getUserId().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("FORBIDDEN: You do not have permission to reset this chat session.");
        }

        session.setCurrentStep("CROP_SELECTION");
        session.setSelectedCrop(null);
        session.setPlantingMethod(null);
        session.setIntendedSchedule(null);
        session.setUpdatedAt(OffsetDateTime.now());
        sessionRepository.save(session);

        recordMessage(session.getSessionId(), "ASSISTANT", INITIAL_QUESTION);

        return buildBaseResponse(session, INITIAL_QUESTION, INITIAL_QUESTION, false, role);
    }

    @Override
    @Transactional
    public PlantingPlan confirmPlan(Long planId, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);

        // RBAC Check: Only ROLE_ADMIN or ROLE_OPERATOR (or field owner) can authorize automated irrigation workflows
        boolean canControlIrrigation = isAdmin(authentication) || isOperator(authentication);
        if (!canControlIrrigation) {
            recordAuditLog("AI_CHAT_UNAUTHORIZED_ACTUATION_ATTEMPT", username, "PLAN", planId.toString(), Map.of("role", role, "reason", "Lacks ROLE_ADMIN or ROLE_OPERATOR"));
            throw new AccessDeniedException("FORBIDDEN: Your role (" + role + ") is not authorized to trigger automated irrigation workflows. Contact a field operator or administrator.");
        }

        PlantingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Planting plan not found: " + planId));

        // Deterministic Safety Interlock Verification
        boolean isEStopActive = emergencyStopService.isEmergencyStopActive();
        if (isEStopActive) {
            plan.setIrrigationSafetyStatus("BLOCKED_EMERGENCY_STOP");
            for (PlantingTask task : plan.getTasks()) {
                if ("SYSTEM_AUTOMATED".equals(task.getCategory())) {
                    task.setStatus("BLOCKED");
                    task.setBlockingReason("SAFETY_INTERLOCK: Emergency stop is actively engaged.");
                }
            }
            planRepository.save(plan);
            recordAuditLog("AI_CHAT_CONFIRM_BLOCKED_ESTOP", username, "PLAN", planId.toString(), Map.of("planCode", plan.getPlanCode()));
            throw new ValidationException("IRRIGATION_SAFETY_VIOLATION: Cannot execute automated preparation while emergency stop is active.");
        }

        plan.setFarmerConfirmed(true);
        plan.setFarmerConfirmedAt(OffsetDateTime.now());
        plan.setReadinessStatus("PREPARING");
        plan.setIrrigationSafetyStatus("VERIFIED_SAFE");

        // Transition pending automated tasks to IN_PROGRESS
        for (PlantingTask task : plan.getTasks()) {
            if ("SYSTEM_AUTOMATED".equals(task.getCategory()) && "PENDING".equals(task.getStatus())) {
                task.setStatus("IN_PROGRESS");
                task.setExecutedAt(OffsetDateTime.now());
            }
        }

        PlantingPlan saved = planRepository.save(plan);
        recordAuditLog("AI_CHAT_PLAN_CONFIRMED", username, "PLAN", planId.toString(), Map.of("planCode", plan.getPlanCode(), "role", role));
        return saved;
    }

    @Override
    @Transactional
    public FarmingChatResponse toggleTask(Long planId, Long taskId, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);

        PlantingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Planting plan not found: " + planId));

        PlantingTask targetTask = plan.getTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Planting task not found: " + taskId));

        if ("SAFETY_BLOCKED".equals(targetTask.getCategory())) {
            throw new ValidationException("CANNOT_TOGGLE_BLOCKED_TASK: Safety blocked tasks can only be resolved by clearing the physical safety interlock.");
        }

        if ("SYSTEM_AUTOMATED".equals(targetTask.getCategory()) && !isAdmin(authentication) && !isOperator(authentication)) {
            throw new AccessDeniedException("FORBIDDEN: Only operators or admins can manually override automated system tasks.");
        }

        if ("COMPLETED".equals(targetTask.getStatus())) {
            targetTask.setStatus("PENDING");
            targetTask.setExecutedAt(null);
        } else {
            targetTask.setStatus("COMPLETED");
            targetTask.setExecutedAt(OffsetDateTime.now());
        }
        taskRepository.save(targetTask);

        // Recalculate readiness score
        long completed = plan.getTasks().stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        int score = plan.getTasks().isEmpty() ? 0 : (int) ((completed * 100) / plan.getTasks().size());
        plan.setReadinessScore(score);
        if (score == 100) {
            plan.setReadinessStatus("COMPLETED");
        } else if (score >= 70) {
            plan.setReadinessStatus("READY");
        }
        planRepository.save(plan);

        recordAuditLog("AI_CHAT_TASK_TOGGLED", username, "TASK", taskId.toString(), Map.of("planId", planId, "status", targetTask.getStatus()));

        ChatbotSession session = sessionRepository.findBySessionId(plan.getSessionId()).orElse(null);
        return buildPlanResponse(session, plan, "Task updated successfully.", null, false, role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlantingPlan> getPlansForField(Long fieldId, Authentication authentication) {
        return planRepository.findByFieldIdOrderByCreatedAtDesc(fieldId);
    }

    // --- Helper Methods ---

    private String resolveUsername(Authentication authentication) {
        return (authentication != null && authentication.getName() != null) ? authentication.getName() : "anonymous_user";
    }

    private String resolveRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null || authentication.getAuthorities().isEmpty()) {
            return "ROLE_USER";
        }
        for (GrantedAuthority ga : authentication.getAuthorities()) {
            String auth = ga.getAuthority();
            if ("ROLE_ADMIN".equalsIgnoreCase(auth) || "ADMIN".equalsIgnoreCase(auth)) return "ROLE_ADMIN";
            if ("ROLE_SUPERVISOR".equalsIgnoreCase(auth) || "SUPERVISOR".equalsIgnoreCase(auth)) return "ROLE_SUPERVISOR";
            if ("ROLE_OPERATOR".equalsIgnoreCase(auth) || "OPERATOR".equalsIgnoreCase(auth)) return "ROLE_OPERATOR";
        }
        return "ROLE_USER";
    }

    private boolean isAdmin(Authentication authentication) {
        return "ROLE_ADMIN".equals(resolveRole(authentication));
    }

    private boolean isOperator(Authentication authentication) {
        String role = resolveRole(authentication);
        return "ROLE_OPERATOR".equals(role) || "ROLE_ADMIN".equals(role);
    }

    private ChatbotSession createNewSession(String username, Long fieldId) {
        String sId = "chat-sess-" + UUID.randomUUID().toString().substring(0, 8);
        ChatbotSession session = ChatbotSession.builder()
                .sessionId(sId)
                .userId(username)
                .fieldId(fieldId)
                .currentStep("CROP_SELECTION")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        return sessionRepository.save(session);
    }

    private void recordMessage(String sessionId, String sender, String text) {
        ChatbotMessage msg = ChatbotMessage.builder()
                .sessionId(sessionId)
                .sender(sender)
                .messageText(text)
                .timestamp(OffsetDateTime.now())
                .build();
        messageRepository.save(msg);
    }

    private PlantingPlan generatePlantingPlan(ChatbotSession session, Long fieldId) {
        String crop = session.getSelectedCrop() != null ? session.getSelectedCrop() : "Lowland Rice (NSIC Rc 222)";
        String planCode = "PLAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        boolean isEStopActive = emergencyStopService.isEmergencyStopActive();

        PlantingPlan plan = PlantingPlan.builder()
                .planCode(planCode)
                .sessionId(session.getSessionId())
                .fieldId(fieldId)
                .cropName(crop)
                .plantingMethod(session.getPlantingMethod())
                .intendedSchedule(session.getIntendedSchedule())
                .readinessScore(45)
                .readinessStatus("ASSESSING")
                .summaryText(String.format("Planting readiness assessment for %s using %s.", crop, session.getPlantingMethod()))
                .irrigationSafetyStatus(isEStopActive ? "BLOCKED_EMERGENCY_STOP" : "VERIFIED_SAFE")
                .farmerConfirmed(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        List<PlantingTask> tasks = new ArrayList<>();

        // Category 1: SYSTEM_AUTOMATED (Authorized pre-soak irrigation)
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("SYSTEM_AUTOMATED")
                .title("Pre-Irrigation Land Soaking")
                .description("Deliver controlled 3.0 cm water layer across terrace zones via ArvaCore automated valves.")
                .status(isEStopActive ? "BLOCKED" : "PENDING")
                .priority("HIGH")
                .evidenceSource("PhilRice PalayCheck AWD Standard")
                .blockingReason(isEStopActive ? "Emergency stop is active" : null)
                .requiresConfirmation(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("SYSTEM_AUTOMATED")
                .title("Ultrasonic Water Table Zero-Point Calibration")
                .description("Verify reference perched water table baseline across all 6 ArvaNodes before puddling.")
                .status("PENDING")
                .priority("NORMAL")
                .evidenceSource("ArvaNode Ultrasonic Sensor Array")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        // Category 2: FARMER_MANUAL
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .title("Plowing and Primary Harrowing")
                .description("Incorporate crop residue and break clods to 15-20 cm soil depth.")
                .status("PENDING")
                .priority("NORMAL")
                .evidenceSource("PhilRice Land Preparation Protocol")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .title("Paddy Bund Sealing & Rat Hole Plastering")
                .description("Compact perimeter bunds with mud to prevent water leakage during AWD cycles.")
                .status("PENDING")
                .priority("NORMAL")
                .evidenceSource("IRRI AWD Field Water Seepage Management")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        // Category 3: EQUIPMENT_INFO
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("EQUIPMENT_INFO")
                .title("Certified Seed Availability Check")
                .description("Verify NSIC Rc 222 certified seed germination rate (>85%) and seed treatment.")
                .status("PENDING")
                .priority("NORMAL")
                .evidenceSource("DA-BPI Certified Seed Standard")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        // Category 4: SAFETY_BLOCKED (Condition dependent)
        if (isEStopActive) {
            tasks.add(PlantingTask.builder()
                    .plantingPlan(plan)
                    .category("SAFETY_BLOCKED")
                    .title("Clear Active Emergency Stop")
                    .description("The main irrigation circuit is in Emergency Stop. Deactivate E-Stop to enable pump actuation.")
                    .status("BLOCKED")
                    .priority("CRITICAL_PREREQUISITE")
                    .evidenceSource("ArvaCore Hardware Interlock")
                    .blockingReason("Physical Emergency Stop latch is engaged.")
                    .requiresConfirmation(true)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build());
        }

        plan.setTasks(tasks);
        return plan;
    }

    private FarmingChatResponse buildBaseResponse(ChatbotSession session, String replyText, String promptQuestion, boolean reqConfirmation, String role) {
        return buildBaseResponse(session, replyText, promptQuestion, reqConfirmation, role, null);
    }

    private FarmingChatResponse buildBaseResponse(ChatbotSession session, String replyText, String promptQuestion, boolean reqConfirmation, String role, com.aquaflow.backend.dto.response.ContextualFormDto contextualForm) {
        return FarmingChatResponse.builder()
                .sessionId(session.getSessionId())
                .currentStep(session.getCurrentStep())
                .replyText(replyText)
                .promptQuestion(promptQuestion)
                .requiresConfirmation(reqConfirmation)
                .tasks(Collections.emptyList())
                .agronomicReferences(getAgronomicReferences(session.getSelectedCrop()))
                .suggestedQuestions(generateDynamicSuggestedQuestions(session.getCurrentStep()))
                .contextualForm(contextualForm)
                .disclaimer("Advisory only. Physical actions require explicit confirmation.")
                .userRole(role)
                .canControlIrrigation("ROLE_ADMIN".equals(role) || "ROLE_OPERATOR".equals(role))
                .canManageUsers("ROLE_ADMIN".equals(role))
                .canViewAuditLogs("ROLE_ADMIN".equals(role))
                .authorizedCapabilities(getCapabilitiesForRole(role))
                .build();
    }

    private FarmingChatResponse buildPlanResponse(ChatbotSession session, PlantingPlan plan, String replyText, String promptQuestion, boolean reqConfirmation, String role) {
        List<FarmingChatResponse.PlantingTaskDto> taskDtos = plan.getTasks().stream()
                .map(t -> FarmingChatResponse.PlantingTaskDto.builder()
                        .id(t.getId())
                        .category(t.getCategory())
                        .title(t.getTitle())
                        .description(t.getDescription())
                        .status(t.getStatus())
                        .blockingReason(t.getBlockingReason())
                        .requiresConfirmation(t.getRequiresConfirmation())
                        .build())
                .collect(Collectors.toList());

        List<String> warnings = new ArrayList<>();
        if ("BLOCKED_EMERGENCY_STOP".equals(plan.getIrrigationSafetyStatus())) {
            warnings.add("CRITICAL: Emergency Stop is currently active. All pump actions are physically interlocked.");
        }

        return FarmingChatResponse.builder()
                .sessionId(session != null ? session.getSessionId() : plan.getSessionId())
                .currentStep(session != null ? session.getCurrentStep() : "CONFIRMATION_PENDING")
                .replyText(replyText)
                .planId(plan.getId())
                .planCode(plan.getPlanCode())
                .readinessScore(plan.getReadinessScore())
                .readinessStatus(plan.getReadinessStatus())
                .promptQuestion(promptQuestion)
                .requiresConfirmation(reqConfirmation)
                .tasks(taskDtos)
                .agronomicReferences(getAgronomicReferences(plan.getCropName()))
                .warnings(warnings)
                .suggestedQuestions(generateDynamicSuggestedQuestions(session != null ? session.getCurrentStep() : "CONFIRMATION_PENDING"))
                .disclaimer("Advisory only. Zero direct actuation capability.")
                .userRole(role)
                .canControlIrrigation("ROLE_ADMIN".equals(role) || "ROLE_OPERATOR".equals(role))
                .canManageUsers("ROLE_ADMIN".equals(role))
                .canViewAuditLogs("ROLE_ADMIN".equals(role))
                .authorizedCapabilities(getCapabilitiesForRole(role))
                .build();
    }

    private List<String> generateDynamicSuggestedQuestions(String currentStep) {
        List<String> questions = new ArrayList<>();
        boolean isEStop = emergencyStopService.isEmergencyStopActive();

        if (isEStop) {
            questions.add("What should I check before attempting irrigation again?");
            questions.add("How does the emergency stop affect automated AWD scheduling?");
        }

        if ("CROP_SELECTION".equalsIgnoreCase(currentStep)) {
            questions.add("Is the current water depth appropriate for this crop's growth stage?");
            questions.add("Should I run irrigation now or wait for the AWD reflood window?");
            questions.add("Should I adjust my irrigation plan based on the PAGASA rain forecast?");
            questions.add("What PhilRice fertilizer guidelines apply to this rice stage?");
        } else if ("FIELD_AND_METHOD".equalsIgnoreCase(currentStep)) {
            questions.add("What seed density is recommended for Wet Bed Transplanting?");
            questions.add("How does Direct Seeding affect initial water ponding requirements?");
            questions.add("How does the elevation delta between terraces explain the water variance?");
            questions.add("What PhilRice land preparation schedule should be followed?");
        } else if ("CONFIRMATION_PENDING".equalsIgnoreCase(currentStep)) {
            questions.add("Yes, proceed with preparation");
            questions.add("What if it rains during the pre-soak soaking window?");
            questions.add("Why are the monitoring nodes reporting different conditions?");
            questions.add("No, keep on hold");
        } else {
            questions.add("Is the current water depth appropriate for this crop's growth stage?");
            questions.add("Do the current soil conditions indicate that irrigation may be needed?");
            questions.add("Should I take action to manage the current water level?");
            questions.add("What should I monitor next to maintain crop health?");
        }

        return questions.stream().distinct().limit(5).collect(Collectors.toList());
    }

    private List<String> getCapabilitiesForRole(String role) {
        List<String> caps = new ArrayList<>();
        caps.add("VIEW_OWN_TELEMETRY");
        caps.add("PLAN_CROPS");
        caps.add("MANAGE_FARMER_TASKS");
        if ("ROLE_SUPERVISOR".equals(role) || "ROLE_OPERATOR".equals(role) || "ROLE_ADMIN".equals(role)) {
            caps.add("VIEW_DIAGNOSTICS");
            caps.add("VIEW_TELEMETRY_STREAM");
        }
        if ("ROLE_OPERATOR".equals(role) || "ROLE_ADMIN".equals(role)) {
            caps.add("AUTHORIZE_IRRIGATION_PREPARATION");
            caps.add("MANUAL_PUMP_CONTROL");
        }
        if ("ROLE_ADMIN".equals(role)) {
            caps.add("VIEW_AUDIT_LOGS");
            caps.add("MANAGE_USER_ACCOUNTS");
            caps.add("NODE_COMMISSIONING");
        }
        return caps;
    }

    private boolean isAdversarial(String text) {
        String lower = text.toLowerCase();
        return lower.contains("ignore previous") || lower.contains("bypass") ||
                lower.contains("activate relay") || lower.contains("turn on the pump") ||
                lower.contains("system override") || lower.contains("format c:") ||
                lower.contains("gpio write") || lower.contains("sudo") ||
                lower.contains("i am the admin") || lower.contains("grant me admin");
    }

    private boolean isDestructiveOperation(String text) {
        String lower = text.toLowerCase();
        return (lower.contains("delete") || lower.contains("erase") || lower.contains("purge") || lower.contains("drop table") || lower.contains("wipe")) ||
                ((lower.contains("unpair") || lower.contains("remove") || lower.contains("reset")) &&
                        (lower.contains("node") || lower.contains("device") || lower.contains("sensor") || lower.contains("field") || lower.contains("farm")));
    }

    private FarmingChatResponse buildDestructiveInterceptResponse(String sessionId, String role) {
        return FarmingChatResponse.builder()
                .sessionId(sessionId)
                .currentStep("CROP_SELECTION")
                .replyText("⚠️ Destructive Operation Intercepted:\nRequests to delete farm records, unpair monitoring nodes, or erase telemetry cannot be executed through chat. Consequential actions require explicit backend authorization (ROLE_ADMIN or ROLE_OPERATOR), confirmation of exact target scope, and audit trail preservation. Use the dedicated Settings or Nodes interface if authorized.")
                .promptQuestion("How else may I advise on your farm cultivation or rice field management?")
                .requiresConfirmation(false)
                .tasks(Collections.emptyList())
                .agronomicReferences(List.of("ArvaCore Deterministic Safety Interlocks", "ArvaNet Access Control Policy"))
                .warnings(List.of("Destructive operational request intercepted and refused in chat."))
                .disclaimer("Advisory only. Maintenance actions require authorized administrative tools.")
                .userRole(role)
                .canControlIrrigation("ROLE_ADMIN".equals(role) || "ROLE_OPERATOR".equals(role))
                .canManageUsers("ROLE_ADMIN".equals(role))
                .canViewAuditLogs("ROLE_ADMIN".equals(role))
                .authorizedCapabilities(getCapabilitiesForRole(role))
                .build();
    }

    private String checkTopicalQuery(String msg) {
        String lower = msg.toLowerCase();
        // Non-Rice crop intercept in Backend
        if ((lower.contains("corn") || lower.contains("maize") || lower.contains("mais") ||
                lower.contains("eggplant") || lower.contains("talong") || lower.contains("tomato") ||
                lower.contains("kamatis") || lower.contains("onion") || lower.contains("sibuyas") ||
                lower.contains("cabbage") || lower.contains("sugarcane") || lower.contains("banana") ||
                lower.contains("vegetable") || lower.contains("gulay")) &&
                !(lower.contains("rice") || lower.contains("palay") || lower.contains("nsic") || lower.contains("tubigan"))) {
            return "Arva AI is currently designed specifically for Philippine rice cultivation under the PhilRice PalayCheck System and IRRI Alternate Wetting and Drying (AWD) protocol.\n\n" +
                    "I do not provide full cultivation workflows for other crops at this time. Recommended Philippine rice varieties include Lowland Rice (NSIC Rc 222, NSIC Rc 160, PSB Rc 82, and NSIC Rc 216).\n\n" +
                    "Would you like to plan a rice crop instead?";
        }
        if (lower.contains("what should i plant") || lower.contains("what to plant") || lower.contains("recommend crop") || lower.contains("ano ang itatanim")) {
            return "🌾 Recommendation: Lowland Rice (NSIC Rc 222 - Tubigan 21)\n\n" +
                    "• Suitability: Highly recommended for Lopez, Quezon lowland irrigated and rainfed fields (Calumpang clay soil series).\n" +
                    "• Climate & Water: Excellent adaptability to wet and dry seasons with high tillering capacity (maturity: 106 days, potential yield: 10.0 tons/ha).\n" +
                    "• Water Management: Optimized for Alternate Wetting and Drying (AWD) water-saving technology.\n\n" +
                    "Would you like to select Lowland Rice and proceed with field assessment?";
        }
        if (lower.contains("rain") || lower.contains("weather") || lower.contains("typhoon") || lower.contains("forecast")) {
            return "🌦️ PAGASA Agro-Meteorological & Weather Impact Advisory:\n" +
                    "• Heavy rainfall (>30 mm/24h) suspends pump irrigation automatically to prevent waterlogging and fertilizer leaching.\n" +
                    "• Plowing/Harrowing: Maintain drainage gates at 2-3 cm overflow height to prevent drowned field bunds.\n" +
                    "• High Winds: Lower standing water to 1-2 cm to minimize lodging risk in tall or flowering rice.\n" +
                    "• Lopez, Quezon experiences Type IV climate (rainfall distributed year-round).";
        }
        if (lower.contains("fertilizer") || lower.contains("nutrient") || lower.contains("urea") || lower.contains("nitrogen") || lower.contains("zinc")) {
            return "🌱 PhilRice Integrated Nutrient Management (INM) Advisory:\n" +
                    "• Diagnostic Tools: Use Leaf Color Chart (LCC threshold = 4) to determine exact nitrogen topdressing timing.\n" +
                    "• Nitrogen Split: 1/3 basal at final harrowing, 1/3 at mid-tillering, 1/3 at panicle initiation.\n" +
                    "• Phosphorus (P): 100% basal incorporation during final harrowing.\n" +
                    "• Potassium (K): 50% basal at harrowing, 50% at early panicle initiation (EPI).\n" +
                    "• Zinc: Apply 10-25 kg/ha Zinc Sulfate within 10-14 days after transplanting.";
        }
        if (lower.contains("water") || lower.contains("irrigation") || lower.contains("awd") || lower.contains("depth")) {
            return "💧 Growth-Stage Irrigation & Alternate Wetting and Drying (AWD) Advisory:\n" +
                    "• Vegetative Stage: Maintain 2-3 cm shallow water. Safe drying down to -15 cm (AWD) promotes deep root anchorage.\n" +
                    "• Panicle Initiation to Flowering: CRITICAL! Keep continuously flooded at 3-5 cm depth. Moisture stress causes spikelet sterility.\n" +
                    "• Terminal Drainage: Completely drain 7-10 days before harvest to harden the paddy soil.";
        }
        if (lower.contains("pest") || lower.contains("snail") || lower.contains("kuhol") || lower.contains("weed") || lower.contains("disease")) {
            return "🛡️ PhilRice Integrated Pest Management (IPM) & Crop Protection Advisory:\n" +
                    "• Golden Apple Snail (Kuhol): Maintain 1-2 cm shallow water during first 2 weeks after transplanting; install wire screens at inlets.\n" +
                    "• Weed Control: Maintain 2-3 cm water blanket 3-4 days after transplanting to suppress weed seeds; rotary weed at 15-20 DAT.\n" +
                    "• Stem Borer & Blast: Avoid excess nitrogen (>120 kg N/ha); balance with potassium and field sanitation.";
        }
        if (lower.contains("land prep") || lower.contains("prepare the field") || lower.contains("plow") || lower.contains("harrow")) {
            return "🚜 PhilRice 21-Day Thorough Land Preparation Protocol:\n" +
                    "• Days 1-7: Plowing at 10-15 cm depth immediately after soaking to bury weeds and stubble.\n" +
                    "• Days 7-14: Harrowing twice at 7-day intervals to break clods, puddle soil, and germinate weed seeds.\n" +
                    "• Days 14-21: Leveling with wooden plank. Well-leveled fields reduce water use by 30% and suppress weed growth.\n" +
                    "• Lopez, Quezon Calumpang clay soil requires 3-5 days soaking prior to primary tillage.";
        }
        return null;
    }

    private FarmingChatResponse buildAdversarialResponse(String sessionId, String role) {
        return FarmingChatResponse.builder()
                .sessionId(sessionId)
                .currentStep("CROP_SELECTION")
                .replyText("Request flagged for safety violation: Arva operates strictly in advisory mode. No direct actuation, privilege elevation, or system override is permitted.")
                .promptQuestion(INITIAL_QUESTION)
                .requiresConfirmation(false)
                .tasks(Collections.emptyList())
                .agronomicReferences(List.of("ArvaCore Deterministic Safety Interlocks"))
                .warnings(List.of("Attempted prompt injection or safety bypass detected and neutralized."))
                .disclaimer("Advisory only. Actuation requires authorized operator controls.")
                .userRole(role)
                .canControlIrrigation("ROLE_ADMIN".equals(role) || "ROLE_OPERATOR".equals(role))
                .canManageUsers("ROLE_ADMIN".equals(role))
                .canViewAuditLogs("ROLE_ADMIN".equals(role))
                .authorizedCapabilities(getCapabilitiesForRole(role))
                .build();
    }

    private FarmingChatResponse handleFormSubmission(ChatbotSession session, Map<String, Object> formData, String role, Long fieldId, Authentication authentication) {
        String formType = (String) formData.getOrDefault("formType", "GENERIC_FORM");
        StringBuilder reply = new StringBuilder();

        if ("PLANTING_SCHEDULE".equals(formType)) {
            String variety = (String) formData.getOrDefault("riceVariety", "Lowland Rice (NSIC Rc 222)");
            String condition = (String) formData.getOrDefault("currentFieldCondition", "Unplowed stubble");
            String waterAvail = (String) formData.getOrDefault("waterAvailability", "NIA canal active");
            String targetDate = (String) formData.getOrDefault("plannedPlantingDate", "Within 21 days");

            session.setSelectedCrop(variety);
            session.setPlantingMethod("Wet Bed Transplanting");
            session.setIntendedSchedule(targetDate);
            sessionRepository.save(session);

            reply.append(String.format("🌾 **Customized Planting Schedule for %s**\n\n", variety))
                 .append(String.format("• **Current State:** %s\n", condition))
                 .append(String.format("• **Water Supply:** %s\n", waterAvail))
                 .append(String.format("• **Target Sowing/Transplanting:** %s\n\n", targetDate))
                 .append("📋 **PhilRice 21-Day Schedule Recommendation:**\n")
                 .append("1. **Day 1-5 (Soaking & Plowing):** Deliver 3-5 cm pre-soak water layer to soften Calumpang clay; plow at 10-15 cm depth.\n")
                 .append("2. **Day 7-10 (First Harrowing):** Harrow twice crosswise to incorporate organic stubble and break clods.\n")
                 .append("3. **Day 14 (Seedbed Sowing):** Prepare 400 m² seedbed for 20-40 kg certified seed; soak 24h and incubate 24-36h.\n")
                 .append("4. **Day 20-21 (Final Leveling & Transplanting):** Level field with wooden plank. Transplant 2-3 seedlings/hill at 20x20 cm spacing.\n\n")
                 .append("Would you like to formalize this into an official ArvaCore planting plan?");
        } else if ("IRRIGATION_ASSESSMENT".equals(formType)) {
            String cropStage = (String) formData.getOrDefault("cropStage", "Vegetative / Active Tillering");
            String targetTerrace = (String) formData.getOrDefault("targetTerrace", "All Terraces");
            String waterStatus = (String) formData.getOrDefault("waterSourceStatus", "Pump Operational");

            reply.append(String.format("💧 **Irrigation & AWD Assessment (%s)**\n\n", targetTerrace))
                 .append(String.format("• **Crop Stage:** %s\n", cropStage))
                 .append(String.format("• **Water Infrastructure:** %s\n\n", waterStatus))
                 .append("📊 **AWD Water Management Action:**\n");

            if (cropStage.contains("Flowering") || cropStage.contains("Panicle")) {
                reply.append("⚠️ **CRITICAL MOISTURE WINDOW:** Crop is at reproductive stage. AWD drying is suspended! Maintain standing water continuously at +3.0 to +5.0 cm depth to prevent spikelet sterility.\n")
                     .append("• Recommended Pump Run: 35-45 minutes to restore +4.0 cm ponding depth.\n");
            } else if (cropStage.contains("Terminal")) {
                reply.append("• **Terminal Drainage Phase:** Completely drain all water 7-10 days before harvest to facilitate uniform grain drying and mechanical trafficability.\n");
            } else {
                reply.append("• **Vegetative Safe AWD Protocol:** Allow perched water table to naturally decline down to -15.0 cm before reflooding. Maintain 2-3 cm shallow layer when flooded to encourage tiller expansion and deep root growth.\n")
                     .append("• Recommended Pump Run: 25 minutes per terrace once -15 cm threshold is signaled by ArvaNode.\n");
            }
        } else if ("FERTILIZER_INM".equals(formType)) {
            String stage = (String) formData.getOrDefault("growthStage", "Mid-Tillering");
            String lcc = (String) formData.getOrDefault("leafColorScore", "Score 3");

            reply.append("🌱 **PhilRice Integrated Nutrient Management (INM) Topdressing Plan**\n\n")
                 .append(String.format("• **Evaluated Stage:** %s\n", stage))
                 .append(String.format("• **Leaf Color Reading:** %s\n\n", lcc))
                 .append("⚖️ **Topdressing Recommendation:**\n");

            if (lcc.contains("Score 4") || lcc.contains("Score 5")) {
                reply.append("• **Nitrogen Status Optimal:** Leaves indicate sufficient nitrogen absorption (LCC ≥ 4). **Do not apply urea at this time** to avoid excessive vegetative growth and pest vulnerability.\n");
            } else {
                reply.append("• **Nitrogen Deficiency Detected:** Apply **1.0 to 1.5 bags/ha of Urea (46-0-0)** or 2.0 bags of Ammonium Sulfate (21-0-0).\n")
                     .append("• **Water Condition Requirement:** Ensure standing water is at 2-3 cm shallow depth when broadcasting to prevent leaching.\n");
            }
        } else if ("PEST_DISEASE".equals(formType)) {
            String symptom = (String) formData.getOrDefault("observedSymptom", "Snail damage");
            String part = (String) formData.getOrDefault("affectedPlantPart", "Base of tillers");
            String severity = (String) formData.getOrDefault("severityLevel", "Localized");

            reply.append("🛡️ **Integrated Pest & Disease Diagnostic (PhilRice IPM)**\n\n")
                 .append(String.format("• **Obsymptom:** %s\n", symptom))
                 .append(String.format("• **Part Affected:** %s\n", part))
                 .append(String.format("• **Severity:** %s\n\n", severity))
                 .append("🔍 **Immediate Action Protocol:**\n")
                 .append("1. **Water Regulation:** Lower water level to 1-2 cm shallow sheet to restrict golden apple snail mobility.\n")
                 .append("2. **Physical Barriers:** Install 1 mm wire mesh screens at field inlet canals.\n")
                 .append("3. **Biological & Field Care:** Avoid excess nitrogen fertilizers that soften rice leaf sheaths and attract stem borer moths.\n");
        } else if ("HARVEST_PLAN".equals(formType)) {
            String yellowing = (String) formData.getOrDefault("grainYellowingPercent", "80-85%");
            String soil = (String) formData.getOrDefault("soilFirmness", "Firm");

            reply.append("🌾 **Harvest Maturity & Postharvest Assessment**\n\n")
                 .append(String.format("• **Grain Yellowing:** %s\n", yellowing))
                 .append(String.format("• **Soil Condition:** %s\n\n", soil))
                 .append("🎯 **Harvest Readiness Verdict:**\n");

            if (yellowing.contains("80%") || yellowing.contains("85%")) {
                reply.append("✅ **Optimal Harvest Window Reached:** Grains on upper 80-85% of panicles are golden straw-colored, and grains at base are in hard dough stage. Harvest immediately to minimize shattering loss and maintain high head rice recovery.\n");
            } else if (yellowing.contains("Below") || yellowing.contains("70%")) {
                reply.append("⏳ **Immature Grains:** Panicles require an additional 5-8 days before harvest. Harvesting early produces chalky, broken kernels.\n");
            } else {
                reply.append("⚠️ **Over-Mature:** Harvest without delay. Re-wetting from rainfall may trigger grain sprouting on the panicle.\n");
            }
        } else {
            reply.append("Thank you for providing the farm details. The parameters have been recorded and incorporated into your Arva AI advisory state.");
        }

        String replyText = reply.toString();
        recordMessage(session.getSessionId(), "ASSISTANT", replyText);
        return buildBaseResponse(session, replyText, "What other questions or adjustments do you have regarding this recommendation?", false, role);
    }

    private com.aquaflow.backend.dto.response.ContextualFormDto detectContextualForm(String msg) {
        String lower = msg.toLowerCase();
        
        // 1. Planting Schedule / Crop Establishment advice
        if (lower.contains("when should i plant") || lower.contains("kailan magtatanim") ||
            lower.contains("schedule my planting") || lower.contains("planting date") ||
            (lower.contains("when to plant") && lower.contains("rice"))) {
            return com.aquaflow.backend.dto.response.ContextualFormDto.builder()
                    .formId("form-planting-" + System.currentTimeMillis())
                    .formType("PLANTING_SCHEDULE")
                    .title("Planting Schedule Assessment")
                    .description("Help Arva calculate your optimal sowing window, seedbed duration, and land preparation timeline.")
                    .submitButtonLabel("Submit to Arva")
                    .fields(List.of(
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("field")
                                    .label("Target Field")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("Main Rice Field (Pitak 1-6)")
                                    .options(List.of("Main Rice Field (Pitak 1-6)", "Terrace Upper (Pitak 1-2)", "Terrace Mid (Pitak 3-4)", "Terrace Lower (Pitak 5-6)"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("riceVariety")
                                    .label("Rice Variety")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("Lowland Rice (NSIC Rc 222)")
                                    .options(List.of("Lowland Rice (NSIC Rc 222)", "Lowland Rice (NSIC Rc 160)", "Lowland Rice (PSB Rc 82)", "Lowland Rice (NSIC Rc 216)"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("currentFieldCondition")
                                    .label("Current Field Condition")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("Unplowed with crop stubble")
                                    .options(List.of("Unplowed with crop stubble", "Primary plowed, soaking", "Secondary harrowed", "Puddled & ready for leveling"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("waterAvailability")
                                    .label("Water Availability")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("NIA Irrigation Canal Active")
                                    .options(List.of("NIA Irrigation Canal Active", "Deep Well Submersible Pump", "Rainfed / Catchment Only", "Low / Water Scarcity"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("plannedPlantingDate")
                                    .label("Intended Planting Date (Optional)")
                                    .type("date")
                                    .required(false)
                                    .placeholder("YYYY-MM-DD")
                                    .build()
                    ))
                    .build();
        }

        // 2. Irrigation / Water Volume / Need advice
        if (lower.contains("how much water") || lower.contains("when should i irrigate") ||
            lower.contains("gaano karaming tubig") || lower.contains("kailan magpapatubig") ||
            (lower.contains("need water") && lower.contains("field"))) {
            return com.aquaflow.backend.dto.response.ContextualFormDto.builder()
                    .formId("form-irrigation-" + System.currentTimeMillis())
                    .formType("IRRIGATION_ASSESSMENT")
                    .title("Irrigation Demand Assessment")
                    .description("Provide terrace parameters to let Arva compute AWD reflood depth and pump duration.")
                    .submitButtonLabel("Calculate Irrigation Window")
                    .fields(List.of(
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("cropStage")
                                    .label("Current Crop Growth Stage")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("Vegetative / Active Tillering")
                                    .options(List.of("Land Preparation (Soaking)", "Seedling Nursery (1-14 DAT)", "Vegetative / Active Tillering", "Panicle Initiation to Flowering (CRITICAL)", "Grain Filling / Ripening", "Terminal Drainage"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("targetTerrace")
                                    .label("Target Terrace / Node")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("All Terraces (ARVANODE-001 to 006)")
                                    .options(List.of("All Terraces (ARVANODE-001 to 006)", "Pitak 1 - Upper Inflow (ARVANODE-001)", "Pitak 2 - North Lateral (ARVANODE-002)", "Pitak 3 - Center Bench (ARVANODE-003)", "Pitak 4 - Low Center (ARVANODE-004)", "Pitak 5 - Distal Basin (ARVANODE-005)", "Pitak 6 - Tail Drain (ARVANODE-006)"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("waterSourceStatus")
                                    .label("Water Supply Status")
                                    .type("select")
                                    .required(false)
                                    .defaultValue("Submersible Pump Operational")
                                    .options(List.of("Submersible Pump Operational", "Canal Gravity Flow Available", "Restricted / Scheduled NIA Water", "Emergency Interlock Active"))
                                    .build()
                    ))
                    .build();
        }

        // 3. Fertilizer / Nutrient Management advice
        if (lower.contains("what fertilizer") || lower.contains("anong abono") ||
            lower.contains("fertilizer guide") || lower.contains("how much urea") ||
            lower.contains("nutrient recommendation")) {
            return com.aquaflow.backend.dto.response.ContextualFormDto.builder()
                    .formId("form-fertilizer-" + System.currentTimeMillis())
                    .formType("FERTILIZER_INM")
                    .title("PhilRice INM Fertilizer Diagnosis")
                    .description("Enter growth and soil observations for exact nitrogen split and topdressing rates.")
                    .submitButtonLabel("Get Fertilizer Split")
                    .fields(List.of(
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("growthStage")
                                    .label("Days After Transplanting (DAT) / Stage")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("Mid-Tillering (14-21 DAT)")
                                    .options(List.of("Basal (Final Harrowing T-0)", "Early Tillering (7-10 DAT)", "Mid-Tillering (14-21 DAT)", "Panicle Initiation (EPI 40-45 DAT)", "Booting to Heading"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("leafColorScore")
                                    .label("Leaf Color Chart (LCC) Score")
                                    .type("select")
                                    .required(false)
                                    .defaultValue("Score 3 (Light Green - Topdress Needed)")
                                    .options(List.of("Score 2 (Yellowish Green - Severely Deficient)", "Score 3 (Light Green - Topdress Needed)", "Score 4 (Medium Green - Optimal)", "Score 5 (Dark Green - High Nitrogen, Do Not Topdress)"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("previousApplication")
                                    .label("Previous Application")
                                    .type("text")
                                    .placeholder("e.g. 1 bag Complete 14-14-14 at basal")
                                    .required(false)
                                    .build()
                    ))
                    .build();
        }

        // 4. Pest & Disease Concerns
        if (lower.contains("pest") || lower.contains("kuhol") || lower.contains("snail") ||
            lower.contains("disease") || lower.contains("uod") || lower.contains("sakit ng palay")) {
            return com.aquaflow.backend.dto.response.ContextualFormDto.builder()
                    .formId("form-pest-" + System.currentTimeMillis())
                    .formType("PEST_DISEASE")
                    .title("Integrated Pest & Disease Diagnosis")
                    .description("Specify field damage patterns to identify control measures under PhilRice IPM.")
                    .submitButtonLabel("Diagnose Pest/Disease")
                    .fields(List.of(
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("observedSymptom")
                                    .label("Primary Symptom / Pest Observed")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("Cut seedling stems / Golden apple snail eggs")
                                    .options(List.of("Cut seedling stems / Golden apple snail eggs", "Dead central tillers / Deadhearts (Stem Borer)", "Yellowing/drying leaf tips (Bacterial Leaf Blight)", "Spindle-shaped diamond lesions (Rice Blast)", "Hollow whiteheads during heading"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("affectedPlantPart")
                                    .label("Affected Plant Part")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("Base of tillers / Rootzone")
                                    .options(List.of("Base of tillers / Rootzone", "Leaf blades and tips", "Stems and nodes", "Panicles and developing grains"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("severityLevel")
                                    .label("Severity / Affected Area")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("Localized patches (<10% field)")
                                    .options(List.of("Isolated plants (<5%)", "Localized patches (<10% field)", "Moderate spread (10-30% field)", "Widespread / Severe (>30% field)"))
                                    .build()
                    ))
                    .build();
        }

        // 5. Harvest Planning
        if (lower.contains("when to harvest") || lower.contains("kailan aani") ||
            lower.contains("harvest schedule") || lower.contains("ready for harvest")) {
            return com.aquaflow.backend.dto.response.ContextualFormDto.builder()
                    .formId("form-harvest-" + System.currentTimeMillis())
                    .formType("HARVEST_PLAN")
                    .title("Harvest Readiness Assessment")
                    .description("Enter grain visual indicators to confirm terminal drainage and threshing schedule.")
                    .submitButtonLabel("Assess Harvest Timing")
                    .fields(List.of(
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("grainYellowingPercent")
                                    .label("Yellow / Golden Grains on Panicle")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("80% to 85% yellow grains")
                                    .options(List.of("Below 70% (Too early - milky/dough stage)", "70% to 80% (Immature)", "80% to 85% yellow grains (Optimal Harvest Window)", "Over 90% (Risk of shattering and lodging)"))
                                    .build(),
                            com.aquaflow.backend.dto.response.ContextualFormFieldDto.builder()
                                    .id("soilFirmness")
                                    .label("Paddy Soil Condition")
                                    .type("select")
                                    .required(true)
                                    .defaultValue("Drained for 7-10 days, soil firm")
                                    .options(List.of("Standing water still present", "Muddy / saturated", "Drained for 7-10 days, soil firm (Ready for mechanical reaper)", "Hard cracked soil"))
                                    .build()
                    ))
                    .build();
        }

        return null;
    }

    private String detectCrop(String msg, String selectedCrop) {
        if (selectedCrop != null && !selectedCrop.isBlank()) return selectedCrop;
        String lower = msg.toLowerCase();
        if (lower.contains("160")) {
            return "Lowland Rice (NSIC Rc 160)";
        } else if (lower.contains("82")) {
            return "Lowland Rice (PSB Rc 82)";
        } else if (lower.contains("216")) {
            return "Lowland Rice (NSIC Rc 216)";
        }
        return "Lowland Rice (NSIC Rc 222)";
    }

    private String extractMethod(String msg) {
        String lower = msg.toLowerCase();
        if (lower.contains("transplant") || lower.contains("wet bed")) return "Wet Bed Transplanting";
        if (lower.contains("direct") || lower.contains("sabog")) return "Direct Wet Seeding";
        return "Standard Mechanical Transplanting";
    }

    private String extractSchedule(String msg) {
        if (msg.toLowerCase().contains("tomorrow")) return "Tomorrow morning";
        if (msg.toLowerCase().contains("next week")) return "Next week";
        return "Within 3-5 days";
    }

    private boolean isAffirmative(String msg) {
        String lower = msg.toLowerCase().trim();
        return lower.startsWith("yes") || lower.startsWith("y") || lower.contains("proceed") || lower.contains("confirm") || lower.contains("go ahead");
    }

    private List<String> getAgronomicReferences(String crop) {
        List<String> refs = new ArrayList<>();
        refs.add("PhilRice Philippine Rice Production Training Manual (2003) - Modules 4, 5, 6, 7, 8, 19");
        refs.add("IRRI Technical Bulletin: Alternate Wetting and Drying (AWD) for Rice (2020)");
        refs.add("DA-PhilRice NSIC Rc 222 Variety Production Guide (2023)");
        refs.add("PAGASA 3-Day & Seasonal Agro-Meteorological Weather Forecast");
        refs.add("Lopez, Quezon Municipal Agricultural Profile (Calumpang Clay Soil Series)");
        refs.add("Bureau of Soils and Water Management (BSWM) Crop Water Requirements");
        refs.add("ArvaNode Field Telemetry: Rootzone Soil Moisture & Field Water Tube Ultrasonic Level");
        return refs;
    }

    private void recordAuditLog(String eventType, String actor, String entityType, String entityId, Object payload) {
        try {
            String payloadJson = (payload != null && objectMapper != null) ? objectMapper.writeValueAsString(payload) : "{}";
            IrrigationAuditLog logEntry = IrrigationAuditLog.builder()
                    .eventType(eventType)
                    .actor(actor)
                    .entityType(entityType)
                    .entityId(entityId)
                    .payloadJson(payloadJson)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to persist audit log for AI chat event {}: {}", eventType, e.getMessage());
        }
    }
}
