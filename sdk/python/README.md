# Decision Trace Python SDK

The Python SDK provides the core tracing API, CLI, examples, and local developer workflow for Decision Trace.

## Install

```bash
pip install -e ".[collector,test]"
```

## Local Dev Flow

Start the local collector:

```bash
decision-trace dev
```

This binds a local-only example collector on `127.0.0.1:8711` and writes events to `./data/events.jsonl`.

## Validate

```bash
pytest
```

## Examples

See [examples/README.md](./examples/README.md) for the runnable Python examples.
