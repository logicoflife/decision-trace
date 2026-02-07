
import json
import sys
from pathlib import Path
from decision_trace import decision

STATE_FILE = Path(__file__).parent / "run_state.json"

def emit_eval():
    if not STATE_FILE.exists():
        print("Run run_decision.py first!")
        sys.exit(1)
        
    state = json.loads(STATE_FILE.read_text())
    original_id = state["decision_id"]
    
    print(f"Evaluating previous decision {original_id}...")
    
    with decision(
        decision_type="loan.review.v1",
        actor={"type": "human", "id": "manager_bob"},
        tenant_id="bank_1",
        environment="production",
    ) as d:
        # Link to past
        d.start(causal_links=[{"type": "depends_on", "target_decision_id": original_id}])
        
        d.evaluation({
            "target_decision_id": original_id,
            "feedback": "Correctly denied, score too low.",
            "score": 1.0
        })
        
        d.outcome("feedback_recorded")
        print("Evaluation recorded.")

if __name__ == "__main__":
    emit_eval()
