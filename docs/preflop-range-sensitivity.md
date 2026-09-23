# Preflop research-pack range sensitivity

The [exact diverse validation pack](../solver/src/test/resources/diverse-validation-pack.json) passes the provisional numeric screen, but its eight hero and seven opponent combos are synthetic. This probe tests how much its hero decisions depend on those range weights. It does not turn the pack into a training lesson.

## Method

Starting from the pack's exact 47-matchup payoff table, change **one combo weight at a time** to 75% and 125% of its original value. Repeat for all eight hero and seven opponent combos: 30 scenarios. Normalize the resulting unblocked deal weights, solve each scenario for 3,000 CFR+ iterations, and record each hero combo's shove EV minus fold EV and shove frequency. The report also identifies which weight change produced each extreme EV. The code is [PreflopRangeSensitivity](../solver/src/main/java/com/pokerlab/solver/PreflopRangeSensitivity.java); its regression test replays the fixture. No equity Monte Carlo is run during the probe.

The baseline best-response gap is 0.000149bb against its exact payoff table. The largest gap across the 30 re-solves is about 0.000306bb. These gaps measure solver convergence **within each specified game**, not how realistic the ranges are.

| Hero combo | Baseline shove edge | Edge across perturbations | Shove frequency across perturbations | Reading |
| --- | ---: | ---: | ---: | --- |
| `Ac Ad` | +82.77bb | +81.05 to +83.81bb | ~100% | Clear shove in this local probe |
| `6h 7h` | −9.90bb | −12.97 to −6.37bb | ~0% | Clear fold in this local probe |
| `5s As` | +3.63bb | +0.0003 to +7.91bb | 87% to ~100% | Shove edge can nearly disappear |
| `Ah Kh` | +0.0005bb | −0.0011 to +0.0029bb | 24% to 98% | Mix is highly assumption-sensitive and close to solver error |
| `Ks Qs` | −0.83bb | −3.46 to +2.17bb | ~0% to ~100% | Preferred action reverses |

The `Ks Qs` reversal is the clearest publication warning: a low best-response gap and exact showdown payoffs do not make a strategy robust to uncertain input ranges. The `Ah Kh` EV edge is comparable to the re-solve gaps, so the precise mix should not be taught as a stable percentage.

This is a local one-at-a-time test. It does not cover simultaneous range changes, alternate bet sizes, rake, multiway action, population estimates or model error. Before publishing a preflop pack, document plausible range sources and their uncertainty, repeat sensitivity checks around those ranges, and have a poker reviewer assess the action tree and interpretation.
