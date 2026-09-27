# Connected-game range and flop validation

The [board-bucket experiment](connected-board-bucket-research.md) initially used only two BTN and two BB exact hands. This follow-up uses a **disjoint synthetic range** with three combos per seat and nine legal private deals: BTN `AhAd` (weight 0.5), `QhJh`, `7c7d`; BB `AcKc`, `TcTd`, `8h8s` (weight 1 each). It retains the same 100bb, no-rake BTN open/BB call tree, 2/4/8bb postflop bets, full physical deck and `BOARD_BUCKETS` observation mode. Its game hash is `25e28ef764e7972baa54710b37d78d3f839a60863b0849f76642abe9beb4ebbf`. These are additional synthetic matchups, **not** realistic population ranges or a calibrated 6-max strategy.

With 5,000 chance-sampled CFR iterations, the independent preflop-deviation audit gives:

| Training seed | Visited information sets | BTN `AhAd` opens | BTN `7c7d` opens | Estimated gain from changing `7c7d` first action |
| --- | ---: | ---: | ---: | ---: |
| 42 | 109,846 | 99.4% | 36.1% | 0.201bb |
| 43 | 107,316 | 99.3% | 34.7% | 0.151bb |

The pocket-aces result extends to this new range, while the medium pair still has a potentially material one-decision improvement. Those gains use 5,000 seeded continuations per legal private deal and hold all later policies fixed. They are approximate, not a full best-response gap.

`PhysicalConnectedFlopDeviationAudit` adds a second quality check beyond preflop. It samples private deals and the learned BTN/BB preflop decisions, then draws a legal physical flop only when both players continue. The first flop action belongs to BB. States are grouped by **BB's information set**, so one alternative check/bet decision must serve every hidden BTN hand and physical board mapped to that observation. Within each group, alternating samples choose an action on a discovery half and evaluate it on an independent held-out half. Later policies stay fixed; missing policy entries use the declared uniform fallback. The printed groups are ranked by discovery-side reach-weighted opportunity, not by the held-out outcome.

For seeds 42 and 43, each with 50,000 attempted deals and four continuation rollouts per action, **16,504** and **17,678** attempts reached the first flop decision. They populated **78** and **79** observable buckets; **2** and **0** reached decisions used the uniform fallback. The held-out results include both positive and negative gains from the action selected on discovery data. For example, seed 42's `8h8s` bucket `m1p0s0f0d1h0` had 154 discovery and 154 held-out states; choosing check from discovery was estimated **0.363bb worse** than the learned mixture on held-out states, with substantial sampling uncertainty. This is direct evidence that picking a promising flop action from a small sample can overfit even inside the coarse abstraction.

Reproduce after compiling from the repository root:

```powershell
mvn -q -pl solver -am -DskipTests compile
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedRangeValidation 5000 42 5000 50000 4
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedRangeValidation 5000 43 5000 50000 4
```

This audit measures only **one BB flop decision under the learned preflop reach**. It does not optimize later actions, evaluate turn/river deviations, certify a best-response bound or quantify error from merging distinct physical boards. The range is still tiny, with four seats folding by assumption. A trainer pack needs a better abstraction test and a much broader strategic quality check before admission.
