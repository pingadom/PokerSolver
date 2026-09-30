# Connected-game range and flop validation

The [board-bucket experiment](connected-board-bucket-research.md) initially used only two BTN and two BB exact hands. This follow-up uses a **disjoint synthetic range** with three combos per seat and nine legal private deals: BTN `AhAd` (weight 0.5), `QhJh`, `7c7d`; BB `AcKc`, `TcTd`, `8h8s` (weight 1 each). It retains the same 100bb, no-rake BTN open/BB call tree, 2/4/8bb postflop bets, full physical deck and `BOARD_BUCKETS` observation mode. Its game hash is `25e28ef764e7972baa54710b37d78d3f839a60863b0849f76642abe9beb4ebbf`. These are additional synthetic matchups, **not** realistic population ranges or a calibrated 6-max strategy.

With 5,000 chance-sampled CFR iterations, the independent preflop-deviation audit gives:

| Training seed | Visited information sets | BTN `AhAd` opens | BTN `7c7d` opens | Estimated gain from changing `7c7d` first action |
| --- | ---: | ---: | ---: | ---: |
| 42 | 109,846 | 99.4% | 36.1% | 0.201bb |
| 43 | 107,316 | 99.3% | 34.7% | 0.151bb |

The pocket-aces result extends to this new range, while the medium pair still has a potentially material one-decision improvement. Those gains use 5,000 seeded continuations per legal private deal and hold all later policies fixed. They are approximate, not a full best-response gap.

`PhysicalConnectedStreetDeviationAudit` adds a quality check beyond preflop. It samples private deals and follows both learned policies through legal physical boards, stopping at **BB's first decision on a requested flop, turn or river** if the hand reaches it. States are grouped by **BB's information set**, so one alternative check/bet decision must serve every hidden BTN hand and physical board mapped to that observation. Within each group, alternating samples choose an action on a discovery half and evaluate it on an independent held-out half. Later policies stay fixed; missing policy entries use the declared uniform fallback. The printed groups are ranked by discovery-side reach-weighted opportunity, not by the held-out outcome.

For seeds 42 and 43, each with 50,000 attempted deals **per street** and four continuation rollouts per action:

| Seed | Street | Reached first BB decision | Observed buckets | Missing first-action policies | Reached states in buckets with at least 10 held-out observations |
| --- | --- | ---: | ---: | ---: | ---: |
| 42 | Flop | 16,504 | 78 | 2 | 98.6% |
| 42 | Turn | 13,025 | 997 | 16 | 72.2% |
| 42 | River | 9,478 | 4,026 | 158 | 9.1% |
| 43 | Flop | 17,678 | 79 | 0 | 98.8% |
| 43 | Turn | 13,818 | 1,057 | 33 | 70.8% |
| 43 | River | 10,056 | 4,284 | 207 | 8.7% |

The held-out results include both positive and negative gains from the action selected on discovery data. For example, seed 42's flop `8h8s` bucket `m1p0s0f0d1h0` had 154 discovery and 154 held-out states; choosing check from discovery was estimated **0.363bb worse** than the learned mixture on held-out states, with substantial sampling uncertainty. By the river, most buckets have fewer than ten held-out observations despite 50,000 attempted deals. This is a more precise scaling bottleneck than the total number of visited information sets: the current observation/history scheme offers too little evidence to validate most later-street choices.

Reproduce after compiling from the repository root:

```powershell
mvn -q -pl solver -am -DskipTests compile
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedRangeValidation 5000 42 5000 50000 4
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedRangeValidation 5000 43 5000 50000 4
```

Each street audit measures only **one first-to-act BB decision under the learned earlier-street reach**. It does not optimize subsequent actions, audit BTN's later decisions, certify a best-response bound or quantify error from merging distinct physical boards. Ten held-out observations are only a descriptive support threshold, not a precision guarantee. The range is still tiny, with four seats folding by assumption. A trainer pack needs a better abstraction test and a much broader strategic quality check before admission.

A [controlled board-granularity follow-up](connected-bucket-granularity-research.md) maps identical physical hands and action paths into fine and coarse observations. The coarse mode sharply improves river sample support, while held-out strategic quality remains unresolved. It is a research comparison rather than a replacement trainer pack.

## Strict connected-policy street deviations

`PhysicalStrictConnectedStreetDeviationAudit` revisits the first BB decision on flop, turn and river using the connected strategy's **own sampled physical reach**. It differs from the earlier audit above by rejecting a deal when an earlier action has no learned strategy and rejecting a candidate state when either check or bet continuation needs an unlearned strategy. It records missing reach, BB root and each continuation branch separately; no uniform action is silently inserted. A BB information set chooses one check/bet action from its discovery observations, then compares it with the original BB mixed strategy on separate held-out physical states. Future cards and actions before the river are sampled with paired random seeds for the two root actions. Once the river is dealt, all remaining positive-probability learned action branches are **enumerated exactly**. A unit test checks the river expectation against explicit terminal payoffs and checks that a missing positive-probability branch is rejected.

The table reports **selected minus learned-mix BB utility**, in bb, for 50,000 attempted physical deals *per street*, four sampled continuation pairs per flop/turn state, ten required discovery observations per BB information set, and coarse-board information sets. The listed seed is the **base seed**: the solve uses `base seed + 100003`, while flop, turn and river audits use `base seed + 0`, `+ 1` and `+ 2`. River uses exact action continuation and only one evaluation per physical state. Parentheses contain the conditional held-out standard error:

| Range | CFR iterations | Seed | Flop | Turn | River |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3×3 | 3,000 | 42 | +0.0021 (0.0239) | **+0.0822 (0.0286)** | +0.0905 (0.0452) |
| 3×3 | 3,000 | 43 | −0.0165 (0.0250) | **+0.0879 (0.0245)** | +0.0455 (0.0430) |
| 5×5 | 3,000 | 42 | −0.0167 (0.0236) | −0.0008 (0.0347) | +0.0723 (0.0374) |
| 5×5 | 3,000 | 43 | −0.0044 (0.0291) | +0.0601 (0.0306) | −0.0058 (0.0397) |
| 3×3 | 10,000 | 42 | −0.0040 (0.0227) | −0.0014 (0.0269) | −0.0077 (0.0384) |
| 3×3 | 10,000 | 43 | −0.0621 (0.0283) | +0.0170 (0.0264) | −0.0389 (0.0458) |
| 5×5 | 10,000 | 42 | −0.0149 (0.0276) | −0.0105 (0.0332) | −0.0966 (0.0385) |
| 5×5 | 10,000 | 43 | +0.0064 (0.0312) | −0.0165 (0.0320) | +0.0033 (0.0491) |

The 3×3 turn gain at 3,000 iterations has an approximate held-out 95% interval above zero for both seeds, but at 10,000 iterations the same audit finds no resolved turn gain. This is **consistent with** improvement as training continues on this tiny fixture; it does not establish general CFR convergence. Selected actions can also be worse than the learned mix on held-out states: 3×3 seed 43's 10,000-iteration flop result is −0.0621bb. Discovery selection, sampled continuations, abstraction and the limited seed sweep all contribute uncertainty. These intervals condition on one trained policy and one discovery split and are not adjusted for looking at multiple streets, ranges, budgets and seeds.

At 3,000 iterations the four runs discarded **zero** deals for missing earlier policy; root and continuation gaps were small but nonzero. At 10,000 iterations all four runs had zero missing keys in the sampled reach and continuations. Near-complete key coverage does not imply sufficient decision support: on 5×5 seed 42 at 10,000 iterations, 4,247 of 5,463 held-out river states belonged to BB information sets with ten discovery observations, versus 8,932 of 8,934 flop held-out states. The report prints both denominators. This remains a one-decision, first-to-act **BB** audit, not a BTN audit, a simultaneous full-game best response, a lower/upper exploitability bound or a 6-max cash trainer pack.

Reproduce from `solver/` after compiling:

```powershell
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.BenchmarkPhysicalStrictConnectedStreetDeviation' '-Dexec.args=3000 50000 4 10 42 3x3'
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.BenchmarkPhysicalStrictConnectedStreetDeviation' '-Dexec.args=10000 50000 4 10 43 5x5'
```

Repeat seeds `42` and `43`, both iteration budgets, and both ranges for the displayed table. The next backend target is a full-game deviation or best-response measurement on a bounded connected game, with separate BTN and BB checks and an explicit policy for unvisited information sets.

## Exact best responses on sampled physical-chance subgames

`PhysicalDeckChanceSubgame` keeps the physical BTN/BB game, its real card evaluator, blockers, legal actions, information sets and **all original weighted private deals**, but replaces each subsequent chance node with one to four declared quantiles of that node's legal physical flop/turn/river distribution. Duplicate physical outcomes are combined with their multiplicity. This creates a finite **different game** on which the existing `HeadsUpBestResponse` can enumerate both players' full best responses. `PhysicalDeckSubgameBestResponseAudit` projects a sparse full-deck CFR policy onto that game, completes only subgame information sets missing from the policy with explicit uniform actions, and reports both the missing-key count and the probability that a trajectory under the completed profile touches one. The exact gap is a valid bound **for the restricted chance game and completed profile only**. The chance menu and completion are part of the model, never a hidden approximation to full-deck exploitability.

At 3,000 physical-game sampled-chance CFR iterations (solve seed 42), with the 3×3 coarse-board synthetic range:

| Chance points per street | Chance seed | Restricted states visited | Missing strategy keys | Physical-trained profile gap on this subgame | Same-subgame CFR+ control gap, 1,000 iterations |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 142 | 1,099 | 0 | 5.8982bb | — |
| 2 | 142 | 7,012 | 0 | 3.9024bb | 0.000545bb |
| 2 | 143 | 7,012 | 0 | 5.3650bb | 0.001257bb |
| 2 | 144 | 7,012 | 0 | 3.4947bb | 0.001910bb |
| 4 | 142 | 50,914 | 0 | 2.9140bb | — |

On 5×5 at two chance points per street and chance seed 142, the physical-trained gap is **2.9429bb**, versus **0.000838bb** for the same-subgame CFR+ control; 19,476 restricted states and 1,758 learned information sets were visited with zero missing keys. Training the 3×3 physical policy for 10,000 rather than 3,000 iterations on the *same* two-point subgame did not close its gap (4.0756bb versus 3.9024bb). These are not signs that the exact best-response calculation is broken: the controls solve their respective finite games to small gaps. They expose that a two-point physical runout menu can induce a very different optimal strategy and public-card information structure. Even four points per street leave a large gap on the inspected menu. The tests check root-deal preservation, blocker-aware physical chance outcomes, duplicate-outcome weighting, state-budget enforcement and invalid strategy rejection.

Reproduce from `solver/` after compiling:

```powershell
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.BenchmarkPhysicalDeckSubgameBestResponse' '-Dexec.args=3000 2 42 142 3x3 2000000 1000'
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.BenchmarkPhysicalDeckSubgameBestResponse' '-Dexec.args=3000 2 42 142 5x5 2000000 1000'
```

Vary the chance seed and menu size to inspect sensitivity. The restricted-game gap cannot be cited as the full-deck policy's Nash gap or used to reject or publish a trainer chart by itself. A stronger next method needs full-deck chance sampling **inside** the best-response computation, or a much broader independently held-out chance approximation with quantified sampling error and fallback sensitivity.
