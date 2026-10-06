# Architecture

PokerLab Cloud separates numerical computation from delivery, persistence and presentation. The framework-free engine supports exact hole cards for 2–9 players, 0–5 known community cards and deterministic Monte Carlo batches. API and worker are independent Spring Boot processes; increasing worker replicas increases concurrent consumption.

```mermaid
flowchart LR
  UI[React + TypeScript] --> API[Spring Boot API]
  API --> DB[(PostgreSQL: durable lifecycle)]
  API --> Cache[(Redis: short-lived cache)]
  DB --> Outbox[Transactional outbox dispatcher]
  Outbox --> Queue[SQS / LocalStack]
  Queue --> Workers[Independent Java workers]
  Workers --> Engine[Poker engine]
  Workers --> DB
  Queue --> DLQ[Dead-letter queue]
  DLQ --> Workers
```

## Responsibilities

| Component                  | Responsibility                                                                                                               |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `engine`                   | Immutable cards/player scenarios, validation, hand ranking, seeded board sampling, batch planning/results, benchmark harness |
| `solver`                   | Offline CFR/CFR+, bounded two-to-six-player games, exact best responses, continuation feedback, saved-policy EV grading       |
| `shared`                   | Validated message codec, relational repositories, Flyway, queue adapters, outbox and transactional aggregation               |
| `api`                      | REST DTOs/validation, status/history/results, cache, OpenAPI, HTTP correlation IDs, publication scheduler                    |
| `worker`                   | Consume, renew visibility, compute outside transactions, submit results, acknowledge after commit, handle dead letters       |
| `frontend`                 | Equity form and results, progress/history, validation-only drills with a six-seat table, action EVs and decision feedback      |
| `infrastructure/terraform` | Restricted AWS demonstration environment; no automatic provisioning                                                          |

The original five-card evaluator is reused. Its seven-card path checks all 21 five-card combinations, comparing packed numeric scores without constructing verbose hand objects. No new external evaluator dependency was introduced.

## Solver and trainer boundary

Equity simulation and strategy training have different jobs. A worker estimates showdown equity for the submitted exact hands. The framework-free `solver` module instead learns action probabilities in an explicitly declared betting game, using chip utilities and private information sets. Its CFR/CFR+ implementation and finite-game best-response evaluators are PokerLab's own code. Weighted ranges, folded-card blockers, correlated reached hands and public betting history determine the game being solved; showdown equity alone cannot grade a betting decision.

The offline [exact payoff-reuse builder](sixmax-preflop-payoff-reuse.md) separates physical showdown shares from betting utilities. A strictly validated exact source can seed a fresh game with different raise targets, stack, rake or positive range weights only when all six-hand deal/subset keys match exactly. It recomputes chance probabilities, pot commitments, utilities, information sets, strategy and best-response quality; missing or removed private worlds fail before solving. Provenance binds both spot hashes and the original pack, while the new pack remains validation-only. A changed source cannot resume an old connected policy checkpoint. Different-game scores do not establish policy improvement.

A [same-game policy comparison](sixmax-connected-policy-stability.md) strictly reloads two source/menu-bound checkpoints and recomputes quality and retained content before reporting independent-solve action-frequency disagreement. It weights information sets by the symmetric average of both policies' full physical decision encounter masses, with separate preflop/postflop normalization so rare selected boards do not hide conditional differences. Unreachable-row differences remain visible through uniform means and maxima. It never selects or admits a trainer policy.

The current connected six-seat research pipeline is offline:

```mermaid
flowchart TB
  Source[Saved ranges, rules and exact payoff tables] --> Joint[Joint six-seat CFR]
  Joint --> Complete[Explicit policy completion]
  Complete --> Post[Conditional postflop CFR+]
  Post --> Initial[Fresh audit and initial checkpoint]
  Resume[Validated average-policy checkpoint] --> Initial
  Initial --> Values[Exact frozen continuation values]
  Values --> Pre[Six-seat preflop CFR+]
  Pre --> Changed[Postflop CFR+ at changed ranges]
  Changed --> Audits[Full-parent and conditional audits]
  Audits --> Gate{Material improvement + conditional target?}
  Gate -->|Pass| Saved[Retained policy checkpoint]
  Saved --> Values
  Gate -->|Fail| Stop[Keep last retained policy and stop]
  Audits --> Reports[Source-bound validation reports]
```

Every seat makes preflop decisions. Selected heads-up histories and physical flops permit connected flop/turn/river betting; other flops and multiway non-all-in pots retain mandatory checkdown. Freezing a completed postflop policy produces one literal utility vector per selected public history and original private deal. Preflop re-solving retains original root chance and own-hand observations, replaces only preflop strategy rows, then recomputes both parent deviations and conditional postflop quality under the changed ranges. A matched control freezes the unrefined continuations; an optional final postflop stage solves at the new ranges. The [alternating workflow](sixmax-alternating-continuation-rounds.md) compares each complete round against the last retained policy, persists only passing candidates and stops on rejection or plateau. Strict checkpoints preserve the explicit average policy and source/menu identity; resume recomputes quality and starts fresh CFR stages. The [private-support audit](sixmax-eight-deal-private-coverage.md) separately records original priors, counterfactual board compatibility and policy-conditioned beliefs, with policy hashes and explicit zero-reach cases. The connected model accepts up to twelve private deals while the study budget remains capped at 16 compatible deal/flop pairs and 2,000,000 states. An opt-in [active-pair selector](sixmax-diverse-active-pair-selection.md) scans a declared reach-ranked window, requires meaningful board-conditioned source hand mass for both players, and picks distinct pairs without pruning counterfactual worlds. Selection settings and skip reasons appear in study reports; v4 adds an offline [physical range-correlation audit](sixmax-correlated-private-ranges.md). This twelve-deal source includes four uncertain seats and overlapping private cards; joint priors cannot be reconstructed by multiplying their physical marginals. Alternating v5 adds [source and retained content screens](sixmax-retained-continuation-coverage.md), keeping public-history reach, physical-board coverage and active-hand diversity separate from the unchanged quality gate. A bounded fixed-policy menu search records cost and content failures; proposed menus require a new solve, and do not receive copied postflop rows or quality scores. Resume still requires an exact menu match. See [continuation feedback](sixmax-continuation-preflop-feedback.md) for paired evidence and remaining model limits.

The live trainer consumes separately saved, strictly loaded solution packs. Its opt-in research endpoints bind questions, submitted decisions and reviews to a pack hash. Loading reconstructs the declared game and checks complete strategy/payoff support and numerical quality. Requests replay a reached public history and evaluate legal-action EVs from that saved policy; they do not launch CFR training or Monte Carlo board work. Research reports from the pipeline above contain audit evidence, not a newly admitted trainer pack. Synthetic ranges and restricted betting rules keep the current drills `VALIDATION_ONLY`; see the [full-round pack and trainer contract](sixmax-full-round-pack-trainer.md).

This separation makes trainer responses reproducible and keeps long numerical work out of HTTP handlers, database transactions and the equity queue. Broader cash-game ranges, physical betting coverage, postflop raises, multiway betting and rake remain solver gates before general GTO content.

## Submission and completion

```mermaid
sequenceDiagram
  participant UI as Browser
  participant API as API
  participant DB as PostgreSQL
  participant Q as SQS
  participant W as Worker
  UI->>API: POST scenario, iterations, optional seed
  API->>DB: Transaction: simulation + batches + outbox
  DB-->>API: Commit
  API-->>UI: 202 + simulationId + statusUrl
  loop Pending outbox rows
    API->>DB: Lock rows, SKIP LOCKED
    API->>Q: Publish independent batch
    API->>DB: Mark published, commit
  end
  Q-->>W: At-least-once delivery
  W->>DB: Lock simulation, validate, record attempt
  W->>W: Execute seeded batch outside transaction
  W->>Q: Renew visibility while computing
  W->>DB: Lock simulation; insert unique result
  alt First accepted delivery
    W->>DB: Increment progress; finalise if last batch
  else Duplicate or terminal simulation
    DB-->>W: No state mutation
  end
  DB-->>W: Commit
  W->>Q: Acknowledge
  loop Every 2 seconds until terminal
    UI->>API: GET status
    API-->>UI: Progress snapshot
  end
  UI->>API: GET results
  API-->>UI: Per-player counts and equity
```

## Persistence and correctness

`simulations` stores typed lifecycle fields and immutable JSONB configuration. `simulation_batches` stores batch identity, derived seed, trials, status and attempts. `batch_results` has a composite primary key and foreign key to its batch. `simulation_results` has one row per simulation. The `batch_outbox` insertion belongs to the submission transaction, closing the database/queue crash window.

Result processing locks the simulation row first. It validates the message configuration and result identity against durable metadata. An `ON CONFLICT DO NOTHING` insert distinguishes duplicates. Only new inserts increment counters. The last result finalises in that same transaction, summing batches in deterministic order and checking total trials. Workers may compute duplicate deliveries, but duplicates cannot alter equity, progress or completion twice. Different simulations can persist concurrently; completion writes within one simulation serialise deliberately.

Wins count outright pots; ties count participation in a split pot. Fractional equity shares preserve multiway splits. Aggregation sums shares and divides by total trials, handling unequal final batches correctly. See [engine contracts](engine.md) for limits, precision and reproducibility boundaries.

## Failure behaviour

| Failure                                               | Behaviour and recovery                                                                                                                                            |
| ----------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| API crashes before submission commit                  | No partial simulation/outbox survives. POST has no client idempotency key, so an uncertain response after commit can lead to a second simulation on resubmission. |
| Queue unavailable                                     | Outbox remains pending; scheduled publication retries.                                                                                                            |
| Crash after queue send, before outbox commit          | Batch may publish again; result uniqueness absorbs duplicates.                                                                                                    |
| Worker crash, database failure or lost acknowledgment | Message becomes visible again after its unacknowledged lease expires.                                                                                             |
| Long-running batch                                    | Visibility renewed every 30 seconds for a 120-second lease; duplicate work remains safe if renewal fails.                                                         |
| Five unsuccessful deliveries                          | SQS redrives to DLQ; worker records terminal failure before acknowledging a valid failed batch.                                                                   |
| Malformed/unidentifiable message                      | Retained in DLQ for inspection, metered and alarmed; never used to fail an unrelated simulation.                                                                  |
| Late result/dead letter after completion              | Terminal state and accepted result remain unchanged.                                                                                                              |
| Concurrent final batches                              | Row lock plus unique final row permits one finalisation.                                                                                                          |
| Redis missing, flushed, corrupt or unavailable        | PostgreSQL reads still succeed. Status may be up to two seconds stale while cached.                                                                               |
| Browser/API connection interrupted                    | Shows error and explicit Retry; no overlapping polling requests or continued polling after terminal state.                                                        |

`CANCELLED` is modelled and rendered, but cancellation is not exposed as an endpoint. Authentication, ranges, S3 exports and autoscaling remain stretch goals. No code assumes exactly-once queue delivery.

## Security and deployment limits

Card/name/count validation applies before persistence, with a 100-million-trial request cap and at most 10,000 batches. Fractional integers are rejected. Browser seeds use decimal strings to avoid losing precision; Java preserves all signed 64-bit values. The API uses explicit detached DTOs. CORS is same-origin by default; Vite and nginx proxy local API requests. No cross-origin wildcard is enabled.

There is no user authentication or per-user quota. Local published ports bind to loopback; AWS ingress requires an explicit restricted CIDR. This is an interview/demo architecture, not a publicly open computation service. AWS secrets are supplied at task startup; database/cache are not publicly reachable. See [deployment guidance](aws-deployment.md) for cost, TLS, credentials and production adaptations.

Decisions: [SQS](decisions/ADR-001-queue.md), [PostgreSQL vs Redis](decisions/ADR-002-postgres-vs-redis.md), [idempotent workers](decisions/ADR-003-idempotent-workers.md).
