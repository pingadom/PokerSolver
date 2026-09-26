# Exact preflop payoff scaling study

The focused trainer pack has 47 legal exact-hand matchups. Each matchup requires all **1,712,304** five-card boards after four hole cards are removed. `ExactPreflopEquityOracle` now reuses a completed enumeration when another matchup differs only by a global renaming of the four suits. It keeps the player roles and card ranks fixed. This is an exact symmetry: every board in one matchup maps one-to-one to a board in the other, with the same win/tie outcome. The reuse is scoped to an oracle instance, so a pack build does not depend on a persistent cache or another build.

`PreflopPayoffCost` counts blockers and suit-equivalent classes **before** a solve. Run its offline report after building the solver module:

```powershell
mvn -q -pl solver -am -DskipTests package
java --class-path 'solver/target/classes;engine/target/classes' com.pokerlab.solver.PreflopPayoffCost diverse
java --class-path 'solver/target/classes;engine/target/classes' com.pokerlab.solver.PreflopPayoffCost suit-probe
```

| Input | Candidate pairs | Blocked | Legal | Distinct suit classes | Five-card boards without / with reuse |
| --- | ---: | ---: | ---: | ---: | ---: |
| Narrow validation spot | 9 | 1 | 8 | 8 | 13,698,432 / 13,698,432 |
| Playable diverse spot | 56 | 9 | 47 | 47 | 80,478,288 / 80,478,288 |
| Synthetic all-AA versus all-KK probe | 36 | 0 | 36 | 3 | 61,642,944 / 5,136,912 |

The current trainer pack gets **no reduction**: its hand pairs already have different rank/suit structures. The synthetic probe gets a **12× reduction in board enumerations**, because the 36 physical pairings collapse to three patterns of suit overlap. These are counts of board evaluations, not wall-clock speedups; the evaluator, cache lookup, solver iterations and pack validation have their own costs. The probe is a scaling check, not new trainer content.

This result argues against expanding the demo by simply adding many exact combos. Larger realistic ranges need documented range provenance and uncertainty bounds first. Their payoff cost should be measured with this report, then benchmarked on the target machine. Even with suit reuse, exact enumeration over broad two-player ranges may be too expensive; a future payoff method would need measured uncertainty and a solver-quality gate that includes it. The current validation-only pack and its saved EVs are unchanged.
