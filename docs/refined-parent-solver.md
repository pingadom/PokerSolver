# Correcting the paired-board parent LP

The new, explicitly selected ascending-row parent mode fixes a reproduced floating-point pivot error on `8d Kc Kd`. The original mode remains unchanged and continues to reproduce that rejection. This is an owned numerical solver correction, not an external library fix or an expansion of admitted trainer content.

## Failure and exact proof

The connected parent LP previously chose a direction component computed as `2.2351741790771478e-10`, above the unchanged eligibility threshold `1e-10`. Exact rational arithmetic over the original binary64 coefficients proves that component is zero. The preceding 81×81 basis has exact rank 81; the selected pivot creates a basis of rank 80. All 81 null-vector products and prior direction equations are checked exactly by the independent pivot checker.

The captured basis and original 79-variable/81-constraint problem live in `solver/src/test/resources/lp-parent-pivot-paired.json.gz` and `lp-parent-problem-paired.json.gz`. Java tests retain the original singular-basis rejection for both the captured LP and the actual paired-board game. The new method certifies the original inequalities and is also checked against all 292 independently generated LP oracle problems.

## Separately selected correction

`BoundedLinearProgram.solveAscendingRefinedDirections` selects `OWNED_ASCENDING_L1_COMPENSATED_PHASE_TWO_DIRECTION_ORIGINAL_CERTIFICATE/v4`. It keeps ascending L1 row ordering, ordinary reduced-cost selection and ratio rules. Before every phase-II ratio test it applies exactly two compensated original-basis residual-correction passes, using the existing FMA residual implementation. Phase I and artificial-basis cleanup retain their behavior. The prior scaled conditional mode remains a distinct method with its existing identity.

Each correction pass charges `20*m*m` arithmetic units plus its triangular solves. The original 4,000,000,000-unit LP limit, cumulative row-mapping accounting, 10,000-pivot cap, coefficient/dimension bounds and `1e-8` original primal/dual certificate tolerance stay in force. Exact budget exhaustion rejects. No failure triggers a retry, mode switch, constraint removal or relaxed threshold.

`FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections` selects a new affine identifier before compiling either LP. Both owned solves are checked against the complete original snapshot and independent information-set best responses. Serializable LP witnesses expose copied primal and dual points for independent checking; parsed witness data cannot construct a solved strategy handle. Existing audit schemas and default solver entry points retain their behavior.

## Paired-board parent study

The separate `SixMaxRefinedParentStudy` request/report protocol binds the new algorithm, original connected input, physical model, private joint prior, folded blockers, exact payoff partitions, immutable policy, complete audit and both LP witnesses. Its private result can only come from an owned solve. Replay rebuilds the physical game and regenerates every report field. Bounded plain/gzip persistence is exclusive, with independent 8 MiB stored and expanded limits and strict JSON reading.

The saved request is [supplied-paired-flop-refined-parent-request.json](data/supplied-paired-flop-refined-parent-request.json); its [report](data/supplied-paired-flop-refined-parent-report.json.gz) has canonical pin `3a97d2fa7fe246be77b20b012243b5f5a3ad559f390143853db960d8917829c3`. The complete model has 24 supplied deals, 1,321 states and 73 information sets. The owned full-snapshot NashConv is approximately `3.12e-15 bb`; an independent literal-tree check obtains approximately `3.16e-15 bb`, both within the unchanged `1e-8` gate. These tiny floating-point differences do not establish real-world poker accuracy.

```text
com.pokerlab.solver.SixMaxRefinedParentStudyMain solve <request> <new-report.json[.gz]>
com.pokerlab.solver.SixMaxRefinedParentStudyMain replay <request> <report.json[.gz]>
```

```sh
python scripts/check-refined-parent-study.py docs/data/supplied-paired-flop-refined-parent-request.json docs/data/supplied-paired-flop-refined-parent-report.json.gz --output <new-receipt.json>
python scripts/check-lp-pivot-regressions.py solver/src/test/resources/lp-parent-pivot-paired.json.gz
python -m unittest discover -s scripts -p 'test_check_refined_parent_study.py'
python -m unittest discover -s scripts -p 'test_lp_pivot_regressions.py'
```

The independent checker enumerates all 666 turn/river runouts for each of the 24 physical worlds, reconstructs the full literal tree, original flow projections/payoff matrices, both LP primal/dual certificates, policy realization ratios and best responses. Full preflop counts come from a byte-pinned prior physical artifact; Java replay regenerates those too. The checker runs no LP optimizer or CFR training. Its receipt binds checker/helper sources and stored/expanded report bytes. Corruption controls reject altered dead cards, physical/residual counts, policies, flows, witness points/certificates, budgets, identities and values before the final fixed-byte binding.

## Scope and next work

This fixes the parent numerical blocker for one paired-board model. It does not certify conditional action EVs, positive question reach across the complete security face, fresh-reference inclusion, educational action classes or a new trainer lesson. Candidate iteration `1` is a strategy-container sentinel, not a CFR training run. No conditional posterior is manufactured at zero reach.

Next, use explicitly versioned feedback studies to assess individual paired-board decisions with all original face/reach/reference/regret gates. Separate rainbow and two-tone probes still have zero-reach rejection witnesses for their first BTN question; this parent correction does not remove that blocker. Wider credible ranges, rake, richer streets and genuinely multiplayer equilibrium remain separate milestones. AWS deployment remains paused.
