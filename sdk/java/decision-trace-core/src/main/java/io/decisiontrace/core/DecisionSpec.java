package io.decisiontrace.core;

import io.decisiontrace.core.model.Actor;

public final class DecisionSpec {
    private final String tenantId;
    private final String environment;
    private final String decisionType;
    private final Actor actor;
    private final String traceId;
    private final String decisionId;
    private final String parentDecisionId;

    private DecisionSpec(Builder builder) {
        this.tenantId = requireNonBlank(builder.tenantId, "tenantId");
        this.environment = requireNonBlank(builder.environment, "environment");
        this.decisionType = requireNonBlank(builder.decisionType, "decisionType");
        if (builder.actor == null) {
            throw new IllegalArgumentException("actor must not be null");
        }
        this.actor = builder.actor;
        this.traceId = blankToNull(builder.traceId);
        this.decisionId = blankToNull(builder.decisionId);
        this.parentDecisionId = blankToNull(builder.parentDecisionId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String tenantId() {
        return tenantId;
    }

    public String environment() {
        return environment;
    }

    public String decisionType() {
        return decisionType;
    }

    public Actor actor() {
        return actor;
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

    public static final class Builder {
        private String tenantId;
        private String environment;
        private String decisionType;
        private Actor actor;
        private String traceId;
        private String decisionId;
        private String parentDecisionId;

        private Builder() {
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder decisionType(String decisionType) {
            this.decisionType = decisionType;
            return this;
        }

        public Builder actor(Actor actor) {
            this.actor = actor;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder decisionId(String decisionId) {
            this.decisionId = decisionId;
            return this;
        }

        public Builder parentDecisionId(String parentDecisionId) {
            this.parentDecisionId = parentDecisionId;
            return this;
        }

        public DecisionSpec build() {
            return new DecisionSpec(this);
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
