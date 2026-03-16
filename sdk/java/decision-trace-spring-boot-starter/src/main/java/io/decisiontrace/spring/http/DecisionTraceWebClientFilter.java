package io.decisiontrace.spring.http;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

public final class DecisionTraceWebClientFilter implements ExchangeFilterFunction {
    private final DecisionTracePropagationSupport propagationSupport;

    public DecisionTraceWebClientFilter(DecisionTracePropagationSupport propagationSupport) {
        this.propagationSupport = propagationSupport;
    }

    @Override
    public Mono<org.springframework.web.reactive.function.client.ClientResponse> filter(
            ClientRequest request,
            ExchangeFunction next) {
        ClientRequest.Builder builder = ClientRequest.from(request);
        builder.headers(propagationSupport::apply);
        return next.exchange(builder.build());
    }
}
