# Build progress

The acceptance checklist is PokerLab_Cloud_Codex_Build_Spec.md. This log distinguishes implemented work from verified work. No infrastructure is deployed by the build.

## Baseline (22 September 2026)

- Existing Java engine, seven-card evaluator (21 five-card combinations), CLI and seeded Monte Carlo implementation are reusable.
- Baseline: `mvn test` passed all 37 tests on Java 21.0.3 and Maven 3.9.12.
- Existing request validation happens at execution rather than construction; it allows ten players and excludes one/two-card boards. These differ from the cloud specification.
- Existing result model lacks per-player ties/losses and durable batch contracts.
- Git branch: `codex/pokerlab-cloud`. The supplied build specification is included as the implementation reference.
- Initial environment: Docker and Terraform absent from PATH; system Node obsolete (6.14.0). Later work used bundled Node 24, a checksum-verified local Terraform 1.13.5, native PostgreSQL 16, and GitHub Linux runners for Docker verification.

## Implemented phases

- Java 21 Maven modules: engine, shared, API and standalone worker. Existing CLI retained.
- Validated 2–9-player scenarios, every 0–5-card board length, reproducible seeds, bounded batches, tie/loss/equity results.
- PostgreSQL migrations, REST lifecycle/history/results, transactional outbox, SQS/LocalStack and durable PostgreSQL development transport.
- Idempotent result persistence, concurrent finalisation, retry, dead-letter handling and optional Redis snapshots.
- React form, progress, results and history; full signed 64-bit seed strings and fractional-input rejection.
- Compose/Dockerfiles, structured logs and metrics, Terraform AWS resources, CI and a gated manual deployment template.
- Three-repeat 1/2/4/8-thread benchmark, measured CSV/plot, architecture/ADRs and local/cloud runbooks.

## Acceptance evidence

The [first complete CI run](https://github.com/pingadom/PokerSolver/actions/runs/35743401927) passed all four jobs. Its downloaded JUnit reports confirm the PostgreSQL and Redis Testcontainers tests actually ran (not skipped). The native PostgreSQL alternative was intentionally skipped on that Linux runner. Later regression and release revisions run the same workflow; see [current PR checks](https://github.com/pingadom/PokerSolver/pull/1/checks).

| Gate                                               | Evidence                                                                                                                                                                                     |
| -------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Engine unit tests and packaging                    | Local Java 21 Maven builds and CI; exhaustive check covers all 2,598,960 five-card hands                                                                                                     |
| Schema from an empty database                      | Native PostgreSQL 16 and fresh Testcontainers/Compose databases                                                                                                                              |
| Duplicate/concurrent/out-of-order/failure handling | PostgreSQL contract: six deliveries, three accepted results, one finalisation; transient publication rollback and retry                                                                      |
| Redis outage correctness                           | Unit test, real Redis container cache/flush/outage test, and Compose simulation after stopping Redis                                                                                         |
| Frontend lint/typecheck/tests/build                | Seven frontend tests plus production build; browser submission and completed results verified                                                                                                |
| Docker images and full Compose startup             | Clean-checkout Linux CI builds all images and starts two workers                                                                                                                             |
| Queue-to-worker-to-aggregation smoke               | CI LocalStack path with Redis on/off; native two-process worker run completed 10 million trials / 100 batches, with both workers consuming; [recorded evidence](data/distributed-smoke.json) |
| OpenAPI and frontend availability                  | HTTP checks in CI plus local browser/API verification                                                                                                                                        |
| Terraform                                          | `fmt -check` and provider-backed `validate` pass locally and in CI                                                                                                                           |
| Workflow YAML                                      | Parsed locally; CI workflow executed successfully                                                                                                                                            |
| Benchmarks                                         | Twelve measured runs, CSV summaries and plot, exact batch-result comparison across worker counts                                                                                             |
| Repository discipline                              | Conventional commits on feature branch, draft PR, ignored secrets/runtime artifacts; release only after green checks                                                                         |

No AWS infrastructure was applied, and no deployment workflow was executed. Terraform validation establishes syntactic/provider-schema correctness; it does not prove an actual AWS deployment. Docker is still absent on the Windows host, with container verification supplied by CI. Authentication, ranges, cancellation endpoints, autoscaling, push updates and S3 exports remain deliberately out of core scope.

Release policy: tag `v1.0.0` only after the final revision passes CI and the working tree is clean. Do not merge or deploy implicitly.
