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
import io.decisiontrace.spring.context.DecisionContext;
import io.decisiontrace.spring.propagation.InboundTraceContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
        classes = DecisionTraceSpringBootStarterTest.TestApplication.class,
        properties = {
                "decision-trace.tenant-id=tenant-a",
                "decision-trace.environment=test",
                "decision-trace.actor-id=risk-engine",
                "decision-trace.validation-enabled=true"
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
    private WebClient.Builder webClientBuilder;

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
    void decisionContextEmitsLifecycleEventsInsideAnnotatedMethod() {
        String result = demoService.performRichDecision();

        assertEquals("ok", result);
        assertEquals(
                List.of(
                        EventType.DECISION_START,
                        EventType.DECISION_EVIDENCE,
                        EventType.DECISION_POLICY_CHECK,
                        EventType.DECISION_ACTION,
                        EventType.DECISION_APPROVAL,
                        EventType.DECISION_EVALUATION,
                        EventType.DECISION_OUTCOME),
                emitter.events().stream().map(DecisionTraceEvent::eventType).toList());
    }

    @Test
    void validationFailuresDoNotBreakBusinessExecution() {
        double before = meterRegistry.find("decision_trace.instrumentation.failures").counter() == null
                ? 0.0d
                : meterRegistry.find("decision_trace.instrumentation.failures").counter().count();

        String result = demoService.performInvalidDecision();

        assertEquals("ok", result);
        assertEquals(
                List.of(EventType.DECISION_START, EventType.DECISION_OUTCOME),
                emitter.events().stream().map(DecisionTraceEvent::eventType).toList());
        assertTrue(meterRegistry.find("decision_trace.instrumentation.failures").counter().count() > before);
    }

    @Test
    void outboundWebClientPropagatesCurrentDecision() {
        CapturingExchangeFunction exchange = new CapturingExchangeFunction();
        WebClient webClient = webClientBuilder.exchangeFunction(exchange).build();

        String body = demoService.callDownstreamWithWebClient(webClient);

        assertEquals("accepted", body);
        assertFalse(exchange.request.headers().getFirst("X-Decision-Trace-Trace-Id").isBlank());
        assertFalse(exchange.request.headers().getFirst("X-Decision-Trace-Parent-Decision-Id").isBlank());
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
        DemoService demoService(
                RestTemplate restTemplate,
                RecordingDecisionEmitter emitter,
                DecisionContext decisionContext) {
            return new DemoService(restTemplate, emitter, decisionContext);
        }

        @Bean
        DecisionController decisionController(DemoService demoService) {
            return new DecisionController(demoService);
        }

        @Bean
        WebClient.Builder webClientBuilder(List<WebClientCustomizer> customizers) {
            WebClient.Builder builder = WebClient.builder();
            customizers.forEach(customizer -> customizer.customize(builder));
            return builder;
        }
    }

    @Service
    static class DemoService {
        private final RestTemplate restTemplate;
        private final RecordingDecisionEmitter emitter;
        private final DecisionContext decisionContext;

        DemoService(RestTemplate restTemplate, RecordingDecisionEmitter emitter, DecisionContext decisionContext) {
            this.restTemplate = restTemplate;
            this.emitter = emitter;
            this.decisionContext = decisionContext;
        }

        @Decision(decisionType = "RISK_CHECK")
        String performDecision() {
            return "ok";
        }

        @Decision(decisionType = "RICH_CHECK")
        String performRichDecision() {
            decisionContext.evidence("input", "value");
            decisionContext.policyCheck("risk_v1", "allow", Map.of("score", 12), null);
            decisionContext.action(new LinkedHashMap<>(Map.of("action", "ALLOW")));
            decisionContext.approval(new LinkedHashMap<>(Map.of("approver", "risk-engine")));
            decisionContext.evaluation(new LinkedHashMap<>(Map.of("quality", "complete")));
            return "ok";
        }

        @Decision(decisionType = "INVALID_CHECK")
        String performInvalidDecision() {
            decisionContext.evidence("", "value");
            decisionContext.action(Map.of());
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

        @Decision(decisionType = "WEBCLIENT_CALL")
        String callDownstreamWithWebClient(WebClient webClient) {
            return webClient.get()
                    .uri("https://risk.example.local/check")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        }

        void enableEmitterFailures(boolean enabled) {
            emitter.failuresEnabled = enabled;
        }
    }

    static final class CapturingExchangeFunction implements ExchangeFunction {
        private ClientRequest request;

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            this.request = request;
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body("accepted")
                    .build());
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
