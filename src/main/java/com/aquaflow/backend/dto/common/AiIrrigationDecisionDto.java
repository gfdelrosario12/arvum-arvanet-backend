package com.aquaflow.backend.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiIrrigationDecisionDto {
    private String decisionId;
    private Long fieldId;
    private String timestamp;
    private String cropGrowthStage;
    private String recommendedAction; // NO_ACTION, MONITOR, IRRIGATE, STOP_IRRIGATION, ASSESS_REQUIRED
    private String agriculturalReasoning;
    private List<Map<String, Object>> supportingObservations;
    private Map<String, Object> operatingConstraints;
    private Boolean requiresConfirmation;
    private Boolean isSimulated;
    private String expirationTimestamp;
    private Double confidenceScore;
    private String modelVersion;
}
