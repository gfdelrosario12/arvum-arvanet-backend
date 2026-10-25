package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyStopRequest {

    @NotBlank(message = "Operator ID is required")
    private String operatorId;

    private Long fieldId; // optional; if null, emergency stop applies system-wide

    @NotBlank(message = "Reason is required for emergency stop")
    private String reason;

    private String scope; // "FIELD" or "SYSTEM_WIDE"

    @Builder.Default
    private Boolean simulationMode = false;
}

