# Reached continuations with suited, pair and broadway support

`SixMaxReachedContinuationStudy` expands the connected six-seat solver beyond the premium-pair fixture. It selects several non-all-in heads-up histories by source-policy reach, preserves all counterfactual private deals, and reports how much play those histories and their selected physical flops cover. The offline CLI trains one joint policy per seed and compares conditional refinement budgets against that same policy.

## Declared game

The [spot](data/sixmax-diverse-spot.json) and [exact source pack](data/sixmax-diverse-source-pack.json) use 100bb stacks, 0.5bb/1bb blinds, raise-to targets of 3bb and 100bb, no rake, and mandatory checkdown before sparse betting continuations are added. The synthetic seat ranges are:

| Seat | Physical combinations |
| --- | --- |
| UTG | 4c 4d |
| HJ | Kd 9d |
| CO | Qh Th |
| BTN | Js Ts; 8s 8h |
| SB | 6c 5c |
| BB | Ac Jc; 7d 7h |

Equal combo weights give four unblocked joint deals, each with probability 0.25. Both late-position players have private uncertainty. The four other ranges contain only one combo, so those hands are inferable from the declared model even though policy observations do not expose hidden cards. Folded cards continue to block later board cards. These ranges are a coverage test, not estimated population ranges or realistic cash charts.

In the preceding premium-pair benchmark's selected UTG-versus-BB continuation, the private variation came from a folded BTN hand; both active players had single-combo ranges. Here, both continuing players can hold either of two combinations. This tests action choices against an unknown active opponent hand as well as physical card blockers.

The exact oracle evaluates all 658,008 five-card boards per physical deal and reuses that enumeration across all 57 active-player subsets. The pack saves 228 payoff entries and a complete 8,305-row, 500-iteration CFR+ preflop policy. Its measured mandatory-checkdown NashConv is 0.019021873bb and terminal payoff sampling SE is zero. This gap concerns the declared finite game, not unrestricted six-max poker.

The source pack hash is `3a8781ca8ab82966bc7290b51fcee4db328c37b862636a682dae4cf3703c9fb6`. Its strict loader rechecks spot provenance, every payoff, strategy support and exact six-player best-response metrics. The existing website trainer keeps its previous pack.

## Selection and coverage accounting

For flop-selection seed 711, the two highest-reach source histories are BTN limping and BB checking, then BTN raising to 3bb and BB calling; UTG, HJ, CO and SB fold in both histories. Selected flops are `3c 4h Ks` and `5d 9s Qc`. Each history retains all four counterfactual deals and its physical flop probability of 1/9,880. There are eight compatible deal/flop pairs.

The source policy reaches a non-all-in heads-up pot with probability 0.008201647. The two selected histories together have probability 0.007780298, about 94.86% of that heads-up mass. Only one physical flop is expanded at each history, so the source probability of entering an actual betting continuation is approximately 0.00000078748. High **history** coverage does not mean high **flop** coverage.

Selection is performed once using the saved source policy; all training seeds use identical histories, flops and bet sizes. Bet requests start at half the pot on each street and are capped by the existing stack rules. Unsupported flops use residual exact mandatory-checkdown payoffs. Multiway non-all-in pots still use mandatory checkdown. Postflop actions remain check/bet and call/fold, with exact 37-turn/36-river physical chance; no postflop raises or rake are added.

Each learned-policy reach report separately records all terminal categories, checkdown mass by live-seat count, total heads-up mass, selected-history mass, unselected heads-up mass, the fraction of heads-up probability selected, expanded physical-flop mass, and other flops within selected histories. It keeps histories whose reach drops to zero in the declared game; conditional refinement reports an explicit skip rather than removing counterfactual support.

## Paired refinement budgets

The [budget study](data/sixmax-reached-continuation-budgets.json) uses joint training seeds 711 and 712, 500 sampled linear-CFR iterations, and separate 100- and 300-iteration exact conditional CFR+ attempts. Private deals and selected flops are enumerated during joint training; turn and river are sampled. Missing rows receive explicit uniform completion before exact audits.

Every refinement attempt starts from the **same completed joint policy**, with the same frozen learned preflop posterior. It does not continue training from the preceding candidate. Consequently, original policy hashes, parent metrics and conditional before gaps agree across budgets within a seed. This makes the budget comparison paired while avoiding repeated joint training.

Two checks are reported independently: every selected branch must be refined and its maximum conditional gap must meet the supplied 0.05bb target; the exact six-player parent NashConv must not increase by more than the declared 1e-9bb numeric tolerance. The first tested budget meeting both checks is recorded, or null if none does. These are research checks, not trainer admission or a safe subgame-replacement theorem.

At 100 iterations, the raised-pot gap is approximately 0.069bb in both seeds and fails the conditional target, although the full parent score improves slightly. At 300 iterations, both seeds meet both checks. The study retains failed attempts rather than presenting only the successful budget.

| Joint seed | Refinement iterations | Maximum conditional gap before (bb) | Maximum gap after (bb) | Parent NashConv before (bb) | Parent NashConv after (bb) | Both checks pass |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 711 | 100 | 13.946260 | 0.068976 | 0.031506801 | 0.031504217 | No |
| 711 | 300 | 13.946260 | 0.013945 | 0.031506801 | 0.031504209 | Yes |
| 712 | 100 | 13.541738 | 0.068404 | 0.037269131 | 0.037266382 | No |
| 712 | 300 | 13.541738 | 0.013839 | 0.037269131 | 0.037266373 | Yes |

The worst conditional gap improves by about 1,000 times for seed 711. The limped-pot gaps at 300 iterations are 0.004158bb and 0.004179bb. Each candidate replaces 214,144 supported postflop rows and preserves all 8,305 preflop rows. The completed joint game has 222,449 information sets and 922,537 visited states in a full completion walk. Joint training initially visits 209,741/210,929 rows, with 12,708/11,520 explicit uniform completions respectively.

The learned policies retain selected-history probabilities of 0.007353235 and 0.008505980, covering 93.24% and 92.52% of their respective heads-up mass. Physical betting-continuation probabilities are still only 0.00000074425 and 0.00000086093. Refinement freezes preflop, so these reach figures remain unchanged. The conditional improvements are substantial on the selected games; betting on unselected physical flops and in multiway pots remains unmodeled.

## Traversal cost experiment

The [paired performance benchmark](data/sixmax-postflop-traversal-benchmark.json) holds the source-policy posterior, physical flop, legal actions, information sets and chip settlement fixed. It compares repeated action-list validation, validation once on information-set creation with action consistency checked on every visit, and an additional experimental game-state cache. Each solve restarts CFR+ from zero regrets after two warmup iterations. The benchmark uses 20 measured iterations and 107,072 information sets; it tests performance, not convergence.

| Mode | Solve time (seconds) | Exact best-response audit time (seconds) |
| --- | ---: | ---: |
| Repeated action-list validation | 14.0920 | 2.2153 |
| Validation once per information set | 13.2037 | 1.9628 |
| Above plus experimental game-state cache | 13.3209 | 2.0482 |

All three complete strategy hashes and exact best-response reports are identical. Moving validation out of repeated visits gives a 1.067× solve speedup in this local run. The same validation pattern now applies to two-player and multi-player CFR; a fresh 500-iteration six-seat solve reproduces the saved source policy exactly. New lists are still checked for empty, blank and duplicate actions, and changed action lists at a previously seen information set are rejected on every visit.

The additional state cache reuses validated node facts and immutable action children, but adds lookup and retained-memory costs. It gives no measured solve benefit here and remains **disabled by default**. Its opt-in audit path has exhaustive cached/uncached state, chance, action, terminal-utility, all-in and policy-equivalence tests. Timings vary with machine, JVM, warmup and concurrent load; this is one paired local measurement, not a universal speed claim or an improvement in strategic quality. No upstream library defect was found.

## Reproduction

From the repository root in PowerShell:

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.GenerateSixMaxPreflopPack' '-Dexec.args=exact docs/data/sixmax-diverse-spot.json docs/data/sixmax-diverse-source-pack.json 500 2026-10-04T12:00:00Z'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxReachedContinuationStudyMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json docs/data/sixmax-reached-continuation-budgets.json 711,712 500 100,300 2 711 0.05'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPostflopTraversalBenchmarkMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json docs/data/sixmax-postflop-traversal-benchmark.json 711 20'
```

The study accepts 1–3 distinct joint seeds, 1–3,000 joint iterations, 1–3 strictly increasing refinement budgets in [1, 500], 1–4 histories, a signed long flop seed and a positive finite conditional target. It caps private support at four deals and selected coverage at eight compatible deal/flop pairs. Oversized requests fail explicitly rather than pruning hidden worlds. The source must contain a complete policy. Source inputs are limited to 16 MiB and cannot be overwritten by the generator, study or performance benchmark. Strategy-study JSON omits wall-clock timing for deterministic exports; performance JSON intentionally records timings and will differ between runs.

## Validation and next gate

Tests check independently reconstructed source-history ranks, public-history identity, posterior versus counterfactual support, terminal/reach mass identities, zero-reach policies, deterministic selection, incomplete-policy rejection and bounded branch budgets. A repeated real-pack CLI study verifies byte-identical exports, source preservation, provenance hashes, paired original policies across refinement budgets, separate failed quality flags and invalid inputs. The saved diverse source test validates all declared ranges, four equiprobable physical deals, exact payoff counts, board trials, zero SE and the complete source policy.

The next solver gate is expanding physical-flop coverage with measured cost and conditional quality, then feeding improved continuations back into preflop training. Broader private ranges, multiway postflop betting, postflop raises and a credible cash rake model still precede full six-max trainer publication. Increasing history reach and reducing selected conditional gaps does not resolve those remaining restrictions.

A follow-up [physical-flop width study](sixmax-flop-width-study.md) now adds nested menus, exact complete-tree cost preflight, explicit sixteen-pair budgets and a plan-only CLI. It keeps the one-flop selection and existing export schema available. Its coverage and strategic results are reported separately from this study.
