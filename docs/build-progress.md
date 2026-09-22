# Build progress

The acceptance checklist is PokerLab_Cloud_Codex_Build_Spec.md. This log distinguishes implemented work from verified work. No infrastructure is deployed by the build.

## Baseline (22 September 2026)

- Existing Java engine, direct seven-card evaluator, CLI and seeded Monte Carlo implementation are reusable.
- Baseline: `mvn test` passed all 37 tests on Java 21.0.3 and Maven 3.9.12.
- Existing request validation happens at execution rather than construction; it allows ten players and excludes one/two-card boards. These differ from the cloud specification.
- Existing result model lacks per-player ties/losses and durable batch contracts.
- Git branch: `codex/pokerlab-cloud`. The supplied build specification is included as the implementation reference.
- Environment: Docker and Terraform are unavailable on PATH; system Node is obsolete (6.14.0). Container and infrastructure acceptance gates remain pending until usable runtimes are available.

## Remaining phases

Engine extraction and hardening; API/PostgreSQL; SQS/workers/idempotency; Redis; React; Compose; observability; Terraform; CI; measured benchmarks and complete acceptance verification.
