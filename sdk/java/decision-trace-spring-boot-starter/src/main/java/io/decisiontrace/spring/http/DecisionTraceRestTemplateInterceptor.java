package io.decisiontrace.spring.http;

import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.context.DecisionFrame;
import io.decisiontrace.spring.config.DecisionTraceProperties;
import io.decisiontrace.spring.propagation.InboundTraceContext;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class DecisionTraceRestTemplateInterceptor implements ClientHttpRequestInterceptor {
    private final DecisionTraceProperties properties;
    private final DecisionContextHolder contextHolder;
    private final InboundTraceContext inboundTraceContext;

    public DecisionTraceRestTemplateInterceptor(
            DecisionTraceProperties properties,
            DecisionContextHolder contextHolder,
            InboundTraceContext inboundTraceContext) {
        this.properties = properties;
        this.contextHolder = contextHolder;
        this.inboundTraceContext = inboundTraceContext;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        DecisionFrame current = contextHolder.current();
        InboundTraceContext.TraceCarrier inbound = inboundTraceContext.current();
        String traceId = current != null ? current.traceId() : inbound != null ? inbound.traceId() : null;
        String parentDecisionId = current != null
                ? current.decisionId()
                : inbound != null ? inbound.parentDecisionId() : null;
        if (traceId != null && !traceId.isBlank()) {
            request.getHeaders().set(properties.getTraceIdHeader(), traceId);
        }
        if (parentDecisionId != null && !parentDecisionId.isBlank()) {
            request.getHeaders().set(properties.getParentDecisionIdHeader(), parentDecisionId);
        }
        return execution.execute(request, body);
    }
}
