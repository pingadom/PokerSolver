# Big-blind coverage and phase-I simplex refinement

The same unpaired flop source now has a complete ordered BB study: 7d7h, 9d9h and AcJc, each first to act and after BB checks / BTN bets. This is the other player's actual decision, with BB's past call/check cancelled from counterfactual reach and BTN's later bet retained in the posterior.

The game is unchanged: 24 supplied physical worlds, all twelve dealt cards including folded blockers, two active players at a six-seat table, board 6d Qc Td, one half-pot flop bet followed by exact checkdown, no rake. It is not a six-active-player cash solution or a general poker chart.

## What failed and what changed

The first six-case v9 endpoint batch rejected every context with `NUMERICAL_FAILURE: Singular LP basis`. Its request, generation budget and complete report remain frozen under `supplied-unpaired-bb-flop-coverage-*`; they were not overwritten or reclassified.

A separate bounded passive diagnostic captured the first BB77 check upper-endpoint LP: 83 constraints and 82 variables. The failure occurred during phase I after 21 pivots, before phase II. A separately generated SciPy/HiGHS diagnostic found an optimum; its original binary64 inequalities, point and dual independently pass exact-fraction certificate checks. The oracle is a development control, never a trainer source or a runtime dependency.

Two predeclared diagnostic rules were compared. Strict phase-I ratios alone still rejected the LP. Applying the existing fixed direction-refinement passes in phase I as well, together with strict ratios, produced a certified solution. This identifies a sensitivity in PokerLab's own simplex implementation; it is not an upstream library defect or evidence that the mathematical LP was infeasible.

`BoundedLinearProgram.solvePhaseOneRefinedBasis` explicitly selects LP v7. The old entry points still select their original rules. New conditional endpoint mode v10 and conditional report v5 use it with the unchanged ascending/refined parent solver. BB study request/report v2 bind the new mode; v1 remains bound to v9. There is no automatic fallback or retry.

The complete original security face, physical chance, prior, utility coefficients, reach floor .0001, security slack 1e-8, .01bb loss threshold, 512-variable/constraint caps, 10,000 cumulative action pivots, 4 billion cumulative action arithmetic units and 8 million compiler units remain unchanged. Extra refinement work is charged through the existing action budget.

## Complete results

All six contexts now produce certified EV intervals. The existing conservative loss envelope leaves at least one strategy-dependent action in every context; none has both a robust within-limit move and a robust outside-limit move. **Zero new trainer questions are admitted.** No fresh CFR references were run for these discovery studies.

| BB hand | History | Check or fold EV, bb | Bet or call EV, bb | Existing loss classifications |
|---|---|---:|---:|---|
| 7d7h | First to act | -1.042569 to -0.976993 | -0.977290 to -0.977176 | Check dependent; bet within |
| 9d9h | First to act | -0.842494 to -0.777067 | -0.777215 to -0.777101 | Check dependent; bet within |
| AcJc | First to act | -1.692685 to -1.565019 | -1.565315 to -1.565141 | Check dependent; bet within |
| 7d7h | Check / BTN bet | -3.000000 to -3.000000 | -2.654040 to -2.229981 | Fold outside; call dependent |
| 9d9h | Check / BTN bet | -3.000000 to -3.000000 | -2.363024 to -1.970555 | Fold outside; call dependent |
| AcJc | Check / BTN bet | -3.000000 to -3.000000 | -3.254852 to -2.875535 | Both dependent |

Values are absolute terminal profit, including previously committed chips. Rounded values above are for reading; machine artifacts retain full precision. Existing loss bounds combine marginal EV endpoints conservatively, so a dominated alternative does not necessarily make the selected action's stored upper loss small. A future separately versioned pairwise regret bound can exploit that an action's loss against itself is exactly zero; current evidence and grading semantics must not be changed retroactively.

## Reproduction and independent checks

After compiling the solver and building its runtime classpath, run:

```text
com.pokerlab.solver.SixMaxBigBlindFlopCoverageStudyMain solve <request> <new-report.json.gz>
com.pokerlab.solver.SixMaxBigBlindFlopCoverageStudyMain replay <request> <saved-report.json.gz>
com.pokerlab.solver.SixMaxBigBlindFlopCoverageStudyMain inspect <saved-report.json.gz>
```

`inspect` labels the report an unverified stored snapshot. `replay` rebuilds the physical game and all six cases and requires complete equality. The producer derives and requires the complete sorted BB hand inventory; a caller cannot drop an inconvenient hand, reorder cases or mix old/new schema and algorithm versions. Existing output paths and input/output aliases reject before solving. No request handler invokes this study.

The new checker can be run without SciPy:

```sh
python3 scripts/check-big-blind-flop-discovery.py --output <new-receipt.json>
python3 -m unittest discover -s scripts -p 'test_check_big_blind_flop_discovery.py'
```

It independently regenerates 15,792,192 preflop boards and 15,984 fixed-flop runouts, reconstructs all 1,321 states and 73 information sets, checks the two original parent certificates and all 51 endpoint certificates using exact fractions of the saved binary64 coefficients, then checks every original full-parent security witness, posterior, action continuation, table context and loss classification. It reconstructs the captured failing upper LP from the literal game and verifies the stored external oracle's exact certificate. It performs zero LP/CFR optimizations.

Java regressions retain the old captured failure, certify the same LP with the new mode, check all 292 existing independent oracle problems plus prior endpoint/near-ratio controls, enforce cumulative budgets, and physically replay both complete six-case batches. Python corruption controls exercise context, cancellation, posterior, flow, dual, continuation, work, loss, inventory and admission edits. CI runs the independent checker controls and includes the new Java classes in the existing complete test inventory.

The original 315 data artifacts, all existing trainer sources, APIs and website routes remain unchanged. AWS deployment remains paused. The next qualification step must use a separately declared loss/strategy protocol and fresh reference policies; discovery results alone never confer grading authority.

## Interview explanation

“I expanded our own poker solver to the other player's decisions and found that the simplex feasibility phase could select a numerically singular basis. I retained every failure, captured a real endpoint LP, checked an independent oracle against the original inequalities, and added an explicit all-phase refinement mode. I validated it against existing oracle problems and independently checked all six poker contexts. I kept ambiguous decisions out of the trainer instead of weakening the accuracy gates.”
