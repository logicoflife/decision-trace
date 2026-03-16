from decision_trace import decision

# Minimal example: Record a single decision
with decision(
    decision_type="example.v1",
    actor={"type": "system", "id": "job-1"},
    tenant_id="global",
    environment="dev",
) as ctx:
    ctx.evidence("input", 42)
    ctx.outcome("processed")

print(f"Trace recorded! Trace ID: {ctx.trace_id}")
