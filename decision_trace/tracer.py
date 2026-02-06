from __future__ import annotations

from contextlib import contextmanager
from typing import Any, Dict, Iterable, List, Optional, Union

from .exporters.base import Exporter
from .exporters.console import ConsoleExporter
from .ids import new_id
from .model import Actor, DecisionTraceEvent, EventType
from .time import now_rfc3339
from .validate import validate_event


class DecisionContext:
    def __init__(
        self,
        *,
        tenant_id: str,
        environment: str,
        decision_type: str,
        actor: Union[Actor, Dict[str, Any]],
        trace_id: Optional[str],
        decision_id: Optional[str],
        parent_decision_id: Optional[str],
        exporter: Exporter,
        validate: bool,
    ) -> None:
        self._tenant_id = tenant_id
        self._environment = environment
        self._decision_type = decision_type
        self._actor = self._normalize_actor(actor)
        self._trace_id = trace_id or new_id()
        self._decision_id = decision_id or new_id()
        self._parent_decision_id = parent_decision_id
        self._exporter = exporter
        self._validate = validate
        self._buffer: List[DecisionTraceEvent] = []

    @property
    def events(self) -> Iterable[DecisionTraceEvent]:
        return tuple(self._buffer)

    def _emit(self, event_type: EventType, payload: Dict[str, Any]) -> DecisionTraceEvent:
        event_dict = {
            "tenant_id": self._tenant_id,
            "environment": self._environment,
            "timestamp": now_rfc3339(),
            "trace_id": self._trace_id,
            "decision_id": self._decision_id,
            "parent_decision_id": self._parent_decision_id,
            "event_id": new_id(),
            "event_type": event_type.value,
            "decision_type": self._decision_type,
            "actor": self._actor,
            "payload": payload,
        }
        if self._validate:
            validate_event(event_dict)
        event = DecisionTraceEvent.model_validate(event_dict)
        self._buffer.append(event)
        self._exporter.export(event)
        return event

    def start(self) -> DecisionTraceEvent:
        return self._emit(EventType.DECISION_START, {})

    def evidence(self, payload: Dict[str, Any]) -> DecisionTraceEvent:
        return self._emit(EventType.DECISION_EVIDENCE, payload)

    def policy_check(self, payload: Dict[str, Any]) -> DecisionTraceEvent:
        return self._emit(EventType.DECISION_POLICY_CHECK, payload)

    def action(self, payload: Dict[str, Any]) -> DecisionTraceEvent:
        return self._emit(EventType.DECISION_ACTION, payload)

    def outcome(self, payload: Dict[str, Any]) -> DecisionTraceEvent:
        return self._emit(EventType.DECISION_OUTCOME, payload)

    def error(self, payload: Dict[str, Any]) -> DecisionTraceEvent:
        return self._emit(EventType.DECISION_ERROR, payload)

    def flush(self) -> None:
        self._exporter.flush()

    @staticmethod
    def _normalize_actor(actor: Union[Actor, Dict[str, Any]]) -> Dict[str, Any]:
        if isinstance(actor, Actor):
            return actor.model_dump(exclude_none=True)
        if "actor_id" in actor or "actor_type" in actor:
            normalized = dict(actor)
            if "actor_id" in normalized:
                normalized["id"] = normalized.pop("actor_id")
            if "actor_type" in normalized:
                normalized["type"] = normalized.pop("actor_type")
            return {key: value for key, value in normalized.items() if value is not None}
        normalized = dict(actor)
        if "type" in normalized and hasattr(normalized["type"], "value"):
            normalized["type"] = normalized["type"].value
        return {key: value for key, value in normalized.items() if value is not None}


@contextmanager
def decision(
    *,
    tenant_id: str,
    environment: str,
    decision_type: str,
    actor: Union[Actor, Dict[str, Any]],
    trace_id: Optional[str] = None,
    decision_id: Optional[str] = None,
    parent_decision_id: Optional[str] = None,
    exporter: Optional[Exporter] = None,
    validate: bool = False,
) -> Iterable[DecisionContext]:
    active_exporter = exporter or ConsoleExporter()
    ctx = DecisionContext(
        tenant_id=tenant_id,
        environment=environment,
        decision_type=decision_type,
        actor=actor,
        trace_id=trace_id,
        decision_id=decision_id,
        parent_decision_id=parent_decision_id,
        exporter=active_exporter,
        validate=validate,
    )
    ctx.start()
    try:
        yield ctx
    except Exception as exc:
        ctx.error({"message": str(exc), "error_type": type(exc).__name__})
        raise
    finally:
        ctx.flush()
