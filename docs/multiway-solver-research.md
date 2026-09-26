# Six-seat all-in solver research

The `solver` module now has an experimental **2–6 player preflop call/fold subgame**. One seat has already shoved; each remaining seat calls or folds in order. This is a real multi-player decision tree with private, weighted two-card ranges, card removal across all dealt players, split-pot showdown payoffs, and a chip-profit value for every seat. It is deliberately bounded: equal effective stacks, no side pots or rake, a forced initial shove, and no further betting or postflop decisions. It is **not** a general six-player no-limit Hold'em solver or a published GTO strategy.

`MultiPlayerCfrSolver` makes an alternating full-tree regret pass for each player and supports vanilla CFR or a CFR+ style regret clip and weighted average. The existing two-player solver and validation packs are unchanged. In a multi-player game, low regret from this run alone is not a Nash certificate. `MultiwayCallBestResponse` separately computes each responder's best unilateral action at every private-hand/public-history information set against the saved profile. Its `nashConvBb` is the sum of those gains **in this finite, precomputed payoff game**. The forced aggressor has no decision to deviate from. The game also records the largest one-standard-error terminal payoff estimate from board sampling; this error is reported separately and is not included in `nashConvBb`.

`MultiwayCallTrainer` draws a seeded joint deal and prior public actions from the saved profile, shows only the acting seat's exact combo, and grades call or fold. Its action EVs marginalize over hidden deals **conditional on the shown hand and prior public actions**. It does not grade against the single hidden hand sampled to make the question. `MultiwayDrillSession` adds ten-decision sessions, mixed or fixed responding seats, deterministic replay and a complete EV-loss review. These drills now have a saved solution-pack format and opt-in HTTP API; a website lesson remains future work.

## Exact payoffs and saved solutions

`ExactMultiwayShowdownOracle` enumerates every possible five-card board after removing all dealt hole cards, including folded hands. With six players this is C(40,5) = 658,008 boards per joint deal. It evaluates each player's hand once per board, then reuses those scores for every active subset. A bounded one-deal cache lets the payoff builder retrieve all subsets without re-enumerating boards. Tie pots are split equally among the best active hands. Exact enumeration has zero board-sampling standard error, subject to floating-point arithmetic and evaluator correctness.

`MultiwayCallSpot` defines seat order, weighted exact-card ranges, equal stacks, prior commitments and dead money. The first seat is already all-in. Each remaining seat acts once. `MultiwaySolutionPack` stores the canonical spot, strategy, all reachable payoff entries, algorithm/version metadata and measured deviation score. Loading checks the spot hash, payoff coverage, probabilities and quality metrics by rebuilding the finite game from saved data. It performs no online solve or board enumeration. A full-pack SHA-256 hash binds drill grading to the exact artifact, including strategy and payoff data; a spot hash alone would not detect a newly solved strategy for the same game.

All generated packs remain `VALIDATION_ONLY`. The research API additionally requires exact payoffs and NashConv at most 0.05bb. This is an engineering admission threshold, not a publication decision. The synthetic fixture has only two combos per seat and a restricted action tree.

Generate the exact fixture from the repository root:

```sh
mvn -pl solver -am -DskipTests install
cd solver
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.GenerateMultiwayPack' '-Dexec.args=exact six-seat-fixture src/test/resources/six-seat-exact-pack.json 1000 2026-09-24T00:00:00Z'
```

Replace `six-seat-fixture` with a spot JSON file to use custom ranges and commitments. `MultiwayPackJson.writeSpot` emits that schema. The generator also accepts `sampled <spot> <output> <iterations> <generated-at> <trials> <seed>` for experiments; sampled packs cannot enter the drill API. To measure convergence on an existing payoff table without repeating expensive enumeration:

```sh
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.MultiwayConvergenceMain' '-Dexec.args=src/test/resources/six-seat-exact-pack.json 10,100,1000'
```

The CSV reports the actual best-response gain at each budget. Multi-player CFR does not promise a monotonically decreasing gain; inspect the measurements rather than using iteration count as a quality certificate.

The committed fixture has 1,984 payoff entries (64 joint deals × 31 reachable call subsets), takes about 478 KiB, and was generated in 100.85 seconds on the development machine. Every entry enumerates 658,008 boards. Its full-pack SHA-256 is `8ce7afb272b902b39134b36e550d561d8dae2319e038f0bee9311dd44c4c3b53`.

| CFR+ iterations | NashConv (bb) | Maximum payoff sampling SE (bb) |
| --- | ---: | ---: |
| 10 | 0.526583362719 | 0 |
| 100 | 0.005788122532 | 0 |
| 1,000 | 0.000058407500 | 0 |

These measurements use exactly the same saved payoff table at each budget. They demonstrate numerical progress for this finite synthetic game; omitted actions and range assumptions are separate sources of model error.

## Reproduce the six-seat run

The small research driver uses 20bb stacks, two synthetic combos per seat, 64 unblocked joint deals, and a fixed seed. It precomputes every reachable call-subset payoff before solving. From the repository root:

```sh
mvn -pl solver -am -DskipTests install
cd solver
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.MultiwayResearchMain' '-Dexec.args=100 300'
```

With 100 CFR+ iterations and 300 board trials per active subset on the development machine, it reported sampled-table NashConv **0.005798bb** and maximum terminal payoff sampling SE **3.433172bb**. The sampling error dwarfs the strategy deviation number; this run demonstrates six-seat mechanics and instrumentation, **not** reliable poker advice. Increase board trials to study payoff precision, and compare repeated seeds before drawing any strategy conclusion. The driver prints generation/solve times and each seat's profile EV and deviation gain.

The precomputation caps joint deals at 4,096 and payoff-table entries at 2,048 so accidental broad ranges fail fast. These are research limits, not claims of production scalability. Exact generation is an offline CPU task and can take several minutes. The next solver work is broader, reviewed ranges and a legal betting tree covering opens and re-raises, unequal stacks/side pots, explicit rake and postflop continuation values. Turn, river, partial-hand and full-hand practice depend on those later models; they are not implemented by this call/fold trainer.
