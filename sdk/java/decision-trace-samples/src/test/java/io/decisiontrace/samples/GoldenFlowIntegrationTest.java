package io.decisiontrace.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.decisiontrace.core.emitter.DecisionEmitter;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.core.model.EventType;
import io.decisiontrace.samples.service.dto.AuthRequest;
import io.decisiontrace.samples.service.dto.AuthResponse;
import io.decisiontrace.samples.telemetry.RecordingDecisionExporter;
import io.decisiontrace.samples.telemetry.ResettableJsonLedgerExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
        classes = GoldenFlowApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "decision-trace.tenant-id=tenant-a",
                "decision-trace.environment=test"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GoldenFlowIntegrationTest {
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "tenant_id",
            "environment",
            "schema_version",
            "timestamp",
            "trace_id",
            "decision_id",
            "event_id",
            "event_type",
            "decision_type",
            "actor",
            "payload");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DecisionEmitter decisionEmitter;

    @Autowired
    private RecordingDecisionExporter recordingDecisionExporter;

    @Autowired
    private ResettableJsonLedgerExporter ledgerExporter;

    @Autowired
    private InMemorySpanExporter spanExporter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void goldenFlowProducesDistributedDagWithNestedRiskDecisions() {
        AuthResponse response = restTemplate.postForObject(
                "/auth/login",
                new AuthRequest("user-1", "device-7", "10.0.0.5"),
                AuthResponse.class);
        decisionEmitter.flush();

        assertEquals("ALLOW", response.status());
        assertEquals("ALLOW", response.riskDecision());
        assertEquals("PASSKEY", response.passkeyStatus());

        List<DecisionTraceEvent> events = recordingDecisionExporter.snapshot();
        assertFalse(events.isEmpty());
        List<DecisionTraceEvent> startEvents = events.stream()
                .filter(event -> event.eventType() == EventType.DECISION_START)
                .toList();

        Map<String, DecisionTraceEvent> startsByType = startEvents.stream()
                .collect(Collectors.toMap(DecisionTraceEvent::decisionType, event -> event));

        assertEquals(Set.of(
                "AUTH_SERVICE_LOGIN",
                "RISK_ORCHESTRATION",
                "DEVICE_TRUST_CHECK",
                "VELOCITY_CHECK",
                "FINAL_RISK_DECISION",
                "PASSKEY_SERVICE_VERIFY"), startsByType.keySet());

        String traceId = startsByType.get("AUTH_SERVICE_LOGIN").traceId();
        assertTrue(startEvents.stream().allMatch(event -> traceId.equals(event.traceId())));
        assertNull(startsByType.get("AUTH_SERVICE_LOGIN").parentDecisionId());
        assertEquals(
                startsByType.get("AUTH_SERVICE_LOGIN").decisionId(),
                startsByType.get("RISK_ORCHESTRATION").parentDecisionId());
        assertEquals(
                startsByType.get("RISK_ORCHESTRATION").decisionId(),
                startsByType.get("DEVICE_TRUST_CHECK").parentDecisionId());
        assertEquals(
                startsByType.get("RISK_ORCHESTRATION").decisionId(),
                startsByType.get("VELOCITY_CHECK").parentDecisionId());
        assertEquals(
                startsByType.get("RISK_ORCHESTRATION").decisionId(),
                startsByType.get("FINAL_RISK_DECISION").parentDecisionId());
        assertEquals(
                startsByType.get("FINAL_RISK_DECISION").decisionId(),
                startsByType.get("PASSKEY_SERVICE_VERIFY").parentDecisionId());
    }

    @Test
    void ledgerAndOtelProjectionStayConsistentForCanonicalEvents() throws Exception {
        restTemplate.postForObject(
                "/auth/login",
                new AuthRequest("user-2", "device-9", "10.0.0.8"),
                AuthResponse.class);
        decisionEmitter.flush();

        List<DecisionTraceEvent> events = recordingDecisionExporter.snapshot();
        List<String> ledgerLines = ledgerExporter.lines();
        assertEquals(events.size(), ledgerLines.size());
        assertEquals(events.size(), spanExporter.getFinishedSpanItems().size());

        List<Map<String, Object>> parsed = ledgerLines.stream()
                .map(line -> {
                    try {
                        return objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {
                        });
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();

        for (Map<String, Object> entry : parsed) {
            assertTrue(entry.keySet().containsAll(REQUIRED_FIELDS));
            assertTrue(entry.get("actor") instanceof Map);
            assertTrue(entry.get("payload") instanceof Map);
        }
    }

    @Test
    void canonicalEventsRemainSchemaEquivalentForCollectorIngestion() {
        restTemplate.postForObject(
                "/auth/login",
                new AuthRequest("user-3", "device-3", "10.0.0.12"),
                AuthResponse.class);
        decisionEmitter.flush();

        List<DecisionTraceEvent> events = recordingDecisionExporter.snapshot();
        assertTrue(events.stream().anyMatch(event -> event.eventType() == EventType.DECISION_POLICY_CHECK));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == EventType.DECISION_ACTION));
        assertTrue(events.stream().allMatch(event -> event.toMap().keySet().containsAll(REQUIRED_FIELDS)));
        assertTrue(events.stream().allMatch(event -> event.actor().toMap().containsKey("id")));
        assertTrue(events.stream().allMatch(event -> event.actor().toMap().containsKey("type")));
    }
}
