package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmConfigurationDto {
    private Long id;
    private Long fieldId;
    private String farmName;
    private String geographicalLocation;
    private Double totalAreaHectares;
    private Integer pitakCount;
    private String soilType;
    private String soilCharacteristics;
    private List<String> waterSources;
    private Object distributionChannels;
    private List<String> equipmentInventory;
    private Boolean isActive;
    private OffsetDateTime updatedAt;
}
