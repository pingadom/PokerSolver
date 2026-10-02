# Expanded six-seat private-card chance experiment

The bounded preflop checkdown game now accepts up to 64 legal physical six-hand deals, subject to a 160,000 deal × public-state work cap. An exact-range game enumerates the blocker-compatible Cartesian product and normalizes the product of range weights. A second path rejection-samples the same weighted product, groups repeated legal deals, and solves only that empirical support. Both use the same betting rules and showdown oracle. The game, convergence audit, and research-trainer question expose `EXACT_RANGE_PRODUCT` or `EMPIRICAL_JOINT_DEALS`; the empirical path also reports accepted draws. `NashConv` is exact against the **constructed finite payoff game**, not against omitted deals or unobserved future betting.

The experiment below gives each of six seats two disjoint, equal-weight physical pocket-pair combinations: UTG A♠A♥/A♦A♣, HJ K♠K♥/K♦K♣, CO Q♠Q♥/Q♦Q♣, BTN J♠J♥/J♦J♣, SB T♠T♥/T♦T♣, and BB 9♠9♥/9♦9♣. All 64 products are legal, so the exact chance distribution is uniform. The game is 100bb, no rake, with only a 100bb raise target and mandatory checkdown after a non-all-in preflop round. These ranges and rules are research controls, not real cash-game assumptions.

From the repository root, reproduce both runs with:

```powershell
mvn -q -pl solver -am test
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxExpandedConvergenceMain' '-Dexec.args=exact 2000 711 1,10,50,200,500'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxExpandedConvergenceMain' '-Dexec.args=256 2000 711 1,10,50,200,500'
```

The seed fixes the empirical deal draws and a separate deterministic showdown stream keyed by each physical deal. Both modes therefore use the **same** 2,000 sampled boards per deal. This holds payoff estimates fixed when comparing chance models. The public tree has 1,360 states and the solved policy has 1,328 information sets. The 64 deals require 128,000 sampled boards. The maximum terminal-payoff **standard error** is 6.532704bb in both modes; that is not a bound on equilibrium deviation or chance-distribution error.

| Iterations | Exact-range NashConv (bb) | Empirical NashConv (bb) | Exact largest seat gain (bb) | Empirical largest seat gain (bb) |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 158.813834 | 158.769700 | 48.435930 | 48.418469 |
| 10 | 8.680611 | 8.686583 | 2.521111 | 2.524775 |
| 50 | 0.553919 | 0.553003 | 0.210391 | 0.209373 |
| 200 | 0.240252 | 0.240010 | 0.209693 | 0.209400 |
| 500 | 0.045531 | 0.045637 | 0.041536 | 0.041619 |

The empirical run accepted 256 draws without collision and happened to include all 64 deals. Its frequencies were nevertheless **0.2265625 total-variation distance** from the true uniform chance distribution. The two NashConv series are close in this one symmetric fixture, but that does not establish that their policies or action EVs are close in other games. The close values cannot remove the 6.53bb worst-case payoff standard error or provide a general chance-sampling error bound. The zero-sum profile totals in both runs check chip accounting under no rake.

For a game with more than 64 legal deals, empirical sampling can give a bounded provisional support, but omitted-deal effects remain unmeasured. More board trials, independent seeds and range profiles, expanded raise menus, and a real postflop continuation model are needed before these policies become trainer-ready. The 3bb/open-shove tree reaches the public/private work cap before 64 deals, so this experiment does not claim that the full raise tree scales to this support yet.
