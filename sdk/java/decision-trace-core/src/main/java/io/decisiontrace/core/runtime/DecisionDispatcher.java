package io.decisiontrace.core.runtime;

import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.model.DecisionTraceEvent;
import java.util.List;
import java.util.Objects;

public final class DecisionDispatcher {
    private final List<DecisionExporter> exporters;
    private final DecisionRuntimeMetrics metrics;

    public DecisionDispatcher(List<DecisionExporter> exporters, DecisionRuntimeMetrics metrics) {
        this.exporters = List.copyOf(Objects.requireNonNull(exporters, "exporters"));
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public void dispatch(DecisionTraceEvent event) {
        for (DecisionExporter exporter : exporters) {
            long start = System.nanoTime();
            try {
                exporter.export(event);
            } catch (RuntimeException ignored) {
                // Exporter isolation is fail-open by design.
                metrics.recordExporterFailure();
            } finally {
                metrics.recordExportNanos(System.nanoTime() - start);
            }
        }
    }

    public void flush() {
        for (DecisionExporter exporter : exporters) {
            try {
                exporter.flush();
            } catch (RuntimeException ignored) {
                // Exporter isolation is fail-open by design.
                metrics.recordExporterFailure();
            }
        }
    }

    public void close() {
        for (DecisionExporter exporter : exporters) {
            try {
                exporter.close();
            } catch (RuntimeException ignored) {
                // Exporter isolation is fail-open by design.
                metrics.recordExporterFailure();
            }
        }
    }
}
