# Connected-game public-card chance audit

The connected BTN-versus-BB research solver has an exact-game best-response gap, but that number only measures strategy quality **inside its declared public-card deck**. `ConnectedChanceAudit` separately forces both players to check down and compares the game's average chip value with `ExactPreflopEquityOracle`, which enumerates all **1,712,304** physical five-card boards for each legal pair of exact hole-card combos. The audit reports a signed range-weighted error, a mean absolute error across matchups and the largest single-matchup error. It reuses one exact oracle across menu variants.

On the four synthetic matchups in the connected fixture, the full-deck forced-check-down value for BB is **+0.196069bb**. The comparison below used 100 CFR+ iterations for each declared chance menu; runtimes are local measurements, not performance guarantees.

| Public chance menu | Information sets | Abstract-game gap | Abstract check-down BB | Signed error vs full deck | Mean absolute matchup error | Worst matchup error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 flop, 2 turns | 6,684 | 0.081232bb | +1.447727bb | +1.251659bb | 1.586709bb | 2.134002bb |
| 2 flops, 2 turns | 11,550 | 0.066854bb | +1.536364bb | +1.340295bb | 1.675346bb | 2.134002bb |
| 4 flops, 4 turns | 42,534 | 0.027256bb | +1.437879bb | +1.241810bb | 1.557164bb | 2.134002bb |

Simply adding a few handpicked cards did **not** remove the bias. In the 1-flop model, BB `Jc Jd` versus BTN `Kh Qh` has +0.229635bb full-deck check-down value but +2.363636bb under the low fixed flop and two turns. This single matchup explains much of the range-level error. Improving the CFR gap of that restricted model would not fix it.

`ConnectedChanceCalibration` explores seeded, four-flop/four-turn candidate menus, choosing the one with the smallest worst exact-combo check-down error, then mean absolute error. This is deliberately an **in-sample diagnostic** on the same four synthetic matchups. With seed `42`, the 100-candidate run rejected seven incompatible menus and selected game hash `37c6284f09a4ed4979055eaee583a2c8d64b89d32f21a2d59f010ffd44bd9c9b`:

| Seed 42 search | Calibration signed / worst error | Unseen holdout signed / worst error |
| --- | ---: | ---: |
| 10 candidate menus | +0.618893bb / 0.823049bb | +0.090816bb / 0.490469bb |
| 100 candidate menus | +0.016905bb / 0.615773bb | +1.125676bb / 3.100318bb |

The holdout uses BTN `As Ah`/`Qs Js` and BB `Tc Td`/`9c 8c`; these combos are absent from the search objective. Selecting from more menus improved the calibration score but substantially worsened this holdout. The 100-candidate selected game's gap after 100 CFR+ iterations is 0.076271bb against **that selected abstract game**. Its lower calibration bias does not generalize to these unseen hands, and neither check-down comparison measures how the optimal betting strategy changes when missing boards return. No menu here is eligible for a trainer strategy pack.

After `mvn -q -pl solver -am -DskipTests compile`, reproduce the audits on Windows from the repository root:

```powershell
java -Xmx3g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedChance 100
java -Xmx3g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.CalibrateConnectedChance 42 100 100
```

The next solver step is a scalable chance model with broader, independently checked hand ranges and a measured strategic abstraction error. A richer flop/turn sample must be validated on combos and boards not used to choose it. The exact physical-deck baseline and a small in-game gap are separate requirements; neither substitutes for the other.
