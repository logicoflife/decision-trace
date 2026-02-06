from decision_trace import decision
from decision_trace.exporters.http import HttpExporter
import sys
import time

try:
    print("Starting client...")
    with decision(
        decision_type="payments.refund.approval.v1",
        actor={"type": "agent", "id": "support_agent"},
        tenant_id="local",
        environment="dev",
        exporter=HttpExporter("http://127.0.0.1:8000"),
        validate=True,
    ) as d:
        d.evidence("order_value", 120)
        d.policy_check("payments.refund.threshold.limit.v3", result="pass", inputs={"amount":120})
        d.outcome("approved")

    print("Decision sent successfully")
except Exception as e:
    print(f"FAILED: {e}")
    sys.exit(1)
