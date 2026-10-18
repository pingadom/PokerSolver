# Four-question BTN flop trainer backend

PokerLab can now cache four qualified BTN decisions from its own solver. These are four supplied hands after BB calls preflop and checks the `2s 8s Ts` flop: `9s Js`, `Ah Kh`, `As Qs` and `Qh Th`. The pot is 6.5 bb and BTN can check or bet 3.25 bb. Two players are active at a six-seat table.

This is a research drill for these supplied beliefs and this restricted betting game. All four contexts certify bet within the 0.01 bb educational loss limit and check outside it. That agreement does not establish a general rule to bet every hand. Rake, wider ranges, further betting streets and a general six-player equilibrium remain outside this model. Source preflop-history reach is unavailable because no source policy was supplied.

## From solver evidence to a trainer

1. The operator supplies the original request, report and exact report pin. Bounded strict parsing, distinct-file checks and pin/request matching happen before expensive work.
2. The loader snapshots real paths, file identities, sizes, modification times and stored SHA-256 values. It then replays the complete physical game, all eight action intervals and two fresh CFR+ reference runs at 80,000 and 160,000 iterations. The shared 240,000 reference-iteration budget covers all four contexts.
3. Only the owned result's private qualified capability can construct `SixMaxBtnFlopTrainer`. Public parsed reports, qualification booleans and caller-generated hashes cannot authorize grades. The loader rechecks the owned pin and both file snapshots before publishing the immutable cache.
4. Requests use that cache. Looking up a question, grading a choice and building a review run no LP solve, CFR training, file read or background job.

Startup can take several minutes. The Compose smoke allows **900 seconds**, separately from the older BB waits. The startup log announces the physical replay. Grades return the source's original certified intervals without changing the LP tolerance, reach floor, slack or numerical work limits. See [the numerical qualification](refined-lp-directions.md).

## API

The route is absent by default. Enable `TRAINER_BTN_FLOP_INTERVAL_RESEARCH_ENABLED=true` and set `TRAINER_BTN_FLOP_INTERVAL_REQUEST_PATH`, `TRAINER_BTN_FLOP_INTERVAL_REPORT_PATH` and `TRAINER_BTN_FLOP_INTERVAL_REPORT_HASH`. These are operator settings; HTTP clients cannot supply file paths or solver configuration.

Base: `/api/v1/trainer/research/btn-flop-interval`.

| Request | Result |
| --- | --- |
| `GET /` | Pin, research status, model/chance/belief and EV/reach provenance, four questions, two active players, 100-attempt limit |
| `GET /questions/{0..3}?reportHash=...` | Exact cached public question |
| `POST /grade` with `{ "reportHash": "...", "questionIndex": 0, "action": "check" }` | Selected move and both certified alternatives |
| `POST /review` with `{ "reportHash": "...", "choices": [{ "questionIndex": 0, "action": "check" }] }` | Ordered replayable mixed review |

Success responses use `Cache-Control: no-store`. Choice bodies are bounded to 32 KiB, reject duplicate/unknown fields, scalar coercion and trailing JSON, and validate required pins, indices, menus and nested choices. Stale pins and invalid input return `400 INVALID_REQUEST`.

Questions expose BTN's hand and public seats, stacks, board and action history. They contain no opponent hole cards, hidden worlds, strategies, private payoff matrices or LP witnesses. EV and loss intervals are returned in feedback after a choice. Public `trainerAdmission` stays false: qualification for this research drill does not claim reviewed general poker training content.

## Reviews and offline use

`pokerlab-btn-flop-drill/v1` binds the exact ordered question inventory, source pin, selection, attempt indices, selected actions, both alternatives and counts. Replay reconstructs every grade from the owned cache and compares the entire review. There are at most 100 attempts across all questions, including retries. Zero attempts are legal. Counts distinguish distinct contexts attempted from repeated decisions within/outside the loss limit or dependent on the strategy. They do not claim general accuracy or cumulative full-hand EV.

Plain and gzip review files each have a 256 KiB stored and expanded cap. Outputs are exclusive new files. The CLI protects the request, source report and review from aliases and validates choices before starting physical replay.

After building the solver and its runtime dependencies, run `com.pokerlab.solver.SixMaxBtnFlopTrainerMain` with:

```text
create <request> <report> <report-hash> <new-review.json[.gz]> [0:check 1:bet ...]
replay <request> <report> <report-hash> <review.json[.gz]>
```

The pinned report is `9686d76c4a9d3b1a0875f7fcaed3edecf4491e0c24698892b516f91a69c89cd1`. The saved [mixed review](data/supplied-four-button-hand-btn-flop-drill.json) contains both actions for every hand, with four contexts and eight attempts.

For local opt-in:

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-btn-flop-interval.yml up --build -d
python scripts/smoke-btn-flop-interval-trainer.py
```

The request and report mounts are read-only. This does not deploy AWS infrastructure.

## Verification and next work

The independent Python checker first validates the source's 32 original endpoint certificates, literal poker physics, three policies and 12 policy/context checks, then verifies the public inventory, all review grades and counts. Its receipt embeds the complete source receipt and binds the checker and stored/expanded review bytes. It runs no optimizer and does not claim to prove fresh CFR execution; the owned Java replay supplies that evidence.

```sh
python scripts/check-btn-flop-drill.py \
  docs/data/supplied-four-button-hand-btn-curriculum-request.json \
  docs/data/supplied-four-button-hand-btn-curriculum-report.json.gz \
  docs/data/supplied-four-button-hand-btn-flop-drill.json
```

Java tests exercise actual pinned API startup, all eight grades, exact mixed replay, privacy, strict input, 0/100/101 attempts, exclusive bounded gzip/plain persistence and rehashed source tampering. Cached trainer tests reuse the existing curriculum test's genuine physical result. The new tamper replay hashes into solver shard 2; API startup stays in services. All existing class assignments, 35-minute backend limits and five-group v3 evidence accounting remain in place. The 25-minute Compose job also exercises the new startup and HTTP workflow.

The next frontend milestone is a separate `#interval-btn-flop` table view, with BTN as blue hero and BB as red villain, four-question navigation, retained per-question feedback, EV/loss on action buttons after choice, and retry/next/review controls beside the table. The current two-BB collection protocol and its saved identity remain unchanged. A new collection version can later combine those two contexts with these four BTN questions.
