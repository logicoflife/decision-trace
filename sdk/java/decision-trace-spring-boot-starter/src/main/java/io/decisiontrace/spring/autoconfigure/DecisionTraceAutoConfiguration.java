package io.decisiontrace.spring.autoconfigure;

import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.emitter.DecisionEmitter;
import io.decisiontrace.core.emitter.LmaxDecisionEmitter;
import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.ids.IdGenerator;
import io.decisiontrace.core.ids.UuidIdGenerator;
import io.decisiontrace.core.runtime.DecisionDispatcher;
import io.decisiontrace.core.runtime.DecisionRuntimeMetrics;
import io.decisiontrace.spring.aspect.DecisionTraceAspect;
import io.decisiontrace.spring.config.DecisionTraceProperties;
import io.decisiontrace.spring.http.DecisionTraceHandlerInterceptor;
import io.decisiontrace.spring.http.DecisionTraceRestTemplateInterceptor;
import io.decisiontrace.spring.metrics.DecisionTraceMetrics;
import io.decisiontrace.spring.propagation.InboundTraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
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
            DecisionTraceMetrics metrics) {
        return new DecisionTraceAspect(
                decisionEmitter,
                decisionContextHolder,
                inboundTraceContext,
                idGenerator,
                decisionTraceClock,
                properties,
                metrics);
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
            DecisionTraceProperties properties,
            DecisionContextHolder decisionContextHolder,
            InboundTraceContext inboundTraceContext) {
        return new DecisionTraceRestTemplateInterceptor(properties, decisionContextHolder, inboundTraceContext);
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
}
