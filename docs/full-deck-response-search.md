# Held-out full-deck response search

`FixedOpponentResponseCfr` trains one player's approximate response while freezing the other player's connected average strategy. Each iteration samples legal physical chance outcomes from the **full deck**, traverses both actions at the responding player's information sets and the fixed opponent's weighted actions, and updates counterfactual regrets. It uses the game's information-set key, so a response cannot inspect the opponent's private cards. Missing fixed-opponent strategy keys receive an explicit uniform policy; the trainer reports missing query and distinct-key counts. The exported response is the responding player's reach-weighted average strategy.

`PhysicalResponseHeldOutAudit` then deals independent physical hands, samples all future public cards from the full legal deck, and rolls out the fixed baseline against itself and against the trained response. Each pair shares its private deal and random continuation seed. It reports the response's target-player utility gain, paired standard error, approximate 95% interval, and missing-policy visits. A response key absent on a held-out path falls back to the baseline target policy; a missing baseline key uses uniform actions. No-op controls confirm that both an identical response and an empty response have exactly zero paired gain. A Kuhn-poker test checks that the response trainer approaches **both** exact finite-game best-response values without receiving hidden opponent cards.

With 50,000 response-training iterations **per player** and 50,000 held-out physical deals per player, coarse-board information sets, and two independently solved baseline seeds:

| Synthetic range | Baseline CFR iterations | Seed | BB response gain, bb (paired SE) | BTN response gain, bb (paired SE) |
| --- | ---: | ---: | ---: | ---: |
| 3×3 | 3,000 | 42 | +0.2858 (0.0372) | +0.1432 (0.0211) |
| 3×3 | 3,000 | 43 | +0.1977 (0.0384) | +0.2098 (0.0246) |
| 5×5 | 3,000 | 42 | +0.0732 (0.0298) | +0.1298 (0.0241) |
| 5×5 | 3,000 | 43 | +0.0191 (0.0279) | +0.1120 (0.0208) |
| 3×3 | 10,000 | 42 | +0.0676 (0.0275) | +0.0886 (0.0188) |
| 3×3 | 10,000 | 43 | −0.0070 (0.0262) | +0.0956 (0.0202) |
| 5×5 | 10,000 | 42 | −0.0019 (0.0301) | +0.0317 (0.0195) |
| 5×5 | 10,000 | 43 | +0.0332 (0.0257) | +0.0411 (0.0188) |

The baseline solve uses `base seed + 100003`, BB and BTN response searches use `base seed + 200003` and `+ 200004`, and their held-out audits use `base seed + 300003` and `+ 300004`. Thus each row is reproducible and response training never uses its held-out runout stream. The 3×3 seed-42, 3,000-iteration BB gain has an approximate held-out interval [+0.2130, +0.3586]bb, while the 10,000-iteration gain is [+0.0137, +0.1216]bb. The 5×5 seed-42 BTN gain falls from +0.1298bb at 3,000 iterations to +0.0317bb at 10,000, with the latter interval crossing zero. These comparisons are *descriptive*: each baseline budget changes its own reached-hand distribution and its opponent strategy. At 3,000 iterations, a shorter 10,000-iteration **response** search missed or could not resolve several 5×5 gains; the 50,000-iteration response budget matters too.

Fallback use was small but not always zero. In the 5×5 seed-42, 3,000-baseline run, BTN response training made 54 uniform-fallback opponent queries across 18 distinct missing keys; only one of 50,000 held-out BTN-response trajectories touched a fallback, while no baseline self-play trajectory did. The CLI prints raw counts because a query-support percentage rounded to 100.000% can hide missing keys. At 10,000 baseline iterations, both 5×5 seed-42 held-out response paths had zero fallback trajectories. Low observed fallback use supports interpreting these particular sampled comparisons, but does not certify every unvisited information set.

Reproduce from `solver/` after compiling:

```powershell
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.BenchmarkPhysicalResponseSearch' '-Dexec.args=3000 50000 50000 42 3x3'
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.BenchmarkPhysicalResponseSearch' '-Dexec.args=10000 50000 50000 43 5x5'
```

Repeat seeds `42` and `43`, baselines `3000` and `10000`, and ranges `3x3` and `5x5` for the table. The gain from any trained response is a **candidate one-player deviation** against a fixed opponent, evaluated with sampling error. It can demonstrate that the baseline leaves value under this model when a held-out interval is convincingly positive; it cannot upper-bound the *best* possible response or certify a Nash gap. The displayed intervals condition on the trained strategies, contain only held-out deal/continuation sampling error, and are not adjusted for the eight-run sweep or response-training uncertainty. The game is still a synthetic, no-rake, two-active-player BTN/BB tree from a six-seat table with fixed 2/4/8bb postflop bet sizes. No result here justifies a general 6-max 100bb trainer chart.

The stability experiment below probes response-training seeds and budgets. Beyond it, the solver still needs a defensible upper bound or confidence-controlled exploitability estimate on a broader physical game. Range provenance, realistic rake, bet sizing and genuine multiway continuations remain separate product requirements.

## Seed and budget stability with an independent confirmation split

`PhysicalResponseStabilityAudit` now trains four candidate responses for each target player: 10,000 and 50,000 iterations, each with two independent training seeds. All four candidates are compared on the **same** 10,000 validation deals, so the best validation gain determines a single candidate before the separate 20,000-deal confirmation stream is used. The CLI also prints confirmation scores for the other candidates to expose training sensitivity, but they cannot replace the preselected candidate after seeing confirmation results. The baseline is fixed at 10,000 sampled-CFR iterations. Training, validation, and confirmation seeds differ; the report records the game hash and missing-policy paths.

| Synthetic range | Base seed | Validation-selected BB (budget, seed offset) | BB confirmation gain, bb (paired SE) | Validation-selected BTN (budget, seed offset) | BTN confirmation gain, bb (paired SE) |
| --- | ---: | --- | ---: | --- | ---: |
| 3×3 | 42 | 50,000, +200003 | +0.0569 (0.0435) | 50,000, +200103 | +0.0560 (0.0343) |
| 3×3 | 43 | 50,000, +200103 | −0.0607 (0.0427) | 50,000, +200003 | +0.0374 (0.0321) |
| 5×5 | 42 | 50,000, +200103 | −0.0050 (0.0411) | 50,000, +200103 | −0.0081 (0.0316) |
| 5×5 | 43 | 50,000, +200103 | +0.0287 (0.0385) | 50,000, +200003 | +0.0532 (0.0293) |

The offsets are relative to the base seed; the baseline solve instead adds `100003`, and the target player index is also added to the response seed. None of the eight **selected** confirmation intervals has a lower endpoint above zero. For example, the 5×5 seed-43 BTN interval is [−0.0043, +0.1107]bb. At 10,000 response iterations, some candidates actually lost value against the fixed baseline on confirmation; increasing the budget to 50,000 generally improved the results but did not remove seed sensitivity. The 3×3 seed-43 BTN candidate selected on +0.1715bb validation gain confirmed at only +0.0374bb, illustrating why selection and scoring must use separate physical hands. This split detects candidate-selection optimism; it still does not supply a best-response upper bound, prove the baseline is close to equilibrium, or account for variation across baseline solves. The nominal intervals cover only confirmation sampling, not the broader eight-comparison research sweep.

Reproduce from `solver/` after compiling:

```powershell
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.BenchmarkPhysicalResponseStability' '-Dexec.args=10000 10000 50000 10000 20000 42 3x3'
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.BenchmarkPhysicalResponseStability' '-Dexec.args=10000 10000 50000 10000 20000 43 5x5'
```

Repeat both baseline seeds (`42`, `43`) and range profiles (`3x3`, `5x5`) for the table. The next validation step needs materially more precise confirmation and a defensible way to bound the strongest possible deviation across full physical chance, while the game model itself still needs real range provenance and realistic multiway action trees.
