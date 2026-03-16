package io.decisiontrace.core.emitter;

import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.model.DecisionTraceEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class InMemoryDecisionEmitter implements DecisionEmitter {
    private final List<DecisionTraceEvent> events = new ArrayList<>();
    private final List<DecisionExporter> exporters;

    public InMemoryDecisionEmitter() {
        this(List.of());
    }

    public InMemoryDecisionEmitter(List<DecisionExporter> exporters) {
        this.exporters = List.copyOf(Objects.requireNonNull(exporters, "exporters"));
    }

    @Override
    public void emit(DecisionTraceEvent event) {
        events.add(event);
        for (DecisionExporter exporter : exporters) {
            exporter.export(event);
        }
    }

    @Override
    public void flush() {
        for (DecisionExporter exporter : exporters) {
            exporter.flush();
        }
    }

    @Override
    public void close() {
        for (DecisionExporter exporter : exporters) {
            exporter.close();
        }
    }

    public List<DecisionTraceEvent> events() {
        return Collections.unmodifiableList(events);
    }
}
