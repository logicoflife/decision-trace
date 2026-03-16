package io.decisiontrace.core.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class CausalLink {
    private final CausalLinkType type;
    private final String targetDecisionId;

    public CausalLink(CausalLinkType type, String targetDecisionId) {
        this.type = Objects.requireNonNull(type, "type");
        if (targetDecisionId == null || targetDecisionId.isBlank()) {
            throw new IllegalArgumentException("targetDecisionId must not be blank");
        }
        this.targetDecisionId = targetDecisionId;
    }

    public CausalLinkType type() {
        return type;
    }

    public String targetDecisionId() {
        return targetDecisionId;
    }

    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.wireValue());
        map.put("target_decision_id", targetDecisionId);
        return map;
    }
}
