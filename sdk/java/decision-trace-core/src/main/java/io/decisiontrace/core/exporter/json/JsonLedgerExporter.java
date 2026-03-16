package io.decisiontrace.core.exporter.json;

import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.json.DecisionJsonSerializer;
import io.decisiontrace.core.model.DecisionTraceEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JsonLedgerExporter implements DecisionExporter {
    private final BufferedWriter writer;
    private final DecisionJsonSerializer serializer = new DecisionJsonSerializer();

    public JsonLedgerExporter(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.writer = Files.newBufferedWriter(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize JSON ledger exporter", exception);
        }
    }

    @Override
    public synchronized void export(DecisionTraceEvent event) {
        try {
            writer.write(serializer.serialize(event));
            writer.newLine();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write decision event", exception);
        }
    }

    @Override
    public synchronized void flush() {
        try {
            writer.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to flush decision ledger", exception);
        }
    }

    @Override
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to close decision ledger", exception);
        }
    }
}
