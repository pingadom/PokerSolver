# Six-seat all-in solver research

The `solver` module now has an experimental **2–6 player preflop call/fold subgame**. One seat has already shoved; each remaining seat calls or folds in order. This is a real multi-player decision tree with private, weighted two-card ranges, card removal across all dealt players, split-pot showdown payoffs, and a chip-profit value for every seat. It is deliberately bounded: equal effective stacks, no side pots or rake, a forced initial shove, and no further betting or postflop decisions. It is **not** a general six-player no-limit Hold'em solver or a published GTO strategy.

`MultiPlayerCfrSolver` makes an alternating full-tree regret pass for each player and supports vanilla CFR or a CFR+ style regret clip and weighted average. The existing two-player solver and validation packs are unchanged. In a multi-player game, low regret from this run alone is not a Nash certificate. `MultiwayCallBestResponse` separately computes each responder's best unilateral action at every private-hand/public-history information set against the saved profile. Its `nashConvBb` is the sum of those gains **in this finite, precomputed payoff game**. The forced aggressor has no decision to deviate from. The game also records the largest one-standard-error terminal payoff estimate from board sampling; this error is reported separately and is not included in `nashConvBb`.

`MultiwayCallTrainer` draws a seeded joint deal and prior public actions from the saved profile, shows only the acting seat's exact combo, and grades call or fold. Its action EVs marginalize over hidden deals **conditional on the shown hand and prior public actions**. It does not grade against the single hidden hand sampled to make the question. This is backend drill logic only; there is no pack format, HTTP route or website lesson for this game yet.

## Reproduce the six-seat run

The small research driver uses 20bb stacks, two synthetic combos per seat, 64 unblocked joint deals, and a fixed seed. It precomputes every reachable call-subset payoff before solving. From the repository root:

```sh
mvn -pl solver -am -DskipTests install
cd solver
mvn -q exec:java '-Dexec.mainClass=com.pokerlab.solver.MultiwayResearchMain' '-Dexec.args=100 300'
```

With 100 CFR+ iterations and 300 board trials per active subset on the development machine, it reported sampled-table NashConv **0.005798bb** and maximum terminal payoff sampling SE **3.433172bb**. The sampling error dwarfs the strategy deviation number; this run demonstrates six-seat mechanics and instrumentation, **not** reliable poker advice. Increase board trials to study payoff precision, and compare repeated seeds before drawing any strategy conclusion. The driver prints generation/solve times and each seat's profile EV and deviation gain.

The precomputation caps joint deals at 4,096 and payoff-table entries at 2,048 so accidental broad ranges fail fast. These are research limits, not claims of production scalability. The next solver work is a payoff method precise enough for the chosen multiway ranges, measured convergence across multiple seeds, a reviewed 6-max ruleset with realistic ranges and bet tree, then a versioned solution pack with publication screening. A trainer UI should consume only such reviewed packs.
