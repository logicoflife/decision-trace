package io.decisiontrace.core.model;

public enum EventType {
    DECISION_START("decision.start"),
    DECISION_EVIDENCE("decision.evidence"),
    DECISION_POLICY_CHECK("decision.policy_check"),
    DECISION_ACTION("decision.action"),
    DECISION_APPROVAL("decision.approval"),
    DECISION_OUTCOME("decision.outcome"),
    DECISION_ERROR("decision.error"),
    DECISION_EVALUATION("decision.evaluation");

    private final String wireValue;

    EventType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
