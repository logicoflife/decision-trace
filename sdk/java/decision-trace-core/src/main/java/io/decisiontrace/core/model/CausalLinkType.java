package io.decisiontrace.core.model;

public enum CausalLinkType {
    DEPENDS_ON("depends_on"),
    TRIGGERED_BY("triggered_by"),
    USES_EVIDENCE_FROM("uses_evidence_from"),
    COMPENSATES("compensates");

    private final String wireValue;

    CausalLinkType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
