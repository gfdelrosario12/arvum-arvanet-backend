package com.aquaflow.backend.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArvaCoreExecutionResultDto {
    private String decisionId;
    private String validationStatus; // APPROVED_FOR_EXECUTION, REJECTED_SAFETY_VIOLATION, SIMULATED_ONLY, PENDING_FARMER_CONFIRMATION
    private String targetAction;      // START_IRRIGATION, STOP_IRRIGATION, HOLD, SAFE_STOP
    private String reason;
    private boolean safetyChecksPassed;
    private boolean requiresOperatorAck;
    private boolean flowVerificationRequired;
    private LocalDateTime timestamp;
}
