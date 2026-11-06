package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "farm_configurations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_id", nullable = false, unique = true)
    private Long fieldId;

    @Column(name = "farm_name", nullable = false)
    private String farmName;

    @Column(name = "geographical_location", nullable = false)
    private String geographicalLocation;

    @Column(name = "total_area_hectares", nullable = false)
    private Double totalAreaHectares;

    @Column(name = "pitak_count", nullable = false)
    private Integer pitakCount;

    @Column(name = "soil_type", nullable = false)
    private String soilType;

    @Column(name = "soil_characteristics", columnDefinition = "TEXT")
    private String soilCharacteristics;

    @Column(name = "water_sources_json", columnDefinition = "TEXT")
    private String waterSourcesJson;

    @Column(name = "distribution_channels_json", columnDefinition = "TEXT")
    private String distributionChannelsJson;

    @Column(name = "equipment_inventory_json", columnDefinition = "TEXT")
    private String equipmentInventoryJson;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
