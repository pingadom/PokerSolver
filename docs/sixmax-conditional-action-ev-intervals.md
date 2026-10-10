# Conditional EV intervals after a re-raise

PokerLab's owned solver now bounds every move at a later information set, allowing the opponent's earlier actions to change both the probability of reaching that decision and the hidden-hand posterior. This extends the [root-only diagnostic](sixmax-root-action-ev-intervals.md). A fixed initial card distribution would give the wrong later EV.

The result is `POSITIVE_REACH_CONDITIONAL_DIAGNOSTIC_ONLY`, with `trainerAdmission=false`. The global security slack is explicitly 1e-8 bb and the existing owned numerical certificate tolerance remains 1e-8. These are numerical bounds on an approximate security face, not symbolic bounds on the exact Nash set. Existing reference comparisons, feedback gates and trainer policies are unchanged.

## Saved physical example

The [request](data/supplied-conditional-five-target-99-decision-request.json) and [report](data/supplied-conditional-five-target-99-decision-report.json) use the existing 27-world supplied joint-prior game: BTN opens to 3 bb, BB raises to 9 bb with 9♦9♥, and BTN re-raises to 22 bb. The staged targets are `[3,9,22,50,100]` bb. Three BTN hands, three BB hands and three folded HJ hands preserve all twelve dealt blockers in every world. Exact showdown payoffs enumerate 17,766,216 boards / 35,532,432 hand evaluations once for all moves.

| BB move | Incremental EV interval (bb) | Conservative decision-loss interval (bb) |
| --- | ---: | ---: |
| Fold | approximately 0 | 9.112156463–9.112156584 |
| Call | 9.112156463–9.112156584 | 0–0.000000121 |
| Raise to 50 bb | 8.628929684–9.112156584 | 0–0.483226900 |

Incremental EV adds BB's **actual 9 bb already committed at this decision**, derived from the public betting state. Adding only the initial 1 bb blind would be wrong. The tiny call interval reflects the declared global security slack and changing posterior; the much wider raise interval reflects different globally near-optimal opponent continuations. Because the intervals overlap, the report does not label calling uniquely dominant at the 1e-8 certificate tolerance.

Decision-loss bounds use differences of separate action intervals, excluding the selected action itself. They are conservative, **not jointly optimized regret extrema**. A small conservative loss bound does not qualify this richer scenario for the existing trainer: its separate alternative-action EV stability requirements still apply.

The minimum chance/opponent mass of this question over the complete numerical face is approximately 0.222222037, with maximum 0.222222231. A conservative dual lower bound subtracts 1e-8 and still exceeds the declared 0.01 floor. This is conditional reach **inside the supplied-prior game** with hero's past moves fixed. It is not source-policy reach for the earlier six-seat history; that remains `UNAVAILABLE_NO_SOURCE_POLICY`.

## Owned algorithm

`FiniteTwoPlayerConditionalActionIntervals` validates and copies the complete game once, then uses only the immutable snapshot. Perfect recall ensures every node in the question shares hero's past own actions. Their common probability cancels on conditioning, so the diagnostic fixes those actions even if the baseline hero never plays them. Chance weights and the opponent's earlier action likelihoods remain.

For original opponent realization `q`, question reach `d(q)` and each forced-action complete hero continuation numerator `n_j(q)` are linear. A descendant opponent sequence already contains its ancestor reach: each contribution uses the **last own sequence mass once**, never the product of ancestor and descendant realization masses. Hero continuation chooses one action per information set across hidden worlds, with at most 64 complete plans.

The solver first minimizes and maximizes `d(q)` over the original global opponent security face. It rejects the request if the dual-certified lower bound, with conservative numerical padding, falls below the declared positive floor. It **does not remove low-reach strategies** to manufacture an interval. A numerical or work rejection grants no result and does not prove mathematical infeasibility.

For affine opponent variables `w` and security dual variables `μ`, write the original face as `A(w,μ) ≤ b`, with nonnegative variables. Once positive reach is certified, the Charnes–Cooper substitution is:

```text
t = 1 / d(w)
w' = w * t, μ' = μ * t
A(w',μ') - b*t ≤ 0
d0*t + dCost·w' = 1
0 ≤ t ≤ 1 / declared minimum reach
n_j(w) / d(w) = n0_j*t + nCost_j·w'
```

The upper endpoint maximizes each complete continuation and takes the largest optimum. The lower endpoint minimizes their maximum. It represents the latter with a nonnegative variable `s = shift - conditional utility`, where `shift = maximum absolute terminal hero utility + 1`, and maximizes `s` subject to every plan being at most `shift-s`. This equivalent upper-shift epigraph avoids extra negative-right-hand-side artificial variables; the initial lower-shift formulation passed analytic games but was numerically rejected on the physical example. No LP tolerance, pivot rule or existing shared solver implementation was changed to accept it.

Every successful witness reconstructs the original unscaled opponent flow and behavior. Independent traversals check global best-response security, actual question reach, the full action-conditioned posterior, its agreement with realization weights, and conditional information-set best responses against all compiled continuation plans. Zero-probability worlds stay in the posterior audit; only positive worlds become chance children in the conditional response game. Each LP's primal/dual points, certificates, scaling factor, original variables and residuals remain available for replay.

All reach controls and interval solves share the existing compiler limit of 8 million units, 10,000 LP pivots and four billion LP arithmetic units. All moves share those budgets; baseline affine solves retain their separately declared existing budgets. The saved all-moves result uses 13 LPs, 303 pivots, 7,081,033 LP arithmetic units and 58,500 compiler units. Upper controls have 22 variables; the raise's lower epigraph has 23 variables and 27 constraints. Tree, sequence, information-set, dimension and expanded 8 MiB byte caps are unchanged.

## Replay and independent verification

With Java 21, Maven and the solver/engine/Jackson classpath, run:

```text
com.pokerlab.solver.SixMaxSuppliedConditionalDecisionIntervalsMain
  solve|replay <request.json[.gz]> <report.json[.gz]>
```

Solve requires a new output path. Strict JSON/gzip replay requires distinct paths and matching request lineage, reenumerates every board, and recomputes all LPs, original flows, posteriors, responses and summary bounds. Rehashing edited physical counts does not bypass replay. Edited posterior probabilities and LP certificates also fail. Only the owned solve/replay path creates the opaque result handle; reports grant no trainer policy.

The optional [independent oracle](../scripts/check-supplied-conditional-intervals.py) builds a literal **729-by-5,832** complete-plan game using saved integer showdown counts, without calling production betting, sequence-form, LP or response code. NumPy/SciPy are optional offline research dependencies, not runtime or CI dependencies. It independently solves the full-face reach extrema and conditional ratios, then converts every owned opponent flow to a complete-plan mixture and checks its security, EV, reach and posterior. All three intervals and all 13 witnesses agree within 2e-8; the [saved summary](data/supplied-conditional-five-target-99-independent-check.json) binds the report bytes and source input.

```text
python scripts/check-supplied-conditional-intervals.py \
  docs/data/supplied-conditional-five-target-99-decision-report.json \
  <new-oracle-summary.json>
```

Eleven analytic JVM tests cover changing posteriors, either hero orientation, hidden-information continuation, repeated opponent actions, unreachable questions, conservative floor certification, zero-probability worlds, fixed hero history, root equivalence, immutable input snapshots and aggregate work caps. Nine physical/report tests cover actual commitment, independent endpoint controls, every posterior, all-move caps, exact gzip replay, immutable handles, strict loading and corrupted evidence.

The [joint conditional decision-loss extension](sixmax-joint-conditional-decision-loss.md) now optimizes action differences against the same opponent strategy, with separate replayable diagnostics. The conservative intervals above retain their identity and values. Next design ambiguity-aware feedback qualification that distinguishes robust action quality from unique alternative EV, while preserving the current admission rules. Broader useful scenarios, full cash ranges, rake, physical postflop coverage and six-player equilibrium remain separate work. This solver still has exactly two active decision makers at a six-seat table and mandatory postflop checkdown. AWS deployment remains paused. No upstream library defect was found in this batch.
