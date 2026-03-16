package io.decisiontrace.samples.telemetry;

import io.decisiontrace.core.exporter.DecisionExporter;
import io.decisiontrace.core.json.DecisionJsonSerializer;
import io.decisiontrace.core.model.DecisionTraceEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class ResettableJsonLedgerExporter implements DecisionExporter {
    private final Path path;
    private final DecisionJsonSerializer serializer = new DecisionJsonSerializer();

    public ResettableJsonLedgerExporter(Path path) {
        this.path = path;
    }

    @Override
    public synchronized void export(DecisionTraceEvent event) {
        try {
            Files.writeString(
                    path,
                    serializer.serialize(event) + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to append to JSON ledger", exception);
        }
    }

    public synchronized List<String> lines() {
        try {
            return Files.exists(path) ? Files.readAllLines(path) : List.of();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read JSON ledger", exception);
        }
    }
}
