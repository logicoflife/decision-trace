from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field

from .version import SCHEMA_VERSION


class EventType(str, Enum):
    DECISION_START = "decision.start"
    DECISION_EVIDENCE = "decision.evidence"
    DECISION_POLICY_CHECK = "decision.policy_check"
    DECISION_ACTION = "decision.action"
    DECISION_APPROVAL = "decision.approval"
    DECISION_OUTCOME = "decision.outcome"
    DECISION_ERROR = "decision.error"
    DECISION_EVALUATION = "decision.evaluation"


class ActorType(str, Enum):
    HUMAN = "human"
    SYSTEM = "system"
    AGENT = "agent"


class Actor(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    id: str
    type: ActorType
    version: Optional[str] = None
    org: Optional[str] = None


class CausalLinkType(str, Enum):
    DEPENDS_ON = "depends_on"
    TRIGGERED_BY = "triggered_by"
    USES_EVIDENCE_FROM = "uses_evidence_from"
    COMPENSATES = "compensates"


class CausalLink(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    type: CausalLinkType
    target_decision_id: str


class DecisionTraceEvent(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    tenant_id: str
    environment: str
    schema_version: str = Field(default=SCHEMA_VERSION)
    timestamp: str
    trace_id: str
    decision_id: str
    parent_decision_id: Optional[str]
    event_id: str
    event_type: EventType
    decision_type: str
    actor: Actor
    payload: Dict[str, Any]
    causal_links: Optional[List[CausalLink]] = None
