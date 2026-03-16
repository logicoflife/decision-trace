package io.decisiontrace.core.runtime;

import io.decisiontrace.core.model.DecisionTraceEvent;

public final class DecisionEventEnvelope {
    private DecisionTraceEvent event;

    public DecisionTraceEvent event() {
        return event;
    }

    public void event(DecisionTraceEvent event) {
        this.event = event;
    }

    public void clear() {
        this.event = null;
    }
}
