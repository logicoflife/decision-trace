package io.decisiontrace.core.emitter;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.decisiontrace.core.model.DecisionTraceEvent;
import io.decisiontrace.core.runtime.DecisionDispatcher;
import io.decisiontrace.core.runtime.DecisionEventEnvelope;
import io.decisiontrace.core.runtime.DecisionRuntimeMetrics;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public final class LmaxDecisionEmitter implements DecisionEmitter {
    private final Disruptor<DecisionEventEnvelope> disruptor;
    private final RingBuffer<DecisionEventEnvelope> ringBuffer;
    private final DecisionDispatcher dispatcher;
    private final DecisionRuntimeMetrics metrics;
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final int bufferSize;

    public LmaxDecisionEmitter(int bufferSize, DecisionDispatcher dispatcher, DecisionRuntimeMetrics metrics) {
        this(bufferSize, dispatcher, metrics, runnable -> {
            Thread thread = new Thread(runnable, "decision-trace-disruptor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public LmaxDecisionEmitter(
            int bufferSize,
            DecisionDispatcher dispatcher,
            DecisionRuntimeMetrics metrics,
            ThreadFactory threadFactory) {
        if (Integer.bitCount(bufferSize) != 1) {
            throw new IllegalArgumentException("bufferSize must be a power of two");
        }
        this.bufferSize = bufferSize;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        EventFactory<DecisionEventEnvelope> factory = DecisionEventEnvelope::new;
        EventHandler<DecisionEventEnvelope> handler = (envelope, sequence, endOfBatch) -> {
            DecisionTraceEvent event = envelope.event();
            if (event != null) {
                dispatcher.dispatch(event);
                processed.incrementAndGet();
            }
            envelope.clear();
            metrics.updateBufferOccupancy((int) Math.max(0, published.get() - processed.get()));
        };
        this.disruptor = new Disruptor<>(
                factory,
                bufferSize,
                threadFactory,
                ProducerType.MULTI,
                new BlockingWaitStrategy());
        this.disruptor.handleEventsWith(handler);
        this.ringBuffer = disruptor.start();
        this.metrics.updateBufferOccupancy(0);
    }

    @Override
    public void emit(DecisionTraceEvent event) {
        if (event == null) {
            return;
        }
        boolean accepted = ringBuffer.tryPublishEvent((envelope, sequence, input) -> envelope.event(input), event);
        if (!accepted) {
            metrics.recordDrop();
            metrics.updateBufferOccupancy(bufferSize);
            return;
        }
        long current = published.incrementAndGet();
        metrics.updateBufferOccupancy((int) Math.max(0, current - processed.get()));
    }

    @Override
    public void flush() {
        flush(Duration.ofSeconds(5));
    }

    public void flush(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (processed.get() < published.get() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        dispatcher.flush();
        metrics.updateBufferOccupancy((int) Math.max(0, published.get() - processed.get()));
    }

    @Override
    public void close() {
        flush(Duration.ofSeconds(5));
        disruptor.halt();
        dispatcher.close();
        metrics.updateBufferOccupancy(0);
    }
}
