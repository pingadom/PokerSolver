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

Repeat with seeds `43`–`49` for the 5×5 sweep, and with seeds `42` and `43` and range `3x3` for the smaller-range runs. The next experiment tests deliberately mis-specified action likelihoods and a response model that changes with hidden BTN cards. A connected strategic-response audit remains separate.

### Belief mis-specification and hand-dependent response

The audit can now separate the **generating** action frequencies from the **assumed** frequencies used to make the river observation. Every run below draws BTN-open/BB-call deals from the original synthetic model and scores the exact same conditional physical-board values for a given seed. Only the observation's belief changes. The four assumptions are: correct (`p`); shrunk halfway toward an uninformative 50% (`0.5 + 0.5(p - 0.5)`); uninformative (`0.5` for every combo); and reversed (`1 - p`). For this **BB** decision, the BB call likelihood is constant once its own combo is known, so only the assumed BTN-open frequencies affect the posterior key. Uniform must reproduce the static-range key and selected gain exactly; a test checks this control. The reversed model is an intentionally severe error, not an estimate of real opponent uncertainty.

At 50,000 boards and ten required discovery boards per bucket, paired **action-conditioned minus static selected gain** in bb is:

| Range | BTN river response | Seeds | Correct | Shrunk | Uninformative | Reversed |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| 5×5 | Always calls 8bb | 42–49 | +0.0669 to +0.0776 | +0.0558 to +0.0627 | 0 exactly | −0.2017 to −0.1835 |
| 5×5 | Calls with pair or better | 42–45 | +0.0308 to +0.0374 | +0.0225 to +0.0269 | 0 exactly | −0.1524 to −0.1456 |
| 3×3 | Calls with pair or better | 42–43 | −0.0269 to −0.0227 | +0.0076 to +0.0087 | 0 exactly | −0.0504 to −0.0485 |

The **3×3 pair-caller** result is a useful counterexample: even the correct *preflop* posterior makes this equity-band observation worse than the static band under that fixed river response. At seed 42, both have about 98% held-out support, yet static regret is 0.0459bb and conditioned regret is 0.0686bb. The feature represents unconditional showdown margin, while the value of a bet depends on which hidden BTN hands call or fold; this response dependence is a plausible explanation, though the audit does not isolate it from other bucket effects. A more accurate preflop belief alone does not solve the action-value abstraction. On 5×5, moderate shrinkage keeps much of the in-model gain; reversing the signal loses substantially more than the correct belief gains. For example, seed 42's always-call paired intervals are [+0.0684, +0.0782]bb for correct, [+0.0553, +0.0639]bb for shrunk and [−0.2021, −0.1824]bb for reversed. These intervals describe held-out board sampling conditional on one discovery split and one fixed generating/response model; they are not adjusted for the displayed sweep or model uncertainty.

The pair-or-better response reads the BTN's exact hidden hand to determine whether it calls, but the BB observation never sees that hand. Its exact bet increment is computed by independently reweighting the original joint deals by the *generating* BTN-open likelihood and removing board-blocked combos. A test confirms that uniform likelihood reduces to the existing hand-dependent response calculation. This is still forced check-down reach with a selected heuristic BTN response, not a strategic opponent or a calibrated connected policy. It strengthens the case for a response-aware observation and independent opponent-model validation before any trainer use.

Reproduce the sensitivity audit after compiling:

```powershell
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalActionBelief 50000 42 10 5x5 reversed call
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalActionBelief 50000 42 10 3x3 correct pair
```

Repeat `correct`, `shrunk`, `uninformative` and `reversed` on the same seed; runs with a shared seed use the same true deal and board draws. The next experiment models informative flop and turn actions. A response-aware observation and independent strategic validation remain separate gates.

## Flop and turn action-conditioned reach

`PostflopActionBelief` extends the explicit likelihood model to completed flop and turn histories. For each candidate opponent combo and visible board, it assigns probabilities to checking, betting, calling and folding using a deliberately simple pair-or-better versus high-card split. The synthetic fixture checks with probability 0.85 on high card and 0.55 on pair or better; when facing a bet, it calls with probability 0.25 and 0.80 respectively. These probabilities are the same for BTN and BB and on both streets. For a `kbc` history, the BB candidate's likelihood is its check probability times its subsequent call probability; the BTN candidate's likelihood is its bet probability. The model uses only candidate cards and the public board/history. It does not inspect the dealt opponent combo to form an information set. Tests cover all three completed street histories (`kk`, `bc`, `kbc`), direct joint-deal conditioning, hidden-card privacy and unchanged physical payoffs. The new mode has a distinct hash and remains research-only.

`PhysicalPostflopActionBeliefAudit` samples preflop deals conditional on the declared BTN open and BB call, then samples legal physical flop and turn cards. On each street it draws *both* players' check actions from the generating model and retains only check-check paths. At seed 42, about 14% of attempted deals reach the first-BB river decision on this path; the audit reports the actual attempted and accepted counts. For every reached board, the exact BTN posterior reweights the original joint deals by BTN-open, flop-check and turn-check likelihoods, while removing blocked combos. The BB's own call/check likelihoods cancel when conditioning on its known combo and public board. Static, preflop-only and postflop-conditioned observations choose check or an 8bb bet from alternating discovery boards and are scored on the **same** held-out reached boards. Unsupported buckets check. BTN's river response is either always-call or pair-or-better-call.

The assumed postflop model can be correct, uninformative (both hand classes check with probability 0.70) or reversed (high cards check at 0.55 and pairs at 0.85). The generating actions and exact river values stay fixed across assumptions for a given seed. An uninformative assumed check model reproduces the preflop-only river observation and selected gain exactly in tests. At **20,000 accepted rivers**, ten discovery boards required per bucket, the paired *postflop minus preflop-only selected gain* is:

| Range | BTN river response | Seeds | Correct check model | Reversed check model |
| --- | --- | --- | ---: | ---: |
| 5×5 | Always calls | 42–49 | +0.0929 to +0.1126bb | −0.2133 to −0.1811bb |
| 5×5 | Pair or better calls | 42–49 | +0.0072 to +0.0306bb | −0.0877 to −0.0625bb |
| 3×3 | Always calls | 42–45 | +0.1273 to +0.1348bb | −0.3907 to −0.3656bb |
| 3×3 | Pair or better calls | 42–45 | +0.0055 to +0.0189bb | −0.1326 to −0.1172bb |

The 5×5, seed-42 always-call paired interval is [+0.0825, +0.1053]bb for the correct model and [−0.2013, −0.1609]bb for the reversed model. Against a pair caller, the correct-model 5×5 seed-44 interval includes zero ([−0.0007, +0.0150]bb). The 3×3 pair-caller intervals include zero in three of four seeds. More importantly, on 3×3 seed 42, **static** selected gain is 1.7003bb, preflop-only is 1.6337bb and postflop-conditioned is 1.6402bb: adding true-model postflop information does not repair the response-dependent river abstraction. The increased detail can still group decision values poorly. These are conditional normal approximations for held-out board sampling with one discovery policy, not multiplicity-adjusted or opponent-model confidence intervals.

Reproduce after compiling:

```powershell
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalPostflopActionBelief 20000 42 10 5x5 correct call
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalPostflopActionBelief 20000 42 10 3x3 correct pair
```

Repeat seeds and `reversed`/`uninformative` assumptions on the same seed. The model remains a synthetic checkdown experiment: real strategies' action frequencies vary with position, previous actions, ranges, bet sizes and opponent behaviour. Connected CFR actions are not constrained to match these assumed frequencies, and no full-game best-response bound exists. The next gate is an independently justified response-aware observation and a strategic-response audit; no policy from this mode belongs in a trainer pack yet.
