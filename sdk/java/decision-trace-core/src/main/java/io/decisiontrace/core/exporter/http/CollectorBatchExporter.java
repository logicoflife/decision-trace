package io.decisiontrace.core.exporter.http;

import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.json.DecisionJsonSerializer;
import io.decisiontrace.core.model.DecisionTraceEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CollectorBatchExporter implements DecisionExporter {
    private final CollectorBatchSender sender;
    private final DecisionJsonSerializer serializer = new DecisionJsonSerializer();
    private final List<DecisionTraceEvent> pending = new ArrayList<>();
    private final int batchSize;

    public CollectorBatchExporter(CollectorBatchSender sender, int batchSize) {
        this.sender = Objects.requireNonNull(sender, "sender");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    @Override
    public synchronized void export(DecisionTraceEvent event) {
        pending.add(Objects.requireNonNull(event, "event"));
        if (pending.size() >= batchSize) {
            flush();
        }
    }

    @Override
    public synchronized void flush() {
        if (pending.isEmpty()) {
            return;
        }
        String payload = serializePending();
        sender.send(payload);
        pending.clear();
    }

    @Override
    public synchronized void close() {
        flush();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    private String serializePending() {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < pending.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(serializer.serialize(pending.get(i)));
        }
        builder.append(']');
        return builder.toString();
    }
}
