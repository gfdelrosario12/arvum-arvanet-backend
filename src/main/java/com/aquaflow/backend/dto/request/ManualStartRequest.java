package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class ManualStartRequest {

    @NotBlank(message = "Operator ID is required")
    private String operatorId;

    @NotNull(message = "Field ID is required")
    private Long fieldId;

    @NotBlank(message = "Rationale is required")
    private String rationale;

    @NotNull(message = "Duration in minutes is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 1440, message = "Duration cannot exceed 1440 minutes (24 hours)")
    private Integer durationMinutes;

    private Double targetWaterDepthCm;

    @Builder.Default
    private Boolean overridePolicy = false;

    @Builder.Default
    private Boolean simulationMode = false;
}

