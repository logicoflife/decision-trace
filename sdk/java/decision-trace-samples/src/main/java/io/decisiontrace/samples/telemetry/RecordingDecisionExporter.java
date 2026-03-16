package io.decisiontrace.samples.telemetry;

import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.model.DecisionTraceEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecordingDecisionExporter implements DecisionExporter {
    private final List<DecisionTraceEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void export(DecisionTraceEvent event) {
        events.add(event);
    }

    public List<DecisionTraceEvent> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}
