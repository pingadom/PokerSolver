# Retained continuation coverage

The twelve-world solver trials improve their same-game parent score, but their final policies do not retain enough varied, meaningfully reached heads-up continuation content. `SixMaxRetainedContinuationCoverage` makes that distinction explicit. It screens the **retained policy**, not just the source policy used to choose the menu.

This is an offline engineering check. A pass is not trainer admission, realistic range validation or a convergence certificate. The existing whole-round quality gate and checkpoint acceptance are unchanged. All artifacts remain `VALIDATION_ONLY`; private range diagnostics never enter live trainer questions.

## Three separate measurements

| Measurement | What it tells us |
| --- | --- |
| Parent NashConv and conditional postflop gaps | Strategy quality in the declared finite game; already checked by the quality gate |
| Public-history reach and selected share of heads-up history mass | Whether the selected continuations are meaningfully reached under this policy |
| Board-conditioned active-seat hand mixes | Whether each active player retains multiple material exact combos, after actions and card removal |

The default screen requires each selected public history to have probability at least **0.0001**, the selected histories to cover at least **25%** of reached heads-up continuation mass, and **two exact combos of at least 5% each** for each active player on every selected board. Threshold comparisons are inclusive. These are provisional, configurable research cutoffs: one history per 10,000 deals is already rare, and passing this minimum does not make a scenario representative of cash poker. The threshold values are saved with every decision.

Public histories count once even when several flops are selected. Every board is screened separately. Folded-player uncertainty remains in joint support, but only the two active seats are subject to the active-hand criterion. Diagnostics distinguish a narrow counterfactual range caused by blockers from a broad counterfactual range whose retained posterior became nearly pure.

A physical flop is at most **1/9880 given a six-hand deal/history**. Consequently there is no arbitrary minimum on an individual flop's unconditional physical probability. Reports show that probability, its probability given the history, and compatible posterior mass (`conditional probability × 9880`). This keeps physical-board coverage visible without rejecting every sparse model simply because an exact board is rare. The current screen does **not** enforce broad physical-board coverage.

Positive action likelihoods can multiply to a number too small for a double. Such histories are reported as `NUMERIC_UNDERFLOW`, with finite log reach and preserved normalized private posteriors. A truly unreachable history is `ZERO_POLICY_REACH`, with null log reach and empty reached marginals. Neither meets the positive history threshold.

Hand diagnostics include material-combo counts, the largest combo probability, Shannon entropy in bits and effective combo count (`2^entropy`). An unreachable board has zero effective reached combos; a deterministic reached hand has one. These are descriptions of the joint model's marginals, not independently sampled ranges.

## Completed checkpoint results

The standalone CLI reloaded and strictly validated each full average-policy checkpoint against its original source, selected menu, budget, completeness and canonical policy hash. It did not train or change either input. The small saved reports are:

- [Seed 711](data/sixmax-correlated-retained-coverage-711.json)
- [Seed 712](data/sixmax-correlated-retained-coverage-712.json)

| Policy | Content screen | Selected history mass | Share of reached heads-up history mass | Material BTN combos on the two boards |
| --- | --- | ---: | ---: | --- |
| Original source | Passes default criteria | 0.0106298375 | 41.81% | 2 / 2 |
| Retained 711 | Fails | 0.0000371482 | 30.82% | 1 / 1 |
| Retained 712 | Fails | 0.0000372219 | 30.85% | 1 / 1 |

Both final policies still pass the unchanged whole-round quality gate: parent NashConv is approximately 0.00257335bb / 0.00262583bb, and worst conditional gaps are below 0.020074bb. These scores describe the same declared game as their inputs; the content failure does not invalidate or undo that quality improvement.

The final limped BB/BTN histories reach 0.0000153566 / 0.0000154278. The CO/BTN histories reach 0.0000217916 / 0.0000217941. Both histories are below 0.0001 in both trials. BB and CO each retain two material hands; BTN has only one on each board. All twelve original counterfactual private worlds remain available. Selected absolute physical-board mass is approximately 3.76e-9. No policy was promoted into trainer content.

## Bounded menu search

`SixMaxContinuationMenuSearch` proposes menus under a **fixed preflop policy**. Before trying boards, it ranks reached public histories and computes two optimistic bounds, ignoring pair diversity, blockers and cost: the reach of the requested last-ranked history, and the largest possible fraction of heads-up mass covered by the requested number of histories. Failure at either bound rules out the declared reach request under that policy, regardless of flop seed.

If feasible, it scans at most sixteen consecutive flop seeds, using the existing top-twenty diverse-pair selector and the declared study budget. It returns the first menu meeting the coverage criteria. Every attempted seed records a typed budget rejection, an ineligible diverse menu with candidate skip reasons, or a completed menu's content decision and exact tree cost. Invalid source policies and unexpected failures propagate; they are not disguised as search misses. A `NO_FIT_IN_SEARCH_WINDOW` result is limited to that bounded greedy search, not a proof that no qualifying menu exists.

For the original twelve-world source, seeds 711–714 need 18 / 24 / 24 / 18 compatible deal-flop pairs and are rejected. Seed **715** is the first fitting proposal, exactly reproducing the current two-history menu, 16 pairs and 1,896,409 states, with every private root preserved.

For the retained trials, the highest-reach heads-up history is only **0.0000382886 / 0.0000382978**. Even the best single history misses 0.0001. The search reports `FEASIBILITY_FAILED` without trying a flop seed. Searching more boards cannot repair this public-history coverage issue under a frozen policy. The next model study needs different range/betting assumptions or an explicitly conditioned training game, followed by a fresh solve and both quality and content checks. Lowering the cutoff is a sensitivity analysis, not evidence of improved content.

A proposed menu changes the connected game. It is not a checkpoint resume: postflop information sets depend on public cards, pot and bet sizes. Old postflop rows and parent quality scores cannot be transferred to the proposed game. Any new solve must establish its own policy completeness, source/menu identity, conditional accuracy and retained coverage.

### Reach-cutoff sensitivity

For seed 711 only, a [separate sensitivity screen](data/sixmax-correlated-relaxed-coverage-711.json) lowers the history minimum tenfold to **0.00001**, keeping the 25% heads-up coverage, two material combos, 5% combo mass and original cost budget unchanged. The declared menu now clears history reach but still fails BTN hand diversity on both boards. The retained-policy search evaluates all sixteen seeds, 711–726: eleven exceed the budget, four have no diverse menu in the candidate window, and seed 713 fits the budget with two material hands for both active players.

That last candidate reaches histories of only 1.82e-7 and 1.09e-7, covering approximately **0.241%** of heads-up continuation mass. It fails both the relaxed history floor and the 25% coverage criterion. The bounded search therefore still reports `NO_FIT_IN_SEARCH_WINDOW`. This one-trial sensitivity result shows why merely weakening one cutoff does not repair the measured content issue; it does not rule out other boards or menus beyond the declared search. Its original checkpoint and quality scores are unchanged.

## Run the screen

From the repository root, build first. Checkpoints are ignored local artifacts; reproduce them using the commands in [the correlated-range study](sixmax-correlated-private-ranges.md) if necessary. These calls read the completed higher-budget checkpoints and write separate reports:

```powershell
mvn -q -pl solver -am install -DskipTests
foreach ($trialSeed in 711, 712) {
    mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxRetainedContinuationCoverageMain' "-Dexec.args=docs/data/sixmax-correlated-source-pack.json .local/sixmax-correlated-linear-policy-$trialSeed.json .local/sixmax-retained-coverage-$trialSeed.json --search 711 16 2 1"
    if ($LASTEXITCODE -ne 0) { throw "Coverage screen $trialSeed failed" }
}
```

Omit `--search` to screen only the declared menu. Optional `--thresholds <history-mass> <heads-up-fraction> <combo-mass> <combos>` replaces all four content cutoffs and records them in the report. Diverse-pair search requires at least two material combos per active seat. Threshold and search options may appear in either order, once each; unknown, duplicate or incomplete options fail before any report mutation.

Reproduce the seed-711 sensitivity using the same source/checkpoint inputs, a separate report path and `--search 711 16 2 1 --thresholds 0.00001 0.25 0.05 2`.

The source limit is 16 MiB; the existing checkpoint reader enforces 128 MiB, strict JSON, source binding, complete rows and the policy hash. Output cannot alias either input, including hard links. The CLI builds all results before atomically replacing the report, leaving an existing report intact on validation failures.

Future alternating-study artifacts use **v5**, adding `sourceContentCoverage` and `retainedContentCoverage`. A plan-only report has source coverage and null retained coverage; a completed report screens the actually retained policy, including after a rejected round. Historical v1–v4 artifacts deserialize with null new fields and remain unchanged. Checkpoint schema v1 and the quality-gate rules remain unchanged.

Tests cover weighted blocker effects, multiple boards without repeated history mass, inclusive threshold boundaries, almost-pure retained hands, zero reach versus numeric underflow, deterministic search, feasibility bounds, both budget and diversity failures, source mismatch, file aliases, malformed options, size limits and unchanged checkpoint bytes. Saved evidence is cross-checked against the original completed solve reports; CI does not rerun the high-budget solves.
