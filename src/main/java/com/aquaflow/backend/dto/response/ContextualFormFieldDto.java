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
public class ContextualFormFieldDto {
    private String id;
    private String label;
    private String type; // 'select', 'date', 'text', 'number'
    private String placeholder;
    private Boolean required;
    private String defaultValue;
    private List<String> options;
}
