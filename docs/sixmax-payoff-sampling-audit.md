# Six-seat payoff sampling budget audit

The bounded six-seat preflop game has an estimated standard error for every sampled terminal payoff. `SixMaxPreflopPayoffSamplingAudit` accepts a declared **maximum terminal-payoff SE target in bb**, a starting board count, and a hard maximum. It rebuilds the same exact-range game on a fixed seeded board stream, doubling boards per deal up to the cap, and records the largest terminal-payoff SE at each budget. The result states whether the target was met. A caller that requires this gate can use `requireTargetMet()`; it rejects an exhausted run rather than silently treating its payoff table as sufficiently sampled.

The statistic is the largest estimated **standard error**, not a confidence bound or a bound on strategy EV, regret, or NashConv. It is estimated from the same boards used to choose the stopping point, so meeting the target does not by itself prove the true sampling error is below it. The board stream is deterministic for a fixed seed and physical deal. This audit only covers exact range-product chance in the bounded checkdown game; it does not address empirical chance-support error or missing postflop betting.

To reproduce the 64-deal mixed-pair fixture from the [independent-board holdout](sixmax-preflop-payoff-holdout.md):

```powershell
mvn -q -pl solver -am test
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPayoffSamplingMain' '-Dexec.args=mixed-pairs 500 2000 7 711'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPayoffSamplingMain' '-Dexec.args=mixed-pairs 500 4000 4 711'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPayoffSamplingMain' '-Dexec.args=mixed-pairs 500 32000 2 711'
```

| Boards per physical deal | Largest estimated terminal-payoff SE (bb) | 7bb target | 4bb target |
| ---: | ---: | :---: | :---: |
| 500 | 13.260163 | Unmet | Unmet |
| 1,000 | 9.301561 | Unmet | Unmet |
| 2,000 | 6.588960 | Met | Unmet |
| 4,000 | 4.644410 | Stopped earlier | Unmet at cap |

The 4bb run explicitly returns `targetMet=false`; `requireTargetMet()` throws. The 7bb run stops at 2,000 boards per deal. These targets remain loose relative to the small strategy deviations in the holdout study.

### Retained board streams and tighter targets

The sampling audit now retains all of its at most 64 physical-deal streams. `SharedBoardMultiwayShowdownOracle` has an opt-in bounded cache and `increaseTrialsTo()` extends each retained stream on its next estimate request. It keeps the random generator, shuffled deck, share sums and square sums, then samples only the missing boards. The public game/payoff table is still rebuilt at each stage, but earlier hand evaluations are reused. Returned estimates are immutable snapshots; later extensions cannot alter an earlier game's payoff table. The default oracle cache remains one deal; larger caches evict the least recently used stream and count any work repeated after eviction. Tests compare every active subset against fresh final-budget runs exactly, exercise eviction, and check actual board counts across multi-deal ladders.

| Boards per physical deal | Largest estimated terminal-payoff SE (bb) | Cumulative boards actually evaluated over 64 deals |
| ---: | ---: | ---: |
| 500 | 13.260163 | 32,000 |
| 1,000 | 9.301561 | 64,000 |
| 2,000 | 6.588960 | 128,000 |
| 4,000 | 4.644410 | 256,000 |
| 8,000 | 3.281926 | 512,000 |
| 16,000 | 2.314335 | 1,024,000 |
| 32,000 | 1.634748 | 2,048,000 |

The 2bb target is met at 32,000 boards per deal. Replaying every stage from scratch would evaluate 4,064,000 boards; retaining streams requires 2,048,000, about **49.6% less board-evaluation work** on this ladder. The 4,000-board ladder drops from 480,000 to 256,000 evaluations, about 46.7%. These are counted work reductions, not measured wall-clock speedups. The original seed-711 values at 500–4,000 boards are unchanged. Independently held-out policy verification, realistic ranges and postflop continuation remain separate requirements; the [higher-budget holdout](sixmax-preflop-payoff-holdout.md#higher-board-budget-holdout) measures the next check on the frozen policy.
