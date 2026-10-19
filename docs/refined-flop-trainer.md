# Qualified paired-board trainer backend

The paired-board BTN response now has a complete owned qualification and a cached research trainer.
BTN holds `As Qs` on `8d Kc Kd`, facing a 3.25 bb bet into the original 6.5 bb pot. The displayed pot
is 9.75 bb. Fold is outside the 0.01 bb loss limit; call is within it. The source remains the declared
24-world, two-active-player model at a six-seat table, with folded cards retained as blockers.

## Qualification and replay

`SixMaxRefinedFlopFeedback` consumes an owned v6 conditional solve and trains two separate fresh CFR+
references at 80,000 and 160,000 iterations. The request binds the complete conditional request,
fixed 240,000-iteration reference budget, reference algorithm and educational loss limit before work
begins. Loaded policies, caller-built audits and checkpoints cannot substitute for those runs.

Primary and reference checks include full-parent quality, separately reached-flop quality, actual
and counterfactual full-path reach, inclusion in the original numerical opponent security face,
forced-action EV/loss intervals and selected-decision regret. Qualification also requires a robust
move within the requested loss limit and a robust costly alternative. All existing tolerances,
reach floors, LP/compiler bounds and older report identities stay unchanged.

Only a complete successful owned solve or replay creates the private `QualifiedFeedback` capability.
Its hash binds the entire report, including fresh policies and their work evidence. Parsing a report,
editing its values or recomputing its hash does not create that capability. Replay regenerates the
physical game, parent/endpoint LPs, fresh references and every report field.

The saved [request](data/supplied-paired-btn-facing-bet-feedback-request.json) and
[report](data/supplied-paired-btn-facing-bet-feedback-report.json.gz) have report pin
`1a5254e971afcc76a3e5b1eba7c1c61f90afd8de17684842846c63da0afe6173`.
The primary LP policy's iteration `1` remains a container sentinel, not a CFR training run.

## Validated inactive-seat pruning

The separately selected reference algorithm uses existing opt-in fixed-utility pruning. Before
training, `SixMaxOwnedFlopReferences` validates the complete immutable owned tree: each of the four
inactive seats must have no decision anywhere and exactly the same binary64 utility at every
terminal. No tolerance or estimated payoff establishes this guarantee. Active-seat passes traverse
the original game; inactive-seat passes can return their proven constant at the root.

At 1,000 iterations, this reduces visited nodes from 7,926,000 to 2,646,000 with an exactly identical
policy. Both full 80k/160k reference policies also match the prior ordinary-run hashes. The reference
report records literal tree size, inactive utilities, traversal counters and pruned roots. This is
about 67% less node work, not a claim of a measured threefold wall-clock speedup. Existing solver
defaults and old reference algorithms are unchanged.

## Cached trainer and operator boundary

`SixMaxRefinedFlopTrainer` accepts only the owned qualified result. Its question contains public
six-seat context, the hero hand and scoped model provenance. Both action grades and zero-to-100
attempt reviews use cached immutable evidence. Reviews distinguish attempts at this one question
from scenario coverage and support exact saved replay. The [saved drill](data/supplied-paired-btn-facing-bet-drill.json)
contains one fold and one call attempt.

The loader requires request/report paths and the exact report pin. It snapshots real file identity,
size, modification time and SHA-256 before and after complete startup replay. A changed file or
rehashed edited report rejects before the trainer can serve grades. Stored/expanded qualification
files are bounded at 8 MiB; drill files at 256 KiB.

The API is off by default at `/api/v1/trainer/research/refined-flop-interval`. Opt-in metadata,
`GET /question`, `POST /grade` and `POST /review` serve only the cached trainer. Requests submit a
report pin and explicit actions, never input files, policies, payoffs or solver limits. The parser
rejects duplicate JSON keys, trailing values, scalar coercion and bodies above 32 KiB. Responses
carry `Cache-Control: no-store` and `trainerAdmission=false` for this research source.

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-refined-flop-interval.yml up --build -d
python scripts/smoke-refined-flop-interval-trainer.py
```

The separate CI smoke job performs real startup qualification and HTTP grading without increasing
the existing Compose or backend job limits. Independent standard-library checkers reconstruct the
physical parent and original LP certificates, verify all three policies, prove literal inactive-seat
utilities, check reference work and replay every saved grade. They run no optimizer or CFR training.

Next integrate this typed source into a separately versioned mixed collection and the table UI.
This backend qualification does not extend the old six-question collection or establish ordinary
six-max cash coverage. AWS deployment remains paused.
