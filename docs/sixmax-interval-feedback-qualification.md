# Selected-question interval feedback qualification

PokerLab can now qualify one later preflop question for **interval-based feedback** while retaining the rejected result of the old scalar-EV study. A move is judged by its worst and best decision loss against the same opponent strategy and action-conditioned posterior. A wide interval is reported as strategy-dependent; it is not assigned a single supposedly precise EV.

This is a backend research format: `SELECTED_QUESTION_INTERVAL_FEEDBACK_RESEARCH_ONLY`, with `trainerAdmission=false`. It supplies an opaque qualified result for a future question/grade/review integration. It does not register a new public trainer pack, certify the whole study, or solve a six-player cash equilibrium. Exactly two players are active at a six-seat table.

## Saved question

The [request](data/supplied-conditional-five-target-99-interval-feedback-request.json), [report](data/supplied-conditional-five-target-99-interval-feedback-report.json) and [independent check](data/supplied-conditional-five-target-99-interval-feedback-independent-check.json) use the existing 27-world supplied-prior game. BTN opens to 3 bb, BB with 9♦9♥ raises to 9 bb, and BTN raises to 22 bb. Allowed targets are `[3,9,22,50,100]` bb. The exact payoff table enumerates 17,766,216 boards, retaining all twelve dealt cards as blockers.

The request explicitly declares **0.0001 bb security slack**, a 0.01 minimum chance/opponent reach, and a **0.01 bb educational loss limit**. These are different quantities: security slack defines the approximate opponent face; the reach floor permits conditioning; the loss limit defines feedback. Numerical certificates still use the existing 1e-8 tolerance.

| BB move | Incremental EV interval (bb) | Joint loss interval (bb) | Feedback at 0.01 bb |
| --- | ---: | ---: | --- |
| Fold | approximately 0 | 9.111777–9.112981 | Outside limit |
| Call | 9.111777–9.112981 | 0–0.000450 | Within limit |
| Raise to 50 bb | 8.627219–9.112981 | 0–0.484558 | Strategy-dependent |

The actual 9 bb committed at this decision comes from the public betting state. A common sunk contribution cancels from loss comparisons. `WITHIN_LIMIT` means the entire certified loss interval is at or below the declared limit. `OUTSIDE_LIMIT` means its lower endpoint is strictly above the limit. All other intervals are `STRATEGY_DEPENDENT`. Classification does not add numerical tolerance to the educational limit.

## Numerical conditioning with an explicit identity

The [joint conditional loss solver](sixmax-joint-conditional-decision-loss.md) is still the underlying calculation. Its old entry points and saved v1 audits are unchanged. The new `FiniteTwoPlayerConditionedDecisionLoss` wrapper uses separately identified v2 action and joint algorithms and records its objective representation and shift.

On the already certified Charnes–Cooper face, the scaled denominator `dPrime` equals one. For each action's upper-bound continuation plan, the new path maximizes:

```text
shift = -max_absolute_terminal_hero_utility - 1
conditioned_objective = original_numerator + shift * dPrime
original_value = raw_certificate_primal_value - shift
```

The saved BB shift is −51 bb. This changes the numerical objective representation, not the security face, reach constraints or mathematical optimum. Raw primal/dual evidence is retained. Every recovered value must agree with the original plan evaluated on the raw point, and every witness must still pass original realization, behavioral-ratio, global best-response, reach, posterior and conditional best-response checks. The action-lower and joint-loss LP formulations are unchanged.

All action reach/EV controls and joint controls share the existing compiler, pivot and arithmetic caps. Extra coefficient operations are charged. The saved solve uses **25 LPs, 664 pivots, 17,471,722 LP arithmetic units and 76,202 compiler units**. Baseline affine solves retain their existing separate budgets. No solver tolerance, pivot rule, cap or behavioral fallback was relaxed.

This is a bounded numerical improvement, not a general cure. An offline 15-question sweep at the wider slack increased complete solves from four to five; six questions still failed full-face reach and four still rejected numerically. Some failures occur after upper-action controls. Rejection remains preferable to manufacturing a witness. No upstream library defect has been identified.

## Separate qualification, preserved scalar rejection

The new format requires a material selected decision, sufficient material private-combo support, primary and reference whole-game quality, and the existing selected-decision mixture-regret, posterior-drift and positive-reference-reach checks. It retains the full old selected row and whole-study rejection reasons as evidence.

Only the scalar `ACTION_EV_DRIFT` failure is handled differently under this new identity: the new format checks actual reference EVs and joint losses against the corresponding certified intervals. It does not erase or reinterpret that failure in the old study. In the saved report, `legacySelectedDecision.stable=false` and `legacyWholeStudyRejections=[UNSTABLE_MATERIAL_DECISIONS]` remain visible even though `qualifiedForIntervalFeedback=true`.

Before any interval comparison, fresh deterministic CFR+ references at **500 and 1,000 iterations** must belong to the declared opponent security face. For the hero, the report independently computes:

```text
required_slack = max(0, hero_best_response_against_reference - global_hero_upper)
inclusion_margin = global_hero_upper + declared_slack - hero_best_response_against_reference
```

The two references need approximately **0.0000298091 bb** and **0.00000745971 bb** slack. Both fit the saved 0.0001 face; neither fits the old 1e-8 face. Their actual reached posteriors and optimal information-set continuations produce EVs and losses inside all saved bounds. The reference solution hashes and quality reports must exactly match fresh runs of the unchanged scalar screen.

An excluded reference has `insideDeclaredFace=false`, `boundsChecked=false` and a **null** bound violation. It cannot acquire qualification through a misleading zero violation or a skipped comparison. The selected question must have at least one robust move within the declared loss limit and one robust move outside it. Tightening the saved limit to 0.00001 bb makes call strategy-dependent and rejects qualification; the code cannot silently enlarge the limit.

`Result` and `QualifiedFeedback` have private constructors. Only a complete owned solve or physical replay can create the qualification capability. It binds the exact request and report hash to immutable move feedback. Deserializing or constructing a caller-owned report is not sufficient.

## Replay and independent verification

With Java 21 and the existing solver/engine/Jackson classpath:

```text
com.pokerlab.solver.SixMaxSuppliedIntervalFeedbackMain
  solve|replay <request.json[.gz]> <report.json[.gz]>
```

Solve requires a new output path. Strict JSON/gzip loading rejects unknown fields and enforces the existing expanded/compressed 8 MiB cap. Replay checks distinct paths and exact request/input lineage, reenumerates every physical board, reruns the owned LPs, regenerates all reference policies and their decision values, and compares the entire report. Changing qualification, shift, reference evidence, feedback or rehashed physical counts fails replay.

The optional [independent oracle](../scripts/check-supplied-interval-feedback.py) uses literal betting contributions and saved integer showdown counts to build a **729-by-5,832 complete-plan game**. It reuses the preceding literal joint-control oracle without changing its v1 results. SciPy independently solves all twelve joint controls and each action's EV extrema. It reconstructs owned opponent flows as complete-plan mixtures, checking original security, reach, hidden-world posterior, joint losses, action witnesses, recovered shifted objectives and feedback classifications.

The oracle also checks arithmetic consistency of recorded reference inclusion and endpoint comparisons. It does **not** regenerate reference policies; the Java physical replay independently does that. Its summary binds both stored and expanded report bytes and the source input hash. NumPy/SciPy remain optional offline tools, with no new runtime or CI dependency.

```text
python scripts/check-supplied-interval-feedback.py \
  docs/data/supplied-conditional-five-target-99-interval-feedback-report.json \
  <new-oracle-summary.json>
```

Analytic tests cover both hero orientations, original extrema, raw objective recovery, utility translation, immutable input snapshots, shared work caps, low-reach rejection and opaque results. Physical tests cover retained scalar rejection, both included references, excluded references, stricter limits, nonmaterial decisions, classification boundaries, physical lineage, exact gzip replay and tampered evidence. Old conditional/root reports remain replay regression controls.

## Next milestone

Build a separately identified saved-question and grade/review path using the qualified capability. Feedback must show an EV and loss **range**, the declared loss limit and strategy-dependent alternatives, together with the six-seat table state. It must preserve explicit supplied-belief provenance and avoid presenting this selected question as whole-pack admission.

Synthetic supplied priors, unavailable source-policy history reach, mandatory postflop checkdown and no rake remain model limits. Broader realistic ranges, strategic postflop and multiway equilibrium are separate work. AWS deployment remains paused.
