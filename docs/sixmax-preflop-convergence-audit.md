# Six-seat preflop convergence audit

`SixMaxPreflopConvergenceAudit` independently re-solves the **same** `SixMaxPreflopCheckdownGame` at each requested CFR+ iteration budget. `MultiPlayerInformationSetBestResponse` computes a pure best response for each of six seats against each saved average strategy. `NashConv` is the sum of those unilateral gains, in big blinds per hand. It measures deviation **inside the specified finite game**, not real-world cash-poker GTO quality.

The CLI uses physical two-card hands and exactly enumerates every possible five-card board from the forty undealt cards for each legal six-hand deal. The payoff sampling standard error is zero. Folded players' cards still block boards. Terminal payoffs are precomputed once and reused at every budget. The `profile_total_bb` output should be zero without rake and negative when the declared rake collects money.

## Reproduce

From the repository root:

```powershell
mvn -q -pl solver -am test
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPreflopConvergenceMain' '-Dexec.args=utg-mix shove none 1,10,50,200'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPreflopConvergenceMain' '-Dexec.args=utg-mix shove five-percent-cap-one 1,10,50,200'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPreflopConvergenceMain' '-Dexec.args=button-mix open-shove none 1,10,50,200,500,1000'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPreflopConvergenceMain' '-Dexec.args=button-mix open-shove five-percent-cap-one 1,10,50,200,500,1000'
```

Both fixtures fix HJ to K♠K♥, CO to Q♠Q♥, SB to T♠T♥, and BB to 9♠9♥. `utg-mix` gives UTG equal-weight A♠A♥ or 5♠5♥ and fixes BTN to J♠J♥. `button-mix` fixes UTG to A♠A♥ and gives BTN equal-weight J♠J♥ or 5♠5♥. Each game therefore has two legal six-hand deals. `shove` permits a 100bb raise target; `open-shove` permits 3bb and 100bb raise targets. The capped-rake case charges 5% up to 1bb with no-flop-no-drop. These are diagnostic fixtures, not position-specific ranges or a representative rake schedule.

## Observed results

The public trees have 1,360 states and 745 information sets with `shove`, or 12,832 states and 7,089 information sets with `open-shove`. All four games have two deals and zero showdown sampling error. CLI results below are rounded for display.

| Fixture | Raises | Rake | Iterations | NashConv (bb) | Largest seat gain (bb) | Profile total (bb) |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| UTG mix | Shove | None | 1 | 142.238579 | 34.228852 | 0 |
| UTG mix | Shove | None | 10 | 7.247371 | 2.105416 | 0 |
| UTG mix | Shove | None | 50 | 0.569788 | 0.315065 | 0 |
| UTG mix | Shove | None | 200 | 0.043782 | 0.033364 | 0 |
| UTG mix | Shove | 5%, 1bb cap | 1 | 141.948132 | 34.125204 | -0.878639 |
| UTG mix | Shove | 5%, 1bb cap | 10 | 7.060562 | 1.937238 | -0.150085 |
| UTG mix | Shove | 5%, 1bb cap | 50 | 0.558415 | 0.318106 | -0.057218 |
| UTG mix | Shove | 5%, 1bb cap | 200 | 0.041190 | 0.030655 | -0.005347 |
| BTN mix | 3bb, shove | None | 1 | 151.394471 | 48.426057 | 0 |
| BTN mix | 3bb, shove | None | 10 | 10.498746 | 2.870833 | 0 |
| BTN mix | 3bb, shove | None | 50 | 0.601015 | 0.205442 | 0 |
| BTN mix | 3bb, shove | None | 200 | 0.129728 | 0.099727 | 0 |
| BTN mix | 3bb, shove | None | 500 | 0.021103 | 0.016599 | 0 |
| BTN mix | 3bb, shove | None | 1000 | 0.005280 | 0.004165 | 0 |
| BTN mix | 3bb, shove | 5%, 1bb cap | 1 | 151.214344 | 48.144584 | -0.889547 |
| BTN mix | 3bb, shove | 5%, 1bb cap | 10 | 10.524430 | 2.886819 | -0.221122 |
| BTN mix | 3bb, shove | 5%, 1bb cap | 50 | 0.615397 | 0.227883 | -0.056258 |
| BTN mix | 3bb, shove | 5%, 1bb cap | 200 | 0.114196 | 0.088617 | -0.018702 |
| BTN mix | 3bb, shove | 5%, 1bb cap | 500 | 0.018334 | 0.014443 | -0.003018 |
| BTN mix | 3bb, shove | 5%, 1bb cap | 1000 | 0.004588 | 0.003622 | -0.000756 |

In these four runs, measured deviation fell at every sampled budget, including with the larger raise menu and rake. This is observed behavior, not a monotonic convergence guarantee for six-player CFR+. The nonzero negative profile totals show the rake rule affects payoff accounting. A small deviation in a two-deal mandatory-checkdown game says little about realistic ranges, additional raise sizes, variable stacks, or later-street betting.

The next solver gate is to increase the number and diversity of physical joint deals without precomputing every board/payoff for every terminal, then measure robustness across ranges and raise menus before generated decisions are approved for the trainer. A connected postflop action model is also required: mandatory checkdown is a rule of this research game, not a solution for flop, turn, or river decisions.
