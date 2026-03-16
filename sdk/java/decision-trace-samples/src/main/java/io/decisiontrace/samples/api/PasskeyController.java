package io.decisiontrace.samples.api;

import io.decisiontrace.samples.service.PasskeyService;
import io.decisiontrace.samples.service.dto.PasskeyRequest;
import io.decisiontrace.samples.service.dto.PasskeyResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasskeyController {
    private final PasskeyService passkeyService;

    public PasskeyController(PasskeyService passkeyService) {
        this.passkeyService = passkeyService;
    }

    @PostMapping("/passkey/verify")
    public PasskeyResponse verify(@RequestBody PasskeyRequest request) {
        return passkeyService.verify(request);
    }
}
