package io.decisiontrace.samples.service;

import io.decisiontrace.samples.service.dto.AuthRequest;
import io.decisiontrace.samples.service.dto.AuthResponse;
import io.decisiontrace.samples.service.dto.RiskRequest;
import io.decisiontrace.samples.service.dto.RiskResponse;
import io.decisiontrace.spring.annotation.Decision;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AuthService {
    private final RestTemplate restTemplate;
    private final SampleServiceEndpoints endpoints;

    public AuthService(RestTemplate restTemplate, SampleServiceEndpoints endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Decision(decisionType = "AUTH_SERVICE_LOGIN", actorId = "auth-service")
    public AuthResponse login(AuthRequest request) {
        RiskResponse risk = restTemplate.postForObject(
                endpoints.local("/risk/evaluate"),
                new RiskRequest(request.userId(), request.deviceId(), request.ipAddress()),
                RiskResponse.class);
        return new AuthResponse("ALLOW", risk.decision(), risk.passkeyStatus());
    }
}
