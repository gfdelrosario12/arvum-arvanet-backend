package com.aquaflow.backend.api;

import com.aquaflow.backend.dto.response.FarmConfigurationDto;
import com.aquaflow.backend.entity.FarmConfiguration;
import com.aquaflow.backend.persistence.FarmConfigurationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/farm-configuration")
@RequiredArgsConstructor
@Tag(name = "Farm Configuration", description = "Canonical farm profile, soil characteristics, and implements inventory")
public class FarmConfigurationController {

    private final FarmConfigurationRepository farmConfigurationRepository;

    @GetMapping("/{fieldId}")
    @Operation(summary = "Get canonical farm configuration for a field")
    public ResponseEntity<FarmConfigurationDto> getFarmConfiguration(@PathVariable Long fieldId) {
        FarmConfiguration config = farmConfigurationRepository.findByFieldIdAndIsActiveTrue(fieldId)
                .or(() -> farmConfigurationRepository.findFirstByIsActiveTrue())
                .orElseGet(() -> FarmConfiguration.builder()
                        .fieldId(fieldId)
                        .farmName("Silang Terraced Rice Field")
                        .geographicalLocation("Barangay Silang, Lopez, Quezon (13.8821° N, 121.9906° E)")
                        .totalAreaHectares(1.1)
                        .pitakCount(6)
                        .soilType("Calumpang Clay (Macolod Series)")
                        .soilCharacteristics("Heavy clay, plow pan at 15-20 cm depth, slow percolation rate (< 2 mm/day once puddled). Ideal for AWD.")
                        .waterSourcesJson("[\"NIA Inflow Canal\", \"Deep Well Submersible Pump (400W)\", \"Rainfall Catchment\", \"Natural Runoff\"]")
                        .distributionChannelsJson("{\"inlet_gates\": [\"Gate 1 (Inflow, Pitak 1-2)\", \"Gate 2 (Mid-Terrace Lateral, Pitak 3-4)\", \"Gate 3 (Distal, Pitak 5-6)\"], \"drainage_outlet\": \"Tail Drainage Flume (Pitak 6)\"}")
                        .equipmentInventoryJson("[\"Hand Tractor (Kuliglig)\", \"Moldboard Plow\", \"Comb Harrow (Suyod)\", \"Wooden Leveling Plank (Haba)\", \"Rotavator Attachment\"]")
                        .isActive(true)
                        .build());

        FarmConfigurationDto dto = FarmConfigurationDto.builder()
                .id(config.getId())
                .fieldId(config.getFieldId())
                .farmName(config.getFarmName())
                .geographicalLocation(config.getGeographicalLocation())
                .totalAreaHectares(config.getTotalAreaHectares())
                .pitakCount(config.getPitakCount())
                .soilType(config.getSoilType())
                .soilCharacteristics(config.getSoilCharacteristics())
                .isActive(config.getIsActive())
                .updatedAt(config.getUpdatedAt())
                .build();

        return ResponseEntity.ok(dto);
    }
}

