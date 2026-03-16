# Java Golden Flow Walkthrough

The sample application models a login decision that fans out into risk and passkey sub-decisions.

## Request Path

1. `POST /auth/login` enters `AuthService.login`.
2. `@Decision(decisionType = "AUTH_SERVICE_LOGIN")` opens the root decision.
3. `DecisionContext` records user and device evidence.
4. `RestTemplate` propagates trace headers to `/risk/evaluate`.
5. `RiskService.evaluate` opens `RISK_ORCHESTRATION`.
6. Nested manual scopes emit `DEVICE_TRUST_CHECK`, `VELOCITY_CHECK`, and `FINAL_RISK_DECISION`.
7. `RestTemplate` propagates the active decision to `/passkey/verify`.
8. `PasskeyService.verify` opens `PASSKEY_SERVICE_VERIFY` and emits approval telemetry.
9. Control returns to risk, then auth, which emit evaluation events before closing.

## Expected Decision DAG

- `AUTH_SERVICE_LOGIN`
- `RISK_ORCHESTRATION` child of `AUTH_SERVICE_LOGIN`
- `DEVICE_TRUST_CHECK` child of `RISK_ORCHESTRATION`
- `VELOCITY_CHECK` child of `RISK_ORCHESTRATION`
- `FINAL_RISK_DECISION` child of `RISK_ORCHESTRATION`
- `PASSKEY_SERVICE_VERIFY` child of `FINAL_RISK_DECISION`

## Lifecycle Coverage

The sample verifies all Phase B lifecycle APIs:

- `decision.evidence`
- `decision.policy_check`
- `decision.action`
- `decision.approval`
- `decision.evaluation`

It also verifies:

- canonical event serialization
- JSON ledger equivalence
- OpenTelemetry projection equivalence
- cross-service causal propagation

See [GoldenFlowIntegrationTest.java](../sdk/java/decision-trace-samples/src/test/java/io/decisiontrace/samples/GoldenFlowIntegrationTest.java) for the executable assertions.
