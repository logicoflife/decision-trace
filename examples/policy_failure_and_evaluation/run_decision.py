
import json
import sys
from pathlib import Path
from decision_trace import decision

STATE_FILE = Path(__file__).parent / "run_state.json"

def run_decision():
    print("Running initial decision (Policy Failure)...")
    
    with decision(
        decision_type="loan.application.v1",
        actor={"type": "system", "id": "loan_engine"},
        tenant_id="bank_1",
        environment="production",
    ) as d:
        d.evidence("credit_score", 580)
        d.evidence("amount", 50000)
        
        # Policy failure
        d.policy_check("credit.min_score.v1", "fail", inputs={"min": 600, "actual": 580})
        
        d.outcome("denied")
        
        # Persist ID for later evaluation
        STATE_FILE.write_text(json.dumps({"decision_id": d.decision_id}))
        print(f"Decision {d.decision_id} denied and saved state.")

if __name__ == "__main__":
    run_decision()
