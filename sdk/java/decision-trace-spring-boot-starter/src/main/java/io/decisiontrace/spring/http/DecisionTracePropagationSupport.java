package io.decisiontrace.spring.http;

import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.context.DecisionFrame;
import io.decisiontrace.spring.config.DecisionTraceProperties;
import io.decisiontrace.spring.propagation.InboundTraceContext;
import org.springframework.http.HttpHeaders;

public final class DecisionTracePropagationSupport {
    private final DecisionTraceProperties properties;
    private final DecisionContextHolder contextHolder;
    private final InboundTraceContext inboundTraceContext;

    public DecisionTracePropagationSupport(
            DecisionTraceProperties properties,
            DecisionContextHolder contextHolder,
            InboundTraceContext inboundTraceContext) {
        this.properties = properties;
        this.contextHolder = contextHolder;
        this.inboundTraceContext = inboundTraceContext;
    }

    public void apply(HttpHeaders headers) {
        DecisionFrame current = contextHolder.current();
        InboundTraceContext.TraceCarrier inbound = inboundTraceContext.current();
        String traceId = current != null ? current.traceId() : inbound != null ? inbound.traceId() : null;
        String parentDecisionId = current != null
                ? current.decisionId()
                : inbound != null ? inbound.parentDecisionId() : null;
        if (traceId != null && !traceId.isBlank()) {
            headers.set(properties.getTraceIdHeader(), traceId);
        }
        if (parentDecisionId != null && !parentDecisionId.isBlank()) {
            headers.set(properties.getParentDecisionIdHeader(), parentDecisionId);
        }
    }
}
