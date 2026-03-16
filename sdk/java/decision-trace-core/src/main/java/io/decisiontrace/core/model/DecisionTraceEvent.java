package io.decisiontrace.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DecisionTraceEvent {
    private final String tenantId;
    private final String environment;
    private final String schemaVersion;
    private final String timestamp;
    private final String traceId;
    private final String decisionId;
    private final String parentDecisionId;
    private final String eventId;
    private final EventType eventType;
    private final String decisionType;
    private final Actor actor;
    private final Map<String, Object> payload;
    private final List<CausalLink> causalLinks;

    public DecisionTraceEvent(
            String tenantId,
            String environment,
            String schemaVersion,
            String timestamp,
            String traceId,
            String decisionId,
            String parentDecisionId,
            String eventId,
            EventType eventType,
            String decisionType,
            Actor actor,
            Map<String, Object> payload,
            List<CausalLink> causalLinks) {
        this.tenantId = requireNonBlank(tenantId, "tenant_id");
        this.environment = requireNonBlank(environment, "environment");
        this.schemaVersion = requireNonBlank(schemaVersion, "schema_version");
        this.timestamp = requireNonBlank(timestamp, "timestamp");
        this.traceId = requireNonBlank(traceId, "trace_id");
        this.decisionId = requireNonBlank(decisionId, "decision_id");
        this.parentDecisionId = parentDecisionId;
        this.eventId = requireNonBlank(eventId, "event_id");
        this.eventType = Objects.requireNonNull(eventType, "event_type");
        this.decisionType = requireNonBlank(decisionType, "decision_type");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.payload = java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(payload, "payload")));
        this.causalLinks = causalLinks == null ? null : List.copyOf(causalLinks);
    }

    public String tenantId() {
        return tenantId;
    }

    public String environment() {
        return environment;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public String timestamp() {
        return timestamp;
    }

    public String traceId() {
        return traceId;
    }

    public String decisionId() {
        return decisionId;
    }

    public String parentDecisionId() {
        return parentDecisionId;
    }

    public String eventId() {
        return eventId;
    }

    public EventType eventType() {
        return eventType;
    }

    public String decisionType() {
        return decisionType;
    }

    public Actor actor() {
        return actor;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public List<CausalLink> causalLinks() {
        return causalLinks;
    }

    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("tenant_id", tenantId);
        map.put("environment", environment);
        map.put("schema_version", schemaVersion);
        map.put("timestamp", timestamp);
        map.put("trace_id", traceId);
        map.put("decision_id", decisionId);
        map.put("parent_decision_id", parentDecisionId);
        map.put("event_id", eventId);
        map.put("event_type", eventType.wireValue());
        map.put("decision_type", decisionType);
        map.put("actor", actor.toMap());
        map.put("payload", new LinkedHashMap<>(payload));
        if (causalLinks != null) {
            List<Map<String, Object>> serializedLinks = new ArrayList<>();
            for (CausalLink causalLink : causalLinks) {
                serializedLinks.add(causalLink.toMap());
            }
            map.put("causal_links", serializedLinks);
        }
        return map;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
