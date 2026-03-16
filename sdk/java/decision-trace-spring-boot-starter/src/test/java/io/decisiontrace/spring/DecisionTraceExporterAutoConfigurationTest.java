package io.decisiontrace.spring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.exporter.http.CollectorBatchExporter;
import io.decisiontrace.core.exporter.http.CollectorBatchSender;
import io.decisiontrace.core.exporter.otel.OpenTelemetryDecisionExporter;
import io.opentelemetry.api.OpenTelemetry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
        classes = DecisionTraceExporterAutoConfigurationTest.TestApplication.class,
        properties = {
                "decision-trace.collector-endpoint=https://collector.example.local/v1/events",
                "decision-trace.collector-batch-size=3",
                "decision-trace.otel-export-enabled=true"
        })
class DecisionTraceExporterAutoConfigurationTest {
    @Autowired
    private List<DecisionExporter> exporters;

    @Test
    void autoConfigurationCreatesCollectorAndOtelExporters() {
        assertTrue(exporters.stream().anyMatch(CollectorBatchExporter.class::isInstance));
        assertTrue(exporters.stream().anyMatch(OpenTelemetryDecisionExporter.class::isInstance));
    }

    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        CollectorBatchSender collectorBatchSender() {
            return payload -> {
            };
        }

        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }
}
