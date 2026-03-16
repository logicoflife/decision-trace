package io.decisiontrace.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.spring.annotation.Decision;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@SpringBootTest(
        classes = DecisionTraceRuntimeIntegrationTest.TestApplication.class,
        properties = {
                "decision-trace.tenant-id=tenant-a",
                "decision-trace.environment=test",
                "decision-trace.actor-id=risk-engine",
                "decision-trace.ring-buffer-size=4"
        })
class DecisionTraceRuntimeIntegrationTest {
    @Autowired
    private DemoService demoService;

    @Autowired
    private BlockingDecisionExporter blockingExporter;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void resetExporter() {
        blockingExporter.reset();
    }

    @Test
    void slowExporterDoesNotDelayBusinessMethod() throws Exception {
        blockingExporter.blockOnExports(2);

        long start = System.nanoTime();
        String result = demoService.performDecision();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals("ok", result);
        assertTrue(elapsedMillis < 150, "business path blocked for " + elapsedMillis + "ms");
        assertTrue(blockingExporter.awaitExportStart());

        blockingExporter.release();
        assertTrue(blockingExporter.awaitExports(2));
    }

    @Test
    void micrometerPublishesRuntimeDropAndLatencyMetrics() throws Exception {
        blockingExporter.blockOnExports(1);
        for (int i = 0; i < 8; i++) {
            demoService.emitSingleDecision();
        }

        assertTrue(blockingExporter.awaitExportStart());
        assertTrue(meterRegistry.get("decision_trace.buffer.occupancy").gauge().value() >= 0.0d);

        blockingExporter.release();
        assertTrue(blockingExporter.awaitExports(1));

        double dropped = meterRegistry.get("decision_trace.events.dropped").functionCounter().count();
        double exportCount = meterRegistry.get("decision_trace.export.latency").functionTimer().count();

        assertTrue(dropped > 0.0d);
        assertTrue(exportCount >= 1.0d);
    }

    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        BlockingDecisionExporter blockingDecisionExporter() {
            return new BlockingDecisionExporter();
        }

        @Bean
        DemoService demoService() {
            return new DemoService();
        }
    }

    @Service
    static class DemoService {
        @Decision(decisionType = "RISK_CHECK")
        String performDecision() {
            return "ok";
        }

        @Decision(decisionType = "SINGLE_CHECK")
        void emitSingleDecision() {
        }
    }

    static final class BlockingDecisionExporter implements DecisionExporter {
        private final List<DecisionTraceEvent> events = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch release = new CountDownLatch(0);
        private volatile CountDownLatch exportStarted = new CountDownLatch(1);
        private volatile CountDownLatch expectedExports = new CountDownLatch(0);
        private final AtomicInteger blockBudget = new AtomicInteger();

        @Override
        public void export(DecisionTraceEvent event) {
            events.add(event);
            expectedExports.countDown();
            exportStarted.countDown();
            if (blockBudget.getAndUpdate(current -> current > 0 ? current - 1 : 0) > 0) {
                try {
                    release.await(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        void blockOnExports(int count) {
            events.clear();
            blockBudget.set(count);
            release = new CountDownLatch(1);
            exportStarted = new CountDownLatch(1);
            expectedExports = new CountDownLatch(count);
        }

        boolean awaitExportStart() throws InterruptedException {
            return exportStarted.await(1, TimeUnit.SECONDS);
        }

        boolean awaitExports(int count) throws InterruptedException {
            if (expectedExports.getCount() == 0L) {
                return events.size() >= count;
            }
            return expectedExports.await(1, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }

        void reset() {
            release();
            events.clear();
            blockBudget.set(0);
            exportStarted = new CountDownLatch(1);
            expectedExports = new CountDownLatch(0);
        }
    }
}
