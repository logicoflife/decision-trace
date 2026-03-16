package io.decisiontrace.core.exporter;

import io.decisiontrace.core.model.DecisionTraceEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InMemoryDecisionExporter implements DecisionExporter {
    private final List<DecisionTraceEvent> events = new ArrayList<>();

    @Override
    public void export(DecisionTraceEvent event) {
        events.add(event);
    }

    public List<DecisionTraceEvent> events() {
        return Collections.unmodifiableList(events);
    }
}
