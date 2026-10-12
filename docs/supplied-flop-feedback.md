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

Next: expose this cached owned drill through an explicitly enabled, operator-pinned API, then place board, six seats, action bounds and immediate feedback together in the existing table UI. Broader numerical conditioning, later histories, partially blocked support, wider ranges, rake, turn/river betting and truly multiplayer equilibrium remain separate work. AWS deployment remains paused.
