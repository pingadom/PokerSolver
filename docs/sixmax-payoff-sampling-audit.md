# Six-seat payoff sampling budget audit

The bounded six-seat preflop game has an estimated standard error for every sampled terminal payoff. `SixMaxPreflopPayoffSamplingAudit` accepts a declared **maximum terminal-payoff SE target in bb**, a starting board count, and a hard maximum. It rebuilds the same exact-range game on a fixed seeded board stream, doubling boards per deal up to the cap, and records the largest terminal-payoff SE at each budget. The result states whether the target was met. A caller that requires this gate can use `requireTargetMet()`; it rejects an exhausted run rather than silently treating its payoff table as sufficiently sampled.

The statistic is the largest estimated **standard error**, not a confidence bound or a bound on strategy EV, regret, or NashConv. It is estimated from the same boards used to choose the stopping point, so meeting the target does not by itself prove the true sampling error is below it. The board stream is deterministic for a fixed seed and physical deal. This audit only covers exact range-product chance in the bounded checkdown game; it does not address empirical chance-support error or missing postflop betting.

To reproduce the 64-deal mixed-pair fixture from the [independent-board holdout](sixmax-preflop-payoff-holdout.md):

```powershell
mvn -q -pl solver -am test
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPayoffSamplingMain' '-Dexec.args=mixed-pairs 500 2000 7 711'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPayoffSamplingMain' '-Dexec.args=mixed-pairs 500 4000 4 711'
```

| Boards per physical deal | Largest estimated terminal-payoff SE (bb) | 7bb target | 4bb target |
| ---: | ---: | :---: | :---: |
| 500 | 13.260163 | Unmet | Unmet |
| 1,000 | 9.301561 | Unmet | Unmet |
| 2,000 | 6.588960 | Met | Unmet |
| 4,000 | 4.644410 | Stopped earlier | Unmet at cap |

The 4bb run explicitly returns `targetMet=false`; `requireTargetMet()` throws. The 7bb run stops at 2,000 boards per deal. At each escalation the game is rebuilt from the start on the same seeded board stream. This keeps results comparable but repeats work; the sum of attempted boards exceeds the final count. A production generator would need an incremental sampler and independently held-out verification before using an SE target as a publication gate. Both 7bb and 4bb are still loose relative to the small strategy deviations in the holdout study, so neither run makes these policies trainer-ready.
