package io.decisiontrace.spring.http;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class DecisionTraceRestTemplateInterceptor implements ClientHttpRequestInterceptor {
    private final DecisionTracePropagationSupport propagationSupport;

    public DecisionTraceRestTemplateInterceptor(DecisionTracePropagationSupport propagationSupport) {
        this.propagationSupport = propagationSupport;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        propagationSupport.apply(request.getHeaders());
        return execution.execute(request, body);
    }
}
