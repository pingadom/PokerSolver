# Qualified weak-hand flop trainer

A second pinned backend source now covers BTN `2c 3c` facing a BB bet on
`8d Kc Kd`. Folding is within the 0.01bb teaching limit. Calling loses at least
2.24949975bb. This is one independently qualified decision with two active
players at a six-seat table, supplied joint beliefs, one flop bet, exact later
checkdown and no rake.

## From solver result to cached grades

The [resolvable/refined endpoint study](resolvable-refined-endpoints.md) supplies
the original physical game, parent and fold/call EV intervals. Its 24 worlds
include all six hands' blockers. The independent checker regenerates all
15,792,192 five-card boards, the selected-flop runouts, complete betting tree,
original parent LPs and eight original endpoint certificates.

The separate qualification request freezes two fresh full-game CFR+ runs at
80,000 and 160,000 iterations. Both pass the existing full-parent and reached-flop
quality thresholds, full-face inclusion, actual and counterfactual reach,
selected-decision regret and all-action feedback checks. The primary policy also
passes. Original thresholds are unchanged.

| Reference | Full-game NashConv (bb) | Selected decision regret (bb) | Counterfactual reach |
| --- | --- | --- | --- |
| 80k | 2.08788729e-9 | 3.51499454e-10 | 0.2499999995 |
| 160k | 5.21975185e-10 | 8.78754127e-11 | 0.2499999999 |

The original generation budget and complete policy/work evidence are retained.
The budget's relative snapshot names describe the original generation location;
the published checker binds the same input bytes at their repository paths.
Independent policy checks reconstruct literal best responses, path posteriors,
forced action values, classification and inactive-seat pruning/work counts.

`SixMaxWeakHandFlopTrainer` is a distinct private-construction cache type. Its
loader requires the exact source pin below and delegates to the existing owned
qualification loader. Startup snapshots the bounded request/report files,
physically rebuilds and re-solves the game, trains both references afresh,
compares the complete result, and checks that both snapshots stayed unchanged.
Only then can the cache serve questions, grade choices or replay reviews.
Parsed reports and client flags cannot construct the cache.

```text
5dbd0cdeba8a74e6da32dae0bddeb69381a90ba4f72220ab4a9bf92bcc667c56
```

A distinct cache type and API namespace keep the existing refined source and
ten-question collection independent. The new source has different supplied
worlds; it is not appended to the existing collection or substituted for its
pinned inputs. No new frontend collection is provided by this backend milestone.

## Operator opt-in and API

The source is off by default. A missing path, missing pin, different source or
failed physical/reference qualification prevents startup of the enabled source.
The local Compose overlay mounts both frozen inputs read-only:

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-weak-hand-flop-interval.yml up --build -d
python scripts/smoke-weak-hand-flop-interval-trainer.py
```

The equivalent `pokerlab.trainer.weak-hand-flop-interval-*` settings are
`research-enabled`, `request-path`, `report-path` and `report-hash`. The overlay
uses `TRAINER_WEAK_HAND_FLOP_INTERVAL_*` environment variables. Full qualification
happens before readiness; cached request handlers perform no optimization.

Base path: `/api/v1/trainer/research/weak-hand-flop-interval`.

| Method and path | Purpose |
| --- | --- |
| GET base path | Pinned source metadata and scope |
| GET `/question` | Public six-seat context and hero hand |
| POST `/grade` | Canonical EV/loss ranges and classification for one move |
| POST `/review` | Canonical saved review for up to 100 explicit attempts |

Grade bodies contain only `reportHash` and `action`; review bodies contain only
`reportHash` and `actions`. Stale pins, illegal moves, caller-supplied EV/context,
duplicate/unknown fields, trailing data, scalar coercions and oversized requests
are rejected. Responses use `Cache-Control: no-store`. Question responses do not
expose the private deal support, policies or LP witnesses. The review response
can be saved as JSON and replayed against the owned cache.

```text
com.pokerlab.solver.SixMaxWeakHandFlopTrainerMain create <request> <report> <new-review.json> fold call
com.pokerlab.solver.SixMaxWeakHandFlopTrainerMain replay <request> <report> <review.json>
```

## Validation and remaining scope

Java tests perform complete pinned qualification before checking both grades,
public context, saved review round trips, foreign questions, forged reviews,
attempt limits, exact source pins and default-off behavior. The production API
smoke compares the entire question, both grade responses and exported review
against the frozen owned fixture. The independent checker has semantic corruption
controls before final stored-byte binding. CI also starts the real Docker API
with opt-in disabled and then enabled.

The source remains `trainerAdmission=false`, meaning it is a restricted research
drill rather than general cash-poker strategy. Its local offline qualification is
explicitly recorded separately. A later source registry/collection must bind each
source's different physical input and qualification budget. More hands, connected
betting streets, rake and six simultaneously active players remain later work.
AWS deployment remains paused.
