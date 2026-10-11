# One qualified interval drill

The offline backend can now turn an owned [interval-feedback qualification](sixmax-interval-feedback-qualification.md) into a playable question, grade each legal move and save/replay explicit attempts. The [saved drill](data/supplied-conditional-five-target-99-interval-drill.json) contains the actual six-seat table plus examples for fold, call and raise-to-50.

The format is `pokerlab-supplied-interval-drill/v1`, with publication status `SELECTED_QUESTION_INTERVAL_DRILL_RESEARCH_ONLY`, selection `SINGLE_QUALIFIED_SELECTED_QUESTION/v1` and `trainerAdmission=false`. It has **one independently qualified question**, with two active players. Repeating it does not increase scenario coverage. The offline drill also has a separately enabled research HTTP boundary. It does not certify a public trainer pack or a six-player cash equilibrium.

## The question

BB holds 9♦9♥. UTG, HJ and CO folded, BTN opened to 3 bb, SB folded, BB raised to 9 bb, and BTN raised to 22 bb. BB acts with 91 bb remaining; BTN has 78 bb. The pot is 31.5 bb including the folded SB's 0.5 bb, and calling costs 13 bb. Legal actions are fold, call and raise to 50 bb total.

`SixMaxSuppliedIntervalFeedback.solve` derives this context from the selected state of the already enumerated physical game. It checks actor, own cards, information-set identity, commitment and legal-action order against the owned evidence. It replays the complete public history into the shared six-seat snapshot. The existing saved qualification report schema and bytes remain unchanged; full replay derives the context again.

The public question binds the exact qualification report hash, information set, declared security slack, reach floor and educational loss limit. It carries supplied-belief/model provenance and unavailable source-policy history reach. Only hero's private cards are exposed. It excludes the joint prior, opponent/folded cards, payoffs, posterior, policies and raw LP diagnostics.

## Grading and review

`new SixMaxSuppliedIntervalTrainer(ownedResult)` requires an opaque qualified result from a complete solve or physical replay. Deserializing a report with `qualifiedForIntervalFeedback=true`, or supplying a matching-looking report hash, cannot construct that result. Both excluded-reference and nonmaterial results are rejected. Existing scalar trainer admission remains unchanged.

`question()`, `grade(question, action)`, `review(question, actions)` and drill replay use immutable cached question/move evidence. They neither enumerate boards nor call a solver. A submitted question must equal the canonical question in every field; legal actions are checked against the owned moves.

Each grade contains the selected action's incremental EV interval and joint decision-loss interval, the declared 0.01 bb loss limit, and all alternatives for a future action-button UI:

| Action | EV interval (bb) | Loss interval (bb) | Result |
| --- | ---: | ---: | --- |
| Fold | approximately 0 | 9.111777–9.112981 | `OUTSIDE_LIMIT` |
| Call | 9.111777–9.112981 | 0–0.000450 | `WITHIN_LIMIT` |
| Raise to 50 | 8.627219–9.112981 | 0–0.484558 | `STRATEGY_DEPENDENT` |

Within-limit means the entire loss interval meets the limit. Outside-limit means the entire interval exceeds it. Strategy-dependent means the available evidence straddles the limit; it is an uncertainty result, not a binary wrong answer. There is no midpoint EV, inferred action frequency, scalar accuracy or sum advertised as realized hand loss.

Reviews accept zero to 100 explicit attempts at this same question. Zero attempts saves a question ready for practice. Each attempt stores its index, action and complete feedback. Counts distinguish all three classifications; repeated attempts remain repetitions of one question. Replay reconstructs every grade and count from the owned qualification and compares the entire review. Altered question fields, grades, alternative ranges, action/index associations or counts fail replay.

## Run the backend demo

Use Java 21 and the existing solver/engine/Jackson classpath. The main class is `com.pokerlab.solver.SixMaxSuppliedIntervalTrainerMain`:

```text
create <request.json[.gz]> <report.json[.gz]> <new-drill.json[.gz]> [action ...]
replay <request.json[.gz]> <report.json[.gz]> <drill.json[.gz]>
```

For the checked-in scenario:

```text
java -Xmx2g -cp <classpath> com.pokerlab.solver.SixMaxSuppliedIntervalTrainerMain create \
  docs/data/supplied-conditional-five-target-99-interval-feedback-request.json \
  docs/data/supplied-conditional-five-target-99-interval-feedback-report.json \
  <new-drill.json> fold call raise:50.0

java -Xmx2g -cp <classpath> com.pokerlab.solver.SixMaxSuppliedIntervalTrainerMain replay \
  docs/data/supplied-conditional-five-target-99-interval-feedback-request.json \
  docs/data/supplied-conditional-five-target-99-interval-feedback-report.json \
  docs/data/supplied-conditional-five-target-99-interval-drill.json
```

Each command first fully replays qualification **once**: all 17,766,216 physical boards, the owned LPs and fresh reference strategies. Startup therefore takes time. Subsequent question/grade/review operations in the constructed adapter use only cached evidence. Create requires a new output path; all input/output paths must be distinct. Strict JSON/gzip loading rejects unknown/duplicate fields, trailing values, null primitive values and scalar coercions. Saved drills have a 256 KiB compressed and expanded cap. Writing validates the review against the owned trainer and never overwrites an existing file.

## Trusted opt-in HTTP boundary

The API leaves this drill disabled by default. Enabling it requires three operator settings: a local request path, a local report path and the exact canonical report hash. `SixMaxSuppliedIntervalTrainerLoader` reads strict bounded evidence and checks the pin before costly replay, physically replays qualification once, constructs the owned trainer and checks the resulting owned hash again. A caller-rehashed `qualified=true` report still fails physical replay. Missing settings, mismatched pins, invalid evidence or an unqualified result fail startup. Files are read only.

With the normal local API database/queue configuration already in place, set these environment variables before starting the API:

```powershell
$env:TRAINER_INTERVAL_RESEARCH_ENABLED = 'true'
$env:TRAINER_INTERVAL_REQUEST_PATH = (Resolve-Path 'docs/data/supplied-conditional-five-target-99-interval-feedback-request.json').Path
$env:TRAINER_INTERVAL_REPORT_PATH = (Resolve-Path 'docs/data/supplied-conditional-five-target-99-interval-feedback-report.json').Path
$env:TRAINER_INTERVAL_REPORT_HASH = '5fb55653ae5f53d0110cea8507be9a37a6af2fe5cc596f433aaab0838b6b0aa9'
```

The corresponding Spring settings are `pokerlab.trainer.interval-research-enabled`, `interval-request-path`, `interval-report-path` and `interval-report-hash` under the same `pokerlab.trainer` prefix. No fixture or pin is bundled as an automatically enabled service. Normal startup with the flag absent/false creates no interval trainer bean and registers no interval routes. Enabled startup performs the physical solve before readiness; budget memory/time for that explicit research operation.

| Method and path | Contract |
| --- | --- |
| `GET /api/v1/trainer/research/interval` | Report identity, research provenance, one available question, two active players and attempt/loss limits. |
| `GET /api/v1/trainer/research/interval/question` | Canonical six-seat public question, with only hero's private cards. |
| `POST /api/v1/trainer/research/interval/grade` | Submit `{ "reportHash": "<64-character pin>", "action": "call" }`; receive the selected interval grade and all alternatives. |
| `POST /api/v1/trainer/research/interval/review` | Submit `{ "reportHash": "<pin>", "actions": ["fold", "call", "raise:50.0"] }`; receive the complete saved-review shape with all canonical grades and separate counts. Zero to 100 attempts are accepted. |

Responses use `Cache-Control: no-store`. Stale/malformed hashes, illegal moves, missing/null/oversized actions and unknown fields such as submitted EVs or private cards return HTTP 400. HTTP requests accept no artifact paths, reports, qualification booleans or caller-computed grades. The controller retains only the cached trainer; it never calls the loader, solver or board evaluator. Review JSON can be saved and verified with the offline replay command. This route does not persist account history or enable the existing scalar research trainers.

Tests start the real configured loader and exercise all four routes, all three classifications, canonical ranges, review counts, malformed submissions and absent routes/beans without opt-in. Separate loader tests verify immutable inputs and refusal of a forged, rehashed qualified report.

For the explicit local container demo, the separate overlay mounts the two checked-in evidence files read only, pins the report and gives the research API a 2 GiB heap:

```text
docker compose -f docker-compose.yml -f docker-compose.trainer-interval.yml up --build -d
python scripts/smoke-interval-trainer.py
```

This overlay enables the interval drill independently of the existing scalar trainer overlay. CI checks default-disabled routes first, then restarts the actual API with the interval overlay and compares the entire question, all three grades and review with the checked-in drill. It also checks stale hashes, illegal actions, submitted EVs and oversized reviews over real HTTP. Docker execution is verified on the Linux CI runner.

## Table UI and replayable attempts

Open `#interval-preflop` from the solver section after explicitly enabling the API above. The page pins the currently loaded canonical report in `#interval-preflop?report=<hash>`. A stale link, changed hash or inconsistent response blocks practice and offers an explicit current-report reset. A default-disabled server shows an unavailable state; the UI does not substitute fixture answers or enable server settings.

Six public seats show stacks, contributions, folds and last moves. Only hero's cards are revealed. Choose Fold, Call or Raise to 50 bb using the buttons beneath hero (or keyboard 1–3 when focus is outside another control). After grading, every button shows its EV and jointly computed loss range. Feedback beneath the controls distinguishes within-limit, outside-limit and strategy-dependent moves. Ranges are rounded outwards with useful precision; expanding the explanation exposes the exact returned endpoints. No midpoint EV, scalar score or policy frequency is invented.

“Try another move” retries this **same question**. The page retains at most 100 explicit attempts in memory and prevents pending or repeated clicks from adding duplicates. “Review attempts” sends only the report pin and action list; the returned canonical question, grades, order and separate counts must agree with the observed attempts before export becomes available. “Download replayable review” saves that server-returned JSON in the offline CLI's existing review schema. Reloading or explicitly resetting the drill clears the in-memory attempts. Account persistence is not implemented.

Frontend tests use the actual checked-in physically replayed drill, cover all three verdicts, public-seat privacy, finite/ordered ranges, stale/malformed responses, zero and 100 attempts, asynchronous cancellation, keyboard guards and JSON download. Existing scalar trainer pages and admission gates remain independent.

## Next coverage

A broader collection needs independently qualified questions and an identity binding its ordered reports and selection settings. Random street/full-hand practice needs that additional coverage first.

Supplied synthetic priors, exactly two active players, mandatory postflop checkdown, unavailable source-policy history reach and no rake remain limitations. Strategic postflop, broader preflop coverage and multiway equilibrium remain separate work. AWS deployment remains paused.
