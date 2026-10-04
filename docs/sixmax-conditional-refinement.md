# Refining conditional postflop play after a six-seat solve

`SixMaxConditionalPostflopRefinement` adds an offline second stage to the [sampled connected solver](sixmax-flop-coverage.md). It holds the learned preflop strategies fixed, derives the reached six-hand posterior for each selected physical flop, solves the complete heads-up flop/turn/river continuation using exhaustive CFR+, and maps the resulting policies back to the original six-seat information sets. It then measures exact unilateral best responses for all six players in the original parent game.

This addresses the large conditional errors exposed by the first sampling study. It remains a bounded research operation; neither the source trainer pack nor its publication status changes.

## Recorded results

The [two-seed study](data/sixmax-conditional-refinement.json) uses the same four flops and two correlated private deals as the preceding study: 500 sampled linear-CFR joint iterations, followed by 100 exhaustive CFR+ iterations per reached conditional game. Seed 711 reproduces the preceding baseline's completed policy hash and joint traversal count. Each candidate replaces all 203,872 postflop rows and preserves all 7,089 preflop rows on this fixture.

| Joint training seed | Worst conditional gap before (bb) | Worst conditional gap after (bb) | Six-player NashConv before (bb) | Six-player NashConv after (bb) |
| --- | ---: | ---: | ---: | ---: |
| 711 | 7.066358 | 0.014419 | 0.004296034 | 0.004295688 |
| 712 | 7.000540 | 0.014419 | 0.004296032 | 0.004295703 |

The largest conditional gap falls by about 490 times for seed 711. Exact parent checks show a small improvement in both cases; this is a measurement for these two complete profiles, not a general monotonicity guarantee. The exact conditional results are identical across these two runs while their sampled original continuation policies differ. This does not establish stability across different private ranges.

For seed 711, the four conditional gaps after refinement are 0.007200bb, 0.014412bb, 0.014042bb and 0.014419bb. Their original gaps were 3.871268bb, 6.165900bb, 7.066358bb and 6.293436bb. Seed 712 also has a maximum of 0.014419bb. Selected betting-continuation reach remains about 0.000015368% of deals in each candidate, so the improvement does not fix sparse coverage or make these decisions common.

The source pack hash remains `2ce2adc9a9cb69179e36f6de3d223b40542d9588da61c58ff66fc9d241b57dcf`; the preceding coverage artifact's canonical hash is `34aaab24eaca07283deabcae15ff2222958a35e11e4a7ed8ca33d23979177d06`. No sampled probabilities replace physical chance, and no new trainer pack is produced.

## What remains fixed

The posterior uses the original joint private-deal weights, every learned preflop action probability, and physical flop compatibility. It retains the four folded players' cards as blockers and keeps private hands fixed across all streets. No independent-range product, new public-card menu, sampled payoff table or extra information-set feature is introduced.

Only rows produced by the exact conditional solver are replaced. Preflop rows and rows outside the reached posterior retain their original values, including counterfactual private hands which a player might reach by deviating earlier. The candidate must have exactly the parent's original information-set keys and valid action distributions. Zero-reach histories and flops are reported explicitly and left untouched.

When a policy row is shared by reached and zero-posterior hidden deals, the same action mix necessarily applies to both. The policy cannot distinguish those hidden worlds; the full parent audit measures the effect of changing that shared row.

Input policies must already be complete. The refinement API validates them under a two-million-state budget and rejects missing rows rather than silently inserting actions. The study's first sampling stage explicitly performs uniform completion and reports the number of inserted rows before invoking refinement. Exhaustive conditional solving then replaces supported postflop rows, including their initially uniform completion. The original `CfrSolution` remains immutable.

## Why the full parent game is checked again

Re-solving against a frozen reached posterior is not a safe-subgame replacement guarantee. A different earlier decision can change which private hands reach the continuation; that changed distribution can alter the value of the new policy. This dependence is discussed in [Brown and Sandholm, Safe and Nested Subgame Solving for Imperfect-Information Games (2017)](https://papers.neurips.cc/paper/6671-safe-and-nested-subgame-solving-for-imperfect-information-games.pdf). PokerLab does not implement that paper's safety gadget or extend its two-player theory to six players.

Instead, the report measures the original and candidate policies with `MultiPlayerInformationSetBestResponse` over the entire declared six-seat tree. It retains all six profile utilities, best-response values and deviation gains, and reports the NashConv change. A conditional improvement does not automatically accept a candidate. Refinement can also be undertrained at a small budget; callers must inspect both conditional and parent results before using it.

Preflop strategies remain unchanged, so the candidate's probability of entering selected betting continuations remains unchanged. The original joint iteration budget is retained in `CfrSolution`; the separate refinement budget is recorded in the result and audit artifact. A future pack format must retain both stages' provenance.

## Reproduce the experiment

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxConditionalRefinementAuditMain' '-Dexec.args=solver/src/test/resources/six-seat-full-round-pack.json docs/data/sixmax-linear-runout-coverage.json docs/data/sixmax-conditional-refinement.json 711,712 500 100'
```

The CLI requires a source pack, the preceding coverage artifact, an output path, 1–3 distinct joint-training seeds, 1–3,000 joint iterations, and 1–500 conditional iterations. It reconstructs the last coverage case from its public histories, flops and bet sizes, verifies the source pack/spot hashes, and checks that every rebuilt coverage field matches the declaration. The audit allows at most eight compatible private-deal/flop pairs. Inputs are capped at 16 MiB and output cannot overwrite either input.

Each seed runs linear CFR with enumerated private deals and selected flops, sampled physical turn/river chance, and explicit completion. Conditional solves use the exact 37-turn/36-river physical game, with stack-capped bets and the existing check/bet, call/fold action abstraction. No raises, multiway postflop or rake are added by this step.

The `six-max-conditional-refinement-audit/v1` artifact binds the source pack/spot and a SHA-256 of the canonical, sorted-map serialization of the preceding coverage artifact. It separates original/candidate policy hashes, joint traversal counts, missing rows, replaced/preserved rows, conditional before/after quality, and full six-seat before/after quality. Timings are omitted for reproducibility. The JSON is `VALIDATION_ONLY` and does not publish a trainer pack.

## Validation and remaining scope

Tests verify a large conditional improvement from a controlled checkdown policy, unchanged preflop rows and original hash, complete candidate support, exact independent parent reevaluation, agreement between the embedded and separately reconstructed conditional audits, preservation of zero-posterior counterfactual rows, blocked-flop and zero-history skips, correct branch labels when coverage sort order differs from selection order, invalid budgets and implicit-completion rejection. A repeated real-pack CLI export checks source/coverage binding, rejects forged coverage fields, separates budgets, checks policy hashes and byte-identical output while preserving both inputs.

This experiment still uses the synthetic two-deal source and sparse physical-flop coverage. Improving its conditional decisions does not increase the probability of reaching those decisions, establish realistic cash ranges, or certify full six-max poker. The next gate is refinement on strategically significant histories reached by learned policies, followed by broader private-hand support and measured parent/conditional quality across those games.
