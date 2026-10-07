# Joint preflop and coarse flop-texture research

The staged 3bb/9bb source uses mandatory checkdown for every non-all-in pot. Its fixed-policy material-content bound was only 20.04%, below the existing 25% gate. This experiment changes the continuation incentives, trains all six players together, and measures the resulting policy. It is an offline research model, not a new trainer pack or a general poker solution.

## Declared game

`SIX_PUBLIC_FLOP_TEXTURES_ONE_BET_THEN_CHECKDOWN/v1` retains the source's exact six-hand private distribution, complete preflop betting tree, stack sizes and folded-card blockers. At each explicitly selected, completed heads-up history, all physical flops are mapped to six public signals:

| Texture | Example | Information revealed |
| --- | --- | --- |
| Distinct, monotone | 2c 3c 4c | Three different ranks; one suit |
| Distinct, two tone | 2c 3c 4d | Three different ranks; two suits |
| Distinct, rainbow | 2c 3d 4h | Three different ranks; three suits |
| Paired, two tone | 2c 2d 3c | One pair; two suits |
| Paired, rainbow | 2c 2d 3h | One pair; three suits |
| Trips | 2c 2d 2h | Three equal ranks |

Players observe their own exact hole cards, the full preflop action history, the texture and flop betting actions. They **do not observe actual board cards, rank values, connectedness or which suit appears**. Texture chance probabilities still depend on all twelve dealt cards. Exact-board poker would provide substantially more strategically relevant information; exact integrated payoffs do not remove this information abstraction.

The first player may check or bet; after a check the second player may check or bet. A bet faces fold/call only. The declared bet is a fraction of the preflop pot, capped by both remaining stacks. The saved studies use half pot. An uncalled bet is refunded. After check/check or bet/call, the turn and river are forced checkdown and integrated exactly. Other heads-up histories and every multiway non-all-in pot keep the source's mandatory checkdown. Rake, flop raises, additional bet sizes and later-street decisions are unsupported.

The research cap is six selected histories, twelve private worlds and 164,000 complete states. This separate coarse-model cap does not change the existing exact-board trainer/content gate of at most four histories. The first declared menu selects source reach ranks 1–3. The second selects ranks 1–6 to cover the three dominant branches into which the first trained policy moved. Ranks always refer to the original source policy; a saved checkpoint contains the complete selected public histories and sizing, not an instruction to rerank them later.

## Exact conditional payoff generation

`SixMaxTexturePayoffTable` removes all twelve private cards and enumerates the remaining 40 choose 5 = **658,008** unordered five-card boards per private world. Each board represents ten possible unordered flop subsets. Classifying those subsets and weighting its showdown result by their texture counts is equivalent to independently enumerating every flop and every remaining turn/river pair:

```text
40 choose 3 = 9,880 flops
37 choose 2 = 666 runouts per flop
658,008 × 10 = 9,880 × 666 = 6,580,080 flop/runout outcomes per world
```

All six hands are evaluated once per five-card board. Those scores update all fifteen possible heads-up pairs. The table stores integer first-seat wins and ties for each pair and texture; the second seat's share is the complement. There is no equity sampling. Across twelve worlds, generation evaluates 7,896,096 five-card boards and accounts for 78,960,960 flop/runout outcomes. Enumeration happens offline, never in an HTTP request.

The loader checks strict schema/model identity, the canonical source pack and spot hashes, complete ordered private support, all fifteen unique pairs, physical texture counts, bounded integer wins/ties and recovery of every exact source pair-equity marginal. Physical counts include folded blockers. The table's canonical SHA-256 hash is bound into each checkpoint. Loading does not repeat millions of showdown evaluations; count and marginal checks alone do not mathematically prove every conditional table entry. The independent small-deck flop-first test and reproducible full-deck generator provide additional enumeration evidence.

## Solving and independent replay

`SixMaxTextureStudyMain solve` runs a fresh exhaustive CFR+ solve of the **entire declared six-player game**. It does not transplant the source's preflop strategy. The source policy is used only to select the fixed menu and build a comparison profile: source preflop actions followed by check/check, with calls at off-path facing-bet decisions.

Both profiles are audited with exact information-set best responses in the same texture game. The source's old checkdown-game score is not a comparable baseline: that game forbids the new betting deviations. The lifted profile must recover all six original expected checkdown utilities before the report is accepted. Six-player CFR has no two-player zero-sum convergence guarantee; a finite best-response gap describes only this declared game and budget.

Each checkpoint binds source, spot, exact payoff table, selected histories, sizing, complete state count and canonical policy hash. A separate canonical game hash binds the model, source, table and menu, so editing a bet fraction also fails reload. The supported solver metadata is exhaustive CFR+. Loading rejects incomplete, foreign and illegal policy rows; it never fills missing decisions with uniform strategy. Strict JSON rejects duplicate fields, trailing values, fractional integers, scalar coercion and null primitive counts. Input/output aliases, including existing hard links, fail before export. Outputs are individually atomic; the checkpoint/report pair is not a filesystem transaction. A saved checkpoint can reconstruct a missing report through read-only `audit`, without rerunning CFR or board generation.

Checkpoints can use plain JSON or deterministic `.json.gz` transport with the same policy and game identity. Both compressed input and expanded JSON are capped at 16 MiB; malformed streams and oversized expansion fail. `repack` validates an existing checkpoint before changing its transport. The four saved complete policies occupy about 217 KiB compressed instead of several MiB of repeated text.

The report separates three different measurements:

1. **Parent quality:** six-player NashConv against the complete policy in the declared texture game.
2. **Conditional quality:** reached joint hand distributions conditioned on each selected history and texture, with local best-response gaps and exact-combo marginals. Folded hands remain in the joint distribution. These describe the reached subgame, not a preflop deviator's counterfactual posterior. Every active seat's combos above 5% are counted descriptively; no texture bucket is admitted as an exact-flop lesson.
3. **Content:** selected history reach plus the unchanged all-physical-flop material-feasibility bound applied to the newly trained preflop policy. Broad texture coverage cannot substitute for material hand diversity on actual public boards. A tiny parent gap or a large aggregate bucket is not sufficient for publication.

The conditional audit refuses subnormal positive posterior masses rather than reporting a confident score after numeric loss. Zero-policy-reach histories remain in the parent game for unilateral deviations, but have no reached conditional report.

## Findings

The first 500-iteration, three-history solve reduced its same-game gap from **0.1726682913bb** for the lifted checkdown profile to **0.0010227108bb**. Its selected histories originally covered **74.38%** of heads-up reach, but after training covered only **0.00175%**. The policy largely moved into unexpanded checkdown branches. Its exact-physical-flop material bound also failed. This is evidence of continuation-model avoidance, not a successful broader trainer lesson.

The second declared menu covers all six leading source histories, including those destination branches. Its lifted checkdown baseline has a same-game gap of **0.3217128769bb**. Both budgets preserve almost all heads-up reach in betting continuations, resolving the first model's dominant avoidance pattern.

| Declared menu / budget | Own texture-game NashConv (bb) | Worst reached texture gap (bb) | Selected fraction of heads-up reach | Optimistic physical-flop material fraction |
| --- | ---: | ---: | ---: | ---: |
| [Ranks 1–3, 500](data/sixmax-staged-texture-study-500.json) | 0.0010227108 | 0.0903745638 | 0.00175355% | 0.00060744% |
| [Ranks 1–3, 1,000](data/sixmax-staged-texture-study-1000.json) | 0.0002581482 | 0.0903579369 | 0.00043890% | 0.00014769% |
| [Ranks 1–6, 500](data/sixmax-staged-texture-broad-study-500.json) | 0.0011488675 | 0.0026544993 | 99.93635061% | 0.08334133% |
| [Ranks 1–6, 1,000](data/sixmax-staged-texture-broad-study-1000.json) | 0.0002897451 | 0.0026378204 | 99.98395250% | 0.00036061% |

The three-history model demonstrates why an integrated parent score can hide a poor, rarely reached conditional strategy. The six-history model has much smaller conditional gaps, but all four policies still fail the unchanged physical-board content gate. Six of its 36 reached texture buckets contain two combos above 5% for both active seats; this aggregate result does not establish that actual visible flops retain those hands. More iterations lower each model's observed parent gap but do not produce useful trainer content. Two finite budgets are not a convergence certificate. Scores across the two menus concern different games and cannot be ranked as convergence improvements.

The original exact staged source is unchanged: pack hash `598d774ab5bf2b90845375dfcd8652b346d046c4d06d71c4f1e896785061000b`, spot hash `501de44ba6d9b2e1897b141834876a80e42f894db2183c02674d9c4c79ddf8a6`. The [exact texture table](data/sixmax-staged-texture-payoffs.json) has canonical hash `b30b33fef6af4d3ff44be379c46be5ebca41b6c5e2ac5129a469f2b8024ff9a7`. The three-history game hash is `2bfc44d9f3be024528f13dc00edd36a0866af186cdcf7728a804bf5ddbccbb2d`; the six-history game hash is `5c2e67e05ad9c33eebb7a1f4c8839747937ab6dfbd3feffa0f245696c4f81048`. Each budget shares its menu's game hash and has its own complete policy hash. Source preflop information sets remain 9,161; the menus add respectively 144/288 postflop rows and produce 140,737/142,681 complete states.

## Reproduction

Run from the repository root after installing the solver and engine modules. These commands write ignored local artifacts; generation is deliberately separate from solving.

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTexturePayoffTableMain' '-Dexec.args=docs/data/sixmax-staged-three-nine-source-pack.json .local/texture-payoffs.json'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTextureStudyMain' '-Dexec.args=solve docs/data/sixmax-staged-three-nine-source-pack.json .local/texture-payoffs.json .local/texture-policy.json .local/texture-study.json 500 1,2,3 0.5'
# Broader declared menu; repeat independently with 1,000 iterations for the budget comparison.
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTextureStudyMain' '-Dexec.args=solve docs/data/sixmax-staged-three-nine-source-pack.json .local/texture-payoffs.json .local/texture-broad-policy.json .local/texture-broad-study.json 500 1,2,3,4,5,6 0.5'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTextureStudyMain' '-Dexec.args=audit docs/data/sixmax-staged-three-nine-source-pack.json .local/texture-payoffs.json .local/texture-policy.json .local/texture-replayed-study.json'
# Validate and compress a snapshot, or replay the committed broad 1,000-iteration evidence.
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTextureStudyMain' '-Dexec.args=repack docs/data/sixmax-staged-three-nine-source-pack.json .local/texture-payoffs.json .local/texture-policy.json .local/texture-policy.json.gz'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTextureStudyMain' '-Dexec.args=audit docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-texture-payoffs.json docs/data/sixmax-staged-texture-broad-policy-1000.json.gz .local/texture-broad-replayed.json'
```

Tests cover every texture and all card permutations, independent flop-first enumeration for all pairs, physical blockers, hidden-hand information sets, all terminal settlements, checkdown recovery, exact tree accounting, unchanged multiway/uncontested paths, action-conditioned posteriors, zero-reach support, strict table/checkpoint validation and deterministic guarded solve/replay exports. Saved real-data tests replay measured evidence rather than repeating long training or full-deck enumeration in CI.

The [fixed-utility pruning and named-board follow-up](sixmax-pruning-and-board-witnesses.md) adds an opt-in traversal shortcut, independently compares same-game checkpoints and measures exact checkdown-equity differences on explicitly named physical flops. It separates conditional payoff variation from blocker-driven posterior information. The next model step is to add strategically meaningful board information, then validate the final policy's retained hand diversity and conditional quality. Multiway continuations, realistic ranges, rake and richer betting remain separate research work. No new trainer API, UI lesson or AWS deployment is enabled by these experiments.
