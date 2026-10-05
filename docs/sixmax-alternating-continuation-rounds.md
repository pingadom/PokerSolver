# Quality-gated preflop/postflop rounds

PokerLab can now repeat complete preflop/postflop rounds in one declared six-seat game, retain the last passing policy and resume from an explicit policy checkpoint. This is an offline research workflow, not a new trainer pack or a general cash-poker solution.

The [preceding feedback study](sixmax-continuation-preflop-feedback.md) exposed the reason for the gates. Preflop re-solving lowered full-game NashConv while making the worst conditional postflop gap grow from about 0.013bb to 0.54–0.57bb. The earlier postflop score belonged to the earlier reached ranges. Copying it to the new policy would give a false quality claim. A second postflop solve restored conditional quality, but that did not establish that further alternating rounds would keep improving.

## Round and acceptance process

`SixMaxAlternatingContinuationSolver` starts from an explicitly complete policy. It independently computes full six-seat best responses, reached history probabilities and exact conditional heads-up best responses. Every selected history/flop must be reached and meet the declared conditional target before training starts. Zero-reach branches cannot silently count as accurate branches.

Each attempted round performs these steps:

1. Integrate exact frozen postflop continuation values for every original private deal and selected public history. Retain counterfactual support and literal physical-board probabilities.
2. Freshly solve every seat's preflop decisions using exhaustive CFR+. Replace only preflop rows and audit the changed reached ranges. Keep this intermediate score in the report, including any loss of conditional quality.
3. Freshly solve selected heads-up postflop branches at those new ranges using exhaustive CFR+. Preserve the new preflop rows and unsupported counterfactual postflop rows.
4. Compute the candidate's exact full-game NashConv and maximum conditional gap. Compare against the **last retained policy in this same game**, not the original joint policy or the preflop-only projected score.
5. Persist the candidate only when all gates pass. Otherwise stop and retain the previous policy.

The independent gates are:

| Check | Rule |
| --- | --- |
| Selected branch coverage | Every declared history/flop is reached and refined |
| Conditional quality | Maximum conditional gap is at most the requested target |
| Parent quality | Full six-seat NashConv does not increase beyond 1e-9bb numerical tolerance |
| Material progress | Full six-seat NashConv improves by at least the requested minimum, which must be at least 1e-9bb |

The material-progress gate is stricter than mere non-increase and prevents repeated work on numerical plateaus. A report preserves all gate booleans as well as a primary stop reason: `SELECTED_BRANCH_UNREACHED`, `CONDITIONAL_TARGET_FAILED`, `PARENT_QUALITY_REGRESSION` or `NO_MATERIAL_IMPROVEMENT`. If every requested round passes, the run stops with `ROUND_LIMIT_REACHED`. A stopped run can be successful execution with a rejected numerical candidate; rejection is an intended outcome.

There are at most three attempted rounds per invocation, 1–3000 preflop iterations and 1–500 postflop iterations per round. The existing cost preflight caps the declared tree at 16 compatible private-deal/flop pairs and 2,000,000 complete-tree states. Requests fail instead of trimming chance support. These limits bound model size and iteration counts, not wall time or peak memory.

## Checkpoint and resume contract

`SixMaxConnectedPolicyCheckpoint` stores the complete average strategy, canonical solution hash, source pack/spot hashes, selected public histories, physical flops, street bet sizes and study budget. Its schema is `six-max-connected-policy-checkpoint/v1` and its status is always `VALIDATION_ONLY`. Large checkpoint files belong in ignored `.local/`; compact audit reports belong in `docs/data/`.

Loading uses strict JSON: no unknown properties, duplicate keys, trailing values, scalar coercion, fractional integer budgets or missing/null primitive fields. A checkpoint must be at most 128 MiB. The source pack must match both canonical hashes. Loading reconstructs the game, rechecks the cost limit, validates all legal policy rows and rejects missing or foreign information sets; it never fills missing rows with an implicit uniform policy. It also verifies the canonical policy hash. Hashes bind content and identify the model; they are not signatures or an equilibrium certificate.

The CLI additionally requires the checkpoint's menu and budget to match the menu declared by its history count, flop seed and width. It reuses the reconstructed, validated checkpoint game and independently recomputes parent and conditional quality before starting a resumed round. It does not trust a saved quality score. Resume restores an average policy as the next round's input; each CFR stage starts fresh regret tables. It is **not** a continuation of an interrupted CFR iteration schedule. Fresh-training seed and budgets remain required syntactic CLI arguments but are unused on resume; the resumed report has no executed initial-training record or fresh-training settings.

A fresh run writes its audited initial policy before attempting a round, then writes only accepted policies. Saving validates before mutation, writes a temporary file in the destination directory and requires atomic replacement. If that filesystem cannot provide atomic replacement, saving fails and preserves the previous checkpoint. Temporary files are cleaned up on normal completion or handled failure; a terminated process can leave an unreferenced temporary file. A rejected candidate never replaces the checkpoint. Resuming in place also leaves the checkpoint's bytes unchanged if the next round is rejected.

The saved policy combines average strategies from separate stages. Its `iterations` marker remains the original joint-training marker; it is not a count of all subsequent preflop and postflop work. Reports carry each executed stage's own budget and hash chain. Quality is measured by fresh evaluation of the assembled policy, not inferred from that marker.

Source, report and checkpoint output paths must be distinct, including existing hard-link/symlink aliases. The resume input may equal the checkpoint output for intentional in-place advancement, but cannot alias the source or report. Plan-only mode writes a cost/settings report and does not train or touch the checkpoint. Final reports are written when an invocation completes; after interruption, an already accepted checkpoint may be ahead of the last completed report. Resume freshly audits that checkpoint rather than inferring its state from an old report.

## Running the study

After building the solver, run from the repository root:

```powershell
mvn -q -pl solver -am verify
$env:MAVEN_OPTS = '-Xmx5g'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxAlternatingContinuationStudyMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json docs/data/sixmax-alternating-seed-711.json .local/sixmax-alternating-seed-711-policy.json 711 500 300 500 300 2 2 711 2 0.05 0.000001'
```

The positional arguments are source pack, report, checkpoint output, fresh training seed, joint iterations, initial postflop iterations, per-round preflop iterations, per-round postflop iterations, maximum rounds, maximum selected histories, flop seed, flops per history, conditional target in bb and minimum parent improvement in bb.

Repeating a round uses fresh regret tables with the same requested budgets. When ranges have nearly stabilized, extra rounds can retain the finite-iteration error of those stages. Resume permits a larger per-round budget while preserving the same source and continuation menu; its fresh audits determine whether that extra work actually improves the retained policy.

Append `--plan-only` to inspect the declared tree before running. Append `--resume .local/sixmax-alternating-seed-711-policy.json` to start from that checkpoint and attempt up to the requested number of **additional** rounds. The local checkpoint is required; the compact committed report cannot reconstruct its strategy rows. Do not rebuild classes while a benchmark JVM is using them.

## Measured paired rounds

The [seed 711 report](data/sixmax-alternating-seed-711.json) and [seed 712 report](data/sixmax-alternating-seed-712.json) use the preceding study's identical four-deal, two-history, two-flop tree: 12 compatible pairs, 1,358,137 states and 372,785 explicit policy rows. Budgets are 500 joint iterations, 300 initial postflop iterations, then two rounds of 500 preflop / 300 postflop iterations. The conditional target is 0.05bb and minimum full-parent improvement is 0.000001bb.

Both initial and first-round policy hashes reproduce the preceding feedback study exactly. Both second rounds also pass all gates and stop at `ROUND_LIMIT_REACHED` with two accepted rounds:

| Retained stage | Seed 711 parent NashConv (bb) | Seed 711 maximum conditional gap (bb) | Seed 712 parent NashConv (bb) | Seed 712 maximum conditional gap (bb) |
| --- | ---: | ---: | ---: | ---: |
| Audited initial postflop refinement | 0.028863301 | 0.013494021 | 0.034310818 | 0.013270315 |
| Accepted round 1 | 0.019167167 | 0.011197594 | 0.019237835 | 0.011209824 |
| Accepted round 2 | 0.019084882 | 0.011376811 | 0.019062648 | 0.011452539 |

Round two improves full-parent NashConv by 0.000082285bb and 0.000175187bb against the last retained policy. Its intermediate, changed-range conditional gaps are 0.036664806bb and 0.043371525bb, compared with 0.54–0.57bb after the first preflop update. The final conditional gaps increase slightly relative to round one while remaining well inside the declared target. This is intentional: accepted parent quality must improve materially, while conditional quality must satisfy a threshold; the gate does not require both scores to decrease every round.

These measured rounds passed. Separate real-game tests exercise rejection and checkpoint preservation, including a resumed candidate whose parent score regresses. No monotonic-convergence guarantee follows from two accepted rounds. The complete local checkpoints are approximately 98 MiB each; the committed reports contain audit evidence and hashes, not those strategy rows.

## Higher-budget resume

The [resumed seed 711 report](data/sixmax-alternating-resumed-seed-711.json) loads the accepted round-two checkpoint and attempts one additional round with **1,000 preflop / 300 postflop iterations**. It repeats no initial joint or postflop training. Fresh audits reproduce the previous retained scores, source identity and continuation menu.

| Seed 711 stage | Full-parent NashConv (bb) | Maximum conditional gap (bb) |
| --- | ---: | ---: |
| Retained round-two input | 0.019084882 | 0.011376811 |
| Accepted higher-budget round | 0.004971259 | 0.011417169 |

The candidate improves parent NashConv by 0.014113623bb, **73.95% relative to this same input policy in this same game**, and meets the conditional target on all four branches. The intermediate conditional gap after preflop is 0.013021280bb. Its exhaustive preflop traversal visits 307,974,000 states and 162,336,000 terminals, exactly twice the preceding 500-iteration traversal. The checkpoint is replaced with the accepted policy hash `581ecb4fb3facb981b18d38093f0d870b45656c58a170bc48bc7b11df017c1e0`.

This is one higher-budget case, not a paired-seed result or evidence that feedback beats a matched 1,000-iteration unrefined control. It shows that the large checkpoint can be loaded, freshly audited and advanced successfully, and that increasing preflop work can substantially improve the finite-game score. The remaining private/board and betting limits are unchanged.

To reproduce it after the fresh seed 711 run:

```powershell
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxAlternatingContinuationStudyMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json docs/data/sixmax-alternating-resumed-seed-711.json .local/sixmax-alternating-seed-711-policy.json 711 500 300 1000 300 1 2 711 2 0.05 0.000001 --resume .local/sixmax-alternating-seed-711-policy.json'
```

This command requires the **round-two** checkpoint. The in-place resume advances it; repeating against the already advanced checkpoint starts from a different policy and is a different experiment. Re-run the fresh study to reconstruct the original input.

## Verification and limits

Tests independently re-evaluate retained policies, verify accepted-round hash chains and persistence callbacks, and check that conditional failures and plateaus stop after one rejected attempt. Checkpoint tests cover deterministic round trips, strict parsing, provenance/hash failures, missing policy rows, oversized input and preservation on failed writes. CLI tests cover planning, source/output alias protection, baseline failure before mutation, resume without initial training and identical resumed decisions.

The solver still uses four physical private deals in the wider fixture, two selected heads-up histories, sparse physical betting flops, one requested bet size per street, no postflop raises, no multiway postflop betting and no rake. Most boards retain mandatory checkdown. A passing round is a measured improvement in that finite game. The gates do not establish a safe subgame-solving theorem or multiplayer convergence, and do not admit a policy to the trainer. Broader private and board coverage, richer betting and realistic cash assumptions remain the next model gates.
