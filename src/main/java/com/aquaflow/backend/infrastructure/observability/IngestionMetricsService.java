package com.aquaflow.backend.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Ingestion Metrics and Observability Service for ArvaCore IoT Platform.
 * Tracks message rates, latencies, duplicates, and invalid payloads.
 */
@Service
public class IngestionMetricsService {

    private final MeterRegistry meterRegistry;

    private final Counter messagesReceivedCounter;
    private final Counter messagesProcessedCounter;
    private final Counter duplicateMessagesCounter;
    private final Counter malformedMessagesCounter;
    private final Counter unauthorizedTopicsCounter;
    private final Counter failedWritesCounter;
    private final Timer writeLatencyTimer;

    private final ConcurrentHashMap<String, AtomicInteger> activeConnectionsPerNode = new ConcurrentHashMap<>();

    public IngestionMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.messagesReceivedCounter = Counter.builder("arvacore.iot.messages.received")
                .description("Total telemetry messages received across all ingestion channels")
                .register(meterRegistry);

        this.messagesProcessedCounter = Counter.builder("arvacore.iot.messages.processed")
                .description("Total telemetry messages successfully validated and persisted")
                .register(meterRegistry);

        this.duplicateMessagesCounter = Counter.builder("arvacore.iot.messages.duplicate")
                .description("Total duplicate telemetry messages dropped safely")
                .register(meterRegistry);

        this.malformedMessagesCounter = Counter.builder("arvacore.iot.messages.malformed")
                .description("Total malformed or unparseable telemetry payloads rejected")
                .register(meterRegistry);

        this.unauthorizedTopicsCounter = Counter.builder("arvacore.iot.security.unauthorized_topic")
                .description("Total payloads rejected due to deviceId mismatch with authorized MQTT topic")
                .register(meterRegistry);

        this.failedWritesCounter = Counter.builder("arvacore.iot.database.failed_writes")
                .description("Total failed database writes during ingestion")
                .register(meterRegistry);

        this.writeLatencyTimer = Timer.builder("arvacore.iot.database.write_latency")
                .description("Duration of database ingestion transactions")
                .register(meterRegistry);
    }

    public void recordReceived() {
        messagesReceivedCounter.increment();
    }

    public void recordProcessed() {
        messagesProcessedCounter.increment();
    }

    public void recordDuplicate() {
        duplicateMessagesCounter.increment();
    }

    public void recordMalformed() {
        malformedMessagesCounter.increment();
    }

    public void recordUnauthorizedTopic() {
        unauthorizedTopicsCounter.increment();
    }

    public void recordFailedWrite() {
        failedWritesCounter.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(writeLatencyTimer);
        }
    }

    public void setNodeConnectionState(String nodeId, boolean connected) {
        if (nodeId != null) {
            activeConnectionsPerNode.computeIfAbsent(nodeId, k -> new AtomicInteger(0))
                    .set(connected ? 1 : 0);
        }
    }

    public int getActiveNodeCount() {
        return (int) activeConnectionsPerNode.values().stream()
                .filter(a -> a.get() == 1)
                .count();
    }
}

