# Bounded physical-suit continuation study

This backend milestone replaces missing suit information for a declared subset of the [rank/texture model](sixmax-rank-texture-continuation.md). It trains fresh six-player preflop policies with a heads-up flop betting round at six selected histories. Everything remains `VALIDATION_ONLY`: broader ranges, multiway postflop betting, flop raises, later-street betting and applied rake are outside this model.

## Public observation and exact payoffs

The source is the unchanged [staged 3bb-open / 9bb-re-raise pack](data/sixmax-staged-three-nine-source-pack.json): 100bb equal stacks, 0.5bb small blind, no rake and twelve legal physical private worlds. All twelve private cards, including folded hands, block the deck.

The new classifier is `DECLARED_PHYSICAL_FLOPS_OTHERWISE_RANK_TEXTURE/v1`. A declared rank/texture group reveals **all three actual flop cards**. Every other group still reveals only its sorted ranks and suit multiplicity. For example, a refined group distinguishes `2s 3s 4s` from `2d 3d 4d`; an unrefined group continues to merge equivalent rank/texture observations. Refinement depends only on public cards, never on the acting player's hand or hidden world. Players also see their own physical hand and public action history. Actual suit-to-rank relationships are therefore available within refined groups without revealing anyone else's cards.

Seventeen named flops select fifteen distinct whole groups: the twelve earlier board witnesses plus the five largest-gap rank/texture groups at 500 iterations. These selectors are not seventeen isolated trained boards. **Every legal physical flop in each selected group** receives its own observation and payoff. The palette contains 1,301 observations: 134 physical flops and 1,167 remaining coarse groups. Across the twelve private worlds, 1,038 refined world/flop entries are legal; each has 666 turn/river runouts.

The incremental generator copies untouched exact rank/texture aggregates and independently enumerates the refined boards. It computes exact integer wins and ties for all fifteen seat pairs. This takes 691,308 refined runouts rather than regenerating every unrefined payoff. Regrouping the refined and untouched observations recovers **every original rank/texture count, win count and tie count exactly** for every world and pair. The original table, schema, hashes and enumeration caps are unchanged.

The [new payoff table](data/sixmax-staged-suit-refinement-payoffs.json.gz) has canonical SHA-256:

```text
152c0c523036238d2b783b473b353a17c4219c2d0c04bd8f555df30cd675c390
```

It binds the source pack, source spot and original rank-table hash. Loading checks the complete sorted physical palette, folded-card blocking, private-world order, bounded counts and exact regrouping. Those checks alone do not prove every conditional entry of an arbitrary supplied table: offsetting changes can preserve aggregates. Regression tests therefore independently enumerate **all 1,038 legal refined entries and all fifteen pair outcomes** with the object-level hand evaluator, in addition to the generation and hash checks. Untouched entries remain bound to the existing exact parent evidence.

## Joint training and independent quality checks

The shared one-bet engine preserves the original rules: check/bet, check/bet after a check, and fold/call facing a bet. Half-pot bets are capped by remaining stacks; uncalled chips are returned. Unselected histories and multiway pots retain the source's mandatory checkdown. Turn/river cards are integrated into settlement, rather than creating later betting decisions.

The complete game has **935,455 states and 70,703 information sets**: 9,161 preflop and 61,542 postflop. Both saved budgets use game hash:

```text
19c76977afa8005adcfc1714bb2bcd24c0089f6df0f7cecf2a468e158fe0af15
```

Fresh exhaustive CFR+ starts without old strategy rows. The source policy is used to select the declared history menu and to establish a forced-checkdown comparison, not as a warm start. Fixed-utility pruning skips only literal folded-player utility suffixes. Small same-game tests verify that pruning produces the identical complete policy. The new `:postflop:suit-refinement:` namespace prevents old coarse rows from being accepted as new rows.

Each study report includes six unrestricted parent information-set best responses and all **7,806 reached history/observation audits**. Each audit derives the joint private posterior from preflop action likelihoods and observation counts. It retains correlations and folded blockers. Local best responses keep preflop fixed. Six separately modified unilateral policies embed those local responses back into the complete parent tree; direct full-game evaluation must match the reach-weighted local gains within `1e-9`bb and remain bounded by the corresponding unrestricted parent gain. These are individual witnesses, not a jointly changed equilibrium policy.

The descriptive thresholds are 0.01bb conditional NashConv and 5% marginal combo mass, recorded in the report. They are not trainer admission criteria. Zero policy reach, impossible observations and numeric underflow remain distinct; incomplete or foreign policies fail closed. Independently enumerated pure own-information-set plans check the five largest-gap cases at each saved budget.

## Saved measurements

Measurements for the completed 100- and 500-iteration studies are recorded below after independent replay. The two budgets share the same source, payoff table, selections and game identity. Scores from this richer observation model cannot be treated as convergence measurements of the previous coarse game.

| Measurement | 100 iterations | 500 iterations |
| --- | ---: | ---: |
| Own-model parent NashConv, bb | 0.029564574106 | 0.001359586788 |
| Reached history/observation cases | 7,806 | 7,806 |
| Largest conditional gap, bb | 6.597603822 | 6.596835625 |
| Largest gap with two 5%-mass hands per active seat, bb | 6.597603822 | 6.596835625 |
| Cases above the descriptive 0.01bb threshold | 3,336 | 2,934 |
| Cases with two 5%-mass hands per active seat | 4,552 | 4,573 |
| Reach-weighted local gain total, bb | 0.005346080058 | 0.000224048477 |
| Heads-up history probability | 0.6590128719 | 0.6663853938 |
| Optimistic physical-content fraction bound | 0.9835327024 | 0.9992977714 |
| Physical feasibility status | `NOT_RULED_OUT` | `NOT_RULED_OUT` |
| Deal probability of selected physical refinements | 0.005768512046 | 0.005866344597 |
| Selected physical refinements as a fraction of heads-up reach | 0.008753261571 | 0.008803231060 |
| Visited nodes with fixed-utility pruning | 210,235,800 | 1,051,179,000 |
| Pruned suffix roots | 2,880,000 | 14,400,000 |

The [100](data/sixmax-staged-suit-refinement-policy-100.json.gz) and [500](data/sixmax-staged-suit-refinement-policy-500.json.gz) complete checkpoints, [100](data/sixmax-staged-suit-refinement-study-100.json.gz) and [500](data/sixmax-staged-suit-refinement-study-500.json.gz) full diagnostic reports, [100](data/sixmax-staged-suit-refinement-board-witnesses-100.json.gz) and [500](data/sixmax-staged-suit-refinement-board-witnesses-500.json.gz) physical-board reports and [100](data/sixmax-staged-suit-refinement-traversal-100.json) / [500](data/sixmax-staged-suit-refinement-traversal-500.json) observed work counters are committed separately. Replay recomputes policy diagnostics and board witnesses; traversal counters describe the original fresh solves and are not regenerated from the exported average policy.

At 500 iterations, the largest local gap occurs on the physical flop `4s 7s Js`, after CO opens to 3bb and calls BB's 9bb re-raise with the other seats folded. The history reaches `0.0001103811823`; that board's conditional probability is approximately `1.900416793e-9`. The resulting parent weight is tiny even though both active seats retain two hands above 5% in the conditional posterior. A common history can also contain rare boards: the CO-open/BTN-re-raise history reaches 35.46% of deals but has a maximum 1.86649bb conditional gap. More public information exposes these gaps instead of averaging them over suits. The two same-game budgets lower the parent and weighted local scores, but do not resolve the worst local decisions or establish multiplayer convergence.

The forced-checkdown baseline has a 1.8251631993bb parent gap. Lifting it recovers the original source utilities within floating-point rounding; the new betting choices explain the baseline deviation opportunities. `NOT_RULED_OUT` is still only an optimistic physical-content feasibility bound. No concrete training menu has passed the original retained content screen or been admitted to the trainer.

The physical refinements cover about **0.88% of heads-up reach** at 500 iterations, calculated by summing `P(history) * P(physical observation | history)` over the declared cases. The optimistic all-flop bound does not measure this model's exact-public-card coverage. Even retaining every current refined case cannot reach the original 25% content-coverage target. Wider observation coverage therefore needs an explicit cost/model decision before this experiment can supply a broad trainer menu.

## Physical-board witnesses

The independently enumerated named-board reports compare actual checkdown shares and private posteriors against the new physical observation before any flop actions. Each budget requests seventeen boards across six histories. `2d 3d 4d` is impossible because fixed UTG `4c 4d` blocks it; `Ah Kh Qh` is blocked in every original private world by the CO range. Those cases remain explicit blocked boards.

The remaining requests produce **90 board/history witnesses, 828 private-world rows and 551,448 runouts** per budget. Refined world-level shares and posterior settlements agree with the physical board to numerical precision. The audit rejects an undeclared board rather than claiming physical accuracy for an unrefined group. This removes the demonstrated suit abstraction discrepancy within the declared groups; it is not an action-EV certificate, full-deck abstraction bound or proof of good strategic play.

## Artifact and command boundaries

Payoff schema is `six-max-suit-refinement-payoffs/v1`, checkpoint schema `six-max-suit-refinement-checkpoint/v1`, study schema `six-max-suit-refinement-study/v1`, and named-board schema `six-max-suit-refinement-board-witnesses/v1`. Checkpoints bind source/spot/parent/table/game/solution hashes, sizing, iterations, algorithm, exhaustive traversal and complete support. Replay strictly recomputes the entire report without training or writing a policy. Gzip exports are deterministic; each file is atomically replaced. All CLI paths must be distinct after normalization, including hardlinks.

Caps are sixteen refined groups, 2,000 observations, twelve private worlds, six selected histories, one million complete states and 1,000 iterations. Table, checkpoint, study and board-witness raw/compressed/expanded caps are respectively 8, 64, 32 and 2MiB. The model is an offline experiment; browser requests never start these solves or expose hidden private-world diagnostics.

Run from the repository root after installing local Maven dependencies:

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxSuitRefinementPayoffTableMain' '-Dexec.args=docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz .local/suit-payoffs.json.gz 2c3d4h;2hQcTd;2h8c8d;AsKsQs;2s3s4s;2d3d4d;2c2d3h;9c9h9s;AcKdQs;5d6d7c;JdQdTd;AhKhQh;4s7sTs;5sTsKs;4s7sJs;8c8dQh;6sTsKs'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxSuitRefinementStudyMain' '-Dexec.args=solve docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz .local/suit-payoffs.json.gz .local/suit-policy-500.json.gz .local/suit-study-500.json.gz 500 1,2,3,4,5,6 0.5 FIXED_UTILITY'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxSuitRefinementStudyMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-policy-500.json.gz docs/data/sixmax-staged-suit-refinement-study-500.json.gz'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxSuitRefinementBoardAuditMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-policy-500.json.gz docs/data/sixmax-staged-suit-refinement-board-witnesses-500.json.gz'
mvn -q -pl solver -am test '-Dtest=SixMaxSuitRefinement*Test,SixMaxRankTexture*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
```

The previous rank/texture wrappers now share betting and conditional diagnostic logic with this model. Full replay of the saved rank/texture policies and conditional reports remains unchanged. Compatibility is checked through the original artifact hashes and every saved metric, not assumed from a similar implementation.

## Evaluator bug found by cross-checking

Full refined-table enumeration caught a defect in PokerLab's object-level hand result: decoding a packed three-of-a-kind score retained the trip rank and first kicker but discarded the second kicker. On board `2c 2d 3h Ac 2h`, `Kd 9d` must beat `6c 5c` with king versus six as the second kicker; object comparisons incorrectly reported a tie. The packed scoring path already retained all three tiebreakers and generated the saved solver payoffs correctly.

The fix includes the second kicker in the object result. Five-/seven-card evaluator regressions and end-to-end showdown tests cover both winner directions and genuine equal-kicker ties. Independent enumeration of every refined pair payoff now checks the corrected object path against packed-score generation. Existing solver policy/table hashes are preserved because their production payoffs use the already correct packed scores. This is an in-house engine defect, not an upstream library report; it is a concrete correctness/debugging example for an interview or CV.

## Next backend gate and interview explanation

The next task is to improve quality at strategically important physical decisions and measure rare-decision errors explicitly. A small parent score can still conceal bad local play. Choose a material physical board/history menu under the resulting joint policy, apply the retained hand-diversity and quality checks, then verify independent-policy/budget stability before proposing trainer admission. Most public groups still hide actual suits, so this milestone cannot replace wider observation research. Broader credible ranges, realistic raise menus, multiway betting, later streets and rake remain separate goals. AWS deployment remains paused.

For an interview, explain the three separate questions: what information players observe, whether payoff/chance calculations match that information, and whether the learned strategy is good within that game. Derive `P(private world | public actions, observation)` from the prior, action likelihoods and physical observation count. Describe exact win/tie arithmetic, immutable hash-bound evidence, the shared engine's compatibility checks, pure-plan best-response validation and direct unilateral parent witnesses. The ability to reproduce a limitation is part of the solver result.
