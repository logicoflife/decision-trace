package io.decisiontrace.spring.context;

import io.decisiontrace.core.DecisionScope;
import io.decisiontrace.core.model.CausalLink;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.spring.metrics.DecisionTraceMetrics;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DefaultDecisionContext implements DecisionContext {
    private final DecisionScopeHolder scopeHolder;
    private final DecisionTraceMetrics metrics;
    private final Validator validator;
    private final boolean validationEnabled;

    public DefaultDecisionContext(
            DecisionScopeHolder scopeHolder,
            DecisionTraceMetrics metrics,
            Validator validator,
            boolean validationEnabled) {
        this.scopeHolder = scopeHolder;
        this.metrics = metrics;
        this.validator = validator;
        this.validationEnabled = validationEnabled;
    }

    @Override
    public boolean isActive() {
        return scopeHolder.current() != null;
    }

    @Override
    public String traceId() {
        DecisionScope scope = scopeHolder.current();
        return scope != null ? scope.traceId() : null;
    }

    @Override
    public String decisionId() {
        DecisionScope scope = scopeHolder.current();
        return scope != null ? scope.decisionId() : null;
    }

    @Override
    public String parentDecisionId() {
        DecisionScope scope = scopeHolder.current();
        return scope != null ? scope.parentDecisionId() : null;
    }

    @Override
    public DecisionTraceEvent evidence(String key, Object value) {
        return evidence(key, value, null);
    }

    @Override
    public DecisionTraceEvent evidence(String key, Object value, List<CausalLink> causalLinks) {
        if (!isValid(new EvidenceInput(key, value))) {
            return null;
        }
        DecisionScope scope = currentScope();
        return scope != null ? scope.evidence(key, value, causalLinks) : null;
    }

    @Override
    public DecisionTraceEvent policyCheck(String policy, String result) {
        return policyCheck(policy, result, null, null);
    }

    @Override
    public DecisionTraceEvent policyCheck(
            String policy,
            String result,
            Map<String, Object> inputs,
            List<CausalLink> causalLinks) {
        if (!isValid(new PolicyCheckInput(policy, result))) {
            return null;
        }
        DecisionScope scope = currentScope();
        return scope != null ? scope.policyCheck(policy, result, inputs, causalLinks) : null;
    }

    @Override
    public DecisionTraceEvent action(Map<String, Object> payload) {
        if (!isValid(new PayloadInput(payload))) {
            return null;
        }
        DecisionScope scope = currentScope();
        return scope != null ? scope.action(payload) : null;
    }

    @Override
    public DecisionTraceEvent approval(Map<String, Object> payload) {
        if (!isValid(new PayloadInput(payload))) {
            return null;
        }
        DecisionScope scope = currentScope();
        return scope != null ? scope.approval(payload) : null;
    }

    @Override
    public DecisionTraceEvent evaluation(Map<String, Object> payload) {
        if (!isValid(new PayloadInput(payload))) {
            return null;
        }
        DecisionScope scope = currentScope();
        return scope != null ? scope.evaluation(payload) : null;
    }

    private DecisionScope currentScope() {
        DecisionScope scope = scopeHolder.current();
        if (scope == null) {
            metrics.recordInstrumentationFailure();
        }
        return scope;
    }

    private boolean isValid(Object value) {
        if (!validationEnabled || validator == null) {
            return true;
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(value);
        if (violations.isEmpty()) {
            return true;
        }
        metrics.recordInstrumentationFailure();
        return false;
    }
}
