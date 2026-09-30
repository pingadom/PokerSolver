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

## River response-value observation

`PublicRiverResponseValueBucket` is an **audit-only** BB river feature. On the same reached checkdown boards, it computes the exact posterior expectation of *8bb bet minus check* under a declared BTN river response, after BTN-open and flop/turn-check likelihoods and public-card blockers. It retains the coarse public-board key and replaces the showdown-equity band with five bands of that expected value. The true response that generates the held-out payoff can differ from the assumed response used in the observation. The discovery/held-out split, minimum-support rule, physical boards, and exact physical oracle are shared with the static, preflop-only and postflop-conditioned modes. A direct joint-deal test checks both response-value calculations. The new mode is not wired into connected CFR or a trainer pack.

At 20,000 accepted rivers, ten discovery observations per bucket and correct synthetic flop/turn check likelihoods, **response-value minus postflop-equity selected gain** was:

| Range | True BTN response | Assumed response | Seeds 42–45, paired gain |
| --- | --- | --- | ---: |
| 5×5 | Pair or better calls | Pair or better calls | +0.0370 to +0.0813bb |
| 5×5 | Pair or better calls | Always calls | +0.0309 to +0.0499bb |
| 5×5 | Always calls | Always calls | +0.0188 to +0.0521bb |
| 5×5 | Always calls | Pair or better calls | **−0.2688 to −0.1745bb** |
| 3×3 | Pair or better calls | Pair or better calls | +0.0789 to +0.1232bb |

For 5×5, seed 42 with the correctly assumed pair caller, selected gain rises from 1.6518bb to 1.7331bb, while held-out support changes from 93.3% to 92.5%; the paired normal-approximation interval is [+0.0697, +0.0930]bb. Under a *wrong* pair-caller assumption when BTN always calls, seed 42 instead loses 0.1745bb with interval [−0.1954, −0.1536]bb. The asymmetric model error is a concrete blocker for promoting this observation. A trial feature based on the worse of the two response values collapsed to the pair-caller buckets on these fixtures and inherited the same always-call loss, so it was removed.

These are in-model fixed-response experiments. The feature directly encodes the assumed response's bet/check value sign, so in-model separation is partly by construction; it is not evidence of an equilibrium policy or a realistic opponent model. The intervals condition on one discovery split and synthetic response and do not cover model uncertainty or the seed sweep. The next backend gate is a river response derived from independently solved or strategically adapting BTN decisions, with a held-out best-response test in the connected game. A credible 6-max trainer chart still needs the broader preflop tree, rake, range provenance and full-game validation.

Reproduce after compiling:

```powershell
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalPostflopActionBelief 20000 42 10 5x5 correct pair pair
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalPostflopActionBelief 20000 42 10 5x5 correct call pair
```

## One-step strategic river caller and support backoff

`StrategicRiverCallPolicy` replaces the BTN's fixed always-call or pair-call rule with an exact one-step call/fold best response to a **declared BB betting likelihood**. For each BTN combo and public river, it removes blocked BB combos, weights the remaining BB prior by its observed preflop call, flop/turn actions and the chance of a river bet, then calls if the weighted call-minus-fold value is nonnegative. The fixture bets with probability 0.25 with high card and 0.75 with pair or better; `strategic-reversed` swaps those two probabilities for a sensitivity test. The model receives a `PublicRiverHistory` containing no private BB cards, and tests verify that changing the dealt BB hand cannot change BTN's choice. Tests also cover pot odds, blockers, action-conditioned inference and agreement between the two independent BB bet-value calculations.

This response is **one-step optimal against the declared betting frequencies only**. The BB bucket policy selected by this audit has different frequencies, and no equilibrium or full-game best-response claim follows. The audit keeps the same physical reached boards, discovery split and oracle when only the assumed response changes.

The strategic model exposed a support problem. With a correctly assumed strategic caller, the raw response-value bucket creates roughly 493–513 discovered buckets and has only 86.5–87.1% held-out support, versus about 93.3–94.0% for the postflop-equity bucket. Unsupported raw response buckets check, erasing positive bets. `responseWithBackoff` instead uses the postflop bucket's discovery decision when the response-value bucket has fewer than ten discovery boards. On 20,000 accepted rivers, ten-board support and 5×5 seeds 42–45, the paired gain over postflop-only was:

| True BTN response | Assumed response | Raw response-value | Response with support backoff |
| --- | --- | ---: | ---: |
| Strategic (0.25 high / 0.75 pair BB betting) | Matching strategic | −0.0358 to −0.0173bb | **+0.0654 to +0.0693bb** |
| Strategic | Reversed strategic betting model | −0.0548 to −0.0252bb | +0.0512 to +0.0549bb |
| Always calls | Pair or better calls | −0.2688 to −0.1745bb | **−0.1115 to −0.0902bb** |
| Pair or better calls | Matching pair response | +0.0370 to +0.0813bb | +0.0802 to +0.0959bb |

On the smaller 3×3 range, matching-strategic backoff gains +0.1373 to +0.1518bb across the same four seeds; reversing the assumed betting likelihood still yields +0.0957 to +0.1059bb. For the matching 5×5 strategic model at seed 42, the backoff gain is +0.0677bb with a conditional paired normal-approximation interval [+0.0608, +0.0746]bb. The same split's raw response-value gain is −0.0289bb, so more detailed information **without adequate support** worsens decisions. Backoff also reduces, but does not eliminate, the wrong pair-caller loss against an always-calling BTN. These intervals condition on one discovery split, synthetic reach and a declared response; the sweep is not multiplicity-adjusted. The feature remains audit-only and should not be exported as a trainer policy.

Reproduce from the repository root after compiling:

```powershell
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalPostflopActionBelief 20000 42 10 5x5 correct strategic strategic
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalPostflopActionBelief 20000 42 10 5x5 correct call pair
```

The next gate is to derive BTN responses from a **connected solved policy**, then score them against BB decisions on independently sampled physical hands. That would test strategic consistency across streets rather than assuming a separate river betting likelihood.

## Connected-policy river response transfer

`ConnectedRiverCallPolicy` reads a **mixed** BTN call probability from a chance-sampled connected CFR solution at the BTN's river information set after a BB bet. It constructs that information set from the BTN combo and `PublicRiverHistory`; the private BB combo is never passed to the policy. Unit tests check that swapping legal hidden BB hands leaves the key unchanged, that a 25% call is scored as a probability rather than thresholded, and that invalid learned probabilities fail. Each missing strategy key uses an explicit pair-call fallback and is counted by query and by distinct information set. The policy identity binds the game hash, iteration count, exact strategy content and fallback definition.

The benchmark trains the connected **coarse-board** game with 3,000 vanilla, sampled-chance CFR iterations. A primary solve uses `audit seed + 100003`; an alternate solve uses `audit seed + 200003`. The same 20,000 physically dealt, action-conditioned checkdown rivers and discovery/held-out split are used when the true response comes from the primary solution and the observation assumes the independently trained alternate solution. This preserves the pairwise comparison while testing sensitivity to a second solved policy. It remains **off-policy transfer**: preflop and flop/turn reach are generated by the synthetic likelihood model, not by the connected solution's own action frequencies. The true response on a missing primary key also uses the documented pair-call fallback. No full-game best-response or on-policy value claim follows.

At ten discovery boards per bucket, **response-value with support backoff minus postflop-equity selected gain** is:

| Range | True / assumed BTN response | Seeds | Paired gain |
| --- | --- | --- | ---: |
| 3×3 | Primary / independent alternate CFR solution | 42–45 | +0.0477 to +0.0688bb |
| 5×5 | Primary / independent alternate CFR solution | 42–45 | **+0.0102 to +0.0392bb** |
| 5×5 | Primary / the same CFR solution | 42 | +0.1466bb |

The large same-solution result is an in-model upper comparison, not evidence that two independently learned policies agree. At 5×5 seed 42, the alternate-solution raw response-value bucket loses 0.0242bb against postflop; support backoff raises its paired gain to +0.0392bb with a conditional interval [+0.0305, +0.0479]bb. At seed 43 the backoff gain is only +0.0102bb with interval [+0.0038, +0.0165]bb. These intervals hold the two solved strategies, synthetic reach and discovery split fixed and are not adjusted for the sweep. They do not measure uncertainty from retraining CFR.

The coarse information sets are almost fully covered **on queried checkdown states**, but not universally. At 5×5 seed 42, the primary policy has 220/220 queried keys; the alternate has 218/220, with 15 of 84,260 alternate response lookups falling back. At seed 44, each solve covers 217/220 distinct queried keys. The displayed query rate rounds to 100.0%, so raw counts must be inspected. These counts say nothing about unqueried river histories or how often the connected solution reaches those histories itself.

Reproduce after compiling:

```powershell
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalPostflopActionBelief 20000 42 10 5x5 correct connected connected-alt 3000
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalPostflopActionBelief 20000 42 10 3x3 correct connected connected-alt 3000
```

The next gate is an **on-policy connected-game audit**: sample reaches from the learned preflop and postflop strategies, evaluate BB decisions against BTN responses from the same policy at those reached public histories, and quantify missing-policy mass and one-decision deviation on independent physical runouts. Only then can the response abstraction be judged as part of a connected solver rather than as a transfer onto a synthetic reach model.

## On-policy connected river response and one-decision audit

`PhysicalConnectedRiverResponseAudit` now samples **physical hands and public runouts through the primary connected CFR average strategy**. It stops at the first BB river decision. A trajectory with an unlearned earlier action policy is discarded and counted, instead of receiving a uniform continuation. At the reached state, it requires a learned BB check/bet strategy, learned BTN call/fold strategies from **both** independently solved policies, and learned primary-policy actions after a BB check. Missing keys are counted separately; this audit never invokes the pair-call fallback used by the earlier off-policy transfer experiment. The BTN response key uses its own cards and public history, without the BB's hidden cards.

For each eligible physical state, the audit evaluates the entire short river tree exactly: BB bet followed by BTN call/fold, or BB check followed by BTN check/bet and then BB call/fold. The alternate solution changes **only BTN's response to a BB bet**. The primary solution generates reach, the BB reference mix and the other continuations. Alternating observations within each BB information set select check/bet on discovery states and score that selection on held-out states against the primary BTN response. This avoids choosing an action with knowledge of the held-out private hand. A same-solution control yields exactly zero response and selection difference. The reported paired standard error conditions on the two fixed solutions and discovery split; it does not cover CFR retraining, game abstraction, or selection across tested seeds.

With 3,000 sampled-chance vanilla CFR iterations **per independent solve**, 50,000 attempted deals, ten discovery states required per BB information set, and coarse-board information sets:

| Range | Seed | River reaches | Fully evaluated | Supported held-out / held-out | Mean absolute BTN call difference | Alternate-informed minus primary-informed held-out BB gain |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 3×3 | 42 | 13,318 | 13,317 | 5,369 / 6,470 | 0.0787 | −0.1021bb (paired SE 0.0283bb) |
| 3×3 | 43 | 12,541 | 12,537 | 4,976 / 6,091 | 0.0857 | −0.0077bb (paired SE 0.0341bb) |
| 5×5 | 42 | 11,178 | 11,177 | 4,188 / 5,402 | 0.0880 | −0.0668bb (paired SE 0.0319bb) |
| 5×5 | 43 | 10,998 | 10,995 | 4,031 / 5,291 | 0.0926 | +0.0239bb (paired SE 0.0465bb) |

Earlier-policy coverage was complete on these four runs, but river coverage was not universal: one missing BB root key on each seed-42 run, and up to four missing BTN response keys in a run. The audit reports those counts instead of hiding them behind rounded percentages. The 5×5 seed-42 paired normal-approximation interval is [−0.1292, −0.0043]bb; seed 43's is [−0.0672, +0.1150]bb. The direction changes across seeds, and the primary-informed action itself sometimes underperforms the original primary mix on held-out hands. Thus the result does **not** validate the learned river policy for trainer grading. It is also one river decision conditional on a primary-policy reach, not a full-game best response or a Nash-gap estimate. The synthetic 3×3/5×5 ranges and coarse observation remain far from general 6-max cash.

Reproduce after compiling (repeat with seed `43` and range `3x3`):

```powershell
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalConnectedRiverResponse 3000 50000 42 10 5x5
```

The next solver gate is a multi-seed and iteration-budget stability study that includes full connected-game deviation measurement and an independently justified response-aware information set. A held-out gain at this one decision cannot substitute for an exploitability bound, so no new trainer pack should be promoted from this audit.
