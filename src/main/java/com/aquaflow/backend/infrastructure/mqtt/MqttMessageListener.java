package com.aquaflow.backend.infrastructure.mqtt;

import com.aquaflow.backend.domain.AlertService;
import com.aquaflow.backend.domain.SensorDataService;
import com.aquaflow.backend.domain.TelemetryIngestionService;
import com.aquaflow.backend.domain.iot.ArvaIotIngestionEngine;
import com.aquaflow.backend.dto.request.SensorDataRequest;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MqttMessageListener implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageListener.class);

    private final SensorDataService sensorDataService;
    private final TelemetryIngestionService telemetryIngestionService;
    private final ArvaIotIngestionEngine iotIngestionEngine;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;

    private MqttClient mqttClient;
    private ExecutorService executor;

    @Autowired
    public MqttMessageListener(
            SensorDataService sensorDataService,
            @Autowired(required = false) TelemetryIngestionService telemetryIngestionService,
            @Autowired(required = false) ArvaIotIngestionEngine iotIngestionEngine,
            ObjectMapper objectMapper,
            @Autowired(required = false) AlertService alertService
    ) {
        this.sensorDataService = sensorDataService;
        this.telemetryIngestionService = telemetryIngestionService;
        this.iotIngestionEngine = iotIngestionEngine;
        this.objectMapper = objectMapper;
        this.alertService = alertService;
    }

    @PostConstruct
    public void init() {
        executor = Executors.newFixedThreadPool(4);
        try {
            String broker = System.getenv().getOrDefault("MQTT_BROKER_URL", "tcp://localhost:1883");
            mqttClient = new MqttClient(broker, "aquaflow-backend-listener", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);

            String user = System.getenv().getOrDefault("MQTT_USERNAME", "aquaflow-backend");
            String pass = System.getenv().getOrDefault("MQTT_PASSWORD", "ArvaBackendSecret2026!");
            if (user != null && !user.isBlank()) {
                options.setUserName(user);
                options.setPassword(pass != null ? pass.toCharArray() : new char[0]);
            }

            mqttClient.setCallback(this);
            mqttClient.connect(options);

            // Subscriptions covering ArvaCore IoT Core V1 topic namespace
            mqttClient.subscribe("arvacore/v1/devices/+/telemetry", 1);
            mqttClient.subscribe("arvacore/v1/devices/+/status", 1);
            mqttClient.subscribe("arvacore/v1/devices/+/events", 1);
            mqttClient.subscribe("arvacore/v1/devices/+/command-results", 1);

            // Legacy backward-compatibility subscriptions
            mqttClient.subscribe("node/+/telemetry", 1);
            mqttClient.subscribe("node/+/event", 1);
            mqttClient.subscribe("sensor/+/data", 0);
            mqttClient.subscribe("device/+/status", 1);
            mqttClient.subscribe("device/+/alert", 1);

            log.info("MQTT listener connected to {} and subscribed to ArvaCore IoT Core topics", broker);
        } catch (Exception e) {
            log.warn("MQTT connection initialization skipped or failed (will retry if broker available): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            if (executor != null) {
                executor.shutdown();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown");
        reconnect();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        executor.submit(() -> {
            try {
                String payload = new String(message.getPayload());
                log.debug("MQTT message received [topic={}, qos={}]: {}", topic, message.getQos(), payload);
                processMessage(topic, payload);
            } catch (Exception e) {
                log.error("Error processing MQTT message on topic {}", topic, e);
            }
        });
    }

    private void processMessage(String topic, String payload) {
        String[] parts = topic.split("/");

        // 1. Primary ArvaCore IoT Core V1 Namespace: arvacore/v1/devices/{deviceId}/{action}
        if (parts.length >= 5 && "arvacore".equals(parts[0]) && "v1".equals(parts[1]) && "devices".equals(parts[2])) {
            String deviceId = parts[3];
            String action = parts[4];

            if ("telemetry".equals(action)) {
                if (iotIngestionEngine != null) {
                    iotIngestionEngine.ingestMqttTelemetry(deviceId, payload);
                }
                return;
            } else if ("events".equals(action) || "status".equals(action)) {
                handleNodeEvent(deviceId, payload);
                return;
            }
        }

        // 2. Legacy node/{nodeId}/telemetry
        if (parts.length >= 3 && "node".equals(parts[0]) && "telemetry".equals(parts[2])) {
            String nodeId = parts[1];
            if (iotIngestionEngine != null) {
                iotIngestionEngine.ingestMqttTelemetry(nodeId, payload);
            } else {
                handleLegacyNodeTelemetry(nodeId, payload);
            }
            return;
        }

        // 3. Node Event: node/{nodeId}/event or device/{id}/alert
        if (parts.length >= 3 && (("node".equals(parts[0]) && "event".equals(parts[2])) || ("device".equals(parts[0]) && "alert".equals(parts[2])))) {
            handleNodeEvent(parts[1], payload);
            return;
        }

        // 4. Raw sensor fallback: sensor/{deviceId}/data
        if (parts.length >= 3 && "sensor".equals(parts[0]) && "data".equals(parts[2])) {
            SensorDataRequest request = new SensorDataRequest();
            request.setDeviceId(parts[1]);
            request.setSensorType(parts.length > 3 ? parts[3] : "soil_moisture");
            try {
                request.setValue(Double.parseDouble(payload.trim()));
                sensorDataService.ingestSensorData(request);
            } catch (NumberFormatException e) {
                try {
                    TelemetryUplinkRequest uplink = objectMapper.readValue(payload, TelemetryUplinkRequest.class);
                    if (telemetryIngestionService != null) {
                        telemetryIngestionService.ingestUplinkForNode(parts[1], uplink);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void handleLegacyNodeTelemetry(String nodeId, String payload) {
        if (telemetryIngestionService == null) return;
        try {
            TelemetryUplinkRequest uplink = objectMapper.readValue(payload, TelemetryUplinkRequest.class);
            if (uplink.getNodeId() == null || uplink.getNodeId().isBlank()) {
                uplink.setNodeId(nodeId);
            }
            telemetryIngestionService.ingestUplinkForNode(nodeId, uplink);
        } catch (Exception e) {
            log.warn("Failed to parse node telemetry from MQTT for node {}: {}", nodeId, e.getMessage());
        }
    }

    private void handleNodeEvent(String nodeId, String payload) {
        if (alertService == null) return;
        try {
            JsonNode node = objectMapper.readTree(payload);
            String title = node.has("title") ? node.get("title").asText() : "Device Alert: " + nodeId;
            String message = node.has("message") ? node.get("message").asText() : payload;
            String severity = node.has("severity") ? node.get("severity").asText() : "WARNING";
            String eventType = node.has("eventType") ? node.get("eventType").asText() : "HARDWARE_ALERT";

            alertService.createNotification(
                    eventType,
                    title,
                    message,
                    severity,
                    "hardware",
                    nodeId,
                    null,
                    null,
                    null,
                    "DEVICE_MQTT",
                    "Node " + nodeId,
                    nodeId,
                    "Review device status and connectivity.",
                    "MQTT_EVENT:" + nodeId + ":" + eventType,
                    30,
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to process node event from MQTT: {}", e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("MQTT delivery complete");
    }

    public void reconnect() {
        if (executor == null || executor.isShutdown()) return;
        executor.submit(() -> {
            try {
                if (mqttClient != null && !mqttClient.isConnected()) {
                    mqttClient.reconnect();
                    log.info("MQTT reconnected successfully");
                }
            } catch (Exception e) {
                log.warn("MQTT reconnect attempt failed: {}", e.getMessage());
            }
        });
    }
}
