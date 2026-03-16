package io.decisiontrace.samples.service;

import io.decisiontrace.core.DecisionScope;
import io.decisiontrace.core.DecisionSpec;
import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.emitter.DecisionEmitter;
import io.decisiontrace.core.ids.IdGenerator;
import io.decisiontrace.core.model.Actor;
import io.decisiontrace.core.model.ActorType;
import io.decisiontrace.samples.service.dto.PasskeyRequest;
import io.decisiontrace.samples.service.dto.PasskeyResponse;
import io.decisiontrace.samples.service.dto.RiskRequest;
import io.decisiontrace.samples.service.dto.RiskResponse;
import io.decisiontrace.spring.annotation.Decision;
import io.decisiontrace.spring.config.DecisionTraceProperties;
import io.decisiontrace.spring.context.DecisionContext;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RiskService {
    private final RestTemplate restTemplate;
    private final SampleServiceEndpoints endpoints;
    private final DecisionEmitter decisionEmitter;
    private final DecisionContextHolder decisionContextHolder;
    private final IdGenerator idGenerator;
    private final Clock decisionTraceClock;
    private final DecisionTraceProperties properties;
    private final DecisionContext decisionContext;

    public RiskService(
            RestTemplate restTemplate,
            SampleServiceEndpoints endpoints,
            DecisionEmitter decisionEmitter,
            DecisionContextHolder decisionContextHolder,
            IdGenerator idGenerator,
            Clock decisionTraceClock,
            DecisionTraceProperties properties,
            DecisionContext decisionContext) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
        this.decisionEmitter = decisionEmitter;
        this.decisionContextHolder = decisionContextHolder;
        this.idGenerator = idGenerator;
        this.decisionTraceClock = decisionTraceClock;
        this.properties = properties;
        this.decisionContext = decisionContext;
    }

    @Decision(decisionType = "RISK_ORCHESTRATION", actorId = "risk-service")
    public RiskResponse evaluate(RiskRequest request) {
        decisionContext.evidence("risk_request_ip", request.ipAddress());
        decisionContext.policyCheck("risk_entry_policy", "continue", Map.of("user_id", request.userId()), null);
        try (DecisionScope deviceTrust = nestedScope("DEVICE_TRUST_CHECK")) {
            deviceTrust.evidence("device_id", request.deviceId());
            deviceTrust.outcome("trusted");
        }

        try (DecisionScope velocity = nestedScope("VELOCITY_CHECK")) {
            velocity.evidence("ip_address", request.ipAddress());
            velocity.outcome("stable");
        }

        String passkeyStatus;
        try (DecisionScope finalRisk = nestedScope("FINAL_RISK_DECISION")) {
            finalRisk.policyCheck("risk_v1", "step_up", Map.of("user_id", request.userId()), null);
            PasskeyResponse passkey = restTemplate.postForObject(
                    endpoints.local("/passkey/verify"),
                    new PasskeyRequest(request.userId()),
                    PasskeyResponse.class);
            passkeyStatus = passkey.status();
            finalRisk.action(new LinkedHashMap<>(Map.of("action", "PASSKEY")));
            finalRisk.outcome("ALLOW");
        }

        decisionContext.evaluation(new LinkedHashMap<>(Map.of(
                "decision", "ALLOW",
                "passkey_status", passkeyStatus)));
        return new RiskResponse("ALLOW", passkeyStatus);
    }

    private DecisionScope nestedScope(String decisionType) {
        Actor actor = new Actor(
                "risk-service",
                ActorType.SYSTEM,
                properties.getActorVersion(),
                properties.getActorOrg());
        DecisionSpec spec = DecisionSpec.builder()
                .tenantId(properties.getTenantId())
                .environment(properties.getEnvironment())
                .decisionType(decisionType)
                .actor(actor)
                .build();
        return DecisionScope.open(spec, decisionEmitter, decisionContextHolder, idGenerator, decisionTraceClock);
    }
}
