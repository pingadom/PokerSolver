# Four-question BTN flop trainer

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
| `POST /review/export` with the same pinned choices | Canonical replayable JSON download, using the solver serializer |
| `POST /review` with `{ "reportHash": "...", "choices": [{ "questionIndex": 0, "action": "check" }] }` | Ordered replayable mixed review |

Generated OpenAPI uses explicit, uniquely named JSON request schemas despite the strict parser. The live smoke checks those schemas. Success responses use `Cache-Control: no-store`. Choice bodies are bounded to 32 KiB, reject duplicate/unknown fields, scalar coercion and trailing JSON, and validate required pins, indices, menus and nested choices. Stale pins and invalid input return `400 INVALID_REQUEST`.

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

## Table practice

Open `#interval-btn-flop` after opting in locally. The page loads and validates all four questions before showing the table. BTN is the blue hero, BB is the red villain with a yellow checked outline, and the other seats are folded. Hand tabs and previous/next controls retain each hand's feedback; unplayed hands keep their answers hidden. After a choice, both buttons show certified EV/loss ranges and feedback appears below the controls. Retry preserves ranges already revealed. Keyboard 1 checks and 2 bets unless an interactive control has focus.

Links bind `report` and `question` (0–3). The browser rejects stale pins, incomplete inventories, inconsistent public histories, wrong-hand grades and edited reviews. Requests are cancelled on route changes, duplicate input is blocked, and all questions share the 100-attempt limit. Navigation, review and export remain available at the cap. No demonstration fixture substitutes for a disabled API or failed source.

Downloads post only the pinned question/action choices to `/review/export`. The cached trainer reconstructs the review and serializes it with the same bounded Java writer as the CLI. The browser validates the returned inventory, every captured grade and the counts, then downloads the original UTF-8 text. For the saved eight-choice sequence, tests and the live smoke require byte-for-byte equality with the owned review artifact. This avoids JavaScript reformatting Java numeric endpoints.

Frontend regression tests cover all four hands, privacy before a choice, retained feedback, direct links, strict protocol validation, cancelled/duplicate requests, forged exports and 0/100/101 bounds. CI compares the raw frontend fixture with the saved solver review before testing. The current two-BB collection protocol and its saved identity remain unchanged. Next is a separately versioned collection combining those two contexts with these four BTN questions.
