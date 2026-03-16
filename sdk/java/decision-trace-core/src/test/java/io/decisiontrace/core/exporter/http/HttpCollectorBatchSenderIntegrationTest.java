package io.decisiontrace.core.exporter.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.decisiontrace.core.model.Actor;
import io.decisiontrace.core.model.ActorType;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.core.model.EventType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpCollectorBatchSenderIntegrationTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void exportsBatchToCollectorEndpointOverHttp() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            requestPath.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
        });

        CollectorBatchExporter exporter = new CollectorBatchExporter(
                new HttpCollectorBatchSender(
                        HttpClient.newHttpClient(),
                        collectorEndpoint(),
                        Duration.ofSeconds(2)),
                2);

        exporter.export(sampleEvent("event-1"));
        exporter.export(sampleEvent("event-2"));

        assertEquals(1, requestCount.get());
        assertEquals("/v1/events", requestPath.get());
        assertEquals("application/json", contentType.get());
        assertTrue(requestBody.get().startsWith("["));
        assertTrue(requestBody.get().endsWith("]"));
        assertTrue(requestBody.get().contains("\"event_id\":\"event-1\""));
        assertTrue(requestBody.get().contains("\"event_id\":\"event-2\""));
        assertTrue(requestBody.get().contains("\"event_type\":\"decision.start\""));
    }

    @Test
    void nonSuccessCollectorResponseFailsExport() throws Exception {
        startServer(exchange -> {
            exchange.sendResponseHeaders(503, -1);
        });

        CollectorBatchExporter exporter = new CollectorBatchExporter(
                new HttpCollectorBatchSender(
                        HttpClient.newHttpClient(),
                        collectorEndpoint(),
                        Duration.ofSeconds(2)),
                1);

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> exporter.export(sampleEvent("event-1")));

        assertTrue(error.getMessage().contains("Collector rejected batch with status 503"));
        assertEquals(1, exporter.pendingCount());
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/events", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private URI collectorEndpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/events");
    }

    private static DecisionTraceEvent sampleEvent(String eventId) {
        return new DecisionTraceEvent(
                "tenant-a",
                "test",
                "1.0",
                "2026-03-16T15:30:00Z",
                "trace-1",
                "decision-1",
                null,
                eventId,
                EventType.DECISION_START,
                "RISK_CHECK",
                Actor.of("risk-engine", ActorType.SYSTEM),
                new LinkedHashMap<>(Map.of("status", "ok")),
                null);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
