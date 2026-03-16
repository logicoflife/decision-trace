package io.decisiontrace.core.exporter;

import io.decisiontrace.core.model.DecisionTraceEvent;

public interface DecisionExporter {
    void export(DecisionTraceEvent event);

    default void flush() {
    }

    default void close() {
    }
}
