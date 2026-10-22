# Cached traversal for fresh CFR references

PokerLab's bounded flop producers run fresh 80,000- and 160,000-iteration CFR+ references before qualifying feedback. These references repeatedly walk the same immutable poker tree. `CachedTwoPlayerCfrGame` compiles the checked tree into an internal traversal graph so those walks avoid repeated state-to-node map lookups and reconstruction of chance lists, information-set strings and terminal utility arrays. Terminal arrays are still copied when returned to the solver.

The original poker game already stores its tree. This change reduces traversal overhead; it does not eliminate construction of a previously uncached tree, change the regret algorithm or extend the supported poker model.

## Construction and ownership

The cache accepts a game through a package-private constructor and runs `FiniteTwoPlayerSequenceForm.checked` itself. The existing positive chance, bounded utility, constant-sum, perfect-recall, label, depth, tree-size and two-decision-maker checks remain in force. Each inactive seat must also have exactly the same terminal payoff at every leaf, compared by double bits. A difference of 1e-16 or a difference between positive and negative zero cannot establish the literal pruning guarantee.

Only after these checks does construction copy the graph. Each node carries a private per-cache identity. Every state operation rejects null and nodes from another cache, including calls that ask for an inactive seat's utility. The node constructor and all fields are private. Action, child and chance lists are immutable; utility arrays never escape without a copy. The cache holds no regrets, accumulated strategy or mutable training checkpoint.

The public `SixMaxOwnedFlopReferences` State interface continues to delegate to its original game, including foreign-state rejection. Only its private training path uses the cache. The older BTN curriculum shares a cache across its reference runs; it constructs a new CFR solver for each run.

## Numerical and work contracts

| Producer | Reference traversal mode |
|---|---|
| Original unpaired BTN curriculum | Exhaustive CFR+, `NONE` pruning |
| Paired and weak-hand owned references | Exhaustive CFR+, `FIXED_UTILITY` pruning |

Caching preserves action/chance order, probability and terminal-utility bits, information-set keys, six alternating player passes, regret clipping and averaging order. It preserves the respective pruning modes, iteration budgets, algorithm identities and published traversal statistics. It does not change the generic multiplayer/sampled solver, LP solver, endpoint tolerances, qualification thresholds, admission rules or saved source reports.

Independent best responses and interval certification still assess policies on the original checked poker game. A cached graph or a matching benchmark policy is not a trainer qualification certificate.

## Verification and measurement

`CachedTwoPlayerCfrGameTest` compares every node on a hidden-information control and complete policies/work counts on the original unpaired, paired and weak-hand physical games in both pruning modes. Other controls cover foreign ownership, illegal operations, mutable returns, independent regret tables, exact inactive payoffs, invalid chance, nonfinite utilities, imperfect recall, a third decision maker and the depth cap. Existing integration tests replay the stored reports and all eleven questions through the complete qualification pipeline.

The optional `CachedFlopTraversalBenchmark` main lives in solver test sources but does not match Surefire's test naming patterns. From the repository root, compile the test sources and invoke it with the solver test/dependency classpath and a new output path:

```text
com.pokerlab.solver.CachedFlopTraversalBenchmark <new-result.json>
```

Before any diagnostic run it creates `<new-result.json>.budget.json`, recording input hashes and a fixed reservation: three games, one 200-iteration warmup per implementation per game, two 2,000-iteration comparison pairs per game, 18 total CFR runs and 25,200 total diagnostic iterations. The second pair reverses execution order. Each pair requires exact complete-policy and work-statistic equality; a mismatch fails without retrying. Original and cached runs use fresh solvers and the same producer-specific pruning mode. The two paired cases exercise `SixMaxOwnedFlopReferences.trainBounded` directly.

The benchmark refuses existing output/budget paths and verifies its three input hashes again before writing a result. Timings cover solver creation and CFR traversal, excluding game/cache construction. Run it without competing CPU-heavy jobs. This is a small machine-specific hot-loop experiment, not a robust throughput estimate, an allocation profile, whole-application startup timing or evidence for general six-active-player GTO.

For an interview: “I reduced overhead in repeated CFR traversals using a privately owned immutable graph. I preserved floating-point operation order and checked whole policies and work counts exactly, then replayed the original qualification pipeline. I measured traversal separately from startup so I could state the benefit without overstating what the benchmark proves.”
