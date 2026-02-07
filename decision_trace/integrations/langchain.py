
from typing import Any, Dict, List, Optional, Union
from uuid import UUID

from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.outputs import LLMResult

from ..tracer import decision, DecisionContext

# Map run_id to DecisionContext to manage nested scopes
_active_decisions: Dict[UUID, DecisionContext] = {}

class DecisionTraceCallbackHandler(BaseCallbackHandler):
    """
    LangChain callback handler that traces chain execution as Decisions.
    Maps:
    - Chain Start -> decision.start
    - Tool Start -> decision.action (or sub-decision)
    - Chain End -> decision.outcome
    - Error -> decision.error / outcome=error
    """
    
    def __init__(
        self, 
        tenant_id: str = "default_tenant", 
        environment: str = "production",
        root_decision_type: str = "langchain.chain.run.v1"
    ):
        self.tenant_id = tenant_id
        self.environment = environment
        self.root_decision_type = root_decision_type

    def on_chain_start(
        self, serialized: Dict[str, Any], inputs: Dict[str, Any], *, run_id: UUID, parent_run_id: Optional[UUID] = None, **kwargs: Any
    ) -> Any:
        # Determine actor and decision type
        name = serialized.get("name", "chain")
        d_type = f"langchain.chain.{name}.v1" if name else self.root_decision_type
        
        # Check if parent exists
        # Note: DecisionContext currently doesn't support manual parenting easily via ID passing 
        # unless we manage the context object.
        
        # Create decision context
        ctx = decision(
            decision_type=d_type,
            actor={"type": "system", "id": "langchain_runtime"},
            tenant_id=self.tenant_id,
            environment=self.environment,
        )
        
        # Start and enter context
        ctx.__enter__()
        _active_decisions[run_id] = ctx
        
        # Record inputs
        ctx.evidence("inputs", inputs)

    def on_chain_end(self, outputs: Dict[str, Any], *, run_id: UUID, parent_run_id: Optional[UUID] = None, **kwargs: Any) -> Any:
        ctx = _active_decisions.pop(run_id, None)
        if ctx:
            ctx.outcome({"status": "success", "outputs": outputs})
            ctx.__exit__(None, None, None)

    def on_chain_error(self, error: Union[Exception, KeyboardInterrupt], *, run_id: UUID, parent_run_id: Optional[UUID] = None, **kwargs: Any) -> Any:
        ctx = _active_decisions.pop(run_id, None)
        if ctx:
            ctx.outcome({"status": "error", "message": str(error)})
            # We don't suppress exception, just log it
            ctx.__exit__(type(error), error, None)

    def on_tool_start(
        self, serialized: Dict[str, Any], input_str: str, *, run_id: UUID, parent_run_id: Optional[UUID] = None, **kwargs: Any
    ) -> Any:
        # Treat tool as a sub-decision or just an action?
        # A tool has start/end, so it matches a Decision lifecycle best.
        name = serialized.get("name", "tool")
        
        ctx = decision(
            decision_type=f"langchain.tool.{name}.v1",
            actor={"type": "system", "id": f"tool:{name}"},
            tenant_id=self.tenant_id,
            environment=self.environment,
        )
        ctx.__enter__()
        _active_decisions[run_id] = ctx
        
        ctx.evidence("tool_input", input_str)
        ctx.action({"action": "tool_call", "tool": name, "input": input_str})

    def on_tool_end(self, output: str, *, run_id: UUID, parent_run_id: Optional[UUID] = None, **kwargs: Any) -> Any:
        ctx = _active_decisions.pop(run_id, None)
        if ctx:
            ctx.outcome({"status": "success", "output": output})
            ctx.__exit__(None, None, None)

    def on_tool_error(self, error: Union[Exception, KeyboardInterrupt], *, run_id: UUID, parent_run_id: Optional[UUID] = None, **kwargs: Any) -> Any:
        ctx = _active_decisions.pop(run_id, None)
        if ctx:
            ctx.outcome({"status": "error", "message": str(error)})
            ctx.__exit__(type(error), error, None)
