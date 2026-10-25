package com.aquaflow.backend.dto.iot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Standardized ArvaCore IoT Core Schema V1 Telemetry Message.
 * Fully compatible with ArvaNode ESP32 firmware while providing
 * idempotent message identifiers, sequence tracking, and diagnostic maps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArvaIotTelemetryPayload {

    @Builder.Default
    private Integer schemaVersion = 1;

    private String deviceId;
    private String nodeId;
    private String messageId;
    private Long sequenceNumber;
    private Long seq;
    private String timestamp;
    private Long uptimeSeconds;

    // Sensor Readings Container
    private SensorValues sensors;

    // Direct sensor properties for backwards compatibility
    private Double soilMoisturePercent;
    private Double waterLevelCm;
    private Double waterLevelDistanceCm;
    private Double batteryVoltage;
    private Double batteryLevel;
    private Double solarVoltage;

    // Diagnostic & Sensor Status Container
    private Map<String, String> sensorStatus;
    private Map<String, String> sensorHealth;
    private String telemetryStatus;

    // Network & RF Metadata
    private NetworkMetadata network;
    private Integer rssiDbm;
    private Double snrDb;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SensorValues {
        private Double soilMoisturePercent;
        private Double waterLevelCm;
        private Double waterLevelDistanceCm;
        private Double batteryVoltage;
        private Double batteryLevel;
        private Double solarVoltage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NetworkMetadata {
        private Integer rssiDbm;
        private Double snrDb;
        private String gatewayId;
    }

    public String resolveEffectiveDeviceId() {
        if (deviceId != null && !deviceId.isBlank()) return deviceId.trim();
        if (nodeId != null && !nodeId.isBlank()) return nodeId.trim();
        return null;
    }

    public Long resolveSequenceNumber() {
        if (sequenceNumber != null) return sequenceNumber;
        return seq;
    }

    public Double resolveSoilMoisture() {
        if (sensors != null && sensors.getSoilMoisturePercent() != null) return sensors.getSoilMoisturePercent();
        return soilMoisturePercent;
    }

    public Double resolveWaterDistance() {
        if (sensors != null && sensors.getWaterLevelDistanceCm() != null) return sensors.getWaterLevelDistanceCm();
        return waterLevelDistanceCm;
    }

    public Double resolveWaterLevel() {
        if (sensors != null && sensors.getWaterLevelCm() != null) return sensors.getWaterLevelCm();
        return waterLevelCm;
    }

    public Double resolveBatteryVoltage() {
        if (sensors != null && sensors.getBatteryVoltage() != null) return sensors.getBatteryVoltage();
        return batteryVoltage;
    }

    public Integer resolveRssi() {
        if (network != null && network.getRssiDbm() != null) return network.getRssiDbm();
        return rssiDbm;
    }
}

