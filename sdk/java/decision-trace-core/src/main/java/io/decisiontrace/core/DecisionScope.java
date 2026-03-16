package io.decisiontrace.core;

import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.context.DecisionFrame;
import io.decisiontrace.core.emitter.DecisionEmitter;
import io.decisiontrace.core.ids.IdGenerator;
import io.decisiontrace.core.ids.UuidIdGenerator;
import io.decisiontrace.core.model.CausalLink;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.core.model.EventType;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DecisionScope implements AutoCloseable {
    private final DecisionSpec spec;
    private final DecisionEmitter emitter;
    private final DecisionContextHolder contextHolder;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final String traceId;
    private final String decisionId;
    private final String parentDecisionId;
    private final DecisionFrame frame;
    private boolean closed;

    private DecisionScope(
            DecisionSpec spec,
            DecisionEmitter emitter,
            DecisionContextHolder contextHolder,
            IdGenerator idGenerator,
            Clock clock) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.emitter = Objects.requireNonNull(emitter, "emitter");
        this.contextHolder = Objects.requireNonNull(contextHolder, "contextHolder");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");

        DecisionFrame parentFrame = contextHolder.current();
        this.traceId = spec.traceId() != null
                ? spec.traceId()
                : parentFrame != null ? parentFrame.traceId() : idGenerator.generateId();
        this.decisionId = spec.decisionId() != null ? spec.decisionId() : idGenerator.generateId();
        this.parentDecisionId = spec.parentDecisionId() != null
                ? spec.parentDecisionId()
                : parentFrame != null ? parentFrame.decisionId() : null;
        this.frame = new DecisionFrame(traceId, decisionId);

        contextHolder.push(frame);
        emit(EventType.DECISION_START, Map.of(), null);
    }

    public static DecisionScope open(DecisionSpec spec, DecisionEmitter emitter) {
        return new DecisionScope(spec, emitter, new DecisionContextHolder(), new UuidIdGenerator(), Clock.systemUTC());
    }

    public static DecisionScope open(
            DecisionSpec spec,
            DecisionEmitter emitter,
            DecisionContextHolder contextHolder,
            IdGenerator idGenerator,
            Clock clock) {
        return new DecisionScope(spec, emitter, contextHolder, idGenerator, clock);
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

    public DecisionTraceEvent evidence(String key, Object value) {
        return evidence(key, value, null);
    }

    public DecisionTraceEvent evidence(String key, Object value, List<CausalLink> causalLinks) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", key);
        payload.put("value", value);
        return emit(EventType.DECISION_EVIDENCE, payload, causalLinks);
    }

    public DecisionTraceEvent policyCheck(String policy, String result) {
        return policyCheck(policy, result, null, null);
    }

    public DecisionTraceEvent policyCheck(
            String policy,
            String result,
            Map<String, Object> inputs,
            List<CausalLink> causalLinks) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("policy", policy);
        payload.put("result", result);
        if (inputs != null && !inputs.isEmpty()) {
            payload.put("inputs", new LinkedHashMap<>(inputs));
        }
        return emit(EventType.DECISION_POLICY_CHECK, payload, causalLinks);
    }

    public DecisionTraceEvent action(Map<String, Object> payload) {
        return emit(EventType.DECISION_ACTION, payload, null);
    }

    public DecisionTraceEvent approval(Map<String, Object> payload) {
        return emit(EventType.DECISION_APPROVAL, payload, null);
    }

    public DecisionTraceEvent outcome(String status) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        return emit(EventType.DECISION_OUTCOME, payload, null);
    }

    public DecisionTraceEvent error(Throwable throwable) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", throwable.getMessage());
        payload.put("error_type", throwable.getClass().getSimpleName());
        return emit(EventType.DECISION_ERROR, payload, null);
    }

    public DecisionTraceEvent evaluation(Map<String, Object> payload) {
        return emit(EventType.DECISION_EVALUATION, payload, null);
    }

    private DecisionTraceEvent emit(EventType eventType, Map<String, Object> payload, List<CausalLink> causalLinks) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        DecisionTraceEvent event = new DecisionTraceEvent(
                spec.tenantId(),
                spec.environment(),
                DecisionTraceVersion.SCHEMA_VERSION,
                DateTimeFormatter.ISO_INSTANT.format(Instant.now(clock)),
                traceId,
                decisionId,
                parentDecisionId,
                idGenerator.generateId(),
                eventType,
                spec.decisionType(),
                spec.actor(),
                new LinkedHashMap<>(payload),
                causalLinks);
        emitter.emit(event);
        return event;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        contextHolder.pop(frame);
    }
}
