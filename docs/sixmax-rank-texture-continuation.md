# Rank-aware six-seat continuation study

PokerLab now has a separately versioned solver model that observes flop ranks as well as suit multiplicity. It jointly trains all six seats' preflop decisions and one heads-up flop betting round at six declared histories. The saved 100- and 500-iteration studies contain complete policies and can be independently audited without training again. This is an offline `VALIDATION_ONLY` experiment; actual suits, richer betting and reviewed trainer content remain unfinished.

## What players observe

The public signal contains three sorted rank values (`2` through `14`) and one of the existing six texture classes. For example, `2s 3s 4s` and `2d 3d 4d` share the signal `(2,3,4,DISTINCT_MONOTONE)`. Distinct, paired and trip rank multiplicities must agree with the texture. Two-tone boards also conceal which ranks share a suit. Each player observes its own physical hand, this public signal and the complete public action history. Neither private-world indices nor other players' hands enter an information-set key.

There are at most **1,183** valid signals: `286*3` distinct-rank signals, `156*2` paired signals and `13` trip signals. The committed source supports **1,182** of them. Its fixed UTG `4c 4d` hand makes a trip-fours flop impossible. The palette is the sorted union of every legal signal across the original twelve private worlds; each world has its own physical counts and may give a palette entry zero probability. Stable signal keys depend only on public ranks/texture, never on the palette's position or a hidden world.

This is a different game from the earlier [six-texture study](sixmax-texture-continuation.md), which hid ranks entirely. Scores from these two models must not be presented as successive convergence measurements of one game.

## Exact payoff generation

`SixMaxConditionalPayoffEnumeration` is a shared integer enumeration kernel for the old and new tables. For each complete six-hand deal it removes all twelve private cards, including folded cards. It evaluates all `40 choose 5 = 658,008` five-card boards, scores each seat once per board and assigns the result to all ten three-card flop subsets. All fifteen seat pairs receive exact win/tie counts per public signal. This is algebraically equivalent to enumerating all `9,880` flops and `37 choose 2 = 666` runouts per flop, without scoring the same final board ten times.

The generator checks the per-signal identity `observedRunouts = physicalFlopCount * 666`. Production uses the full remaining deck. Small-deck tests independently enumerate flop first, score runouts through the object-level hand evaluator and compare every pair/signal entry. The committed rank-aware table also regroups **every** win/tie count into the previous six-class table exactly, across all twelve worlds and fifteen pairs.

The loader verifies strict schema, exact-source hashes, ordered full private support, the complete physical palette, all flop counts, bounded pair counts and recovery of every original pairwise checkdown marginal. These checks do not independently prove every conditional equity entry in an arbitrary supplied file: offsetting changes can preserve marginals. The committed evidence therefore includes reproducible exact generation, independent enumeration tests, canonical hashes and the saved-table regrouping check.

Table schema is `six-max-rank-texture-payoffs/v1`; classifier is `SORTED_BOARD_RANKS_SUIT_MULTIPLICITY/v1`. The [compressed table](data/sixmax-staged-rank-texture-payoffs.json.gz) has canonical hash:

```text
702409adf91e20bed38facb3328889fcb308bf2724589816a5281d1723705bd3
```

## Betting, solving and replay

The source remains the [staged 3bb-open / 9bb-re-raise pack](data/sixmax-staged-three-nine-source-pack.json): 100bb equal stacks, 0.5bb small blind, no rake and twelve legal joint private worlds. Its ranges are deliberately narrow and synthetic. Each selected history must end in a non-all-in heads-up pot with equal live commitments. Flop actions are check/bet, check/bet after a check, and fold/call facing a bet. A half-pot bet is capped by both remaining stacks; an uncalled bet is returned. Called and checked pots use the exact signal-conditioned pair shares. Other histories and all multiway non-all-in pots retain the source's mandatory checkdown.

The complete declared game has **888,205 states** and **65,771 information sets**: 9,161 preflop and 56,610 postflop. There are no flop raises, turn/river betting decisions or multiway flop decisions in this model. Integrated future runouts produce payoffs, rather than additional decision streets.

Fresh exhaustive CFR+ solves explicitly select `NONE` or `FIXED_UTILITY` pruning. The saved studies use the [literal inactive-player optimization](sixmax-pruning-and-board-witnesses.md); all players' own decision passes still retain their complete legal support. Neither the source policy nor a previous texture policy supplies the learned postflop rows. Checkdown baseline lifting is used only for comparison and must recover the original source utilities.

`SixMaxRankTextureStudy` binds the model, source pack, source spot, payoff table, selected histories/sizing and complete policy. Checkpoints use `six-max-rank-texture-checkpoint/v1` and require zero missing information sets when rebuilt. A new namespace prevents old texture rows from being accepted as rank-aware rows. The CLI rejects path aliases, including hardlinks, before output changes; exports are individually atomic. Caps are twelve private worlds, six selected histories, one million complete states and 1,000 iterations. Raw/compressed/expanded table inputs are bounded at 4MiB; checkpoint inputs are bounded at 64MiB. Gzip exports are deterministic and strict JSON rejects duplicate fields, trailing values and numeric coercion.

Read-only assessment rebuilds the full policy, independently computes each seat's information-set best response, checks zero-sum settlement and source checkdown recovery, and recomputes physical-board feasibility under the new preflop policy. Report scope is explicitly `PARENT_INFORMATION_SET_BEST_RESPONSES_ONLY`; this release does **not** certify every reached rank-signal subgame separately.

## Saved results

Both budgets use the same game hash `bb3cf0231e9cb367a84ee805df551e041b46f270876ccba891485babffd66e53` and the same six histories/sizing.

| Measurement | 100 iterations | 500 iterations |
| --- | ---: | ---: |
| Own-model parent NashConv, bb | 0.0297393909307953 | 0.0013729729940781645 |
| Heads-up history probability | 0.6584921080 | 0.6663642066 |
| Selected fraction of heads-up reach | 0.9947704725 | 0.9997532154 |
| Optimistic physical-content bound | 0.9834091694 | 0.9992942566 |
| Physical feasibility status | `NOT_RULED_OUT` | `NOT_RULED_OUT` |
| Visited nodes with fixed-utility pruning | 200,785,800 | 1,003,929,000 |
| Pruned suffix roots | 2,880,000 | 14,400,000 |

The checkdown baseline's own-model gap is 1.8239059358bb; source utility recovery errors are below `2e-15`bb. The decline between the two budgets is finite-budget evidence for this same game, not a six-player convergence theorem. `NOT_RULED_OUT` means an **optimistic upper bound cannot reject the candidate**. It does not mean a concrete board/history menu passed the retained content screen, local quality checks, range review or trainer admission.

The [100-iteration report](data/sixmax-staged-rank-texture-study-100.json), [500-iteration report](data/sixmax-staged-rank-texture-study-500.json) and their [100](data/sixmax-staged-rank-texture-policy-100.json.gz) / [500](data/sixmax-staged-rank-texture-policy-500.json.gz) complete checkpoints are replay-tested. The traversal observations are saved separately for [100](data/sixmax-staged-rank-texture-traversal-100.json) and [500](data/sixmax-staged-rank-texture-traversal-500.json); node counts are work measurements, not wall-time benchmark claims.

## Exact-board witnesses and remaining suit error

`SixMaxRankTextureBoardAuditMain` audits one to twenty-four distinct named flops against a complete checkpoint. It keeps the action-conditioned joint six-hand distribution, removes worlds blocked by actual cards and enumerates all 666 remaining turn/river pairs per compatible world. Zero-reach histories and physically blocked boards are explicit; positive subnormal private/board/signal masses fail instead of becoming confident zero claims. Hidden-hand rows are offline evidence and must not become trainer observations.

The audit separates three first-player checkdown shares: exact named-board equity (`E`), signal-table equity using the named-board posterior (`B`), and signal-table equity using the signal posterior (`T`). It reports `E-B`, `B-T` and `E-T`, plus the paired settlement differences for checked/called pots. These are measured before postflop action likelihoods; they are not learned button EVs or general poker exploitability.

The [100](data/sixmax-staged-rank-texture-board-witnesses-100.json) and [500](data/sixmax-staged-rank-texture-board-witnesses-500.json) reports use twelve declared boards across six histories: sixty compatible witnesses, 660 private-world entries and **439,560** runouts per report. The largest total share differences are **0.412013** and **0.412416** respectively. On the materially reached 500-iteration CO/BTN 19.5bb-pot history, `2s 3s 4s` gives the cutoff approximately **0.144525** exact share versus **0.556940** in the rank/texture model, a **−8.0421bb conditional checkdown settlement difference**. This is one board's conditional comparison, not full-game EV loss or a full-deck error bound. Revealing ranks has not resolved actual-suit information.

The next model milestone is a bounded public signal that observes strategically relevant suits and rank-to-suit association. Its grouping must preserve own-hand suit relationships and folded-card blockers; arbitrary suit canonicalization can lose information. Declare its chance weights, information sets and state budget before retraining. Then replay independent parent/conditional quality checks and construct a physically meaningful retained-content menu. Broader credible ranges, multiway postflop, raises, rake and later-street betting remain separate steps toward the wider 6-max trainer.

## Reproduction

Run from the repository root. Generate long-running evidence separately from read-only replay. Outputs below go to ignored `.local` paths.

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxRankTexturePayoffTableMain' '-Dexec.args=docs/data/sixmax-staged-three-nine-source-pack.json .local/rank-texture-payoffs.json.gz'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxRankTextureStudyMain' '-Dexec.args=solve docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz .local/rank-texture-policy-500.json.gz .local/rank-texture-study-500.json 500 1,2,3,4,5,6 0.5 FIXED_UTILITY'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxRankTextureStudyMain' '-Dexec.args=audit docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-rank-texture-policy-500.json.gz .local/rank-texture-replay-500.json'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxRankTextureBoardAuditMain' '-Dexec.args=docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-rank-texture-policy-500.json.gz .local/rank-texture-board-witnesses.json 2c3d4h;2hQcTd;2h8c8d;AsKsQs;2s3s4s;2d3d4d;2c2d3h;9c9h9s;AcKdQs;5d6d7c;JdQdTd;AhKhQh'
mvn -q -pl solver -am test '-Dtest=SixMaxRankTexture*Test,SixMaxTexturePayoffTableTest,SixMaxTextureArtifactTest,SixMaxTextureFollowupArtifactTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Freeze compiled solver/engine classes before a long study if Maven rebuilds continue concurrently. A running process must not load a mixture of class versions. `repack` exports the same checked checkpoint as raw JSON or gzip; it does not resume optimizer state or continue training.

For an interview: this contribution refines the game's public information, shares an exact integer payoff kernel, preserves complete six-player policies and proves what the richer model still omits through independent physical-board measurements. No upstream library issue was found in this work.
