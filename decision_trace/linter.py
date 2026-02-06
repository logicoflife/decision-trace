import yaml
from pathlib import Path
from typing import Any, Dict, List, Optional, Set

from .model import EventType

class Violation:
    def __init__(self, message: str, event: Dict[str, Any]):
        self.message = message
        self.event = event

    def __str__(self) -> str:
        decision_id = self.event.get("decision_id", "?")
        return f"[{decision_id}] {self.message}"

class ContractValidator:
    def __init__(self, contract_path: Path):
        self.contract_path = contract_path
        self.decisions: Set[str] = set()
        self.actor_types: Set[str] = set()
        self._load()

    def _load(self) -> None:
        if not self.contract_path.exists():
             raise FileNotFoundError(f"Contract file {self.contract_path} not found.")
        
        with self.contract_path.open("r", encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}
            
        for d in data.get("decisions", []):
            if "type" in d:
                self.decisions.add(d["type"])
                
        for a in data.get("actors", []):
            if "type" in a:
                self.actor_types.add(a["type"])

    def validate_event(self, event: Dict[str, Any]) -> List[Violation]:
        violations = []
        
        # Check decision_type
        # Only relevant for decision events that carry types. 
        # Actually all events have "decision_type".
        # But we only really care if the decision type itself is allowed.
        # So yes, check all events or just start? 
        # All events for a decision share the decision_type, so checking every event is fine/redundant but safe.
        
        d_type = event.get("decision_type")
        if d_type and d_type not in self.decisions:
            violations.append(Violation(f"Unknown decision_type: '{d_type}'", event))
            
        # Check actor type
        actor = event.get("actor", {})
        if isinstance(actor, dict):
            a_type = actor.get("type")
            if a_type and a_type not in self.actor_types:
                violations.append(Violation(f"Unknown actor type: '{a_type}'", event))
                
        return violations

    def validate_stream(self, events: List[Dict[str, Any]]) -> List[Violation]:
        all_violations = []
        for event in events:
            all_violations.extend(self.validate_event(event))
        return all_violations
