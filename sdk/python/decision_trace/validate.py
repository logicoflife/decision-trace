import json
from functools import lru_cache
from importlib.resources import files
from typing import Any, Dict

from jsonschema import Draft7Validator


class SchemaUnavailableError(RuntimeError):
    pass


@lru_cache(maxsize=1)
def load_event_schema() -> Dict[str, Any]:
    try:
        schema = files("decision_trace").joinpath("schemas", "decision-trace-event-1.0.json")
        with schema.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except (FileNotFoundError, ModuleNotFoundError) as exc:
        raise SchemaUnavailableError(
            "Decision Trace schema resource is unavailable. Reinstall or rebuild the package."
        ) from exc


@lru_cache(maxsize=1)
def _event_validator() -> Draft7Validator:
    return Draft7Validator(load_event_schema())


def validate_event(event_dict: Dict[str, Any]) -> None:
    errors = sorted(_event_validator().iter_errors(event_dict), key=str)
    if errors:
        error = errors[0]
        path = ".".join(str(part) for part in error.path) or "<root>"
        raise ValueError(f"Event validation failed at '{path}': {error.message}")
