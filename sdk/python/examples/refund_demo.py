from decision_trace.tracer import decision


def main() -> None:
    actor = {"id": "agent-1", "type": "agent", "version": "1.0", "org": "support"}

    with decision(
        tenant_id="acme",
        environment="dev",
        decision_type="refund.classify",
        actor=actor,
    ) as classify:
        classify.evidence("ticket_id", "T-100")
        classify.evidence("amount", 42)
        classify.policy_check({"policy": "refund_v1", "result": "pass"})
        classify.outcome({"classification": "eligible"})

    classify_id = next(iter(classify.events)).decision_id

    with decision(
        tenant_id="acme",
        environment="dev",
        decision_type="refund.approve",
        actor=actor,
        parent_decision_id=classify_id,
    ) as approve:
        approve.evidence(
            "classification",
            "eligible",
            causal_links=[{"type": "uses_evidence_from", "target_decision_id": classify_id}],
        )
        approve.policy_check({"policy": "refund_v1", "result": "pass"})
        approve.outcome({"approval": "approved"})

    approve_id = next(iter(approve.events)).decision_id

    with decision(
        tenant_id="acme",
        environment="dev",
        decision_type="refund.execute",
        actor=actor,
        parent_decision_id=approve_id,
    ) as execute:
        execute.action(
            {"action": "issue_refund", "amount": 42},
            causal_links=[{"type": "triggered_by", "target_decision_id": approve_id}],
        )
        execute.outcome({"status": "refunded"})


if __name__ == "__main__":
    main()
