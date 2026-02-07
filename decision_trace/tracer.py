from __future__ import annotations

from contextlib import contextmanager
from typing import Any, Dict, Iterable, List, Optional, Union

from .exporters.base import Exporter
from .exporters.file import FileJsonlExporter
from .ids import new_id
from .model import Actor, DecisionTraceEvent, EventType
from .time import now_rfc3339
from .validate import validate_event
from .version import SCHEMA_VERSION



from copy import deepcopy
import sys

# Redaction defaults
DEFAULT_REDACT_KEYS = {"password", "token", "secret", "auth", "api_key", "access_token"}

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

    @property
    def decision_id(self) -> str:
        return self._decision_id

    @property
    def trace_id(self) -> str:
        return self._trace_id

    def build_event(
        self,
        event_type: str,
        payload: Dict[str, Any],
        causal_links_override: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        if not isinstance(payload, dict):
            raise ValueError("payload must be a dict")
        
        # Phase 10 Safety: Snapshot evidence at emission time
        try:
            safe_payload = deepcopy(payload)
        except Exception:
            # Fallback for non-pickleable objects, basic dict copy or str repr?
            # For strict safety, we might want to warn or stringify.
            # V1.1 policy: standard deepcopy. If fails, user gets error (which is good signal).
            safe_payload = deepcopy(payload)

        # Phase 10 Safety: Redaction
        self._redact_pii(safe_payload)

        event_dict = {
            "tenant_id": self._tenant_id,
            "environment": self._environment,
            "schema_version": SCHEMA_VERSION,
            "timestamp": now_rfc3339(),
            "trace_id": self._trace_id,
            "decision_id": self._decision_id,
            "parent_decision_id": self._parent_decision_id,
            "event_id": new_id(),
            "event_type": event_type,
            "decision_type": self._decision_type,
            "actor": self._actor,
            "payload": safe_payload,
        }
        if causal_links_override is not None:
            event_dict["causal_links"] = causal_links_override
        return event_dict

    def _redact_pii(self, data: Any) -> None:
        """Recursive in-place redaction."""
        if isinstance(data, dict):
            for k, v in data.items():
                if isinstance(k, str) and k.lower() in DEFAULT_REDACT_KEYS:
                    data[k] = "[REDACTED]"
                else:
                    self._redact_pii(v)
        elif isinstance(data, list):
            for item in data:
                self._redact_pii(item)

    def _record(self, event_dict: Dict[str, Any]) -> DecisionTraceEvent:
        if self._validate:
            # Phase 10 Safety: Validation failures SHOULD break tests/dev, 
            # but maybe log-only in prod? V1.1 stays strict on schema.
            validate_event(event_dict)
        
        event = DecisionTraceEvent.model_validate(event_dict)
        self._buffer.append(event)
        
        # Phase 10 Safety: Failure Isolation
        # Exporter failure must NEVER crash business logic
        try:
            self._exporter.export(event)
        except Exception as e:
            # Log to stderr but swallow exception
            sys.stderr.write(f"[DecisionTrace] Exporter failed: {e}\n")
            
        return event

    def start(self, causal_links: Optional[List[Dict[str, Any]]] = None) -> DecisionTraceEvent:
        event_dict = self.build_event(EventType.DECISION_START.value, {}, causal_links)
        return self._record(event_dict)

    def evidence(
        self, key: str, value: Any, causal_links: Optional[List[Dict[str, Any]]] = None
    ) -> DecisionTraceEvent:
        payload = {"key": key, "value": value}
        event_dict = self.build_event(EventType.DECISION_EVIDENCE.value, payload, causal_links)
        return self._record(event_dict)

    def policy_check(
        self,
        policy: str,
        result: str,
        inputs: Optional[Dict[str, Any]] = None,
        causal_links: Optional[List[Dict[str, Any]]] = None,
    ) -> DecisionTraceEvent:
        payload: Dict[str, Any] = {"policy": policy, "result": result}
        if inputs:
            payload["inputs"] = inputs
        event_dict = self.build_event(EventType.DECISION_POLICY_CHECK.value, payload, causal_links)
        return self._record(event_dict)

    def action(
        self, payload: Dict[str, Any], causal_links: Optional[List[Dict[str, Any]]] = None
    ) -> DecisionTraceEvent:
        event_dict = self.build_event(EventType.DECISION_ACTION.value, payload, causal_links)
        return self._record(event_dict)

    def approval(
        self, payload: Dict[str, Any], causal_links: Optional[List[Dict[str, Any]]] = None
    ) -> DecisionTraceEvent:
        event_dict = self.build_event(EventType.DECISION_APPROVAL.value, payload, causal_links)
        return self._record(event_dict)

    def outcome(
        self, status: str, causal_links: Optional[List[Dict[str, Any]]] = None
    ) -> DecisionTraceEvent:
        payload = {"status": status}
        event_dict = self.build_event(EventType.DECISION_OUTCOME.value, payload, causal_links)
        return self._record(event_dict)

    def error(
        self, payload: Dict[str, Any], causal_links: Optional[List[Dict[str, Any]]] = None
    ) -> DecisionTraceEvent:
        event_dict = self.build_event(EventType.DECISION_ERROR.value, payload, causal_links)
        return self._record(event_dict)

    def evaluation(
        self, payload: Dict[str, Any], causal_links: Optional[List[Dict[str, Any]]] = None
    ) -> DecisionTraceEvent:
        event_dict = self.build_event(EventType.DECISION_EVALUATION.value, payload, causal_links)
        return self._record(event_dict)

    def flush(self) -> None:
        if not self._buffer:
            return
        # Phase 10 Safety: Exporter isolation for flush too
        try:
            self._exporter.flush()
        except Exception as e:
            sys.stderr.write(f"[DecisionTrace] Exporter flush failed: {e}\n")

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
    active_exporter = exporter or FileJsonlExporter("./decision-trace.jsonl")
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
