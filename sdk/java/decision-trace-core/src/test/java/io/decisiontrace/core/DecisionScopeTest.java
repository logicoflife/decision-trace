package io.decisiontrace.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.emitter.InMemoryDecisionEmitter;
import io.decisiontrace.core.exporter.InMemoryDecisionExporter;
import io.decisiontrace.core.ids.IdGenerator;
import io.decisiontrace.core.json.DecisionJsonSerializer;
import io.decisiontrace.core.model.Actor;
import io.decisiontrace.core.model.ActorType;
import io.decisiontrace.core.model.CausalLink;
import io.decisiontrace.core.model.CausalLinkType;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.core.model.EventType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionScopeTest {
    @Test
    void nestedParentageUsesThreadLocalLineage() {
        InMemoryDecisionEmitter emitter = new InMemoryDecisionEmitter();
        DecisionContextHolder contextHolder = new DecisionContextHolder();
        FixedIdGenerator ids = new FixedIdGenerator("trace-root", "decision-root", "event-start-root",
                "decision-child", "event-start-child");
        Clock clock = Clock.fixed(Instant.parse("2026-03-16T15:30:00Z"), ZoneOffset.UTC);

        DecisionSpec rootSpec = baseSpec("AUTH_SERVICE");
        DecisionSpec childSpec = baseSpec("RISK_CHECK");

        try (DecisionScope root = DecisionScope.open(rootSpec, emitter, contextHolder, ids, clock)) {
            try (DecisionScope child = DecisionScope.open(childSpec, emitter, contextHolder, ids, clock)) {
                assertEquals("trace-root", root.traceId());
                assertEquals("trace-root", child.traceId());
                assertEquals("decision-root", root.decisionId());
                assertEquals("decision-child", child.decisionId());
                assertEquals("decision-root", child.parentDecisionId());
            }
            assertEquals("decision-root", contextHolder.current().decisionId());
        }
        assertNull(contextHolder.current());

        List<DecisionTraceEvent> events = emitter.events();
        assertEquals(2, events.size());
        assertNull(events.get(0).parentDecisionId());
        assertEquals("decision-root", events.get(1).parentDecisionId());
    }

    @Test
    void serializerMatchesCanonicalSchemaShape() {
        DecisionJsonSerializer serializer = new DecisionJsonSerializer();
        DecisionTraceEvent event = new DecisionTraceEvent(
                "tenant-a",
                "prod",
                "1.0",
                "2026-03-16T15:30:00Z",
                "trace-123",
                "decision-456",
                null,
                "event-789",
                EventType.DECISION_EVIDENCE,
                "RISK_CHECK",
                new Actor("risk-engine", ActorType.SYSTEM, "1.2.3", "trust"),
                new LinkedHashMap<>(Map.of("key", "device_trust", "value", true)),
                List.of(new CausalLink(CausalLinkType.USES_EVIDENCE_FROM, "decision-111")));

        String json = serializer.serialize(event);

        assertTrue(json.contains("\"tenant_id\":\"tenant-a\""));
        assertTrue(json.contains("\"schema_version\":\"1.0\""));
        assertTrue(json.contains("\"event_type\":\"decision.evidence\""));
        assertTrue(json.contains("\"actor\":{\"id\":\"risk-engine\",\"type\":\"system\",\"version\":\"1.2.3\",\"org\":\"trust\"}"));
        assertTrue(json.contains("\"causal_links\":[{\"type\":\"uses_evidence_from\",\"target_decision_id\":\"decision-111\"}]"));
        assertFalse(json.contains("additionalProperties"));
    }

    @Test
    void eventsStayOrderedWithinScope() {
        InMemoryDecisionEmitter emitter = new InMemoryDecisionEmitter();
        DecisionContextHolder contextHolder = new DecisionContextHolder();
        FixedIdGenerator ids = new FixedIdGenerator("trace-1", "decision-1", "event-1", "event-2", "event-3", "event-4");
        Clock clock = Clock.fixed(Instant.parse("2026-03-16T15:30:00Z"), ZoneOffset.UTC);

        try (DecisionScope scope = DecisionScope.open(baseSpec("FINAL_RISK_DECISION"), emitter, contextHolder, ids, clock)) {
            scope.evidence("velocity", "high");
            scope.policyCheck("risk_v1", "pass", Map.of("score", 42), null);
            scope.outcome("approved");
        }

        List<DecisionTraceEvent> events = emitter.events();
        assertEquals(List.of(
                EventType.DECISION_START,
                EventType.DECISION_EVIDENCE,
                EventType.DECISION_POLICY_CHECK,
                EventType.DECISION_OUTCOME), events.stream().map(DecisionTraceEvent::eventType).toList());
        assertEquals("approved", events.get(3).payload().get("status"));
    }

    @Test
    void inMemoryEmitterForwardsCanonicalEventsToExporters() {
        InMemoryDecisionExporter exporter = new InMemoryDecisionExporter();
        InMemoryDecisionEmitter emitter = new InMemoryDecisionEmitter(List.of(exporter));
        DecisionContextHolder contextHolder = new DecisionContextHolder();
        FixedIdGenerator ids = new FixedIdGenerator("trace-1", "decision-1", "event-1", "event-2");
        Clock clock = Clock.fixed(Instant.parse("2026-03-16T15:30:00Z"), ZoneOffset.UTC);

        try (DecisionScope scope = DecisionScope.open(baseSpec("DEVICE_TRUST_CHECK"), emitter, contextHolder, ids, clock)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", "step_up");
            scope.action(payload);
        }

        assertEquals(2, emitter.events().size());
        assertEquals(2, exporter.events().size());
        assertEquals(emitter.events().get(1).toMap(), exporter.events().get(1).toMap());
    }

    private static DecisionSpec baseSpec(String decisionType) {
        return DecisionSpec.builder()
                .tenantId("tenant-a")
                .environment("prod")
                .decisionType(decisionType)
                .actor(Actor.of("risk-engine", ActorType.SYSTEM))
                .build();
    }

    private static final class FixedIdGenerator implements IdGenerator {
        private final ArrayDeque<String> ids;

        private FixedIdGenerator(String... ids) {
            this.ids = new ArrayDeque<>(List.of(ids));
        }

        @Override
        public String generateId() {
            return ids.removeFirst();
        }
    }
}
