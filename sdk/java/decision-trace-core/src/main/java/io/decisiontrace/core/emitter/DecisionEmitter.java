package io.decisiontrace.core.emitter;

import io.decisiontrace.core.model.DecisionTraceEvent;

public interface DecisionEmitter {
    void emit(DecisionTraceEvent event);

    default void flush() {
    }

    default void close() {
    }
}
