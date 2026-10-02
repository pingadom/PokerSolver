# Independent-board holdout for the six-seat preflop solver

The six-seat checkdown research game estimates showdown payoffs from sampled shared boards. A low CFR+ NashConv on that **same estimated payoff table** can miss sensitivity to its board sample. `SixMaxPreflopPolicyHoldoutAudit` freezes one trained strategy, rebuilds the identical game with independently seeded board estimates, and recomputes profile utility and each seat's information-set best response. It reports the signed per-seat EV and deviation-gain changes, their largest absolute changes, both NashConv values, and the largest terminal-payoff standard error in each game.

The audit refuses a comparison if the betting rules, rake, chance model, chance draw count, ordered physical deals, or their probabilities differ. The two payoff tables may differ; the policy is **not retrained** on holdout boards. This isolates board-estimation sensitivity for one specified finite game. The holdout best response is itself optimized against a sampled table, and the reported maximum standard error is a property of individual terminal payoffs, not a confidence bound on NashConv. Neither number certifies real 100bb six-max cash GTO.

Reproduce the 64-deal, 100bb, no-rake, shove-only, mandatory-checkdown experiment from the repository root:

```powershell
mvn -q -pl solver -am test
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPreflopHoldoutMain' '-Dexec.args=hierarchy 2000 2000 711 712,713 500'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPreflopHoldoutMain' '-Dexec.args=mixed-pairs 2000 2000 711 712,713 500'
```

Both fixtures give each seat two non-colliding, equally weighted physical pocket-pair combos. `hierarchy` gives UTG AA, HJ KK, CO QQ, BTN JJ, SB TT, and BB 99, with two suit variants of each pair. `mixed-pairs` gives the same strong pair or a weak pair: 22, 33, 44, 55, 66, or 77 respectively. All 64 range-product deals are legal and equally likely. Each game has 1,360 public states and the learned profile has 1,328 information sets. Every seed generates a separate 2,000-board stream for each physical deal; the three seeds never share the same stream. The training strategy uses 500 CFR+ iterations. This is a controlled solver stress test, not a representative cash-game range.

| Fixture | Board seed | NashConv (bb) | Change from training (bb) | Largest absolute seat EV change (bb) | Largest absolute seat deviation-gain change (bb) | Largest terminal-payoff SE (bb) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Hierarchy | 711 training | 0.045135 | — | — | — | 6.590976 |
| Hierarchy | 712 holdout | 0.045683 | +0.000548 | 0.000415 | 0.000409 | 6.552879 |
| Hierarchy | 713 holdout | 0.045148 | +0.000012 | 0.000172 | 0.000054 | 6.572998 |
| Mixed pairs | 711 training | 0.022813 | — | — | — | 6.588960 |
| Mixed pairs | 712 holdout | 0.038229 | +0.015416 | 0.010983 | 0.015065 | 6.550603 |
| Mixed pairs | 713 holdout | 0.035435 | +0.012622 | 0.005853 | 0.011471 | 6.560442 |

The hierarchy policy is nearly unchanged across these two samples, but the mixed-pair policy shows a roughly 55–68% higher measured NashConv off its training board seed. Those percentages describe only this finite game and two holdouts; they are not a statistical confidence interval. The 6.55–6.59bb largest terminal-payoff SE remains much larger than the observed profile-level differences. Future work needs more independent seeds and board trials, a predeclared precision target for decision EVs, realistic ranges and bet sizes, and a validated postflop continuation. Keep these six-seat packs validation-only until those gates pass.
