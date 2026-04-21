# Releasing

This repository now contains release automation for both package ecosystems:

- PyPI workflow: `.github/workflows/publish-python.yml`
- Maven Central workflow: `.github/workflows/publish-java.yml`

## Versions And Coordinates

- Python distribution: `decision-trace`
- Java groupId: `io.github.logicoflife.decisiontrace`
- Java artifacts:
  - `decision-trace-bom`
  - `decision-trace-core`
  - `decision-trace-spring-boot-starter`

The Java groupId was moved off `io.decisiontrace` because Sonatype Central requires a verified namespace. For this repository owner, `io.github.logicoflife` is the claimable path documented by Sonatype for GitHub-backed namespaces. If you later verify a DNS-backed namespace and want different Maven coordinates, do that before a broadly adopted release.

## One-Time Setup

### PyPI

1. Create the `decision-trace` project on PyPI if the name is still available.
2. In PyPI project settings, add a Trusted Publisher for:
   - owner: `logicoflife`
   - repository: `decision-trace`
   - workflow: `.github/workflows/publish-python.yml`
   - environment: `pypi`

PyPI's Trusted Publisher flow for GitHub Actions is documented here:

- https://docs.pypi.org/trusted-publishers/
- https://docs.pypi.org/trusted-publishers/adding-a-publisher/
- https://docs.pypi.org/trusted-publishers/using-a-publisher/

### Maven Central

1. Sign in to the Sonatype Central Portal with the GitHub account that owns `logicoflife`.
2. Verify the `io.github.logicoflife` namespace or confirm it was provisioned automatically.
3. Generate a Central Portal user token.
4. Create or export an ASCII-armored GPG private key for signing.
5. Add these GitHub repository secrets:
   - `CENTRAL_TOKEN_USERNAME`
   - `CENTRAL_TOKEN_PASSWORD`
   - `CENTRAL_GPG_PRIVATE_KEY`
   - `CENTRAL_GPG_PASSPHRASE`

Relevant Sonatype documentation:

- https://central.sonatype.org/register/namespace/
- https://central.sonatype.org/publish/requirements/
- https://central.sonatype.org/publish/publish-portal-maven/
- https://central.sonatype.org/faq/namespaces-vs-groupids/

## Local Validation

Python:

```bash
python -m pip install --upgrade build twine
python -m build --no-isolation
python -m twine check dist/*
```

Collector smoke test from the built wheel:

```bash
python -m venv /tmp/decision-trace-smoke
WHEEL="$(ls dist/*.whl | head -n 1)"
/tmp/decision-trace-smoke/bin/pip install "decision-trace[collector] @ file://$(pwd)/${WHEEL}"
DECISION_TRACE_DATA_PATH=/tmp/decision-trace-smoke/events.jsonl /tmp/decision-trace-smoke/bin/decision-trace-collector
```

Java:

```bash
cd sdk/java
mvn -Prelease verify
```

If you want to generate the Central bundle locally without uploading it:

```bash
cd sdk/java
mvn -pl decision-trace-bom,decision-trace-core,decision-trace-spring-boot-starter -am -Prelease -DskipTests -Dcentral.skipPublishing=true deploy
```

The current Central plugin still expects a `central` server entry in `~/.m2/settings.xml` even when `skipPublishing=true`, so this dry run is only useful after you have generated a Central token and configured Maven credentials.

To validate signing locally, run with a configured GPG key:

```bash
cd sdk/java
mvn -pl decision-trace-bom,decision-trace-core,decision-trace-spring-boot-starter -am -Prelease -Dgpg.skip=false verify
```

## Publish

1. Update versions if needed.
2. Commit the release state.
3. Create and push a tag like `v0.1.1`.
4. Both publish workflows will start automatically on that tag.

The Java workflow publishes only the library modules. The sample app and benchmark module remain in the reactor for tests, but are skipped from Central deployment.
