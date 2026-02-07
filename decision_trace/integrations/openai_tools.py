
import functools
from typing import Any, Callable, Optional

from ..tracer import decision

def openai_tool_decision_traced(
    decision_type: str = "agent.tool_call.v1",
    tenant_id: str = "default_tenant",
    environment: str = "production",
):
    """
    Decorator for functions used as OpenAI tools.
    Records the tool call execution as a decision/action.
    
    Usage:
        @openai_tool_decision_traced()
        def get_weather(location: str):
            ...
    """
    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            tool_name = func.__name__
            
            with decision(
                decision_type=decision_type,
                actor={"type": "system", "id": f"tool:{tool_name}"},
                tenant_id=tenant_id,
                environment=environment,
            ) as d:
                # Record the tool invocation details
                d.evidence("tool_name", tool_name)
                d.evidence("arguments", kwargs) # OpenAI tools usually called with kwargs
                
                # Semantic Action
                d.action({
                    "action": "tool_execution",
                    "tool": tool_name,
                    "parameters": kwargs
                })
                
                try:
                    result = func(*args, **kwargs)
                    d.outcome({"status": "success", "result": result})
                    return result
                except Exception as e:
                    d.outcome({"status": "error", "error": str(e)})
                    raise e
                    
        return wrapper
    return decorator
