# Joint conditional decision-loss bounds

PokerLab now bounds the loss of each legal move against the **same opponent strategy**, including its effect on reaching a later decision and on the hidden-hand posterior. Separate action EV intervals can exaggerate loss because their endpoints may come from different opponent strategies. The new owned solver optimizes the difference directly.

This is `JOINT_CONDITIONAL_LOSS_DIAGNOSTIC_ONLY`, with `trainerAdmission=false`. It extends the [conditional action-EV backend](sixmax-conditional-action-ev-intervals.md), retains its complete numerical security face and positive-reach requirement, and leaves existing trainer admission rules unchanged. Exactly two players make decisions at a six-seat table; this does not certify a six-player cash equilibrium.

## Saved poker example

The [request](data/supplied-conditional-five-target-99-joint-loss-request.json), [report](data/supplied-conditional-five-target-99-joint-loss-report.json) and [independent check](data/supplied-conditional-five-target-99-joint-loss-independent-check.json) use the existing 27-world supplied-prior game. BTN opens to 3 bb, BB with 9♦9♥ raises to 9 bb, and BTN raises to 22 bb. The staged targets are `[3,9,22,50,100]` bb. All twelve dealt cards, including folded players' cards, block the 17,766,216 enumerated boards.

| BB move | Incremental EV interval (bb) | Joint decision-loss interval (bb) | Separate-EV upper loss (bb) |
| --- | ---: | ---: | ---: |
| Fold | approximately 0 | 9.112156463–9.112156584 | 9.112156584 |
| Call | 9.112156463–9.112156584 | 0–0.000000045 | 0.000000121 |
| Raise to 50 bb | 8.628929684–9.112156584 | 0–0.483226780 | 0.483226900 |

Call's upper loss falls by about 62.5%. Its numerical upper bound still exceeds the unchanged 1e-8 certificate tolerance, so no move is marked robust at that tolerance. Raising can be equally good under some opponent continuations and about 0.483 bb worse under others. A small aggregate equilibrium gap alone cannot supply a unique EV for every alternative.

EV adds the actual **9 bb committed at this decision**, derived from the public betting state. The common sunk amount cancels in comparisons. Reach is certified over the whole original face before conditioning; its minimum is about 0.222222037, above the declared 0.01 floor. This is chance/opponent reach in the supplied game, with hero's own past fixed. Earlier source-policy history reach remains `UNAVAILABLE_NO_SOURCE_POLICY`.

## How the joint calculation works

Let `X` be the existing Charnes–Cooper scaled security face. For action `a`, each complete hero continuation plan `j` has a linear conditional utility `l[a,j](x)`. Hero must choose one continuation action at each information set across all hidden worlds. It cannot choose separately after seeing the concealed opponent hand.

```text
F_a(x) = max_j l[a,j](x)
R_s(x) = max(0, max_{a != s} F_a(x) - F_s(x))
```

The algorithm finds the minimum and maximum of `R_s` on the **same** `X` for every selected move `s`.

For the lower endpoint, enumerate each selected continuation `j`. Solve an LP minimizing `v >= 0` with `l[b](x) - l[s,j](x) <= v` for every complete plan `b` of every other action. Take the smallest optimum. The identity is:

```text
min_x R_s(x) = min_j min_x max(0, max_b(l[b](x) - l[s,j](x)))
```

For the upper endpoint, enumerate each other-action plan `b`. Solve an LP maximizing a **signed** gap `g` with `g <= l[b](x) - l[s,j](x)` for every selected continuation `j`. Take the largest optimum, then clamp it at zero:

```text
max_x R_s(x) = max(0, max_b max_x min_j(l[b](x) - l[s,j](x)))
```

The owned nonnegative-variable LP represents `g = positive - negative`. Clamping each control by imposing `g >= 0` would wrongly reject controls whose best gap is negative. Shifting the gap passed small analytic controls but produced a rejected behavioral witness in the physical game. The signed representation passes both. No LP tolerance, pivot rule, work cap or flow-to-behavior validation was loosened.

`FiniteTwoPlayerConditionalDecisionLoss` copies the original game once. Existing per-action solves run on that immutable snapshot and provide their owned context; caller-created audits cannot grant solve provenance. All actions must have identical baseline, original/scaled face hashes, affine projections, denominator and fixed hero history. The context is internal and is not serialized into earlier reports.

Every joint LP retains its raw primal/dual certificate and point. A witness reconstructs the original opponent realization and behavior, checks global security and positive reach, checks behavioral versus realization posterior weights, then independently evaluates **every action's optimal information-set continuation** on that one point. The chosen lower and upper controls must attain their claimed endpoints. All controls' actual losses must lie inside the reported range. A single legal move has zero loss without invented joint certificates.

## Bounds, replay and tests

All action reach/EV controls and joint controls share the original limits: 8 million compiler units, 10,000 LP pivots and four billion LP arithmetic units. Baseline affine solves retain their separately declared existing budgets. Tree, sequence, continuation-plan, LP dimension and expanded 8 MiB report limits are unchanged. The saved result uses **25 LPs, 667 pivots, 17,535,247 LP arithmetic units and 76,150 compiler units**: 13 existing reach/EV controls plus 12 joint controls.

With Java 21 and the solver/engine/Jackson classpath:

```text
com.pokerlab.solver.SixMaxSuppliedConditionalDecisionLossMain
  solve|replay <request.json[.gz]> <report.json[.gz]>
```

Solve requires a new output path. Strict JSON/gzip replay checks distinct paths and exact request lineage, reenumerates physical boards, reruns every owned LP and witness, and compares the entire report. Editing counts and rehashing their binding still fails. Posterior, certificate, utility or summary changes cannot create an owned result handle.

The optional [independent oracle](../scripts/check-supplied-conditional-decision-loss.py) builds a literal **729-by-5,832 complete-plan** game from saved integer showdown counts. It uses separate contribution arithmetic and SciPy's LP solver, without production betting, sequence-form or response code. It independently optimizes all joint controls, reconstructs every owned opponent flow as a full complete-plan mixture, and checks original security, reach, posterior, every move's utility, planned gaps, actual loss and endpoint witnesses. The saved summary binds the expanded report bytes and source input. NumPy/SciPy are offline dependencies only.

```text
python scripts/check-supplied-conditional-decision-loss.py \
  docs/data/supplied-conditional-five-target-99-joint-loss-report.json \
  <new-oracle-summary.json>
```

Nine analytic tests cover common EV movement, negative signed gaps, both actor orientations, multiple continuations, concealed-world consistency, actual witness loss, unreachable questions, single-move questions, immutable snapshots and shared work limits. Nine physical/report tests cover independent extrema, the common posterior, actual commitment, aggregate caps, strict loading, gzip replay, modified physical evidence and opaque immutable results. Earlier conditional and root reports remain regression controls.

## Next milestone

The next backend step is now implemented as [selected-question interval feedback qualification](sixmax-interval-feedback-qualification.md). It uses separately identified objective conditioning, explicit fresh-reference inclusion and interval containment, while retaining this v1 report and the rejected old scalar study. Public trainer admission remains off; saved question/grade/review integration comes next.

Design a separately identified ambiguity-aware feedback qualification using robust **decision loss**, with explicit checks that fresh reference policies lie inside its declared security face. Preserve material-decision, posterior, source-provenance and reference-quality requirements. Keep the old scalar-EV stability rule and its rejected five-target studies intact; any new feedback format needs its own identity and replay evidence before serving questions.

This work adds solver diagnostics, not trainer content. Mandatory postflop checkdown, synthetic supplied ranges and no rake remain model limits. Broader realistic ranges, strategic postflop coverage and multiway equilibrium are separate milestones. AWS deployment remains paused. No upstream library defect was found in this batch.
