# Range-aware river observations in the connected physical-deck game

The [board-observation frontier](connected-abstraction-frontier.md) found that coarse buckets provide enough river samples but merge boards with opposite showdown incentives. This experiment adds `RANGE_EQUITY_RIVER_BUCKETS` to the research-only connected BTN–BB game. The physical private deals, all legal public runouts, betting actions and terminal chip payoffs are unchanged. Flop and turn observations use the existing coarse made-hand/paired-board key; river observations add a five-band showdown-margin feature. Own exact cards and earlier street observations/actions remain in the information set.

The river margin is **win probability minus loss probability**, with ties worth zero, calculated by exact seven-card showdown against every *unblocked* combo in the declared static opponent range. The bands split at −0.5, 0 and +0.5, with a separate zero band. The key uses only the observer's own cards, public board and declared range; it never reads the dealt opponent hand. Its hash is distinct from the existing three modes. The range is **not updated for preflop or postflop actions**, so the feature may misrepresent the actual opponent distribution after an open, call, bet or raise. It is an abstraction experiment, not a Bayesian belief solver or a GTO chart.

## Common-reach support and fixed-response decision tests

All comparisons map the *same* legal physical hands and action paths into each observation mode. At 50,000 uniform-action attempted deals, the fraction of reached first-BB river decisions in buckets with at least 20 observations is:

| Synthetic range | Seed | Fine | Texture | Coarse | Range-aware |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3×3 | 42 | 1.5% | 8.7% | 57.9% | 16.6% |
| 3×3 | 43 | 0.9% | 8.5% | 53.5% | 13.9% |
| 5×5 | 42 | 0.0% | 2.5% | 43.6% | 7.9% |
| 5×5 | 43 | 0.0% | 4.6% | 44.7% | 8.7% |

The range-aware key is a refinement of coarse on the river, so it sacrifices coverage. In the [always-called 8bb bet counterfactual](connected-bucket-granularity-research.md#river-information-loss-counterfactual), its river buckets never contain both positive and negative exact showdown margins on the sampled states. That is **by construction**: the feature encodes the margin's sign. Zero alias loss in that test is a correctness check, not independent evidence of poker strategy quality.

`PhysicalRiverHeldOutDecisionAudit` chooses each observation's check/bet action from alternating discovery boards, requiring ten observations; it checks when unsupported. It then evaluates the chosen action on separate held-out boards, all with exact blocker-aware opponent weighting. Regret is against an oracle that sees each physical board's exact conditional value in the *same fixed-response model*. These are synthetic response models, not equilibrium play:

- **Hand-independent calls:** BTN calls the 8bb bet with a fixed probability and otherwise folds, regardless of its private hand. A 50% call rate shifts the profitable-bet threshold away from the margin's zero boundary.
- **Pair-or-better calls:** BTN calls when its own seven-card hand makes at least one pair and folds high-card hands. This response depends on hidden BTN cards, so the range-aware margin feature does not directly encode its action.

At 50,000 sampled boards, held-out regret in bb is:

| Range | Seed | Response | Fine | Texture | Coarse | Range-aware |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| 3×3 | 42 | 50% independent call | 0.577 | 0.298 | 0.147 | 0.033 |
| 3×3 | 43 | 50% independent call | 0.626 | 0.326 | 0.146 | 0.046 |
| 5×5 | 42 | 50% independent call | 0.696 | 0.345 | 0.093 | 0.087 |
| 5×5 | 43 | 50% independent call | 0.693 | 0.367 | 0.091 | 0.087 |
| 3×3 | 42 | pair-or-better call | 1.345 | 1.080 | 0.859 | 0.059 |
| 3×3 | 43 | pair-or-better call | 1.403 | 1.110 | 0.870 | 0.083 |
| 5×5 | 42 | pair-or-better call | 1.108 | 0.867 | 0.523 | 0.109 |
| 5×5 | 43 | pair-or-better call | 1.099 | 0.892 | 0.513 | 0.104 |

The hand-dependent test is a stronger check than always-call, but it is still a chosen heuristic opponent and forced check-down reach. The small 5×5 advantage over coarse at 50% calls illustrates that a richer feature is not automatically worth its lost sample support. The samples and ranges do not provide a confidence-certified exploitability bound.

### Paired held-out uncertainty

The benchmark now scores the **difference in selected gain** between the range-aware and coarse buckets on each of the *same* 25,000 held-out physical boards. It reports a standard error from the per-board paired differences and a normal-approximation 95% interval. Positive values favour the range-aware bucket in the stated fixed-response experiment:

| Range | Seed | BTN response | Range-aware minus coarse gain (bb) | Paired SE (bb) | Approximate 95% interval (bb) |
| --- | ---: | --- | ---: | ---: | ---: |
| 3×3 | 42 | 50% independent call | 0.1145 | 0.0026 | [0.1094, 0.1196] |
| 3×3 | 43 | 50% independent call | 0.0995 | 0.0028 | [0.0940, 0.1050] |
| 5×5 | 42 | 50% independent call | 0.0052 | 0.0023 | [0.0006, 0.0098] |
| 5×5 | 43 | 50% independent call | 0.0046 | 0.0025 | [−0.0002, 0.0094] |
| 3×3 | 42 | pair-or-better call | 0.8000 | 0.0125 | [0.7755, 0.8246] |
| 3×3 | 43 | pair-or-better call | 0.7876 | 0.0127 | [0.7626, 0.8125] |
| 5×5 | 42 | pair-or-better call | 0.4140 | 0.0081 | [0.3981, 0.4300] |
| 5×5 | 43 | pair-or-better call | 0.4088 | 0.0082 | [0.3928, 0.4248] |

The 5×5, seed-43 independent-call interval includes zero. A wider **seed-sensitivity sweep** of the same 5×5, 50% independent-call experiment (seeds 42–57, 50,000 attempted boards each) finds paired gains from **−0.0071bb to +0.0124bb**. Five of the 16 splits favour coarse; seed 44 gives −0.0071bb with an individual approximate interval of [−0.0124, −0.0018]bb. Eight of the 16 individual intervals include zero. Thus the small positive gain in the original two seeds is not robust to reselecting discovery and held-out samples. These are descriptive split-level results, not multiplicity-adjusted intervals.

Each per-split interval conditions on the **one discovery sample** that selected its bucket actions and on the declared fixed BTN response. It does not include uncertainty from reselecting the discovery sample, the opponent model, the synthetic ranges or the CFR strategy. The benchmark also prints held-out regret standard errors and approximate upper limits for each observation mode. None of these intervals is a full-game best-response bound.

## Connected-CFR smoke runs

At 5,000 chance-sampled CFR iterations, the new mode remains affordable but costs more than the previous texture mode. Seeded runs with the existing preflop one-decision and sample-split first-BB-street audits give:

| Range | Seed | Visited information sets | Solve time | BTN aces open | Reached BB river states with ≥10 held-out observations |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3×3 | 42 | 14,362 | 17.3s | 99.8% | 21.7% |
| 3×3 | 43 | 13,984 | 17.3s | 99.6% | 21.7% |
| 5×5 | 42 | 24,872 | 50.7s | 99.8% | 22.8% |
| 5×5 | 43 | 24,404 | 50.6s | 99.9% | 20.0% |

These learned-policy reaches are **different** across modes and cannot be read as a controlled quality comparison. Aces behave sensibly, but on 5×5 seed 42 the independent preflop audit estimates a **0.215bb** one-decision gain for BTN `7c7d`, with substantial sampling uncertainty. Some river policies are still missing, and most reached river states do not have ten held-out observations. No full-game best-response or strategy-gap bound is available for this physical-deck profile. It remains outside solution packs and the trainer.

Reproduce from the repository root after compiling:

```powershell
mvn -q -pl solver -am -DskipTests compile
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalBoardObservationCoverage 50000 42 20 5x5
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalRiverHeldOutDecision 50000 42 10 5x5 0.5 independent
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalRiverHeldOutDecision 50000 42 10 5x5 1.0 pair
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedRangeValidation 5000 42 1000 10000 2 equity 5x5
```

Repeat with seed `43` and range `3x3`. The next experiment is an explicitly action-conditioned opponent belief, followed by tests of its robustness and a strategic-response audit over the connected game. More fixed-response wins alone cannot make this a publishable 6-max trainer policy.

To reproduce the 16-seed sensitivity sweep, repeat the independent-call command above with seeds `42` through `57` and range `5x5`.

## Explicit preflop-action belief experiment

`PreflopActionBelief` now supplies a separate, versioned river observation mode. For a BB river decision, each legal BTN combo's prior weight is multiplied by its declared probability of opening to 3bb; for a BTN river decision, the analogous update uses the BB combo's declared call probability. Own cards and the public river remove blocked combos before the equity band is calculated. The game hash includes every likelihood. Chance deals, legal actions, chip payoffs and the original static-range mode are unchanged. Tests compare this update with direct joint-deal conditioning and confirm that two states with the same own cards and public history have the same information set even when the dealt opponent cards differ.

The synthetic fixture assigns BTN open probabilities of 0.95 for AA, 0.75 for QJ suited and 0.50 for 77; the 5×5 fixture also assigns 0.35 for 65 suited and 0.65 for KQ suited. BB call probabilities are 0.80 for AK suited, 0.75 for TT and 0.60 for 88; the 5×5 fixture adds 0.35 for 44 and 0.65 for 99. These are **chosen test likelihoods**, not estimated poker frequencies or a strategy learned by this solver.

`PhysicalActionBeliefAudit` draws physical deals in proportion to prior weight times both observed-action likelihoods. It forces flop and turn checks, then compares the static and action-conditioned river bucket's check/bet decisions on the same held-out boards. Discovery uses alternating boards and needs ten observations per bucket; an unsupported bucket checks. BTN always calls the 8bb river bet. The oracle sees each physical board's exact BTN distribution after the declared BTN open likelihood and blockers, computed independently from the initial joint deals. The paired result is conditioned on this synthetic model and a single discovery split:

| Range | Seeds | Action-conditioned minus static selected gain | Interpretation |
| --- | --- | --- | --- |
| 3×3 | 42 | +0.0008bb; approximate 95% interval [−0.0043, +0.0059]bb | No resolved gain |
| 3×3 | 43 | −0.0030bb; approximate 95% interval [−0.0089, +0.0029]bb | No resolved gain |
| 5×5 | 42 | +0.0733bb; approximate 95% interval [+0.0684, +0.0782]bb | Helps under the declared model |
| 5×5 | 43–49 | +0.0669 to +0.0776bb | Positive in all seven further splits |

At 5×5 seed 42, static and conditioned held-out regret are 0.1193bb and 0.0460bb respectively; both have about 97% ten-board support. The 5×5 improvement is an **in-model proof of concept**: the data-generating reach and posterior use the same specified likelihoods. It does not test mis-specified beliefs, data-estimated likelihoods, informative postflop actions, or a strategic BTN response. The connected CFR tree is still free to choose preflop actions with different frequencies, so its policies are not automatically calibrated to this exogenous belief. This mode stays out of solution packs and the trainer.

Reproduce after compiling:

```powershell
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalActionBelief 50000 42 10 5x5
```

Repeat with seeds `43`–`49` for the 5×5 sweep, and with seeds `42` and `43` and range `3x3` for the smaller-range runs. The next gate is robustness to deliberately mis-specified action likelihoods and a response model that changes with hidden BTN cards and public actions; only then should this be considered for a connected strategic-response audit.
