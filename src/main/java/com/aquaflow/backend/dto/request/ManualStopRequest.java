package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualStopRequest {

    @NotBlank(message = "Operator ID is required")
    private String operatorId;

    @NotNull(message = "Field ID is required")
    private Long fieldId;

    @NotBlank(message = "Rationale is required")
    private String rationale;

    @Builder.Default
    private Boolean simulationMode = false;
}

