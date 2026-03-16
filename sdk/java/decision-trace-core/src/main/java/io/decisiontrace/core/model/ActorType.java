package io.decisiontrace.core.model;

public enum ActorType {
    HUMAN("human"),
    SYSTEM("system"),
    AGENT("agent");

    private final String wireValue;

    ActorType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
