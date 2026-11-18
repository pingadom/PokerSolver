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

## 17 November 2026 solver scope assessment

The project owner's current direction remains a complete explainable solver demo
and small trainer, progressing toward the broader six-max cash GTO product.
A rough planning assessment is about 90% for the focused demo and 40% for the
broader product. These are judgment estimates by scope, not test-count percentages
or a claim that remaining mathematical work is linear. Realistic broad ranges,
rake, general six-active-player solving and coherent full-hand strategy remain
substantial work.

The next proposed range-input backend covers 169 classes and 1,326 exact physical
hands with per-combo weights, blocker handling and a standalone CLI. All 22 local
tests and packaging passed. It integrates with existing input types and preserves
all solver caps; no new trained chart or lesson is claimed. The owner cleared this revision as `dcb58cd`; full
PR CI follows that clearance. AWS deployment remains paused.

## Six-seat weighted range preflight — 18 November 2026, owner cleared

- Added a bounded complete card-legality planner for six explicit weighted ranges,
  with immutable full joint priors, unsupported-hand visibility, log-weight
  normalization and the existing 64-world cap. Prefix checks are bounded at one
  million and can be reduced; incomplete inputs never return a partial plan.
- Added a private offline export and an explicit bridge to existing research-spot
  validation. No strategy solving, new trainer admission or HTTP/UI change follows.
- All nineteen new Java tests and twenty-two retained range tests passed. A literal
  Cartesian oracle checks weighted probabilities; all twelve weighted joint worlds
  also match the existing game's chance model with analytic equal-share payoffs.
  Spotless, compilation and packaging passed; full PR CI follows owner clearance.
- Existing source files, numerical evidence, resources and dependencies remain
  unchanged. Broader preflop support/physical payoff budgets/qualification remain
  the next boundary; the rough planning estimates stay 90% for the focused demo
  and 40% for the broad product. The owner cleared this revision as `1dfc54a`.

## Six seat payoff work — 18 November 2026, owner cleared

- Added owned exact/shared-board payoff planning and explicit execution for complete
  six-seat private support. Board/hand costs must fit both lowerable limits; there
  is no fallback sampling or support truncation. Private CLI plan/evaluate modes
  preserve signed seed identity and reject existing outputs before evaluation.
- All 17 new cases and 41 retained input cases passed. A complete one-world exact
  result agrees with an independent two-active/six-dealt board enumerator. A
  twelve-world sampled table reconciles 96 actual board draws and feeds the
  existing game without more physical evaluations. These are payoff controls,
  not solved policies or trainer admission.
- Spotless, compilation and packaging passed. Existing Java, 441 data files,
  28 resources, 400 prior CI assignments and dependencies remain unchanged.
  Full PR CI follows owner clearance. Fresh range-driven source solving, payoff
  error checks and independent strategy qualification remain next. Planning
  estimates remain about 90% for the focused demo and 40% for the broad product;
  these are scope judgments rather than test-count measures. AWS stays paused.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`87673cb036635e75d85b7e6c59409b6fb1aea3d4` and cleared PR creation on
2026-11-18 (Europe/London), with “I reviewed 87673cb — clear it”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
