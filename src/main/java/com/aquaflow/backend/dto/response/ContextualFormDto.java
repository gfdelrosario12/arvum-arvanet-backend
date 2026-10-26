package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextualFormDto {
    private String formId;
    private String formType; // 'PLANTING_SCHEDULE', 'IRRIGATION_ASSESSMENT', 'FERTILIZER_INM', 'PEST_DISEASE', 'HARVEST_PLAN'
    private String title;
    private String description;
    private String submitButtonLabel;
    private List<ContextualFormFieldDto> fields;
}
