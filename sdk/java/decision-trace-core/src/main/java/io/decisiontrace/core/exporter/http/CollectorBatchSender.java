package io.decisiontrace.core.exporter.http;

public interface CollectorBatchSender {
    void send(String payload);
}
