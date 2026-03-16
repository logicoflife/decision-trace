package io.decisiontrace.spring.propagation;

public final class DecisionTraceHeaders {
    public static final String W3C_TRACEPARENT = "traceparent";

    private DecisionTraceHeaders() {
    }

    public static String parseTraceParent(String traceParentHeader) {
        if (traceParentHeader == null || traceParentHeader.isBlank()) {
            return null;
        }
        String[] parts = traceParentHeader.split("-");
        if (parts.length != 4 || parts[1].length() != 32) {
            return null;
        }
        return parts[1];
    }
}
