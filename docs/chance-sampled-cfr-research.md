# Chance-sampled CFR research

The connected BTN-versus-BB game can now run seeded vanilla CFR with one sampled outcome at each chance node. `SAMPLED` draws the private matchup and public cards. `SAMPLED_AFTER_ROOT` enumerates the first chance layer (the four private matchups in this fixture) and samples subsequent public cards. Both traverse every player action, reuse a random quantile at each chance depth across branches within an iteration, and reset their random sequence for a repeated solve with the same seed. Reusing the quantile preserves each chance node's marginal distribution; it does not guarantee a smaller variance in every game.

`EXHAUSTIVE` remains the default and the only mode supported with CFR+. Chance sampling is restricted to vanilla CFR because the existing CFR+ update and averaging schedule was validated for full traversal only. Sampled solves may omit information sets entirely. `StrategyCompletion` fills those with uniform actions for a **bounded-game audit** and rejects a profile with information sets outside the game. This is an audit convention, not a trained policy for unseen spots or a scalable full-deck best-response evaluator.

## Measured connected-game comparison

These are single local runs on the same four-combo synthetic ranges, seed `42`, and the four-flop/four-turn abstract game (hash `f34735895f553c37814def19ab9828197ba024c592c24b0dc426f94703f5a752`). The gap is an exact best-response gap **within that declared game**. Seconds include solving only; wall times vary by machine and JVM warm-up.

| Traversal / update | Iterations | Information sets visited | Solve time | Bounded-game gap |
| --- | ---: | ---: | ---: | ---: |
| Sample every chance layer, vanilla CFR | 10,000 | 41,256 / 42,534 | 2.65s | 1.110184bb |
| Sample every chance layer, vanilla CFR | 100,000 | 42,534 / 42,534 | 20.10s | 0.335014bb |
| Enumerate private matchups; sample public cards, vanilla CFR | 10,000 | 42,534 / 42,534 | 8.85s | 0.475169bb |
| Enumerate private matchups; sample public cards, vanilla CFR | 100,000 | 42,534 / 42,534 | 77.22s | 0.101549bb |
| Exhaustive chance, CFR+ | 100 | 42,534 / 42,534 | 17.81s | 0.027256bb |

Sampling lowers the cost of an iteration, but at these budgets exhaustive CFR+ is both faster and more accurate for this small fixture. Keeping all root matchups greatly improves the sampled gap, yet still does not win the measured quality/time comparison. Thus no sampled policy is exported as a trainer pack. The larger-game scaling hypothesis remains open and needs a fixture whose public-card support is broad enough that exhaustive traversal is genuinely prohibitive.

The separate [full-deck check-down audit](connected-chance-abstraction-audit.md) finds **+1.241810bb** range-weighted bias for this same four-flop/four-turn public-card menu. Reducing the bounded-game strategy gap does not fix that error. A realistic six-max preflop pack needs independently validated ranges, much broader chance coverage, and a measured strategy impact of abstraction, in addition to solver convergence.

After `mvn -q -pl solver -am -DskipTests compile`, reproduce the runs from the repository root on Windows:

```powershell
java -Xmx3g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkChanceSampledCfr 10000 42 4 4 all
java -Xmx3g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkChanceSampledCfr 10000 42 4 4 after-root
java -Xmx3g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkChanceSampledCfr 100 42 4 4 exhaustive-plus
```

The benchmark fully enumerates the small abstract game for profile completion and best-response measurement. Its 10-million-state guard intentionally prevents it from silently attempting a physical full-deck audit.
