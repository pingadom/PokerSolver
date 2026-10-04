# Six-seat preflop with connected postflop decisions

`SixMaxConnectedPreflopGame` connects all six preflop policies to selected heads-up flop, turn and river branches in **one six-player CFR tree**. Preflop decisions are updated using the current postflop policies, and postflop decisions use the current preflop action reach. This advances beyond the [connected postflop study](sixmax-connected-postflop.md), whose preflop policy was frozen.

This is a bounded, sparse physical-flop experiment, marked `VALIDATION_ONLY`. One or two completed heads-up histories and one or two physical flops per history can be selected. All other flops retain mandatory checkdown. Fold wins, all-in preflop showdowns and multiway checkdown terminals retain their original payoffs. Postflop allows check/bet and call/fold with one configured, stack-capped bet per street, no raises and no rake. No new trainer pack is published.

## Private support and physical chance

The parent game draws one correlated six-hand deal at the start. It never redraws private hands at the flop or multiplies separate live-player marginals. The nested postflop root maps back to exactly that physical deal, retaining all folded cards as blockers. There are 40 remaining cards before the flop, 37 before the turn, and 36 before the river.

`SixMaxPolicyFlopTransition.counterfactualSupport` keeps every positive source private-chance outcome, including hands with zero reach under an earlier strategy. Applying the old action posterior here would permanently exclude hands that a changing preflop policy could reach through a deviation. The parent CFR traversal supplies chance and action reach. Counterfactual-support mode therefore refuses to report a policy reach probability. The ordinary policy-conditioned transition remains available for interpreting a solved profile.

Each selected, compatible physical flop has probability **1 / C(40,3) = 1 / 9,880** for its particular six-hand deal. A blocked flop has probability zero. Selected flops are not renormalized to make them common. The remainder carries probability `1 - legalSelectedCount / 9880`. Turn and river chance on selected flops enumerate all `37 × 36` ordered physical trajectories; final-board scores reuse 666 unordered pairs. Preflop and postflop preserve their public action history and each actor's own hand without exposing the private-deal index or opponents' cards in an information set.

## Exact recovery of the checkdown model

Simply attaching betting branches to the old terminal value would count their checkdown payoff twice. For each private deal and each seat, the residual branch instead uses:

```text
residual utility =
  (source full-board checkdown utility
   - sum(selected-flop probability × that flop's exact checkdown utility))
  / remaining-flop probability
```

Forcing checkdown on every betting-enabled flop then recovers the source utility for every seat. The audit verifies that recovery under a complete preflop profile. Folded players retain their earlier losses; matched bets change the two live commitments, uncalled bets are returned, and the six chip utilities conserve the pot.

This formula assumes a physically consistent source payoff table. The implementation requires zero declared payoff sampling error and rejects non-finite, non-conserving or impossible residual values. Those checks cannot establish the provenance of an arbitrary custom oracle. The committed study uses the existing source pack's exact 658,008-board, six-hand payoff table. The toy equal-share oracle used in algebra tests is a synthetic fixture, not a poker equity claim.

## Quality and coverage are separate measurements

`SixMaxConnectedPreflopAudit` trains a fresh mandatory-checkdown baseline and the connected model with the same CFR+ iteration budget. It also lifts the baseline into the connected model with checks/calls at every postflop information set, including off-policy ones. All six players receive exact information-set unilateral best-response values in the declared model. NashConv sums their deviation gains; multi-player CFR does not inherit the two-player zero-sum convergence guarantee.

The report records unconditional betting-continuation probability separately for the source policy, fresh baseline and joint solve. A tiny probability must not be interpreted as broad postflop coverage. The full-game NashConv can hide conditional errors on rare branches, so the audit also derives the selected flop's posterior from the final joint preflop profile and measures the connected postflop policy's two-player best-response gap in that conditional game. This evaluation translates the existing joint policy; it does not perform a fresh postflop solve. Root action EVs and frequencies use the same translated policy.

The maximum preflop frequency change compares matching information-set rows in the same-budget profiles. It is an unweighted maximum and can come from a rarely reached decision; it is not an overall improvement score. Complete sorted policy hashes bind the solutions without exporting tens of thousands of strategy rows. The source policy chooses the example history and physical flop only; its policies and posterior do not constrain the new CFR solve.

## Reproduce the study

The [source-bound JSON report](data/sixmax-connected-preflop.json) records seed 711, 200 iterations for both new profiles, half-pot-derived fixed bet requests, two source private deals and a single physical flop. It is a study summary rather than a playable policy pack.

The source pack hash is `2ce2adc9a9cb69179e36f6de3d223b40542d9588da61c58ff66fc9d241b57dcf`. The selected history is UTG call, HJ/CO/BTN/SB fold, BB check; the board is `3c 7s 9d`. Both source deals are compatible. Flop, turn and river bet requests are 1.25bb, 2.5bb and 5bb, respectively, with effective-stack caps.

| Measurement | Result |
| --- | ---: |
| Same-budget checkdown information sets | 7,089 |
| Joint preflop/postflop information sets | 60,625 |
| Checkdown model NashConv | 0.129728072bb |
| Checkdown profile NashConv in the connected game | 0.129810052bb |
| Joint model NashConv | 0.129725475bb |
| Maximum forced-checkdown recovery error | 0bb |
| Conditional selected-flop best-response gap | 0.001148100bb |
| Source policy betting-continuation probability | 0.000167222% |
| Joint policy betting-continuation probability | 0.001081008% |
| Maximum absolute preflop action-frequency change | 0.000925533 percentage points |
| Largest absolute seat utility change | 0.000002117bb |

The new 200-iteration profile has a larger deviation score than the saved 500-iteration source (0.021103bb). It must not replace that pack on the strength of this integration. Compared with the **same-budget** baseline, preflop strategies and utilities barely change, as expected from the tiny physical-flop coverage. The conditional postflop metric is more informative about the selected branch than the aggregate game's almost unchanged score. BB bets 99.90085% at the selected flop root; bet EV is 1.500172bb and check EV is 1.207010bb against the saved joint continuation, measured from the start of the hand.

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxConnectedPreflopAuditMain' '-Dexec.args=solver/src/test/resources/six-seat-full-round-pack.json docs/data/sixmax-connected-preflop.json 711 200 0.5'
```

The CLI requires five arguments, refuses source overwrite and caps input files at 16 MiB. The audit caps the budget at 500 iterations. The game caps source support at four private deals and connected compatible deal/flop pairs at four. Output JSON is sorted and excludes elapsed time. Repeated bounded exports must be byte-identical and leave the input unchanged.

Tests cover zero-source-reach counterfactual support, physical flop probabilities, folded-card blockers, continuity of private cards, information privacy, actual seat mapping, uncalled refunds, unchanged unselected terminals, full-profile checkdown recovery, jointly updated preflop/postflop strategies, six-player best responses, conditional postflop quality, unsupported rake and sampled payoffs, support limits, forged state rejection and reproducible source-bound export.

The parent wrapper caches validated node classification, legal actions and settled six-seat utilities for immutable states. This avoids repeating the same validation and chip settlement on every player's CFR pass. Public payoff arrays remain defensive copies, and invalid state links are checked before a node can enter the cache. Like the existing CFR implementation, each game instance is used by a single offline traversal thread.

Repeating the complete 200-iteration study after this change produced **byte-identical JSON**, including both policy hashes, every EV, all best responses and coverage values. Observed local elapsed time fell from 448.7 to 260.2 seconds, about 42%. Regression suites ran alongside both studies, so these are local timings rather than an isolated hardware benchmark or a portable speed guarantee. The cache trades additional per-game memory for reduced repeated work; broader models still need an explicit memory and traversal budget.

The next integration gate is broader measured flop coverage and a computational model that can support it. More private deals with justified position-specific provenance, wider action menus, multiway postflop decisions and specified cash rake remain necessary before a realistic six-max trainer pack. This experiment establishes the joint solving path; its sparse coverage does not complete those gates.
