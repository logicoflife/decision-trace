from decision_trace import decision
from decision_trace.exporters.file import FileJsonlExporter

exporter = FileJsonlExporter("refunds.jsonl")

# 1. Classify the refund request
with decision(
    decision_type="refund.classify.v1",
    actor={"type": "agent", "id": "classifier-bot"},
    tenant_id="acme",
    environment="prod",
    exporter=exporter
) as classify:
    classify.evidence("amount", 120.00)
    classify.evidence("customer_tier", "gold")
    classify.outcome("eligible")

print(f"Classified. Trace: {classify.trace_id}")

# 2. Approve (linked to classification)
with decision(
    decision_type="refund.approve.v1",
    actor={"type": "human", "id": "alice"},
    tenant_id="acme",
    environment="prod",
    # Link to the previous decision in the same trace
    trace_id=classify.trace_id,
    parent_decision_id=classify.decision_id,
    exporter=exporter
) as approve:
    approve.evidence("manual_review", True)
    approve.outcome("approved")

# 3. Execute (linked to approval)
with decision(
    decision_type="refund.execute.v1",
    actor={"type": "system", "id": "payment-gateway"},
    tenant_id="acme",
    environment="prod",
    trace_id=classify.trace_id,
    parent_decision_id=approve.decision_id,
    exporter=exporter
) as execute:
    execute.action({"method": "stripe", "transaction_id": "tx_123"})
    execute.outcome("completed")

print("Refund workflow completed.")
