
# Migration & Adoption Guide

Adopting Decision Trace does not require a "big bang" rewrite. We recommend an incremental "Dual-Write" strategy.

## 1. Dual-Write Strategy

You likely already have logging. Keep it.
Add Decision Trace alongside your existing logging for critical "Fork in the Road" moments.

**Existing Code:**
```python
logger.info(f"Checking score for user {user_id}")
if score > 700:
    logger.info("Approved")
    return True
else:
    logger.info("Denied")
    return False
```

**With Decision Trace:**
```python
logger.info(f"Checking score for user {user_id}")
with decision("loan.approval", actor=SYSTEM) as d:
    d.evidence("score", score) # Structured capture
    
    if score > 700:
        logger.info("Approved")
        d.policy_check("min_score_700", "pass")
        d.outcome("approved")
        return True
    else:
        logger.info("Denied")
        d.policy_check("min_score_700", "fail")
        d.outcome("denied")
        return False
```

This allows you to verify the decision graph without disrupting existing observability dashboards.

## 2. Coexistence with Logs

- **Logs**: Use for high-volume debugging info (connection pools, raw HTTP dumps, stack traces).
- **Decisions**: Use for business logic checkpoints (Approvals, Tools calls, Routing logic).

Don't trace every loop iteration. Trace the *decisions*.

## 3. Incremental Rollout

1.  **Phase 1: The "Crown Jewels"** - Instrument only the most critical decision in your system (e.g., Final Approval, Payment Execution).
2.  **Phase 2: The Inputs** - Instrument the immediate precursors (Classification, Risk Check). Link them with `depends_on`.
3.  **Phase 3: The Agent** - Instrument AI tool calls and reasoning steps.
