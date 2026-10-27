# Reachable BB responses from explicit bet-size models

PokerLab now has two qualified backend questions for a pot-size flop bet on 6d Qc Td: call with BB99 and fold with AcJc after BB checks and BTN bets. The two questions are available through an owned cached drill and canonical four-attempt review. Website/API integration is a later step; older trainer routes and practice histories remain unchanged.

The source still has two active players at a six-seat table, the same supplied 24-world joint prior, all twelve dealt-card blockers, no rake, and one flop bet followed by exact checkdown. These are strategies for explicitly bounded games, not general six-active-player cash charts.

## Screen the necessary conditions before training references

The previous half-pot source separated some conditional choices but failed actual reach. `SixMaxBigBlindPrimaryScreen` now checks all contexts against the mandatory primary-policy gates before spending work on fresh CFR references. It builds one owned game, computes all six interval contexts and pairwise moves, then checks full-parent/local quality, actual and counterfactual reach, face inclusion, EV/regret coverage and action separation.

It exposes no grading method or qualified-question capability. Its result can only say that a question passes the necessary primary conditions; fresh qualification is still required. Reading an edited JSON snapshot cannot create an owned result. Numerical failures, insufficient reach and ambiguous decisions remain in the complete ordered inventory. The CLI labels `inspect` output as an unverified stored snapshot and requires full owned equality on `replay`.

Two models were declared before execution, without changing cards, priors, algorithms or accuracy gates:

| Model | Bet after the 6.5bb initial pot | Primary-eligible source indices | Fresh runs reserved |
|---|---:|---|---:|
| Quarter-pot | 1.625bb | None | 0 |
| Pot-size | 6.5bb | 3 (BB99), 5 (AcJc) | 2 |

All twelve contexts remain saved. The quarter-pot responses still fail actual reach and selected regret after a BB check; its initial decisions lack a robust outside-limit alternative. That model was not sent through another long reference batch. This records a result under the declared models, rather than asserting impossibility for every other poker game.

## Fresh qualification of the pot-size game

The pot-size source retains all six contexts, trains fresh CFR+ references at 80,000 and 160,000 iterations, and checks the primary and both references for every context: eighteen assessments. Only source indices 3 and 5 pass every existing gate. The other four remain explicit rejections for lacking a robust outside-limit alternative.

| Qualified question | Correct action | Call EV interval, absolute bb | Fold EV | Actual question reach in fresh policies |
|---|---|---:|---:|---:|
| BB99 after check / BTN bet | Call | -2.625987 to -2.624551 | Approximately -3 | Approximately .267 |
| AcJc after check / BTN bet | Fold | -4.203426 to -4.201684 | Approximately -3 | Approximately .267 |

EV includes earlier committed chips. The pot is 13bb when the question is presented, the outstanding bet and amount to call are both 6.5bb, and BB has already committed 3bb. Counterfactual hero-past cancellation remains separate from actual reach. No threshold was relaxed: the reach floor remains .0001, decision loss tolerance .01bb, full-parent/reached-flop gap .001bb and original numerical security slack 1e-8.

These are different betting games from the half-pot source. They have different parent strategies, security faces, EVs and report hashes even when the private cards, board and history labels match. Sources and cached questions must use the complete report pin; card labels alone are not an identity.

## Cached questions, grades and review replay

`SixMaxBigBlindPairwiseDrill` consumes the existing private, owned qualification result. Its question indices 0 and 1 map to source indices 3 and 5. Public questions contain the hero's cards and the actual six-seat table context; they exclude opponent private cards, joint priors and solver policies. Grades and review verification use the in-memory result and perform no LP or CFR work.

The canonical review contains both legal actions for each question: four attempts, two within limit and two outside limit. Reviews preserve the report pin, dense question index, source index, actual pot/call prices, selected move and alternatives. Strict review parsing has a 1MiB stored/expanded cap and 100-attempt bound. Complete cached regrading rejects edited EVs, prices, cards, identities or summaries. A parsed review is data, not grading authority.

The offline mains support:

```text
SixMaxBigBlindPrimaryScreenMain solve|replay <BB-v2-request> <screen.json.gz>
SixMaxBigBlindPairwiseFeedbackMain solve <feedback-request> <new-source.json.gz>
SixMaxBigBlindPairwiseFeedbackMain solve-review <request> <new-source.json.gz> <new-review.json> [questionIndex:action ...]
SixMaxBigBlindPairwiseFeedbackMain replay-review <request> <saved-source.json.gz> <new-review.json> [questionIndex:action ...]
```

The classes are in `com.pokerlab.solver`. Without explicit choices, review modes export every legal action of every qualified question. Output/alias, attempt-count and malformed-choice checks precede expensive work. `solve-review` shares one owned result between source output and review; `replay-review` performs one complete fresh source replay before exporting cached grades. If no question qualifies, no review authority is created. Source/report and review output are not an atomic pair; a failed review can leave the source diagnostic intact.

## Evidence and checks

Predeclared budgets cover two screen builds/twelve contexts, at most 36 parent solves/24 action computations and no fresh CFR runs. Pot-size qualification separately reserves one build, two fresh runs/240,000 iterations and eighteen assessments. The canonical review was generated from one separately reserved full source replay; its four grades then use caches. All original compiler, LP, byte and numerical limits remain in place.

The new literal checker uses each model's declared fraction and stack cap at all four preflop boundaries. It independently regenerates the original physical counts, reconstructs each complete 1,321-state/73-information-set tree, checks both parent certificates per model and all 102 action endpoint certificates, then recomputes every primary gate. Separate checks recompute all eighteen fresh pot-size assessments and regrade the public review, including price and source identity. Independent checkers perform no LP/CFR optimization; complete Java replay establishes fresh training.

Java tests replay both models and the fresh qualified source, check correct call/fold behavior and rejected indices, verify source-bound prices and review round trips, exercise 100 cached attempts and reject forged review content. Python tests mutate source fractions, prices, EVs, reach, policies, failures, eligibility, budgets and reviews, including duplicate/nonfinite/trailing JSON and gzip expansion attacks.

CI assignment also changes under manifest v5. Three complete collection replay suites previously collided in `solver-3`; each now runs on a different lighter shard. The same eight solver/two service jobs, 35-minute limit, full automatic class inventory and aggregate evidence checks remain. No test is excluded, and old assignment manifests are rejected. Tests verify disjoint complete coverage and the explicit expensive-suite allocation.

All 332 older evidence files are preserved. AWS deployment remains paused. The next product step is integrating these qualified backend questions into a separately pinned collection/API and an actor-aware table UI.
