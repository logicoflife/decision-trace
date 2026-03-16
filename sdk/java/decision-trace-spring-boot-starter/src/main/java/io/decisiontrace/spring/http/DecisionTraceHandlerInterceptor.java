package io.decisiontrace.spring.http;

import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.context.DecisionFrame;
import io.decisiontrace.spring.config.DecisionTraceProperties;
import io.decisiontrace.spring.propagation.DecisionTraceHeaders;
import io.decisiontrace.spring.propagation.InboundTraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class DecisionTraceHandlerInterceptor implements HandlerInterceptor {
    private final DecisionTraceProperties properties;
    private final InboundTraceContext inboundTraceContext;
    private final DecisionContextHolder contextHolder;

    public DecisionTraceHandlerInterceptor(
            DecisionTraceProperties properties,
            InboundTraceContext inboundTraceContext,
            DecisionContextHolder contextHolder) {
        this.properties = properties;
        this.inboundTraceContext = inboundTraceContext;
        this.contextHolder = contextHolder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader(properties.getTraceIdHeader());
        if (traceId == null || traceId.isBlank()) {
            traceId = DecisionTraceHeaders.parseTraceParent(request.getHeader(DecisionTraceHeaders.W3C_TRACEPARENT));
        }
        String parentDecisionId = request.getHeader(properties.getParentDecisionIdHeader());
        inboundTraceContext.set(traceId, parentDecisionId);
        if (traceId != null && !traceId.isBlank() && parentDecisionId != null && !parentDecisionId.isBlank()) {
            contextHolder.push(new DecisionFrame(traceId, parentDecisionId));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        inboundTraceContext.clear();
        contextHolder.clear();
    }
}
