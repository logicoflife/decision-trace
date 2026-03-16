package io.decisiontrace.spring.autoconfigure;

import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.emitter.DecisionEmitter;
import io.decisiontrace.core.emitter.LmaxDecisionEmitter;
import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.exporter.http.CollectorBatchExporter;
import io.decisiontrace.core.exporter.http.CollectorBatchSender;
import io.decisiontrace.core.exporter.http.HttpCollectorBatchSender;
import io.decisiontrace.core.exporter.json.JsonLedgerExporter;
import io.decisiontrace.core.exporter.otel.OpenTelemetryDecisionExporter;
import io.decisiontrace.core.ids.IdGenerator;
import io.decisiontrace.core.ids.UuidIdGenerator;
import io.decisiontrace.core.runtime.DecisionDispatcher;
import io.decisiontrace.core.runtime.DecisionRuntimeMetrics;
import io.decisiontrace.spring.aspect.DecisionTraceAspect;
import io.decisiontrace.spring.config.DecisionTraceProperties;
import io.decisiontrace.spring.context.DecisionContext;
import io.decisiontrace.spring.context.DecisionScopeHolder;
import io.decisiontrace.spring.context.DefaultDecisionContext;
import io.decisiontrace.spring.http.DecisionTraceHandlerInterceptor;
import io.decisiontrace.spring.http.DecisionTracePropagationSupport;
import io.decisiontrace.spring.http.DecisionTraceRestTemplateInterceptor;
import io.decisiontrace.spring.http.DecisionTraceWebClientFilter;
import io.decisiontrace.spring.metrics.DecisionTraceMetrics;
import io.decisiontrace.spring.propagation.InboundTraceContext;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.validation.Validator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@EnableConfigurationProperties(DecisionTraceProperties.class)
public class DecisionTraceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public DecisionRuntimeMetrics decisionRuntimeMetrics() {
        return new DecisionRuntimeMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionDispatcher decisionDispatcher(
            List<DecisionExporter> exporters,
            DecisionRuntimeMetrics runtimeMetrics) {
        return new DecisionDispatcher(exporters, runtimeMetrics);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public DecisionEmitter decisionEmitter(
            DecisionTraceProperties properties,
            DecisionDispatcher dispatcher,
            DecisionRuntimeMetrics runtimeMetrics) {
        return new LmaxDecisionEmitter(properties.getRingBufferSize(), dispatcher, runtimeMetrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionContextHolder decisionContextHolder() {
        return new DecisionContextHolder();
    }

    @Bean
    @ConditionalOnMissingBean
    public InboundTraceContext inboundTraceContext() {
        return new InboundTraceContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionScopeHolder decisionScopeHolder() {
        return new DecisionScopeHolder();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdGenerator idGenerator() {
        return new UuidIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock decisionTraceClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry decisionTraceMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionTraceMetrics decisionTraceMetrics(
            MeterRegistry meterRegistry,
            DecisionRuntimeMetrics runtimeMetrics) {
        return new DecisionTraceMetrics(meterRegistry, runtimeMetrics);
    }

    @Bean
    public DecisionTraceAspect decisionTraceAspect(
            DecisionEmitter decisionEmitter,
            DecisionContextHolder decisionContextHolder,
            InboundTraceContext inboundTraceContext,
            IdGenerator idGenerator,
            Clock decisionTraceClock,
            DecisionTraceProperties properties,
            DecisionTraceMetrics metrics,
            DecisionScopeHolder scopeHolder) {
        return new DecisionTraceAspect(
                decisionEmitter,
                decisionContextHolder,
                inboundTraceContext,
                idGenerator,
                decisionTraceClock,
                properties,
                metrics,
                scopeHolder);
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionTracePropagationSupport decisionTracePropagationSupport(
            DecisionTraceProperties properties,
            DecisionContextHolder decisionContextHolder,
            InboundTraceContext inboundTraceContext) {
        return new DecisionTracePropagationSupport(properties, decisionContextHolder, inboundTraceContext);
    }

    @Bean
    @ConditionalOnClass(name = "jakarta.validation.Validator")
    @ConditionalOnMissingBean(Validator.class)
    public LocalValidatorFactoryBean decisionTraceValidator() {
        return new LocalValidatorFactoryBean();
    }

    @Bean
    @ConditionalOnClass(name = "jakarta.validation.Validator")
    @ConditionalOnMissingBean
    public DecisionContext decisionContext(
            DecisionScopeHolder scopeHolder,
            DecisionTraceMetrics metrics,
            DecisionTraceProperties properties,
            org.springframework.beans.factory.ObjectProvider<Validator> validatorProvider) {
        return new DefaultDecisionContext(
                scopeHolder,
                metrics,
                validatorProvider.getIfAvailable(),
                properties.isValidationEnabled());
    }

    @Bean
    @ConditionalOnProperty(prefix = "decision-trace", name = "collector-endpoint")
    @ConditionalOnMissingBean
    public CollectorBatchSender decisionTraceCollectorBatchSender(DecisionTraceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getCollectorConnectTimeoutMillis()))
                .build();
        return new HttpCollectorBatchSender(
                httpClient,
                URI.create(properties.getCollectorEndpoint()),
                Duration.ofMillis(properties.getCollectorRequestTimeoutMillis()));
    }

    @Bean
    @ConditionalOnBean(CollectorBatchSender.class)
    @ConditionalOnMissingBean(name = "decisionTraceCollectorExporter")
    public DecisionExporter decisionTraceCollectorExporter(
            CollectorBatchSender sender,
            DecisionTraceProperties properties) {
        return new CollectorBatchExporter(sender, properties.getCollectorBatchSize());
    }

    @Bean
    @ConditionalOnProperty(prefix = "decision-trace", name = "json-ledger-path")
    @ConditionalOnMissingBean(name = "decisionTraceJsonLedgerExporter")
    public DecisionExporter decisionTraceJsonLedgerExporter(DecisionTraceProperties properties) {
        return new JsonLedgerExporter(Path.of(properties.getJsonLedgerPath()));
    }

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnProperty(prefix = "decision-trace", name = "otel-export-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "decisionTraceOpenTelemetryExporter")
    public DecisionExporter decisionTraceOpenTelemetryExporter(OpenTelemetry openTelemetry) {
        return new OpenTelemetryDecisionExporter(openTelemetry.getTracer("decision-trace-java"));
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    public DecisionTraceHandlerInterceptor decisionTraceHandlerInterceptor(
            DecisionTraceProperties properties,
            InboundTraceContext inboundTraceContext,
            DecisionContextHolder decisionContextHolder) {
        return new DecisionTraceHandlerInterceptor(properties, inboundTraceContext, decisionContextHolder);
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    public WebMvcConfigurer decisionTraceWebMvcConfigurer(DecisionTraceHandlerInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }

    @Bean
    @ConditionalOnClass(RestTemplate.class)
    public DecisionTraceRestTemplateInterceptor decisionTraceRestTemplateInterceptor(
            DecisionTracePropagationSupport propagationSupport) {
        return new DecisionTraceRestTemplateInterceptor(propagationSupport);
    }

    @Bean
    @ConditionalOnClass(RestTemplate.class)
    public RestTemplateCustomizer decisionTraceRestTemplateCustomizer(
            DecisionTraceRestTemplateInterceptor interceptor) {
        return restTemplate -> {
            if (!restTemplate.getInterceptors().contains(interceptor)) {
                restTemplate.getInterceptors().add(interceptor);
            }
        };
    }

    @Bean
    @ConditionalOnClass(WebClient.class)
    public DecisionTraceWebClientFilter decisionTraceWebClientFilter(
            DecisionTracePropagationSupport propagationSupport) {
        return new DecisionTraceWebClientFilter(propagationSupport);
    }

    @Bean
    @ConditionalOnClass(WebClient.class)
    public WebClientCustomizer decisionTraceWebClientCustomizer(DecisionTraceWebClientFilter filter) {
        return webClientBuilder -> webClientBuilder.filter(filter);
    }
}
