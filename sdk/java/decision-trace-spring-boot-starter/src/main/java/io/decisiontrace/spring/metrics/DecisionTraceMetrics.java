package io.decisiontrace.spring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.decisiontrace.core.runtime.DecisionRuntimeMetrics;
import java.util.concurrent.TimeUnit;

public final class DecisionTraceMetrics {
    private final Counter instrumentationFailures;

    public DecisionTraceMetrics(MeterRegistry meterRegistry, DecisionRuntimeMetrics runtimeMetrics) {
        this.instrumentationFailures = Counter.builder("decision_trace.instrumentation.failures")
                .register(meterRegistry);
        io.micrometer.core.instrument.Gauge.builder(
                        "decision_trace.buffer.occupancy",
                        runtimeMetrics,
                        metrics -> metrics.bufferOccupancy())
                .register(meterRegistry);
        FunctionCounter.builder(
                        "decision_trace.events.dropped",
                        runtimeMetrics,
                        metrics -> (double) metrics.droppedEvents())
                .register(meterRegistry);
        FunctionCounter.builder(
                        "decision_trace.export.failures",
                        runtimeMetrics,
                        metrics -> (double) metrics.exporterFailures())
                .register(meterRegistry);
        FunctionTimer.builder(
                        "decision_trace.export.latency",
                        runtimeMetrics,
                        metrics -> metrics.exportCount(),
                        metrics -> metrics.exportNanos(),
                        TimeUnit.NANOSECONDS)
                .register(meterRegistry);
    }

    public void recordInstrumentationFailure() {
        instrumentationFailures.increment();
    }
}
