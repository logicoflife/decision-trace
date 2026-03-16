package io.decisiontrace.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.emitter.DecisionEmitter;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.core.model.EventType;
import io.decisiontrace.spring.annotation.Decision;
import io.decisiontrace.spring.config.DecisionTraceProperties;
import io.decisiontrace.spring.propagation.InboundTraceContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
        classes = DecisionTraceSpringBootStarterTest.TestApplication.class,
        properties = {
                "decision-trace.tenant-id=tenant-a",
                "decision-trace.environment=test",
                "decision-trace.actor-id=risk-engine"
        })
@AutoConfigureMockMvc
class DecisionTraceSpringBootStarterTest {
    @Autowired
    private DemoService demoService;

    @Autowired
    private RecordingDecisionEmitter emitter;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DecisionContextHolder contextHolder;

    @Autowired
    private InboundTraceContext inboundTraceContext;

    @Autowired
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void resetEmitter() {
        emitter.clear();
    }

    @Test
    void annotationEmitsValidLifecycleEvents() {
        String result = demoService.performDecision();

        assertEquals("ok", result);
        assertEquals(2, emitter.events().size());
        DecisionTraceEvent start = emitter.events().get(0);
        DecisionTraceEvent outcome = emitter.events().get(1);
        assertEquals(EventType.DECISION_START, start.eventType());
        assertEquals(EventType.DECISION_OUTCOME, outcome.eventType());
        assertEquals("tenant-a", start.tenantId());
        assertEquals("test", start.environment());
        assertEquals("risk-engine", start.actor().id());
        assertEquals(start.traceId(), outcome.traceId());
        assertEquals(start.decisionId(), outcome.decisionId());
        assertEquals("success", outcome.payload().get("status"));
    }

    @Test
    void errorSemanticsRethrowBusinessException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> demoService.failDecision());

        assertEquals("boom", exception.getMessage());
        assertEquals(2, emitter.events().size());
        assertEquals(EventType.DECISION_ERROR, emitter.events().get(1).eventType());
        assertEquals("boom", emitter.events().get(1).payload().get("message"));
    }

    @Test
    void inboundTraceHeadersAreAdoptedAndThreadLocalsAreCleared() throws Exception {
        mockMvc.perform(get("/decision")
                        .header("X-Decision-Trace-Trace-Id", "trace-inbound")
                        .header("X-Decision-Trace-Parent-Decision-Id", "decision-upstream"))
                .andExpect(status().isOk());

        assertFalse(emitter.events().isEmpty());
        DecisionTraceEvent start = emitter.events().get(0);
        assertEquals("trace-inbound", start.traceId());
        assertEquals("decision-upstream", start.parentDecisionId());
        assertNull(contextHolder.current());
        assertNull(inboundTraceContext.current());
    }

    @Test
    void outboundRestTemplatePropagatesCurrentDecision() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(MockRestRequestMatchers.requestTo("https://risk.example.local/check"))
                .andExpect(request -> assertFalse(
                        request.getHeaders().getFirst("X-Decision-Trace-Trace-Id").isBlank()))
                .andExpect(request -> assertFalse(
                        request.getHeaders().getFirst("X-Decision-Trace-Parent-Decision-Id").isBlank()))
                .andRespond(MockRestResponseCreators.withSuccess("accepted", MediaType.TEXT_PLAIN));

        String body = demoService.callDownstream();

        assertEquals("accepted", body);
        server.verify();
    }

    @Test
    void failOpenInstrumentationDoesNotBreakBusinessPath() {
        double before = meterRegistry.find("decision_trace.instrumentation.failures").counter() == null
                ? 0.0d
                : meterRegistry.find("decision_trace.instrumentation.failures").counter().count();
        demoService.enableEmitterFailures(true);

        String result = demoService.performDecision();

        assertEquals("ok", result);
        assertNotNull(meterRegistry.find("decision_trace.instrumentation.failures").counter());
        assertTrue(meterRegistry.find("decision_trace.instrumentation.failures").counter().count() > before);
        demoService.enableEmitterFailures(false);
    }

    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        RecordingDecisionEmitter recordingDecisionEmitter() {
            return new RecordingDecisionEmitter();
        }

        @Bean
        @Primary
        DecisionEmitter decisionEmitter(RecordingDecisionEmitter emitter) {
            return emitter;
        }

        @Bean
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        RestTemplate restTemplate(RestTemplateBuilder builder) {
            return builder.build();
        }

        @Bean
        DemoService demoService(RestTemplate restTemplate, RecordingDecisionEmitter emitter) {
            return new DemoService(restTemplate, emitter);
        }

        @Bean
        DecisionController decisionController(DemoService demoService) {
            return new DecisionController(demoService);
        }
    }

    @Service
    static class DemoService {
        private final RestTemplate restTemplate;
        private final RecordingDecisionEmitter emitter;

        DemoService(RestTemplate restTemplate, RecordingDecisionEmitter emitter) {
            this.restTemplate = restTemplate;
            this.emitter = emitter;
        }

        @Decision(decisionType = "RISK_CHECK")
        String performDecision() {
            return "ok";
        }

        @Decision(decisionType = "FAIL_CHECK")
        String failDecision() {
            throw new IllegalStateException("boom");
        }

        @Decision(decisionType = "DOWNSTREAM_CALL")
        String callDownstream() {
            return restTemplate.getForObject("https://risk.example.local/check", String.class);
        }

        void enableEmitterFailures(boolean enabled) {
            emitter.failuresEnabled = enabled;
        }
    }

    @RestController
    static class DecisionController {
        private final DemoService demoService;

        DecisionController(DemoService demoService) {
            this.demoService = demoService;
        }

        @GetMapping("/decision")
        String decision(@RequestHeader(name = "X-Decision-Trace-Trace-Id", required = false) String traceId) {
            return demoService.performDecision() + ":" + traceId;
        }
    }

    static class RecordingDecisionEmitter implements DecisionEmitter {
        private final List<DecisionTraceEvent> events = new CopyOnWriteArrayList<>();
        private volatile boolean failuresEnabled;

        @Override
        public void emit(DecisionTraceEvent event) {
            if (failuresEnabled) {
                throw new IllegalStateException("emitter offline");
            }
            events.add(event);
        }

        @Override
        public void flush() {
            if (failuresEnabled) {
                throw new IllegalStateException("flush offline");
            }
        }

        List<DecisionTraceEvent> events() {
            return new ArrayList<>(events);
        }

        void clear() {
            events.clear();
            failuresEnabled = false;
        }
    }
}
