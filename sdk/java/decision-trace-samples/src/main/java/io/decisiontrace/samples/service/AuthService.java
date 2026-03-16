package io.decisiontrace.samples.service;

import io.decisiontrace.samples.service.dto.AuthRequest;
import io.decisiontrace.samples.service.dto.AuthResponse;
import io.decisiontrace.samples.service.dto.RiskRequest;
import io.decisiontrace.samples.service.dto.RiskResponse;
import io.decisiontrace.spring.annotation.Decision;
import io.decisiontrace.spring.context.DecisionContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AuthService {
    private final RestTemplate restTemplate;
    private final SampleServiceEndpoints endpoints;
    private final DecisionContext decisionContext;

    public AuthService(RestTemplate restTemplate, SampleServiceEndpoints endpoints, DecisionContext decisionContext) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
        this.decisionContext = decisionContext;
    }

    @Decision(decisionType = "AUTH_SERVICE_LOGIN", actorId = "auth-service")
    public AuthResponse login(AuthRequest request) {
        decisionContext.evidence("user_id", request.userId());
        decisionContext.evidence("device_id", request.deviceId());
        RiskResponse risk = restTemplate.postForObject(
                endpoints.local("/risk/evaluate"),
                new RiskRequest(request.userId(), request.deviceId(), request.ipAddress()),
                RiskResponse.class);
        decisionContext.evaluation(new LinkedHashMap<>(Map.of(
                "risk_decision", risk.decision(),
                "passkey_status", risk.passkeyStatus())));
        return new AuthResponse("ALLOW", risk.decision(), risk.passkeyStatus());
    }
}
