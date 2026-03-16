
# Decision Trace Examples

This directory contains flagship examples demonstrating how to use Decision Trace in various agentic patterns.

## Prerequisites

Ensure you have installed the package with the collector extras:

```bash
pip install -e ".[collector]"
# or
pip install decision-trace[collector]
```

Start the local collector in a separate terminal:

```bash
decision-trace dev
```

## Running the Examples

### 1. Refund Workflow (DAG Pattern)
A multi-step business process: `Classify` -> `Approve` -> `Execute`.

```bash
# Run the workflow
python examples/refund_workflow/run.py

# Inspect the trace
decision-trace inspect --last --verbose
```

### 2. Agent Approval Chain (Human-in-the-loop)
An AI Agent proposes code, and a Human must approve it before execution.

```bash
# Run with automatic approval (simulated human)
python examples/agent_approval_chain/run.py --approve

# Run with denial
python examples/agent_approval_chain/run.py --deny

# Inspect
decision-trace inspect --last --verbose
```

### 3. Policy Failure & Evaluation
Demonstrates how to record policy failures (denials) and latent evaluation steps.

```bash
# Run the decision (result: denied)
python examples/policy_failure_and_evaluation/run_decision.py

# Inspect the denial
decision-trace inspect --last --verbose

# Run the evaluation (posts feedback to the trace)
python examples/policy_failure_and_evaluation/emit_evaluation_later.py

# Inspect the feedback link
decision-trace inspect --last --verbose
```

## Legacy Examples
- `quickstart.py`: Minimal single-file example.
- `refund_demo.py`: Older version of refund workflow.
