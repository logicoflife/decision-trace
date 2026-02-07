
# Project Governance

Decision Trace is an open-source project managed by a core team of maintainers.

## Maintainer Model

- **Decision Authority**: Final decisions on architecture, schema, and roadmap rest with the Core Maintainers.
- **Spec-First**: Changes to the specification (schema, protocol) take precedence over implementation convenience.
- **Curated Contributions**: We value quality and stability over feature velocity. We may decline features that increase maintenance burden or deviate from the core philosophy.

## Roles

- **Contributor**: Submits PRs, issues, and docs.
- **Maintainer**: Reviews PRs, triages issues, manages releases.
- **Core Maintainer**: Sets strategic direction, approves schema changes.

## Decision Process

1.  **Proposal**: Open an Issue describing the problem (not just the solution).
2.  **Discussion**: Async discussion on the Issue.
3.  **Consensus**: Maintainers aim for consensus but Core Maintainers have tie-breaking authority.
4.  **Implementation**: Code is written only after the design is agreed upon.

This model ensures strict adherence to our [Safety Guarantees](docs/safety.md).
