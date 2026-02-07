
import functools
from typing import Any, Callable, Dict

from ..tracer import decision

def decision_traced_node(
    func: Callable,
    decision_type_prefix: str = "langgraph.node",
    tenant_id: str = "default_tenant",
    environment: str = "production",
):
    """
    Wraps a LangGraph node function to trace it as a decision.
    
    Usage:
        builder.add_node("agent", decision_traced_node(agent_node))
    """
    @functools.wraps(func)
    def wrapper(state: Dict, config: Any = None):
        node_name = func.__name__
        d_type = f"{decision_type_prefix}.{node_name}.v1"
        
        with decision(
            decision_type=d_type,
            actor={"type": "system", "id": "langgraph_node"},
            tenant_id=tenant_id,
            environment=environment,
        ) as d:
            # Record state snapshot as evidence
            d.evidence("state_in", state)
            
            try:
                # Call original node function
                # LangGraph nodes often take (state) or (state, config)
                # We handle generic signature mapping implies calling it same way
                if config:
                    new_state = func(state, config)
                else:
                    new_state = func(state)
                
                # Record state change output
                d.outcome({"status": "success", "state_out": new_state})
                return new_state
                
            except Exception as e:
                d.outcome({"status": "error", "error": str(e)})
                raise e
                
    return wrapper
