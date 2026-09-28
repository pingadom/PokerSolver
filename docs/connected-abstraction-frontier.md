# Connected-game observation frontier

This follow-up to the [board-granularity study](connected-bucket-granularity-research.md) adds an intermediate `TEXTURE_BOARD_BUCKETS` observation and a larger synthetic range. It also evaluates actions on a separate set of physical river boards. All three modes draw from the same full physical deck, use the same betting/payoff rules, preserve exact own hole cards and action histories, and remain **offline research only**.

The intermediate key retains made-hand category, paired-board flag, public three-or-more-card suit threat and a queen-or-higher top-card band. The existing fine key also retains own flush-draw and broad straight features; the coarse key keeps only made-hand category and the paired-board flag. The texture mode has a distinct game hash. No existing fine or coarse hash changes.

The new `STRESS_5X5` fixture has five exact BTN combos and five exact BB combos, giving 25 legal private deals instead of nine. It adds suited connector, suited broadway, and smaller/medium pairs to probe range sensitivity. Both ranges remain intentionally synthetic; they do not represent a population or a solved six-seat opening range. The four other seats still fold by assumption.

## Coverage versus information loss

The common-reach benchmark maps identical physical trajectories into each observation. At 50,000 attempted deals and a threshold of 20 observations per bucket, the **river** results are:

| Range | Seed | Reached first BB river decisions | Fine supported | Texture supported | Coarse supported |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3×3 | 42 | 4,881 | 1.5% | 8.7% | 57.9% |
| 3×3 | 43 | 4,845 | 0.9% | 8.5% | 53.5% |
| 5×5 | 42 | 4,881 | 0.0% | 2.5% | 43.6% |
| 5×5 | 43 | 4,845 | 0.0% | 4.6% | 44.7% |

The [forced-check/called-bet river counterfactual](connected-bucket-granularity-research.md#river-information-loss-counterfactual) measures empirical observation loss against an exact-physical-board action on the *same* 50,000 sampled boards:

| Range | Seed | Fine loss | Texture loss | Coarse loss |
| --- | ---: | ---: | ---: | ---: |
| 3×3 | 42 | 0.893bb | 0.965bb | 1.033bb |
| 3×3 | 43 | 0.882bb | 0.948bb | 1.026bb |
| 5×5 | 42 | 0.518bb | 0.590bb | 0.644bb |
| 5×5 | 43 | 0.526bb | 0.593bb | 0.649bb |

The intermediate mode lies between the two existing modes on both measures. This is a descriptive abstraction frontier, not a ranking of actual poker strategies.

## Sample-split river action check

`PhysicalRiverHeldOutDecisionAudit` alternates sampled full-deck boards between discovery and held-out sets. At each BB river observation, it estimates the sign of the *exact conditional showdown margin* from discovery boards only, requiring at least ten such boards; otherwise it checks. It scores that fixed bet/check choice on held-out boards against an always-check baseline and a physical-board oracle. BTN always checks back or calls the 8bb bet. The same held-out boards, prior and action rule are used for all modes. This measures how observation support and aliasing affect **one fixed-response learning problem**; it is not a GTO, exploitability or real-game EV estimate.

At 50,000 sampled boards:

| Range | Seed | Fine support / regret | Texture support / regret | Coarse support / regret |
| --- | ---: | ---: | ---: | ---: |
| 3×3 | 42 | 78.0% / 1.518bb | 93.5% / 1.216bb | 99.7% / 0.978bb |
| 3×3 | 43 | 75.5% / 1.589bb | 92.4% / 1.251bb | 99.7% / 0.984bb |
| 5×5 | 42 | 69.8% / 1.368bb | 89.8% / 1.048bb | 99.3% / 0.662bb |
| 5×5 | 43 | 69.9% / 1.362bb | 89.1% / 1.076bb | 99.4% / 0.652bb |

At 200,000 sampled boards on the 3×3 range, texture's held-out regret falls to **0.965–0.966bb** and coarse's is **0.961–0.978bb** across seeds 42 and 43. Fine remains at **1.119–1.121bb**. The modes approach one another as sample support improves. The finite-sample action result can favor coarse even though its perfect-information loss is larger; neither fact alone selects a solver abstraction. New seeds/ranges, real opponent responses and a game-wide strategic-response bound are required before admitting a profile to the trainer.

## Connected CFR smoke runs

At 5,000 chance-sampled CFR iterations, texture mode visits **33,806/33,186** information sets for 3×3 seeds 42/43, and **61,376/60,762** for 5×5. BTN aces open **99.6%/98.8%** on 3×3 and **99.6%/99.6%** on 5×5. These are sanity checks, not convergence claims. With 10,000 attempted deals per street, only **2.5–4.3%** of reached first-BB river states have ten held-out observations on 3×3 and **2.7–3.2%** on 5×5. Later-street quality remains the blocker.

Reproduce from the repository root after compiling:

```powershell
mvn -q -pl solver -am -DskipTests compile
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalBoardObservationCoverage 50000 42 20 5x5
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalRiverAlias 50000 42 5x5
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalRiverHeldOutDecision 50000 42 10 5x5
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedRangeValidation 5000 42 1000 10000 2 texture 5x5
```

Change seeds to `43` for replication, or `5x5` to `3x3` for the original fixture. The default benchmark profile remains 3×3. All support thresholds are descriptive; no mode is serialized to a solution pack or served by the trainer.
