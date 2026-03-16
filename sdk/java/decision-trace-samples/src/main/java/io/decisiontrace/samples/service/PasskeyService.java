package io.decisiontrace.samples.service;

import io.decisiontrace.samples.service.dto.PasskeyRequest;
import io.decisiontrace.samples.service.dto.PasskeyResponse;
import io.decisiontrace.spring.annotation.Decision;
import org.springframework.stereotype.Service;

@Service
public class PasskeyService {
    @Decision(decisionType = "PASSKEY_SERVICE_VERIFY", actorId = "passkey-service")
    public PasskeyResponse verify(PasskeyRequest request) {
        return new PasskeyResponse("PASSKEY");
    }
}
