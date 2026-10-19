# Refined basis solves for paired-board decisions

The explicitly selected v7 conditional study resolves the numerical blockers for
BTN `9s Js`, `Ah Kh` and `Qh Th` facing a bet on `8d Kc Kd`. It also replays the
existing `As Qs` hand as a control. All four requests use the original 24 supplied
worlds, original physical game and original certified parent. The two active
players sit at a six-seat table; this does not establish full six-max cash coverage.

## The failure and correction

The captured lower endpoint problems have 86 constraints and 81 variables. The
previous endpoint mode rejects two with lost primal feasibility and one with an
unresolved positive reduced cost. Independent original-problem oracle witnesses
show that these are numerical failures, with substantial work/pivot budgets left.

A minimal two-constraint example isolates the ratio problem:

```text
maximize x
x <= 1.0236165219481362e-12
35491.780049572175*x <= 2.2032117954153633e-8
x >= 0
```

The two positive step ratios are distinct but less than 1e-12 apart. Treating them
as a tie can select the larger step and violate the second constraint by about
1.42978545e-8, beyond the unchanged 1e-8 certificate tolerance. The regression
checker proves this using exact fractions of the supplied binary64 numbers.

The new LP mode uses two fixed compensated refinement passes for the primal basis
and dual solves, alongside the existing direction refinement. It orders computed
phase-II ratios strictly; only equal computed ratios use the basis-index tie rule.
This is a comparison of binary64 ratios, not an exact rational simplex algorithm.
Primal/dual refinement alone and strict ratios alone were insufficient for all
three captured cases; the combined method resolves them. Original primal/dual
certificates remain mandatory, with the same coefficient, dimension, pivot and
arithmetic bounds. Added refinement work is charged to those existing bounds.

## Explicit versions and immutable prior evidence

`BoundedLinearProgram.solveAscendingRefinedBasis` selects the new v5 LP protocol.
It is never an automatic retry or replacement for an older mode. The parent still
uses the original v2 affine/v4 LP representation. Only the endpoints use the new
LP mode through `OWNED_PHYSICAL_FLOP_ASCENDING_PARENT_REFINED_BASIS_ENDPOINTS/v7`.

Requests retain the existing request schema and bind this algorithm before work
begins. New reports use `pokerlab-refined-flop-conditional-report/v2`; old v6
requests/reports retain their original protocol and values. Report parsing cannot
construct the private owned result. No previous report or saved trainer is upgraded.

The four new `supplied-paired-basis-btn-*-conditional-request.json` files each have
a separate compressed report and independent receipt. The normal conditional CLI
supports explicit solve/replay of these files:

```text
com.pokerlab.solver.SixMaxRefinedConditionalStudyMain solve <v7-request> <new-report.json.gz>
com.pokerlab.solver.SixMaxRefinedConditionalStudyMain replay <v7-request> <report.json.gz>
```

## Validation and remaining gates

Each physical replay reconstructs the original deal/runout model, parent, complete
opponent security face, positive full-path reach, fold/call endpoints, posterior
weights and public table context. The independent checker verifies eight endpoint
certificates per hand, original flows, literal best responses, conservative action
EV/loss bounds and cumulative work. It additionally checks the three captured
lower-endpoint witnesses with exact fractions on the original binary64 inputs.
This means residuals are within the declared tolerance; it is not a claim of zero
rational residual or exact game-theoretic optimality.

```sh
python -m unittest discover -s scripts -p 'test_check_refined_basis_conditional_study.py'
```

Regression coverage includes all three captured failures, the minimal near-tie
example, all 292 independent LP oracle controls, original certificate checks,
invalid/infeasible/unbounded cases, work limits, complete physical replay, report
version swaps and zero-reach rejection. Independent corruption controls reject
changes to values, policies, witnesses, reach, posteriors, work and provenance.

These are research intervals, with `trainerAdmission=false`. Fresh full-game CFR
reference inclusion, selected-decision regret and educational qualification are
still required before adding the three hands to a trainer. The current single
paired-board trainer continues to use its original v6 evidence. AWS remains paused.
