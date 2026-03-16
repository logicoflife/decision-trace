package io.decisiontrace.spring.aspect;

import io.decisiontrace.core.DecisionScope;
import io.decisiontrace.core.DecisionSpec;
import io.decisiontrace.core.context.DecisionContextHolder;
import io.decisiontrace.core.context.DecisionFrame;
import io.decisiontrace.core.emitter.DecisionEmitter;
import io.decisiontrace.core.ids.IdGenerator;
import io.decisiontrace.core.model.Actor;
import io.decisiontrace.spring.annotation.Decision;
import io.decisiontrace.spring.config.DecisionTraceProperties;
import io.decisiontrace.spring.metrics.DecisionTraceMetrics;
import io.decisiontrace.spring.propagation.DecisionTraceHeaders;
import io.decisiontrace.spring.propagation.InboundTraceContext;
import java.time.Clock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
public class DecisionTraceAspect {
    private final DecisionEmitter emitter;
    private final DecisionContextHolder contextHolder;
    private final InboundTraceContext inboundTraceContext;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final DecisionTraceProperties properties;
    private final DecisionTraceMetrics metrics;

    public DecisionTraceAspect(
            DecisionEmitter emitter,
            DecisionContextHolder contextHolder,
            InboundTraceContext inboundTraceContext,
            IdGenerator idGenerator,
            Clock clock,
            DecisionTraceProperties properties,
            DecisionTraceMetrics metrics) {
        this.emitter = emitter;
        this.contextHolder = contextHolder;
        this.inboundTraceContext = inboundTraceContext;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Around("@annotation(decision)")
    public Object aroundDecision(ProceedingJoinPoint joinPoint, Decision decision) throws Throwable {
        DecisionScope scope = openScope(decision);
        try {
            Object result = joinPoint.proceed();
            safeEmitOutcome(scope);
            return result;
        } catch (Throwable throwable) {
            safeEmitError(scope, throwable);
            throw throwable;
        } finally {
            safeClose(scope);
        }
    }

    private DecisionScope openScope(Decision decision) {
        try {
            DecisionFrame current = contextHolder.current();
            InboundTraceContext.TraceCarrier inbound = resolveInboundTraceContext();
            String traceId = current != null ? current.traceId() : inbound != null ? inbound.traceId() : null;
            String parentDecisionId = current != null
                    ? current.decisionId()
                    : inbound != null ? inbound.parentDecisionId() : null;
            DecisionSpec spec = DecisionSpec.builder()
                    .tenantId(properties.getTenantId())
                    .environment(properties.getEnvironment())
                    .decisionType(decision.decisionType())
                    .actor(resolveActor(decision))
                    .traceId(traceId)
                    .parentDecisionId(parentDecisionId)
                    .build();
            return DecisionScope.open(spec, emitter, contextHolder, idGenerator, clock);
        } catch (Exception exception) {
            metrics.recordInstrumentationFailure();
            return null;
        }
    }

    private Actor resolveActor(Decision decision) {
        String actorId = decision.actorId().isBlank() ? properties.getActorId() : decision.actorId();
        String actorVersion = decision.actorVersion().isBlank() ? properties.getActorVersion() : decision.actorVersion();
        String actorOrg = decision.actorOrg().isBlank() ? properties.getActorOrg() : decision.actorOrg();
        return new Actor(actorId, decision.actorType(), actorVersion, actorOrg);
    }

    private InboundTraceContext.TraceCarrier resolveInboundTraceContext() {
        InboundTraceContext.TraceCarrier inbound = inboundTraceContext.current();
        if (inbound != null && inbound.traceId() != null) {
            return inbound;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return inbound;
        }
        String traceId = attributes.getRequest().getHeader(properties.getTraceIdHeader());
        if (traceId == null || traceId.isBlank()) {
            traceId = DecisionTraceHeaders.parseTraceParent(
                    attributes.getRequest().getHeader(DecisionTraceHeaders.W3C_TRACEPARENT));
        }
        String parentDecisionId = attributes.getRequest().getHeader(properties.getParentDecisionIdHeader());
        if (traceId == null && parentDecisionId == null) {
            return inbound;
        }
        return new InboundTraceContext.TraceCarrier(traceId, parentDecisionId);
    }

    private void safeEmitOutcome(DecisionScope scope) {
        if (scope == null) {
            return;
        }
        try {
            scope.outcome("success");
        } catch (Exception exception) {
            metrics.recordInstrumentationFailure();
        }
    }

    private void safeEmitError(DecisionScope scope, Throwable throwable) {
        if (scope == null) {
            return;
        }
        try {
            scope.error(throwable);
        } catch (Exception exception) {
            metrics.recordInstrumentationFailure();
        }
    }

    private void safeClose(DecisionScope scope) {
        if (scope == null) {
            return;
        }
        try {
            scope.close();
        } catch (Exception exception) {
            metrics.recordInstrumentationFailure();
            contextHolder.clear();
        }
    }
}
