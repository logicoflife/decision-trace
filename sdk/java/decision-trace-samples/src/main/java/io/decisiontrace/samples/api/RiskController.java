package io.decisiontrace.samples.api;

import io.decisiontrace.samples.service.RiskService;
import io.decisiontrace.samples.service.dto.RiskRequest;
import io.decisiontrace.samples.service.dto.RiskResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RiskController {
    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @PostMapping("/risk/evaluate")
    public RiskResponse evaluate(@RequestBody RiskRequest request) {
        return riskService.evaluate(request);
    }
}
