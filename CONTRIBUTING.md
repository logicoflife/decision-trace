
# Contributing to Decision Trace

Thank you for your interest in contributing to Decision Trace! This project aims to be the standard for recording agentic reasoning.

## Scope of Contribution

Decision Trace follows a **Spec-First** philosophy. The core SDK semantics (append-only ledger, schema validation, failure isolation) are strictly controlled to ensure stability for all users.

### We Welcome:
- **Adapters**: Integrations with new frameworks (CrewAI, AutoGen, etc.) in `decision_trace/integrations/`.
- **Exporters**: New storage backends (Postgres, ClickHouse, OTLP) in `decision_trace/exporters/`.
- **Documentation**: Improvements to guides, examples, and docstrings.
- **Bug Fixes**: Fixes for reproducible issues that do not alter core behavior.

### Please Discuss First:
- **Core SDK Changes**: Changes to `tracer.py`, `context.py`, or `schema/` require an approved RFC issue before code is written.
- **Breaking Changes**: We prioritize backward compatibility.

## Pull Request Process

1.  **Search Issues**: Ensure your idea isn't already being worked on.
2.  **Fork & Branch**: Create a feature branch from `main`.
3.  **Test**: Ensure `pytest` passes locally.
    ```bash
    pip install -e ".[collector,test]"
    pytest
    cd sdk/java
    mvn test
    ```
4.  **Lint**: Code must be typed and formatted.
5.  **Submit**: Open a PR with a clear description of the change.

**Note**: All PRs reflect a best-effort review process. We appreciate your patience.

## Development Setup

```bash
# Clone
git clone https://github.com/decision-trace/decision-trace.git
cd decision-trace

# Venv
python -m venv .venv
source .venv/bin/activate

# Install
pip install -e ".[collector,test]"

# Verify
decision-trace dev
pytest

# Java verify
cd sdk/java
mvn test
```
