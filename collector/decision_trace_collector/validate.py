import json
from pathlib import Path
from typing import Any, Dict

from jsonschema import Draft7Validator


def load_event_schema() -> Dict[str, Any]:
    schema_rel = Path("spec/schema/decision-trace-event-1.0.json")
    schema_path = None
    for parent in Path(__file__).resolve().parents:
        candidate = parent / schema_rel
        if candidate.exists():
            schema_path = candidate
            break
    if schema_path is None:
        raise FileNotFoundError("Decision Trace schema file not found.")
    with schema_path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def validate_event(event_dict: Dict[str, Any]) -> None:
    schema = load_event_schema()
    validator = Draft7Validator(schema)
    errors = sorted(validator.iter_errors(event_dict), key=str)
    if errors:
        error = errors[0]
        path = ".".join(str(part) for part in error.path) or "<root>"
        raise ValueError(f"Event validation failed at '{path}': {error.message}")
