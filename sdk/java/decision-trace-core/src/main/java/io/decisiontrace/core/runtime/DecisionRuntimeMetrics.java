package io.decisiontrace.core.runtime;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class DecisionRuntimeMetrics {
    private final AtomicLong droppedEvents = new AtomicLong();
    private final AtomicInteger bufferOccupancy = new AtomicInteger();
    private final AtomicLong exportCount = new AtomicLong();
    private final AtomicLong exportNanos = new AtomicLong();
    private final AtomicLong exporterFailures = new AtomicLong();

    public void recordDrop() {
        droppedEvents.incrementAndGet();
    }

    public void updateBufferOccupancy(int occupancy) {
        bufferOccupancy.set(Math.max(occupancy, 0));
    }

    public void recordExportNanos(long nanos) {
        exportCount.incrementAndGet();
        exportNanos.addAndGet(Math.max(nanos, 0L));
    }

    public void recordExporterFailure() {
        exporterFailures.incrementAndGet();
    }

    public long droppedEvents() {
        return droppedEvents.get();
    }

    public int bufferOccupancy() {
        return bufferOccupancy.get();
    }

    public long exportCount() {
        return exportCount.get();
    }

    public long exportNanos() {
        return exportNanos.get();
    }

    public long exporterFailures() {
        return exporterFailures.get();
    }
}
