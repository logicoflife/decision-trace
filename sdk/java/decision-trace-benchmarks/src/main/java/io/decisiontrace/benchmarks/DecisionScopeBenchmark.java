package io.decisiontrace.benchmarks;

import io.decisiontrace.core.DecisionScope;
import io.decisiontrace.core.DecisionSpec;
import io.decisiontrace.core.emitter.LmaxDecisionEmitter;
import io.decisiontrace.core.exporter.InMemoryDecisionExporter;
import io.decisiontrace.core.model.Actor;
import io.decisiontrace.core.model.ActorType;
import io.decisiontrace.core.runtime.DecisionDispatcher;
import io.decisiontrace.core.runtime.DecisionRuntimeMetrics;
import java.util.List;
import java.util.Map;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class DecisionScopeBenchmark {
    private LmaxDecisionEmitter emitter;
    private DecisionSpec spec;

    @Setup
    public void setUp() {
        DecisionRuntimeMetrics metrics = new DecisionRuntimeMetrics();
        emitter = new LmaxDecisionEmitter(
                1024,
                new DecisionDispatcher(List.of(new InMemoryDecisionExporter()), metrics),
                metrics);
        spec = DecisionSpec.builder()
                .tenantId("tenant-a")
                .environment("bench")
                .decisionType("RISK_CHECK")
                .actor(Actor.of("benchmark", ActorType.SYSTEM))
                .build();
    }

    @TearDown
    public void tearDown() {
        emitter.close();
    }

    @Benchmark
    public void emitLifecycle(Blackhole blackhole) {
        try (DecisionScope scope = DecisionScope.open(spec, emitter)) {
            blackhole.consume(scope.evidence("score", 42));
            blackhole.consume(scope.policyCheck("risk_v1", "allow", Map.of("score", 42), null));
            blackhole.consume(scope.outcome("allow"));
        }
    }
}
