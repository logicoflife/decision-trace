package io.decisiontrace.core.exporter.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class HttpCollectorBatchSender implements CollectorBatchSender {
    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration requestTimeout;

    public HttpCollectorBatchSender(HttpClient httpClient, URI endpoint, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    @Override
    public void send(String payload) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Collector rejected batch with status " + response.statusCode());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to export decision batch to collector", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while exporting decision batch to collector", exception);
        }
    }
}
