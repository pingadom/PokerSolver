# A numerical pivot correction and four BTN contexts

The new opt-in LP path removes a reproduced floating-point pivot error that blocked BTN holding Ah Kh or Qh Th after BB checks on 2s 8s Ts. The separate four-hand feedback report covers those hands, 9s Js and As Qs. It uses the original supplied joint prior, folded blockers, physical flop event, complete connected game and exact residual checkdown.

This is offline research qualification. A typed BTN trainer/API and a versioned collection extension follow separately. The current BB collection and the original single-J9 qualification retain their protocols and evidence. The game still has two active players at a six-seat table, no rake and one flop bet; these results do not establish general six-player GTO or a coherent full-hand strategy.

## The reproduced error

The existing revised simplex solver refactorizes its original basis rather than accumulating a tableau. In two physical cases, rounding while solving the entering column made an exact-zero direction component appear positive. The ratio test selected that row and created a singular basis, which the next factorization correctly rejected.

| BTN hand | Previous basis rank | Chosen direction, double | Chosen direction, exact | New basis rank |
| --- | --- | --- | --- | --- |
| Ah Kh | 85 / 85 | 7.450580596923828e-9 | 0 | 84 / 85 |
| Qh Th | 86 / 86 | 2.9802322387695312e-8 | 0 | 85 / 86 |

Both computed components exceeded the existing `1e-10` eligibility threshold. The [recorded inputs](../solver/src/test/resources/lp-btn-pivot-ak.json.gz) and [second input](../solver/src/test/resources/lp-btn-pivot-qt.json.gz) include the original prior/rejected basis matrices and entering column. The [exact checker](../scripts/check-lp-pivot-regressions.py) treats every binary64 coefficient as an exact rational, proves the previous bases have full rank, checks all direction equations, and produces nonzero null vectors whose products with every rejected-basis row are exactly zero. This is a bug in PokerLab's own numerical solver, not an external-library issue.

## Explicit correction

`BoundedLinearProgram.solveRefinedDirections` selects `OWNED_EXACT_POWER_TWO_RESOLVABLE_COST_COMPENSATED_DIRECTION_ORIGINAL_CERTIFICATE/v2` before optimization. It retains exact power-of-two row scaling and the resolvable-cost rule, then evaluates each phase-II entering direction with exactly two original-basis residual-correction passes:

1. Solve the entering column with the existing LU factorization.
2. Calculate `rhs - B*x` from the original basis coefficients using compensated summation and `Math.fma` product residuals.
3. Solve the residual with the same LU, then apply its correction.
4. Repeat the residual correction once more, regardless of whether the first correction was small.

Each pass reserves `20*m*m` arithmetic units before evaluating its residual; both triangular solves are charged by the existing LU solver. The original and cumulative `4,000,000,000` work cap, 10,000 pivot cap, coefficient/dimension limits and `1e-8` original primal/dual certificate tolerance stay unchanged. Nonfinite arithmetic rejects. There is no retry after rejection, automatic algorithm selection or tolerance relaxation. Phase I and the ordinary/ascending/resolvable/old power-of-two entry points retain their behavior.

The physical interval path has a separate preselected identifier, `OWNED_PHYSICAL_FLOP_CONDITIONAL_REACH_REFINED_DIRECTION_ENDPOINTS/v5`. Its LP witnesses identify the new v2 method. The old v4 path still reproduces both singular-basis rejections; existing successful v4 evidence remains replayable. All final points and duals are checked against the copied original inequalities after undoing row order and exact scaling.

## Certified results in this supplied game

All four contexts contain a robust within-limit bet and an outside-limit check at the declared 0.01 bb loss limit. Approximate conservative loss ranges are:

| BTN hand | Check loss (bb) | Bet loss (bb) |
| --- | --- | --- |
| 9s Js | [0.081319, 0.082259] | [0, 0.000922] |
| Ah Kh | [3.413065, 3.415253] | [0, 0.001748] |
| As Qs | [0.133363, 0.134266] | [0, 0.000874] |
| Qh Th | [1.335370, 1.335876] | [0, 0.000371] |

These are independently bounded losses under this supplied model and original security face. Four agreeing actions do not establish a general betting rule or four independent equilibrium solutions. The canonical report pin is `9686d76c4a9d3b1a0875f7fcaed3edecf4491e0c24698892b516f91a69c89cd1`.

## Shared-budget four-hand qualification

`SixMaxBtnFlopCurriculum` requires an ordered selection of exactly `9s Js`, `Ah Kh`, `As Qs`, `Qh Th`, the BB-call/BB-check path, the v5 interval algorithm and the explicit fresh CFR+ budget `[80000, 160000]`, total 240,000 iterations. Both references start from new solver instances over the same original complete game. They share only immutable physical payoffs, not regret tables or checkpoints.

The original parent policy plus those two references are assessed at every selected context. This is **three unique policies and twelve policy-context checks**, not twelve fresh strategies. All eight legal-action endpoints must pass before the reference runs begin. Every context must then pass original-face inclusion, complete-parent and reached-flop quality, actual and counterfactual path reach, forced-action optimal future values/loss, selected regret, and both robust within/outside educational classes. BB's call and check both remain in BTN's counterfactual reach. No source policy exists for the supplied historical prefix.

Only a complete owned solve or replay creates the private result and qualified capability. Parsed report flags, imported policies and a computed hash cannot grant authority. An exact replay regenerates all eight endpoints and both fresh reference runs, then compares every field. Stored and expanded inputs/outputs keep the 8 MiB bound, strict JSON handling, distinct paths and exclusive output creation.

The [request](data/supplied-four-button-hand-btn-curriculum-request.json), [report](data/supplied-four-button-hand-btn-curriculum-report.json.gz) and [receipt](data/supplied-four-button-hand-btn-curriculum-independent-check.json) are separate artifacts. The [independent checker](../scripts/check-btn-flop-curriculum.py) rebuilds the literal physical tree, hero3 payoff orientation, original matrices, behavioral witnesses, posteriors and all policy assessments. It verifies **32 original endpoint certificates**, eight moves and twelve context checks. It runs no optimizer or CFR training; Java's full replay establishes fresh-run provenance. Its receipt binds checker/helper sources and stored/expanded artifact bytes.

```text
com.pokerlab.solver.SixMaxBtnFlopCurriculumMain solve <request> <new-report.json[.gz]>
com.pokerlab.solver.SixMaxBtnFlopCurriculumMain replay <request> <report.json[.gz]>
```

```sh
python scripts/check-lp-pivot-regressions.py solver/src/test/resources/lp-btn-pivot-ak.json.gz solver/src/test/resources/lp-btn-pivot-qt.json.gz
python -m unittest discover -s scripts -p 'test_lp_pivot_regressions.py'
python scripts/check-btn-flop-curriculum.py docs/data/supplied-four-button-hand-btn-curriculum-request.json docs/data/supplied-four-button-hand-btn-curriculum-report.json.gz --output <new-receipt.json>
python -m unittest discover -s scripts -p 'test_check_btn_flop_curriculum.py'
```

Regression coverage includes all 292 independent LP oracle problems, the two physical old-failure/new-success cases, exact rational rank/direction proofs, original-certificate checks, finite/invalid/infeasible/unbounded cases, exact work exhaustion, a complete owned four-context replay and semantic corruptions across every hand. CI retains all source-discovered tests, four solver partitions, five required manifests and the existing per-job bounds. AWS deployment remains paused.
