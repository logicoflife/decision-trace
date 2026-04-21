# Decision Trace Python SDK

The Python SDK provides the core tracing API, CLI, examples, and local collector workflow for Decision Trace.

## Install

```bash
pip install decision-trace
```

Install the optional local collector dependencies if you want the bundled FastAPI collector and `decision-trace dev` workflow:

```bash
pip install "decision-trace[collector]"
```

## Quickstart

```python
from decision_trace import decision

with decision("loan.approval", actor={"type": "system", "id": "risk_engine"}) as d:
    d.evidence("credit_score", 720)
    d.policy_check("min_score_700", "pass")
    d.outcome("approved")
```

## Local Collector

Start the local collector:

```bash
decision-trace dev
```

This binds a local-only example collector on `127.0.0.1:8711` and writes events to `./data/events.jsonl`.

## Development

```bash
pip install -e ".[collector,test]"
pytest
```

## Docs And Examples

- Examples: https://github.com/logicoflife/decision-trace/tree/main/sdk/python/examples
- Repository: https://github.com/logicoflife/decision-trace
- Issues: https://github.com/logicoflife/decision-trace/issues
