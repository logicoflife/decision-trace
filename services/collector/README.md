# Decision Trace Collector

This service is the minimal HTTP collector used for local development and ingestion validation.

## Behavior

- accepts Decision Trace events over HTTP
- validates incoming payloads against the shared schema
- writes accepted events to local storage

## Local Run

From the repository root:

```bash
pip install -e ".[collector,test]"
decision-trace dev
```

`decision-trace dev` starts a local-only collector on `127.0.0.1:8711`.

The module entry point also exposes a standalone collector process that binds `0.0.0.0:8000` by default for container or explicit local service use.
