package io.decisiontrace.core.exporter.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.decisiontrace.core.model.Actor;
import io.decisiontrace.core.model.ActorType;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.core.model.EventType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectorBatchExporterTest {
    @Test
    void flushesWhenBatchThresholdIsReached() {
        RecordingSender sender = new RecordingSender();
        CollectorBatchExporter exporter = new CollectorBatchExporter(sender, 2);

        exporter.export(sampleEvent("event-1"));
        assertEquals(0, sender.payloads.size());

        exporter.export(sampleEvent("event-2"));

        assertEquals(1, sender.payloads.size());
        assertTrue(sender.payloads.get(0).contains("\"event_id\":\"event-1\""));
        assertTrue(sender.payloads.get(0).contains("\"event_id\":\"event-2\""));
        assertEquals(0, exporter.pendingCount());
    }

    @Test
    void closeFlushesRemainingBatch() {
        RecordingSender sender = new RecordingSender();
        CollectorBatchExporter exporter = new CollectorBatchExporter(sender, 10);

        exporter.export(sampleEvent("event-1"));
        exporter.close();

        assertEquals(1, sender.payloads.size());
        assertTrue(sender.payloads.get(0).startsWith("["));
        assertTrue(sender.payloads.get(0).endsWith("]"));
    }

    @Test
    void batchPayloadMatchesCollectorIngestionShape() {
        RecordingSender sender = new RecordingSender();
        CollectorBatchExporter exporter = new CollectorBatchExporter(sender, 2);

        exporter.export(sampleEvent("event-1"));
        exporter.export(sampleEvent("event-2"));

        String payload = sender.payloads.get(0);
        assertTrue(payload.startsWith("["));
        assertTrue(payload.endsWith("]"));
        assertTrue(payload.contains("\"tenant_id\":\"tenant-a\""));
        assertTrue(payload.contains("\"environment\":\"test\""));
        assertTrue(payload.contains("\"schema_version\":\"1.0\""));
        assertTrue(payload.contains("\"event_type\":\"decision.start\""));
        assertTrue(payload.contains("\"actor\":{\"id\":\"risk-engine\",\"type\":\"system\"}"));
        assertTrue(payload.contains("\"payload\":{\"status\":\"ok\"}"));
    }

    @Test
    void failedFlushLeavesBatchPendingForRetry() {
        RecordingSender sender = new RecordingSender();
        sender.failuresRemaining = 1;
        CollectorBatchExporter exporter = new CollectorBatchExporter(sender, 1);

        assertThrows(IllegalStateException.class, () -> exporter.export(sampleEvent("event-1")));
        assertEquals(1, exporter.pendingCount());

        exporter.flush();

        assertEquals(1, sender.payloads.size());
        assertEquals(0, exporter.pendingCount());
    }

    private static DecisionTraceEvent sampleEvent(String eventId) {
        return new DecisionTraceEvent(
                "tenant-a",
                "test",
                "1.0",
                "2026-03-16T15:30:00Z",
                "trace-1",
                "decision-1",
                null,
                eventId,
                EventType.DECISION_START,
                "RISK_CHECK",
                Actor.of("risk-engine", ActorType.SYSTEM),
                new LinkedHashMap<>(Map.of("status", "ok")),
                null);
    }

    private static final class RecordingSender implements CollectorBatchSender {
        private final List<String> payloads = new ArrayList<>();
        private int failuresRemaining;

        @Override
        public void send(String payload) {
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IllegalStateException("collector unavailable");
            }
            payloads.add(payload);
        }
    }
}
