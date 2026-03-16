package io.decisiontrace.spring.propagation;

public final class InboundTraceContext {
    private static final ThreadLocal<TraceCarrier> CURRENT = new ThreadLocal<>();

    public void set(String traceId, String parentDecisionId) {
        CURRENT.set(new TraceCarrier(traceId, parentDecisionId));
    }

    public TraceCarrier current() {
        return CURRENT.get();
    }

    public void clear() {
        CURRENT.remove();
    }

    public record TraceCarrier(String traceId, String parentDecisionId) {
    }
}
