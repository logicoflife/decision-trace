import pytest
import yaml
from pathlib import Path
from decision_trace.linter import ContractValidator

@pytest.fixture
def contract_path(tmp_path):
    p = tmp_path / "contract.yaml"
    data = {
        "decisions": [{"type": "valid.decision"}],
        "actors": [{"type": "valid.actor"}]
    }
    with p.open("w") as f:
        yaml.dump(data, f)
    return p

def test_contract_load(contract_path):
    validator = ContractValidator(contract_path)
    assert "valid.decision" in validator.decisions
    assert "valid.actor" in validator.actor_types

def test_validate_valid_event(contract_path):
    validator = ContractValidator(contract_path)
    events = [{
        "decision_type": "valid.decision",
        "actor": {"type": "valid.actor", "id": "1"},
        "decision_id": "d1"
    }]
    violations = validator.validate_stream(events)
    assert len(violations) == 0

def test_validate_invalid_decision_type(contract_path):
    validator = ContractValidator(contract_path)
    events = [{
        "decision_type": "invalid.decision",
        "actor": {"type": "valid.actor", "id": "1"},
        "decision_id": "d1"
    }]
    violations = validator.validate_stream(events)
    assert len(violations) == 1
    assert "Unknown decision_type" in violations[0].message

def test_validate_invalid_actor_type(contract_path):
    validator = ContractValidator(contract_path)
    events = [{
        "decision_type": "valid.decision",
        "actor": {"type": "invalid.actor", "id": "1"},
        "decision_id": "d1"
    }]
    violations = validator.validate_stream(events)
    assert len(violations) == 1
    assert "Unknown actor type" in violations[0].message
