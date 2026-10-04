# Sampling connected six-seat poker without changing the game

`MultiPlayerCfrSolver` now supports seeded chance sampling alongside the existing exhaustive traversal. `SixMaxConnectedPreflopGame` supports up to four completed heads-up preflop histories, eight selected physical flops per history, and 32 compatible private-deal/flop pairs in total. The source private support remains capped at four deals. These limits allow wider connected experiments; they do not establish broad six-max cash coverage.

The [first connected preflop study](sixmax-connected-preflop.md) walked every turn and river at each iteration. The new traversal samples public chance while retaining all player actions. The [committed coverage study](data/sixmax-flop-coverage.json) compares one and four selected physical flops, two seeds, two proposals and a paired control-variate setting. All results remain `VALIDATION_ONLY`, and no sampled profile is published to the trainer.

## Sampling and importance weights

The default constructor still runs exhaustive CFR or CFR+ with unchanged policy results. The optional modes are `SAMPLED` (private and public chance), `SAMPLED_AFTER_ROOT` (enumerate initial private chance, sample later chance), and `SAMPLED_RUNOUTS` (enumerate the first two chance layers, then sample). In this connected game, those first two layers are the private deal and selected-flop/residual menu. Every selected compatible flop receives work on each player's pass in runout mode, while turns and rivers retain physical sampling. All player actions are traversed in every mode. Sampled traversal uses unclipped regret matching and rejects CFR+.

At each sampled chance node, physical probability `p` is replaced **for drawing only** by:

```text
q = (1 - mixture) * p + mixture / numberOfOutcomes
importance ratio = p / q
```

Mixture zero draws physical chance. Mixture 0.5 makes a rare selected flop much more likely to be visited, while still sampling the residual checkdown branch. The actual game remains unchanged: a compatible physical flop still has probability 1/9,880, blocked flops still have probability zero, and the same six private hands persist across streets. Proposal settings are solver parameters, not a new public-card game or information-set feature.

The product of **prefix** importance ratios enters counterfactual regret updates and strategy averaging. Sampled **suffix** values are weighted on their return to earlier decisions. Omitting either correction would train against the proposal distribution. All opponent reaches enter a target player's counterfactual update; that player's own action reach is excluded. Quantiles are shared across alternative action branches at one chance depth within a pass, preserving each node's marginal. Each alternating player's pass receives fresh independent draws, so its proposal is independent of the preceding player's stochastic update. `solve` resets the seed, sampled draws, strategy tables and traversal counters.

The sampling formulation is informed by [Lanctot et al., Monte Carlo Sampling for Regret Minimization in Extensive Games (2009)](https://proceedings.neurips.cc/paper/2009/file/00411460f7c92d2124a67ea0f4cb5f85-Paper.pdf). That paper's equilibrium analysis concerns two-player zero-sum games. PokerLab's six-player variant is checked through controlled EV examples and exact declared-game unilateral best responses; sampling does not add a six-player equilibrium guarantee.

## Linear regret and averaging weights

An optional linear weighting setting multiplies **both** each iteration's regret increments and its average-policy contributions by the iteration number. Earlier mistakes consequently carry less relative weight. This follows the LCFR definition in [Brown and Sandholm, Solving Imperfect-Information Games via Discounted Regret Minimization (2019)](https://arxiv.org/abs/1809.04040). It uses unclipped regrets and is rejected with CFR+. It is a separate parameter from chance traversal and changes no game probabilities. The report declares ordinary or linear CFR explicitly; neither is the default solver mode.

Tests include a controlled safe-versus-rare decision and an independent five-round alternating matrix calculation, which detects weighting only the average while leaving regrets unweighted. A two-layer chance control confirms that runout mode is identical to exhaustive traversal when no later chance layer exists.

## Centering the rare-flop estimate

Oversampling a rare betting flop can create variance even when the weights are correct. The ordinary checkdown remainder carries nearly all physical probability, but only part of the proposal probability. Multiplying its large known utility by `p/q` needlessly makes preflop values fluctuate.

The game supplies the original exact six-seat checkdown utility as a fixed internal baseline `B` at the selected-flop chance node. The sampled estimate becomes:

```text
B + (p / q) * (sampledContinuation - B)
```

Its proposal expectation is `B + sum(p * (continuation - B))`, which equals the original chance expectation. Prefix weights for descendant regret and averaging updates remain unchanged. The baseline is a computational control variate, never an action EV substitute or a private-card feature in the policy. It does not freeze postflop decisions. Ordinary physical sampling has `p/q = 1` and returns the sampled continuation directly. Other chance nodes use the default zero baseline.

This fixed centering follows the use of baselines for variance reduction discussed in [Schmid et al., VR-MCCFR (2018)](https://arxiv.org/abs/1809.03057). It is narrower than that paper's state-action and bootstrapped baseline methods, and does not justify enabling sampled CFR+ here. The study includes the same wider game, seed, mixture and budget with centering disabled to separate this choice from traversal savings.

## An unvisited decision remains untrained

Chance sampling can leave public-card information sets unseen. Normal policy evaluation continues to reject missing rows; no implicit uniform fallback is introduced into the trainer or decision evaluator.

`MultiPlayerStrategyCompletion.uniformAtUnseen` is an explicitly named offline audit operation. It walks the complete bounded game under a state-visit budget, adds uniform actions at missing rows and validates all supplied rows, including probabilities and legal action names. Foreign policy keys, invalid distributions and exhausted enumeration budgets fail. The resulting completed profile receives an exact six-player best-response audit. Its conditional flop/turn/river policy is also evaluated against its complete physical continuation game without re-solving it.

The report separates visited rows, uniformly completed rows, sampled and completed policy hashes, actual betting-continuation probability, six-player NashConv and conditional postflop gaps. Visiting a row does not establish that it has sufficient samples or positive average-policy reach. A small full-game score can conceal large errors on rare flops. Uniform completion is part of the audited profile; its quality cannot be attributed solely to learned actions.

The reported traversal reduction compares deterministic state visits at the same iteration count. Complete-tree visits times six players times the budget give the exhaustive count. Sampled counters include every visited chance, decision and terminal node. This is an iteration-cost comparison, not a wall-clock speed or convergence guarantee; full completion and best responses still incur bounded exhaustive work.

## Measured results and the remaining quality problem

All three artifacts use the same unchanged synthetic two-deal pack, hash `2ce2adc9a9cb69179e36f6de3d223b40542d9588da61c58ff66fc9d241b57dcf`. The wider menu contains `3c 7s 9d`, `5s 8c Qc`, `2s 3c 5s`, and `2s 5d Tc`. Folded-card blockers leave two compatible flops for the first deal and four for the second: six physical deal/flop pairs in total, 210,961 information sets, and a source-policy betting-continuation probability of 0.000501664%. Every selected compatible flop retains its actual 1/9,880 probability.

The following comparisons use seed 711, four selected flops and 500 iterations. Gaps are in bb and include explicit uniform completion of missing rows.

| Algorithm and chance schedule | Proposal mixture / centering | Visited rows | Uniformly added rows | Traversal reduction | Six-player NashConv | Maximum conditional postflop gap |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Ordinary CFR, sample flop and runout | 0 / enabled | 7,089 | 203,872 | 26.46× | 0.695895 | 6.872654 |
| Ordinary CFR, sample flop and runout | 0.5 / enabled | 67,177 | 143,784 | 26.37× | 0.695882 | 7.080481 |
| Ordinary CFR, sample flop and runout | 0.5 / disabled | 67,177 | 143,784 | 26.37× | 0.699842 | 7.059193 |
| Ordinary CFR, enumerate flops / sample runout | 0 / enabled | 192,961 | 18,000 | 25.75× | 0.695877 | 4.989862 |
| Linear CFR, enumerate flops / sample runout | 0 / enabled | 192,961 | 18,000 | 25.75× | 0.004296 | 7.066358 |

Physical sampling never visits a selected betting flop in either original 500-iteration seed, leaving every postflop row untrained. The mixture proposal increases coverage, and centering reduces preflop variance in the paired comparison; neither fixes decision quality. Enumerating flops increases visited rows from 3.36% to 91.47% while retaining substantial traversal savings, but 18,000 rows still need uniform completion. Even visited rows can retain poor or effectively untrained policy averages.

Linear weighting sharply improves the full-game score at the same traversal count, but **does not improve the worst conditional postflop gap**. Its learned probability of entering any selected betting continuation is only 0.0000153681%, roughly 33 times below the source policy's reach. Those conditional games consequently have little weight in the full-game score. The smaller number is not evidence of a playable postflop trainer, broad poker quality, or superiority to exhaustive CFR+. These experiments change the algorithm as well as traversal when comparing ordinary and linear runs; they do not supply an equal-quality runtime comparison.

The next priority is useful conditional strategy quality and support at histories actually reached by learned policies, with independent seeds and training budgets. Further widening the flop menu before that gate would add mostly untrained decisions. The existing trainer source and its publication status remain unchanged.

## Reproduce and inspect

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxFlopCoverageAuditMain' '-Dexec.args=solver/src/test/resources/six-seat-full-round-pack.json docs/data/sixmax-flop-coverage.json 711,712 500 4 0,0.5'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxFlopCoverageAuditMain' '-Dexec.args=solver/src/test/resources/six-seat-full-round-pack.json docs/data/sixmax-runout-coverage.json 711 500 4 0 SAMPLED_RUNOUTS false'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxFlopCoverageAuditMain' '-Dexec.args=solver/src/test/resources/six-seat-full-round-pack.json docs/data/sixmax-linear-runout-coverage.json 711 500 4 0 SAMPLED_RUNOUTS true'
```

The CLI binds the original source pack hash and spot hash, selects the existing highest-reach heads-up history using seed 711, and samples additional distinct legal physical flops from full counterfactual support. It compares widths one and the requested maximum. The first training seed receives an extra wider-game run without centering for each positive mixture. Run progress appears on stdout; elapsed time is excluded from JSON. Repeated bounded exports must be byte-identical and leave the source unchanged.

The six required arguments retain the original ordinary-CFR, `SAMPLED_AFTER_ROOT` workflow. Two optional arguments declare chance traversal and the literal `true`/`false` linear weighting flag. The [ordinary runout study](data/sixmax-runout-coverage.json) and [linear runout study](data/sixmax-linear-runout-coverage.json) use the same flops, private support, seed and budget as the first study, separating a sampling-schedule change from an algorithm change.

The audit allows 1–3 distinct seeds, 1–3 distinct mixture values in [0,0.95], 1–3,000 iterations, one to four physical flops, no more than eight compatible deal/flop pairs and two million visits per completion. Input files are capped at 16 MiB, and source overwrite is rejected. These audit caps are deliberately below the game constructor's larger limits.

Tests independently check rare-event EV accounting, both prefix and suffix corrections, controlled six-player optimal actions, seeded reproducibility and counter reset, exhaustive compatibility, traversal reduction, baseline expectation and disabling, non-finite baseline rejection, explicit completion/probability/state-budget validation, wider physical probabilities and blockers, connected-game support caps, source-bound export and exact full/conditional best responses.

The next gate is adequate postflop sample support and quality at a wider physical-flop coverage. A scalable lazy public-card game, more effective sampling or state-action baselines, and an independent held-out strategic audit must be measured before realistic content can be promoted. Credible position-specific private ranges, wider action menus, multiway postflop play and specified cash rake remain separate requirements.
