# Decision Trace

Minimal Decision Trace v1 SDK.

Run the collector (local JSONL ingest):
`uvicorn decision_trace_collector.api:app --app-dir collector`

Send events to the collector:
`decision(..., exporter=HttpExporter("http://localhost:8000"))`
