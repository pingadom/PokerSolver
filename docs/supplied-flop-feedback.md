# Qualified initial-flop feedback and offline drill

This milestone turns the [connected flop action bounds](supplied-flop-action-bounds.md) into one physically replayed offline question. The saved decision is BB holding `7d 7h` on `2s 8s Ts`, after calling BTN's 3bb open. Six seats are represented, four are folded, the pot is 6.5bb and the allowed bet is 3.25bb. This is a supplied-belief, two-active-player research model with one flop bet and exact later checkdown. It is not a general six-player cash equilibrium. `trainerAdmission=false` remains on every artifact.

## Qualification

The separately versioned [request](data/supplied-four-button-hand-flop-feedback-request.json) selects an initial flop information set and an educational maximum loss, capped at 0.01bb. An owned action-bounds solve supplies the unchanged complete-parent security face and certified minimum question reach. Qualification neither adds a reach constraint nor changes the numerical solver, certificate tolerance, original chance distribution, source payoffs or existing parent/bounds artifacts.

For each move, the existing pure classifier applies:

| Condition | Grade |
| --- | --- |
| Upper loss ≤ requested limit | `WITHIN_LIMIT` |
| Lower loss > requested limit | `OUTSIDE_LIMIT` |
| Otherwise | `STRATEGY_DEPENDENT` |

Qualification requires a robust within-limit move and a robust outside-limit alternative. It preserves all rejection reasons. There is no arbitrary EV-width cutoff. Separate endpoint bounds remain conservative envelopes; they are not exact joint regret bounds or action frequencies.

At the saved 0.01bb limit:

| Move | Absolute terminal profit (bb) | Conservative loss (bb) | Grade |
| --- | --- | --- | --- |
| Check | −3.00000001 to −2.99985179 | 0 to 0.00014822 | Within limit |
| Bet 3.25bb | −3.45138890 to −3.09177870 | 0.09177869 to 0.45153711 | Outside limit |

EV is absolute terminal chip profit, including the 3bb already committed. It is not an incremental return from this screen. `99` and `AcJc` at the same boundary retain ambiguous bet bounds and fail `NO_ROBUST_MOVE_OUTSIDE_LIMIT`; no trainer capability is created. Tightening the 77 limit to 0.00001bb fails `NO_ROBUST_MOVE_WITHIN_LIMIT`. Unknown hands, wrong actors and later flop histories are unsupported by this initial-question format.

## Strategy and reach checks

The owned primary policy and actual fresh 2,000/4,000-iteration CFR+ runs are assessed separately. Each check recomputes full-parent best responses, reached-flop quality, question reach, forced-action optimal future continuation values and mixed-current-action regret. Both full-parent and reached-flop gaps must be at most the existing 0.001bb threshold; selected regret must be at most 0.01bb. The complete-parent hero best response must lie in the declared security face, allowing the existing numerical certificate tolerance when comparing. An outside-face reference has `boundsChecked=false`, null maximum bound violation and an explicit rejection; it cannot certify the question by skipping comparisons.

Actual policy reach includes every preflop action. Counterfactual question reach cancels the hero's own past while retaining opponents' likelihoods. Both are labeled, with actual raw mass and physical-event-conditioned mass preserved. The physical flop probability is `1/9880`. The saved question has about `1/3` counterfactual conditional reach but only `1/29640` raw mass. The existing 0.0001 conditional reach floor is compared in physical-flop units; treating it as a raw-chance floor would incorrectly reject this question. The source public history remains supplied/conditioned: no unavailable source-policy reach is invented.

The saved fresh policies have positive original face inclusion margins and zero computed utility/loss envelope violations. Their 77 bet losses are approximately 0.45138750bb and 0.45138854bb. Those observed values verify the envelope; they do not replace it with a scalar grade.

## Ownership, grading and replay

Only a complete owned solve or complete physical replay constructs the private `Result` and `QualifiedFeedback` capability. A JSON boolean, caller-created report or matching hash cannot authorize grading. The six-seat table, cards, board, commitments, stacks, bet size and legal moves come from owned handoff states, not by parsing an opaque information-set string. No opponent private cards are included in the question context.

`SixMaxSuppliedFlopTrainer` caches one qualified question and its moves. Asking, grading and reviewing do not invoke the solver. It requires exact question equality, rejects illegal actions, and supports zero to 100 explicit attempts. Feedback retains all EV/loss ranges and alternatives. Review counts describe repeated attempts at this single question, not curriculum coverage or general poker accuracy. Replay regenerates every grade, index and count and compares the complete saved review.

`SixMaxSuppliedFlopTrainerLoader` checks an operator-supplied 64-character report hash before expensive replay, performs complete physical replay, requires owned qualification and rechecks the resulting owned hash. Rehashing an edited report does not bypass replay. Reports retain stored/expanded 8MiB limits; drill artifacts have a 256KiB limit. Plain and gzip output creation is exclusive.

The [saved qualification](data/supplied-four-button-hand-flop-feedback-report.json.gz) embeds the complete unchanged action-bound evidence. Its Java canonical report hash is `49b1be1e91bb0ae7cae6a3171f4e15e516fc0a52be80efabc6d281e8ec5863d8`. The [saved drill](data/supplied-four-button-hand-flop-drill.json) records one check and one bet, with one within-limit and one outside-limit attempt.

## Verification and commands

Run these Java entry points on the solver/engine runtime classpath:

```text
com.pokerlab.solver.SixMaxSuppliedFlopFeedbackMain solve <feedback-request> <new-feedback-report>
com.pokerlab.solver.SixMaxSuppliedFlopFeedbackMain replay <feedback-request> <feedback-report>
com.pokerlab.solver.SixMaxSuppliedFlopTrainerMain create <feedback-request> <feedback-report> <new-drill> check bet
com.pokerlab.solver.SixMaxSuppliedFlopTrainerMain replay <feedback-request> <feedback-report> <drill>
```

The [stdlib checker](../scripts/check-supplied-flop-feedback.py) supports the saved byte-pinned 77 case only. It checks the unchanged original 27 LP certificates through the existing action-bound checker, rebuilds the literal physical tree, independently evaluates all three policies, recomputes six forced-action continuation values, face inclusion, both reach units, table context, interval classification and every saved attempt. The Java canonical hash is tied to the fixed qualification's stored bytes; Python does not claim to reproduce Java's canonical numeric serialization. Java physical replay remains necessary and regenerates full preflop board counts, owned LPs, hashes and fresh reference runs. The [receipt](data/supplied-four-button-hand-flop-feedback-independent-check.json) binds checker/helper sources and request, report and drill bytes.

```sh
python scripts/check-supplied-flop-feedback.py docs/data/supplied-four-button-hand-flop-feedback-request.json docs/data/supplied-four-button-hand-flop-feedback-report.json.gz docs/data/supplied-four-button-hand-flop-drill.json --output <new-receipt.json>
python -m unittest discover -s scripts -p 'test_check_supplied_flop_feedback.py'
```

Java controls cover robust/ambiguous qualification, tightened loss limits, unknown hands/actors/histories, a genuinely outside-face policy, private constructors and immutable evidence, exact cached grades, review caps, altered context, wrong startup pins, rehashed report tampering, exclusive output and complete physical startup replay. Python corruption controls separately target physics, reach units, full-parent quality, action values, face status, grades, counts, indices and bounded parsing.

## Opt-in API

The separate `/api/v1/trainer/research/flop-interval` protocol is absent by default. Enabling `pokerlab.trainer.flop-interval-research-enabled` requires operator-configured request/report paths and the exact report hash. Configuration invokes the owned loader once before application readiness. HTTP requests never choose input files or start a solve.

| Method and route | Behavior |
| --- | --- |
| `GET /api/v1/trainer/research/flop-interval` | Model/chance/belief provenance, EV and reach units, report pin, one available question, two active players and attempt limit |
| `GET .../question` | Cached owned question with board and six-seat public context |
| `POST .../grade` | `{ "reportHash": "...", "action": "check" }`; returns canonical interval feedback |
| `POST .../review` | `{ "reportHash": "...", "actions": ["check", "bet"] }`; reconstructs the complete review |

All successful responses use `Cache-Control: no-store`. Stale pins, illegal actions, extra context/payoff/grade fields and malformed or over-100-attempt reviews return an invalid-request error. Responses never serialize the embedded solver report, private opponent cards, physical prior or LP witnesses. Metadata retains `UNAVAILABLE_NO_SOURCE_POLICY`; the small supplied model is not presented as a general cash strategy chart.

The [local Compose overlay](../docker-compose.trainer-flop-interval.yml) explicitly opts in with read-only fixture mounts and the canonical report pin:

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-flop-interval.yml up --build -d
python scripts/smoke-flop-interval-trainer.py
```

The API slice tests perform real pinned physical startup, compare both grades and the entire saved review, reject client-supplied facts, and verify default-disabled routes and missing settings. Compose CI independently checks default absence, then restarts with the real overlay and compares live question, grades and review to the saved fixture. Readiness controls distinguish temporary startup transport failures from an incorrect successful report pin.

## Table UI

The separate `#interval-flop` page now uses the cached protocol. It shows the three physical board cards, six seats, hero's cards, pot, public contributions and stacks. Hero stays blue, opponents red and folded players subdued; a check gets a yellow outline and a bet a red outline. Opponents wait on the flop rather than displaying an old preflop raise as a current-street action.

Both check and bet controls sit below hero. Choosing a move reveals the server's hand-EV and loss ranges on both buttons, with the verdict and retry/review controls immediately below. Retrying the same question retains the already revealed ranges. Hand EV is labeled as final profit including preflop commitments; separate-endpoint conservative loss bounds are explained without the older preflop view's incremental-EV or joint-regret wording. Exact server endpoints remain available in a disclosure, with outward-rounded displayed ranges.

The browser validates the model/chance/EV/reach identities, report pin, visible cards, public chip/history consistency, legal menu, feedback classification consistency and canonical review. It never chooses source files or computes a replacement solver grade. Stale URL pins, changed ranges, mismatched grades/reviews, illegal public contexts and unexpected hidden-data fields block practice. In-flight requests abort on navigation; duplicate click/keyboard submissions are guarded. Zero to 100 explicit attempts and exact server-review downloads match the offline protocol. A disabled backend shows an unavailable state, with no substitute fixture question.

The checked-in frontend test fixture must remain byte-identical to the physically replayed saved drill; CI enforces this separately from component/protocol tests. Live browser verification used the real pinned Java startup, played check and bet, and compared the downloaded two-attempt JSON to the complete saved review. Laptop checks keep the board, all seats, action ranges, retry control and feedback visible together at 1366×768; phone layouts retain legible cards and avoid horizontal overflow at 390px and 320px widths. Longer reviews and detailed explanations can scroll.

## Owned public context for later decisions

`SixMaxSuppliedFlopBettingGame.publicFlopState` derives a public table view for any owned active flop decision. It follows the game's action edges from the matching preflop handoff and uses the same bet-size calculation as settlement. It returns the board, acting seat, six player statuses, contributions, remaining stacks, pot, amount to call, legal menu and explicit public flop moves. It exposes no opponent hole cards or private world index, and never parses an information-set key to infer table state.

After BB checks and BTN bets 3.25 bb in the saved hand, the view reports BB to act, a 9.75 bb pot and 3.25 bb to call. BTN has committed 6.25 bb with 93.75 bb behind; BB has committed 3 bb with 97 bb behind. Tests compare all four active decision paths across every compatible hidden world at every handoff, check chip conservation, and reject foreign, preflop, chance and terminal states. Public collections are immutable.

## Later BB fold/call qualification

The separate `SixMaxLaterFlopFeedback` request/report qualifies BB holding 7d 7h after BB checks and BTN bets on 2s 8s Ts. The request selects the exact power-of-two interval algorithm before solving and names the public preflop handoff and flop actions. The owned game derives the information set and table context. The complete original parent game, private worlds, physical chance and initial-flop report remain unchanged.

The saved later decision has fold loss `[0, 0.00000002]` bb and call loss approximately `[0.448282, 0.451560]` bb. These are conservative separate-endpoint bounds on absolute final profit. At the explicit 0.01 bb educational limit, fold is robust within the limit and call is robust outside it. A tighter limit can reject the question. This is one supplied-belief research model, with no rake and one flop bet followed by exact checkdown.

Qualification computes actual reach along BB's preflop call, flop check and BTN's bet. Counterfactual reach cancels BB's own past while retaining BTN's bet. The primary policy and fresh independent 2,000/4,000-iteration policies must pass complete-parent and reached-flop quality, actual and counterfactual question reach, original-face inclusion, action-value/loss comparisons and selected-decision regret. An outside-face policy records unchecked bounds with a null violation and explicit failures. Parsed report booleans cannot create the private qualified capability.

The [request](data/supplied-four-button-hand-later-bb-flop-feedback-request.json), [report](data/supplied-four-button-hand-later-bb-flop-feedback-report.json.gz) and [independent receipt](data/supplied-four-button-hand-later-bb-flop-feedback-independent-check.json) are separate from the initial drill. The Java CLI can solve or completely replay the later report:

```text
com.pokerlab.solver.SixMaxLaterFlopFeedbackMain solve <later-request> <new-later-report>
com.pokerlab.solver.SixMaxLaterFlopFeedbackMain replay <later-request> <later-report>
```

The [stdlib checker](../scripts/check-later-bb-flop-feedback.py) supports this fixed saved case. It rebuilds the literal physical tree and original LP matrices, checks all eight endpoint certificates, maps their points into behavioral witnesses, recomputes full-parent security, posteriors and both path-reach measures, and verifies all three policies' action values and qualification. It runs no optimizer. The receipt binds checker/helper sources and stored request/report bytes; Java's canonical report hash still requires owned physical replay.

```sh
python scripts/check-later-bb-flop-feedback.py docs/data/supplied-four-button-hand-later-bb-flop-feedback-request.json docs/data/supplied-four-button-hand-later-bb-flop-feedback-report.json.gz --output <new-receipt.json>
python -m unittest discover -s scripts -p 'test_check_later_bb_flop_feedback.py'
```

## Later BB trainer backend

`SixMaxLaterFlopTrainer` consumes only the private capability returned by an owned later-decision solve or complete replay. Its separate `pokerlab-later-bb-flop-drill/v1` protocol caches one fold/call question, with hero cards, the owned public table and betting history, the report pin, model/chance/belief provenance, EV and reach units, security slack, reach floor and educational loss limit. Public questions contain no opponent hole cards, physical prior, reference strategies or LP witnesses.

Grading requires the exact cached question and a legal fold/call action. Feedback includes the selected move and both certified alternatives. Reviews reconstruct zero to 100 explicit attempts and compare every question field, grade, index and count on replay. Counts describe retries at one selected question; they are not a measure of general poker accuracy or curriculum coverage. The [saved drill](data/supplied-four-button-hand-later-bb-flop-drill.json) contains one fold and one call. Plain/gzip artifacts have stored and expanded 256 KiB bounds and exclusive output creation.

`SixMaxLaterFlopTrainerLoader` requires operator-configured request/report paths and exact Java report pin `4526ff928e05bc0e962d78661a19b82cb127d5e058f7a25bb8fd1e393c51f7ea`. It checks the pin before expensive work, performs complete owned physical replay, requires qualification, and checks the owned pin again. Before/after file snapshots include resolved paths, file identities, modification times, sizes and stored-byte hashes. Changed startup files fail closed. Matching a caller-rehashed edited report never bypasses physical replay.

```text
com.pokerlab.solver.SixMaxLaterFlopTrainerMain create <later-request> <later-report> <new-drill.json[.gz]> fold call
com.pokerlab.solver.SixMaxLaterFlopTrainerMain replay <later-request> <later-report> <drill.json[.gz]>
```

The [independent drill checker](../scripts/check-later-bb-flop-drill.py) first runs the complete fixed-case report checker, including the eight original endpoint certificates and all three policies. It then checks the exact question, legal actions, every feedback field and repeated-attempt counts. Its [receipt](data/supplied-four-button-hand-later-bb-flop-drill-independent-check.json) binds the drill bytes, checker source and full report-check receipt. Twenty-five additional corruption controls target question/provenance/context, selected and alternative ranges, action order, indices, pins, hidden data and counts. The existing report, original report receipt and initial check/bet artifacts retain their exact bytes.

```sh
python scripts/check-later-bb-flop-drill.py docs/data/supplied-four-button-hand-later-bb-flop-feedback-request.json docs/data/supplied-four-button-hand-later-bb-flop-feedback-report.json.gz docs/data/supplied-four-button-hand-later-bb-flop-drill.json --output <new-receipt.json>
python -m unittest discover -s scripts -p 'test_check_later_bb_flop_drill.py'
```

The separate `/api/v1/trainer/research/later-flop-interval` API is absent by default. Enabling `pokerlab.trainer.later-flop-interval-research-enabled` requires the request path, report path and report hash settings. Startup completes physical replay before the bean becomes ready. HTTP requests use immutable cached evidence and cannot select files or launch solver work.

| Method and route | Behavior |
| --- | --- |
| `GET /api/v1/trainer/research/later-flop-interval` | Report pin, research status, model/chance/belief provenance, units and attempt limit |
| `GET .../question` | Owned BB fold/call question with six seats, board and public betting history |
| `POST .../grade` | `{ "reportHash": "...", "action": "fold" }`; returns exact cached interval feedback |
| `POST .../review` | `{ "reportHash": "...", "actions": ["fold", "call"] }`; rebuilds the canonical review |

All successful responses use `Cache-Control: no-store`. Stale pins, unknown actions, additional context/EV/grade fields and malformed or over-100-attempt reviews fail validation. API tests use real pinned physical startup, compare the complete saved review and both grades, and verify disabled routes. Compose CI checks default absence, starts the read-only pinned overlay, then compares live metadata, question, both grades and review against the saved artifact.

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-later-flop-interval.yml up --build -d
python scripts/smoke-later-flop-interval-trainer.py
```

The initial check/bet API and table UI remain independently versioned. Next, connect the later fold/call API to the table UI, then consider an explicitly selected collection containing both independently qualified decisions. These intervals do not establish a coherent full-hand policy. Partially blocked support, wider ranges, rake, turn/river betting and truly multiplayer equilibrium remain separate work. AWS deployment remains paused.
