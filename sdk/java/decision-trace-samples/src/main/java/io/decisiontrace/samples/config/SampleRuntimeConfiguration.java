package io.decisiontrace.samples.config;

import io.decisiontrace.core.emitter.DecisionEmitter;
import io.decisiontrace.core.emitter.LmaxDecisionEmitter;
import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.exporter.otel.OpenTelemetryDecisionExporter;
import io.decisiontrace.core.runtime.DecisionDispatcher;
import io.decisiontrace.core.runtime.DecisionRuntimeMetrics;
import io.decisiontrace.samples.telemetry.RecordingDecisionExporter;
import io.decisiontrace.samples.telemetry.ResettableJsonLedgerExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

@Configuration
public class SampleRuntimeConfiguration {
    @Bean(destroyMethod = "close")
    SdkTracerProvider sampleTracerProvider(InMemorySpanExporter spanExporter) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
    }

    @Bean(destroyMethod = "close")
    OpenTelemetrySdk sampleOpenTelemetrySdk(SdkTracerProvider tracerProvider) {
        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    }

    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }

    @Bean
    RecordingDecisionExporter recordingDecisionExporter() {
        return new RecordingDecisionExporter();
    }

    @Bean
    Path sampleLedgerPath() throws IOException {
        return Files.createTempFile("decision-trace-golden-", ".jsonl");
    }

    @Bean
    ResettableJsonLedgerExporter resettableJsonLedgerExporter(Path sampleLedgerPath) {
        return new ResettableJsonLedgerExporter(sampleLedgerPath);
    }

    @Bean
    DecisionRuntimeMetrics decisionRuntimeMetrics() {
        return new DecisionRuntimeMetrics();
    }

    @Bean(destroyMethod = "close")
    @Primary
    DecisionEmitter decisionEmitter(
            RecordingDecisionExporter recordingDecisionExporter,
            ResettableJsonLedgerExporter resettableJsonLedgerExporter,
            OpenTelemetrySdk openTelemetrySdk,
            DecisionRuntimeMetrics decisionRuntimeMetrics) {
        List<DecisionExporter> exporters = List.of(
                recordingDecisionExporter,
                resettableJsonLedgerExporter,
                new OpenTelemetryDecisionExporter(openTelemetrySdk.getTracer("decision-trace-samples")));
        return new LmaxDecisionEmitter(256, new DecisionDispatcher(exporters, decisionRuntimeMetrics), decisionRuntimeMetrics);
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
