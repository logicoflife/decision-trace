package io.decisiontrace.core.json;

import io.decisiontrace.core.model.DecisionTraceEvent;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class DecisionJsonSerializer {
    public String serialize(DecisionTraceEvent event) {
        return serializeValue(event.toMap());
    }

    @SuppressWarnings("unchecked")
    private String serializeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return "\"" + escape(stringValue) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                builder.append(serializeValue(String.valueOf(entry.getKey())));
                builder.append(':');
                builder.append(serializeValue(entry.getValue()));
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append('}');
            return builder.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            for (int i = 0; i < list.size(); i++) {
                builder.append(serializeValue(list.get(i)));
                if (i + 1 < list.size()) {
                    builder.append(',');
                }
            }
            builder.append(']');
            return builder.toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
