package io.decisiontrace.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.decisiontrace.core.DecisionScope;
import io.decisiontrace.core.DecisionSpec;
import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.emitter.LmaxDecisionEmitter;
import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.exporter.InMemoryDecisionExporter;
import io.decisiontrace.core.exporter.json.JsonLedgerExporter;
import io.decisiontrace.core.exporter.otel.OpenTelemetryDecisionExporter;
import io.decisiontrace.core.ids.IdGenerator;
import io.decisiontrace.core.model.Actor;
import io.decisiontrace.core.model.ActorType;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.core.model.EventType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LmaxDecisionEmitterTest {
    @Test
    void closingScopeDoesNotFlushEmitterOnRequestThread() {
        DecisionRuntimeMetrics metrics = new DecisionRuntimeMetrics();
        CountDownLatch exportStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        DecisionExporter slowExporter = event -> {
            exportStarted.countDown();
            try {
                release.await(250, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        LmaxDecisionEmitter emitter = new LmaxDecisionEmitter(
                8,
                new DecisionDispatcher(List.of(slowExporter), metrics),
                metrics);
        DecisionContextHolder contextHolder = new DecisionContextHolder();
        FixedIdGenerator ids = new FixedIdGenerator("trace-1", "decision-1", "event-1");
        Clock clock = Clock.fixed(Instant.parse("2026-03-16T15:30:00Z"), ZoneOffset.UTC);

        long start = System.nanoTime();
        try (DecisionScope ignored = DecisionScope.open(baseSpec(), emitter, contextHolder, ids, clock)) {
            assertTrue(exportStarted.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            release.countDown();
            emitter.close();
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMillis < 200, "scope close blocked for " + elapsedMillis + "ms");
    }

    @Test
    void saturatedBufferDropsEventsSafely() throws Exception {
        DecisionRuntimeMetrics metrics = new DecisionRuntimeMetrics();
        CountDownLatch release = new CountDownLatch(1);
        DecisionExporter blockingExporter = new DecisionExporter() {
            @Override
            public void export(DecisionTraceEvent event) {
                try {
                    release.await(250, TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        LmaxDecisionEmitter emitter = new LmaxDecisionEmitter(
                2,
                new DecisionDispatcher(List.of(blockingExporter), metrics),
                metrics);
        try {
            for (int i = 0; i < 20; i++) {
                emitter.emit(sampleEvent("event-" + i));
            }
        } finally {
            release.countDown();
            emitter.close();
        }

        assertTrue(metrics.droppedEvents() > 0);
        assertTrue(metrics.bufferOccupancy() >= 0);
    }

    @Test
    void exporterFailureDoesNotBlockHealthyExporters() {
        DecisionRuntimeMetrics metrics = new DecisionRuntimeMetrics();
        InMemoryDecisionExporter healthy = new InMemoryDecisionExporter();
        DecisionExporter broken = event -> {
            throw new IllegalStateException("broken exporter");
        };
        LmaxDecisionEmitter emitter = new LmaxDecisionEmitter(
                8,
                new DecisionDispatcher(List.of(broken, healthy), metrics),
                metrics);
        try {
            emitter.emit(sampleEvent("event-1"));
            emitter.flush();
        } finally {
            emitter.close();
        }

        assertEquals(1, healthy.events().size());
        assertTrue(metrics.exportCount() >= 2);
        assertTrue(metrics.exporterFailures() >= 1);
    }

    @Test
    void otelExporterProjectsCanonicalAttributes() {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        OpenTelemetryDecisionExporter exporter = new OpenTelemetryDecisionExporter(
                sdk.getTracer("decision-trace-test"));
        exporter.export(sampleEvent("event-otel"));

        assertEquals(1, spanExporter.getFinishedSpanItems().size());
        var span = spanExporter.getFinishedSpanItems().get(0);
        assertEquals("trace-1", span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("dt.trace_id")));
        assertEquals("decision-1", span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("dt.decision_id")));
        assertEquals("decision.start", span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("dt.event_type")));
        assertEquals("1.0", span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("dt.schema_version")));
        assertEquals("risk-engine", span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("dt.actor.id")));
        tracerProvider.close();
    }

    @Test
    void flushAndShutdownPersistLedger() throws Exception {
        DecisionRuntimeMetrics metrics = new DecisionRuntimeMetrics();
        Path output = Files.createTempFile("decision-trace-", ".jsonl");
        JsonLedgerExporter ledgerExporter = new JsonLedgerExporter(output);
        LmaxDecisionEmitter emitter = new LmaxDecisionEmitter(
                8,
                new DecisionDispatcher(List.of(ledgerExporter), metrics),
                metrics);
        try {
            emitter.emit(sampleEvent("event-ledger"));
            emitter.flush();
        } finally {
            emitter.close();
        }

        List<String> lines = Files.readAllLines(output);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"event_id\":\"event-ledger\""));
        assertEquals(0, metrics.bufferOccupancy());
    }

    @Test
    void droppedEventMetricsAdvanceWhenBufferIsFull() throws Exception {
        DecisionRuntimeMetrics metrics = new DecisionRuntimeMetrics();
        AtomicInteger exports = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        DecisionExporter slowExporter = event -> {
            exports.incrementAndGet();
            try {
                release.await(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        LmaxDecisionEmitter emitter = new LmaxDecisionEmitter(
                2,
                new DecisionDispatcher(List.of(slowExporter), metrics),
                metrics);
        try {
            for (int i = 0; i < 10; i++) {
                emitter.emit(sampleEvent("drop-" + i));
            }
        } finally {
            release.countDown();
            emitter.close();
        }

        assertTrue(metrics.droppedEvents() > 0);
        assertTrue(exports.get() >= 1);
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

    private static DecisionSpec baseSpec() {
        return DecisionSpec.builder()
                .tenantId("tenant-a")
                .environment("test")
                .decisionType("RISK_CHECK")
                .actor(Actor.of("risk-engine", ActorType.SYSTEM))
                .build();
    }

    private static final class FixedIdGenerator implements IdGenerator {
        private final ArrayDeque<String> ids;

        private FixedIdGenerator(String... ids) {
            this.ids = new ArrayDeque<>(List.of(ids));
        }

        @Override
        public String generateId() {
            return ids.removeFirst();
        }
    }
}
