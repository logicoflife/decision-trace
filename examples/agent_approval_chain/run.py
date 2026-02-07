
import sys
import uuid
import argparse
from decision_trace import decision

def run_agent_chain(approve: bool):
    print(f"Running agent chain (Human approval: {approve})...")
    trace_id = str(uuid.uuid4())
    
    # 1. AI Proposal
    with decision(
        decision_type="agent.proposal.generate.v1",
        actor={"type": "agent", "id": "gpt-4o", "version": "2024-05-13"},
        tenant_id="org_A",
        environment="production",
        trace_id=trace_id,
    ) as prop:
        prop.evidence("prompt", "Draft SQL migration for users table")
        prop.evidence("context", ["schema.sql", "ticket_882"])
        prop.evidence("thought_process", "User wants a non-destructive add column. Checking existing constraints.")
        
        prop.action({"action": "generate_code", "tokens": 150, "latency_ms": 450})
        prop.outcome("proposal_ready")
        proposal_id = prop.decision_id

    # 2. Human Review
    with decision(
        decision_type="human.approval.review.v1",
        actor={"type": "human", "id": "developer_alice"},
        tenant_id="org_A",
        environment="production",
        trace_id=trace_id,
    ) as review:
        review.start(causal_links=[{"type": "depends_on", "target_decision_id": proposal_id}])
        
        if approve:
            review.approval({"status": "granted"})
            review.outcome("approved")
        else:
            review.evidence("rejection_reason", "Safety violation: drops table")
            review.outcome("denied")
        review_id = review.decision_id

    # 3. System Execution (if approved)
    if approve:
        with decision(
            decision_type="system.execution.apply.v1",
            actor={"type": "system", "id": "cd_pipeline"},
            tenant_id="org_A",
            environment="production",
            trace_id=trace_id,
        ) as exe:
            exe.start(causal_links=[{"type": "triggered_by", "target_decision_id": review_id}])
            exe.action({"action": "db.migrate", "script": "alter_table.sql"})
            exe.outcome("success")
            
    print("Chain complete.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--approve", action="store_true", help="Simulate approval")
    parser.add_argument("--deny", action="store_true", help="Simulate denial")
    args = parser.parse_args()
    
    if args.deny:
        run_agent_chain(approve=False)
    else:
        run_agent_chain(approve=True)
