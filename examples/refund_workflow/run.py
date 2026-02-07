
import time
import uuid
from decision_trace import decision

def run_refund_process():
    print("Starting refund workflow...")
    trace_id = str(uuid.uuid4())
    print(f"Trace ID: {trace_id}")
    
    # 1. Classify
    with decision(
        decision_type="refund.classify.v1",
        actor={"type": "system", "id": "classifier_bot"},
        tenant_id="customer_1",
        environment="production",
        trace_id=trace_id,
    ) as classify:
        classify.evidence("request_id", "req_123")
        classify.evidence("amount", 120.00)
        classify.evidence("customer", {"id": "cust_99", "tier": "VIP", "tenure_years": 5})
        classify.evidence("reason", "defective_product")
        
        # Simulate logic
        is_eligible = True
        classify.policy_check("refund.eligibility.v1", "pass", inputs={"policy_limit": 500})
        
        classify.outcome("eligible")
        classify_id = classify.decision_id
        time.sleep(0.1)

    # 2. Approve (Human)
    with decision(
        decision_type="refund.approve.v1",
        actor={"type": "agent", "id": "support_agent_007"},
        tenant_id="customer_1",
        environment="production",
        trace_id=trace_id,
    ) as approve:
        # Link to classification
        approve.start(causal_links=[{"type": "depends_on", "target_decision_id": classify_id}])
        
        approve.evidence("risk_score", 0.05)
        approve.policy_check("risk.check.v2", "pass")
        
        approve.approval({"granter": "human:support_agent_007", "notes": "Customer is VIP"})
        approve.outcome("approved")
        approve_id = approve.decision_id
        time.sleep(0.1)

    # 3. Execute
    with decision(
        decision_type="refund.execute.v1",
        actor={"type": "system", "id": "payment_gateway"},
        tenant_id="customer_1",
        environment="production",
        trace_id=trace_id,
    ) as execute:
        execute.start(causal_links=[{"type": "depends_on", "target_decision_id": approve_id}])
        
        execute.action({"action": "stripe.refund.create", "amount": 12000})
        execute.outcome("success")

    print("Refund workflow complete.")

if __name__ == "__main__":
    run_refund_process()
