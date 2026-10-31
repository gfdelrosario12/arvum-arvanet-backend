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
public class PlanStatusUpdateRequest {
    @NotBlank(message = "Target lifecycle status is required")
    private String status; // NOT_STARTED, IN_PROGRESS, CONTINUED, COMPLETED, CANCELLED, FAILED

    private String reason;
    private String completionEvidence;
}

