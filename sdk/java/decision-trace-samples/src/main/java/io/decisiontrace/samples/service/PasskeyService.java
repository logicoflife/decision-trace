package io.decisiontrace.samples.service;

import io.decisiontrace.samples.service.dto.PasskeyRequest;
import io.decisiontrace.samples.service.dto.PasskeyResponse;
import io.decisiontrace.spring.annotation.Decision;
import io.decisiontrace.spring.context.DecisionContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PasskeyService {
    private final DecisionContext decisionContext;

    public PasskeyService(DecisionContext decisionContext) {
        this.decisionContext = decisionContext;
    }

    @Decision(decisionType = "PASSKEY_SERVICE_VERIFY", actorId = "passkey-service")
    public PasskeyResponse verify(PasskeyRequest request) {
        decisionContext.approval(new LinkedHashMap<>(Map.of(
                "channel", "PASSKEY",
                "user_id", request.userId())));
        return new PasskeyResponse("PASSKEY");
    }
}
