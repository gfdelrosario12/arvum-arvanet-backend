package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.ManualStartRequest;
import com.aquaflow.backend.dto.request.ManualStopRequest;
import com.aquaflow.backend.dto.response.CommandStatusResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.event.SystemEvent;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.infrastructure.event.SystemEventType;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class ManualControlServiceImpl implements ManualControlService {

    private static final Logger log = LoggerFactory.getLogger(ManualControlServiceImpl.class);

    private final FieldRepository fieldRepository;
    private final EdgeNodeRepository edgeNodeRepository;
    private final MonitoringZoneRepository monitoringZoneRepository;
    private final DownlinkQueueService downlinkQueueService;
    private final DownlinkQueueItemRepository downlinkQueueItemRepository;
    private final IrrigationAuditLogRepository auditLogRepository;
    private final SystemEventPublisher systemEventPublisher;
    private final ObjectMapper objectMapper;
    private final IrrigationCommandStateMachine stateMachine;

    // Track command responses in memory indexed by correlationId
    private final Map<String, CommandStatusResponse> statusTracker = new ConcurrentHashMap<>();

    public ManualControlServiceImpl(FieldRepository fieldRepository,
                                    EdgeNodeRepository edgeNodeRepository,
                                    MonitoringZoneRepository monitoringZoneRepository,
                                    DownlinkQueueService downlinkQueueService,
                                    DownlinkQueueItemRepository downlinkQueueItemRepository,
                                    IrrigationAuditLogRepository auditLogRepository,
                                    SystemEventPublisher systemEventPublisher,
                                    ObjectMapper objectMapper) {
        this(fieldRepository, edgeNodeRepository, monitoringZoneRepository, downlinkQueueService, downlinkQueueItemRepository, auditLogRepository, systemEventPublisher, objectMapper, null);
    }

    @Autowired
    public ManualControlServiceImpl(FieldRepository fieldRepository,
                                    EdgeNodeRepository edgeNodeRepository,
                                    MonitoringZoneRepository monitoringZoneRepository,
                                    DownlinkQueueService downlinkQueueService,
                                    DownlinkQueueItemRepository downlinkQueueItemRepository,
                                    IrrigationAuditLogRepository auditLogRepository,
                                    @Autowired(required = false) SystemEventPublisher systemEventPublisher,
                                    ObjectMapper objectMapper,
                                    @Autowired(required = false) IrrigationCommandStateMachine stateMachine) {
        this.fieldRepository = fieldRepository;
        this.edgeNodeRepository = edgeNodeRepository;
        this.monitoringZoneRepository = monitoringZoneRepository;
        this.downlinkQueueService = downlinkQueueService;
        this.downlinkQueueItemRepository = downlinkQueueItemRepository;
        this.auditLogRepository = auditLogRepository;
        this.systemEventPublisher = systemEventPublisher;
        this.objectMapper = objectMapper;
        this.stateMachine = stateMachine;
    }

    @Override
    public CommandStatusResponse startManualIrrigation(ManualStartRequest request) {
        log.info("Processing manual start request for fieldId: {} by operator: {}", request.getFieldId(), request.getOperatorId());

        Field field = fieldRepository.findById(request.getFieldId())
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + request.getFieldId()));

        if (request.getDurationMinutes() != null && (request.getDurationMinutes() < 1 || request.getDurationMinutes() > 1440)) {
            throw new ValidationException("Duration must be between 1 and 1440 minutes");
        }

        String correlationId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        boolean isSimulation = Boolean.TRUE.equals(request.getSimulationMode());

        if (stateMachine != null) {
            try {
                stateMachine.registerCommand(correlationId, "MANUAL_START", field.getId(), null, request.getOperatorId(), request.getRationale());
                stateMachine.transitionState(correlationId, isSimulation ? CommandState.COMPLETED : CommandState.QUEUED,
                        request.getOperatorId(), isSimulation ? "Simulated workflow test passed" : "Manual start queued", null, request.getOperatorId(), "OPERATOR");
            } catch (Exception e) {
                log.warn("Failed to register manual start state transition: {}", e.getMessage());
            }
        }

        // 1. Log audit entry (clearly separated if in simulation)
        saveAuditLog(isSimulation ? "SIMULATED_MANUAL_IRRIGATION_START" : "MANUAL_IRRIGATION_START",
                request.getOperatorId(), "FIELD", field.getId().toString(), request);

        // 2. Hardware intercept for simulation mode: bypass LoRaWAN/MQTT downlink dispatch
        if (isSimulation) {
            log.info("SIMULATION MODE ACTIVE: Bypassing physical actuator downlinks for correlationId: {}", correlationId);
            CommandStatusResponse simResponse = CommandStatusResponse.builder()
                    .correlationId(correlationId)
                    .fieldId(field.getId())
                    .operatorId(request.getOperatorId())
                    .status(CommandLifecycleState.EXECUTED)
                    .commandType("MANUAL_START")
                    .rationale(request.getRationale())
                    .details("Workflow Test Passed — Irrigation activation was processed successfully in simulation mode. No physical device was activated.")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            statusTracker.put(correlationId, simResponse);

            if (systemEventPublisher != null) {
                try {
                    systemEventPublisher.publish(SystemEvent.builder()
                            .eventType(SystemEventType.IRRIGATION_EXECUTION_UPDATED)
                            .fieldId(field.getId())
                            .aggregateId(correlationId)
                            .payload(simResponse)
                            .build());
                } catch (Exception e) {
                    log.warn("Failed to publish simulation event for manual start: {}", e.getMessage());
                }
            }
            return simResponse;
        }

        // 2. Build payload JSON
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "command", "MANUAL_START",
                    "operatorId", request.getOperatorId(),
                    "fieldId", request.getFieldId(),
                    "durationMinutes", request.getDurationMinutes(),
                    "overridePolicy", request.getOverridePolicy() != null ? request.getOverridePolicy() : false,
                    "correlationId", correlationId
            ));
        } catch (Exception e) {
            payloadJson = String.format("{\"command\":\"MANUAL_START\",\"fieldId\":%d,\"correlationId\":\"%s\"}", request.getFieldId(), correlationId);
        }

        // 3. Queue downlink for all edge nodes in the field
        List<MonitoringZone> zones = monitoringZoneRepository.findByFieldId(field.getId());
        for (MonitoringZone zone : zones) {
            List<EdgeNode> nodes = edgeNodeRepository.findByMonitoringZoneId(zone.getId());
            for (EdgeNode node : nodes) {
                downlinkQueueService.queueDownlink(node, payloadJson, correlationId);
            }
        }

        // 4. Record command status response
        CommandStatusResponse response = CommandStatusResponse.builder()
                .correlationId(correlationId)
                .fieldId(field.getId())
                .operatorId(request.getOperatorId())
                .status(CommandLifecycleState.QUEUED)
                .commandType("MANUAL_START")
                .rationale(request.getRationale())
                .details("Manual start queued for field " + field.getName())
                .createdAt(now)
                .updatedAt(now)
                .build();

        statusTracker.put(correlationId, response);

        // 5. Publish event
        if (systemEventPublisher != null) {
            try {
                systemEventPublisher.publish(SystemEvent.builder()
                        .eventType(SystemEventType.IRRIGATION_EXECUTION_UPDATED)
                        .fieldId(field.getId())
                        .aggregateId(correlationId)
                        .payload(response)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to publish event for manual start: {}", e.getMessage());
            }
        }

        return response;
    }

    @Override
    public CommandStatusResponse stopManualIrrigation(ManualStopRequest request) {
        log.info("Processing manual stop request for fieldId: {} by operator: {}", request.getFieldId(), request.getOperatorId());

        Field field = fieldRepository.findById(request.getFieldId())
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + request.getFieldId()));

        String correlationId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        boolean isSimulation = Boolean.TRUE.equals(request.getSimulationMode());

        if (stateMachine != null) {
            try {
                stateMachine.registerCommand(correlationId, "MANUAL_STOP", field.getId(), null, request.getOperatorId(), request.getRationale());
                stateMachine.transitionState(correlationId, isSimulation ? CommandState.COMPLETED : CommandState.QUEUED,
                        request.getOperatorId(), isSimulation ? "Simulated workflow test passed" : "Manual stop queued", null, request.getOperatorId(), "OPERATOR");
            } catch (Exception e) {
                log.warn("Failed to register manual stop state transition: {}", e.getMessage());
            }
        }

        // 1. Log audit entry
        saveAuditLog(isSimulation ? "SIMULATED_MANUAL_IRRIGATION_STOP" : "MANUAL_IRRIGATION_STOP",
                request.getOperatorId(), "FIELD", field.getId().toString(), request);

        // 2. Hardware intercept for simulation mode: bypass LoRaWAN/MQTT downlink dispatch
        if (isSimulation) {
            log.info("SIMULATION MODE ACTIVE: Bypassing physical actuator downlinks for correlationId: {}", correlationId);
            CommandStatusResponse simResponse = CommandStatusResponse.builder()
                    .correlationId(correlationId)
                    .fieldId(field.getId())
                    .operatorId(request.getOperatorId())
                    .status(CommandLifecycleState.EXECUTED)
                    .commandType("MANUAL_STOP")
                    .rationale(request.getRationale())
                    .details("Workflow Test Passed — Irrigation deactivation was processed successfully in simulation mode. No physical device was activated.")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            statusTracker.put(correlationId, simResponse);

            if (systemEventPublisher != null) {
                try {
                    systemEventPublisher.publish(SystemEvent.builder()
                            .eventType(SystemEventType.IRRIGATION_EXECUTION_UPDATED)
                            .fieldId(field.getId())
                            .aggregateId(correlationId)
                            .payload(simResponse)
                            .build());
                } catch (Exception e) {
                    log.warn("Failed to publish simulation event for manual stop: {}", e.getMessage());
                }
            }
            return simResponse;
        }

        // 2. Build payload JSON
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "command", "MANUAL_STOP",
                    "operatorId", request.getOperatorId(),
                    "fieldId", request.getFieldId(),
                    "rationale", request.getRationale(),
                    "correlationId", correlationId
            ));
        } catch (Exception e) {
            payloadJson = String.format("{\"command\":\"MANUAL_STOP\",\"fieldId\":%d,\"correlationId\":\"%s\"}", request.getFieldId(), correlationId);
        }

        // 3. Queue downlink for all edge nodes in the field
        List<MonitoringZone> zones = monitoringZoneRepository.findByFieldId(field.getId());
        for (MonitoringZone zone : zones) {
            List<EdgeNode> nodes = edgeNodeRepository.findByMonitoringZoneId(zone.getId());
            for (EdgeNode node : nodes) {
                downlinkQueueService.queueDownlink(node, payloadJson, correlationId);
            }
        }

        // 4. Record command status response
        CommandStatusResponse response = CommandStatusResponse.builder()
                .correlationId(correlationId)
                .fieldId(field.getId())
                .operatorId(request.getOperatorId())
                .status(CommandLifecycleState.QUEUED)
                .commandType("MANUAL_STOP")
                .rationale(request.getRationale())
                .details("Manual stop queued for field " + field.getName())
                .createdAt(now)
                .updatedAt(now)
                .build();

        statusTracker.put(correlationId, response);

        // 5. Publish event
        if (systemEventPublisher != null) {
            try {
                systemEventPublisher.publish(SystemEvent.builder()
                        .eventType(SystemEventType.IRRIGATION_EXECUTION_UPDATED)
                        .fieldId(field.getId())
                        .aggregateId(correlationId)
                        .payload(response)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to publish event for manual stop: {}", e.getMessage());
            }
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CommandStatusResponse getCommandStatus(String correlationId) {
        log.info("Fetching command status for correlationId: {}", correlationId);
        if (statusTracker.containsKey(correlationId)) {
            CommandStatusResponse cached = statusTracker.get(correlationId);

            // Check if downlink queue item updated status
            Optional<DownlinkQueueItem> itemOpt = downlinkQueueItemRepository.findByCorrelationId(correlationId);
            if (itemOpt.isPresent()) {
                DownlinkQueueItem item = itemOpt.get();
                CommandLifecycleState mappedState = mapQueueStatusToCommandState(item.getStatus());
                cached.setStatus(mappedState);
                cached.setUpdatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt() : LocalDateTime.now());
            }
            return cached;
        }

        Optional<DownlinkQueueItem> itemOpt = downlinkQueueItemRepository.findByCorrelationId(correlationId);
        if (itemOpt.isPresent()) {
            DownlinkQueueItem item = itemOpt.get();
            return CommandStatusResponse.builder()
                    .correlationId(correlationId)
                    .fieldId(item.getEdgeNode() != null && item.getEdgeNode().getMonitoringZone() != null && item.getEdgeNode().getMonitoringZone().getField() != null ? item.getEdgeNode().getMonitoringZone().getField().getId() : null)
                    .status(mapQueueStatusToCommandState(item.getStatus()))
                    .commandType("COMMAND")
                    .createdAt(item.getCreatedAt())
                    .updatedAt(item.getUpdatedAt())
                    .build();
        }

        throw new ResourceNotFoundException("Command not found with correlationId: " + correlationId);
    }

    @Override
    public void updateCommandStatus(String correlationId, CommandLifecycleState newState, String details) {
        log.info("Updating command status for correlationId: {} to state: {}", correlationId, newState);
        CommandStatusResponse response = statusTracker.get(correlationId);
        if (response != null) {
            response.setStatus(newState);
            if (details != null) {
                response.setDetails(details);
            }
            response.setUpdatedAt(LocalDateTime.now());
            statusTracker.put(correlationId, response);
        }
    }

    private void saveAuditLog(String eventType, String actor, String entityType, String entityId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            IrrigationAuditLog logEntry = IrrigationAuditLog.builder()
                    .eventType(eventType)
                    .actor(actor)
                    .entityType(entityType)
                    .entityId(entityId)
                    .payloadJson(json)
                    .build();
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save audit log entry: {}", e.getMessage());
        }
    }

    private CommandLifecycleState mapQueueStatusToCommandState(ConfigSyncStatus status) {
        if (status == null) return CommandLifecycleState.QUEUED;
        return switch (status) {
            case QUEUED, PENDING -> CommandLifecycleState.QUEUED;
            case ACKNOWLEDGED -> CommandLifecycleState.ACKNOWLEDGED;
            case TIMEOUT, FAILED -> CommandLifecycleState.FAILED;
        };
    }
}
