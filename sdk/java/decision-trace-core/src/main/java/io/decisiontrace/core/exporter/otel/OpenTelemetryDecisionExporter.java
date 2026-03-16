package io.decisiontrace.core.exporter.otel;

import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.json.DecisionJsonSerializer;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import java.util.Objects;

public final class OpenTelemetryDecisionExporter implements DecisionExporter {
    private final Tracer tracer;
    private final DecisionJsonSerializer serializer = new DecisionJsonSerializer();

    public OpenTelemetryDecisionExporter(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    @Override
    public void export(DecisionTraceEvent event) {
        Span span = tracer.spanBuilder(event.decisionType() + ":" + event.eventType().wireValue())
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        try {
            span.setAttribute("dt.trace_id", event.traceId());
            span.setAttribute("dt.decision_id", event.decisionId());
            span.setAttribute("dt.event_id", event.eventId());
            span.setAttribute("dt.event_type", event.eventType().wireValue());
            span.setAttribute("dt.decision_type", event.decisionType());
            span.setAttribute("dt.tenant_id", event.tenantId());
            span.setAttribute("dt.environment", event.environment());
            if (event.parentDecisionId() != null) {
                span.setAttribute("dt.parent_decision_id", event.parentDecisionId());
            }
            span.setAttribute(AttributeKey.stringKey("dt.payload_json"), serializer.serialize(event));
        } finally {
            span.end();
        }
    }
}
