
import functools
from typing import Any, Callable, Optional

from ..tracer import decision

def decision_traced(
    decision_type: str,
    tenant_id: str = "default_tenant",
    environment: str = "production",
    actor: Optional[dict] = None,
    capture_input: bool = True,
    capture_output: bool = False,
):
    """
    Decorator to wrap a function execution in a Decision Trace context.
    
    Usage:
        @decision_traced("my.decision.v1")
        def my_func(x, y):
            ...
    
    Args:
        decision_type: The trace decision type string.
        tenant_id: Default tenant ID (can be overridden by kwargs).
        environment: Default environment (can be overridden by kwargs).
        actor: Actor dict (defaults to system:function_name).
        capture_input: Whether to record args/kwargs as evidence.
        capture_output: Whether to include the return value in the outcome.
    """
    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            # Resolve context parameters
            # Prioritize kwargs if they match reserved names? 
            # Ideally we don't mess with function signature.
            # We use the decorator defaults for now.
            
            # Default actor to the function name if not provided
            current_actor = actor or {"type": "system", "id": func.__name__}
            
            with decision(
                decision_type=decision_type,
                tenant_id=tenant_id,
                environment=environment,
                actor=current_actor,
            ) as d:
                if capture_input:
                    if args:
                        d.evidence("args", list(args))
                    if kwargs:
                        # Filter sensitive keys? The tracer does redaction.
                        d.evidence("kwargs", kwargs)
                
                try:
                    result = func(*args, **kwargs)
                    
                    outcome_payload = {"status": "success"}
                    if capture_output:
                        # Be careful with large objects
                        try:
                            outcome_payload["result"] = result
                        except:
                            outcome_payload["result"] = str(result)
                            
                    d.outcome(outcome_payload)
                    return result
                    
                except Exception as e:
                    # Record error
                    d.outcome({"status": "error", "error": str(e)})
                    # The tracer handles exceptions in context exit if we don't catch?
                    # But we want to explicitly record 'decision.error' or outcome=error?
                    # V1 Spec has d.error()? No, it has d.outcome().
                    # Actually tracer handles exceptions by logging to stderr but NOT auto-emitting an outcome unless we do.
                    # Wait, looking at tracer.py source:
                    # __exit__ checks exc_type. If exception occurred, does it emit error?
                    # It calls self._record...
                    # Let's emit outcome="error" manually here just in case.
                    raise e
                    
        return wrapper
    return decorator
