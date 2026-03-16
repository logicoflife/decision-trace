package io.decisiontrace.spring.context;

import io.decisiontrace.core.model.CausalLink;
import io.decisiontrace.core.model.DecisionTraceEvent;
import java.util.List;
import java.util.Map;

public interface DecisionContext {
    boolean isActive();

    String traceId();

    String decisionId();

    String parentDecisionId();

    DecisionTraceEvent evidence(String key, Object value);

    DecisionTraceEvent evidence(String key, Object value, List<CausalLink> causalLinks);

    DecisionTraceEvent policyCheck(String policy, String result);

    DecisionTraceEvent policyCheck(
            String policy,
            String result,
            Map<String, Object> inputs,
            List<CausalLink> causalLinks);

    DecisionTraceEvent action(Map<String, Object> payload);

    DecisionTraceEvent approval(Map<String, Object> payload);

    DecisionTraceEvent evaluation(Map<String, Object> payload);
}
