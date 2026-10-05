# Feeding postflop values back into six-seat preflop

The [physical-flop study](sixmax-flop-width-study.md) improves selected postflop policies while leaving preflop decisions unchanged. `SixMaxPreflopContinuationFeedback` closes that experimental loop: freeze the completed postflop policy, integrate its exact continuation values, re-solve every player's preflop decisions, and audit the resulting full game and newly reached postflop ranges.

## Exact frozen continuation values

`SixMaxFrozenContinuationPreflopGame` retains the source game's original private chance distribution, legal preflop actions and information sets. At each selected public preflop terminal, it integrates the fixed postflop policy separately for **every original private deal**, including deals with zero current action reach. No posterior distribution replaces root chance. Public-history identity remains part of the payoff key: identical surviving players can reach different pots, commitments and future bet sizes.

The integration includes the actual physical probability of each compatible selected flop, every legal turn/river card, and the residual mandatory-checkdown branch. Blocked flops contribute zero; selected flops are not renormalized into an artificial restricted deck. Unselected histories keep their original terminal payoffs. The resulting six-seat utility vectors are finite and zero-sum. They are immutable inside the projection, and callers receive defensive arrays. The report includes each frozen vector, its original checkdown vector and the difference in bb.

The input policy must already be explicitly complete across the whole connected game. Missing, malformed or foreign rows are rejected; feedback never silently supplies new postflop actions. Pair/state preflight uses the same [offline study budget](sixmax-flop-width-study.md#cost-preflight-and-explicit-limits), before payoff integration or training.

## Preflop re-solving and separate quality checks

The projected game is solved from scratch with exhaustive six-player CFR+. All six seats can act; postflop actions are fixed only during this stage. The new preflop rows must have exactly the original preflop support. They are lifted into the complete connected policy while preserving **every postflop row**, including counterfactual rows outside the reached posterior. The original joint iteration marker is preserved; the independent preflop budget is recorded separately.

The report measures:

- Exact preflop-only best responses before and after the update, against the frozen continuation values.
- Exact six-player parent best responses, which can also deviate in postflop play.
- Exhaustive preflop traversal work, maximum action-frequency change, replaced/preserved row counts and input/candidate hashes.
- Terminal, selected-history and physical-flop reach under both preflop policies.
- Conditional postflop best responses recomputed from the changed preflop posterior.

Projected and parent profile utilities must agree within 1e-9bb, and a projected best response cannot exceed its full-game counterpart beyond that tolerance. These are checked at runtime, not inferred from a small projected NashConv. Conditional gaps can change even though no postflop row changes: reaching a branch with different private-hand probabilities changes the game being evaluated. A low preflop-only gap therefore cannot substitute for the full parent or conditional audits.

## Alternating study and matched control

`SixMaxContinuationFeedbackStudyMain` runs these stages on fixed source-ranked histories and physical boards:

1. Train joint linear CFR with sampled runouts; explicitly complete missing information sets.
2. Run a matched preflop CFR+ control against the **unrefined** joint postflop policy.
3. Refine the joint policy's reached postflop branches with exact conditional CFR+.
4. Re-solve preflop against those **refined** continuation values, at the same preflop budget as the control.
5. Optionally refine postflop again under the candidate's changed ranges.

The control and candidate both start from the same completed joint policy; the control does not become an input to the candidate. Their preflop frequency difference measures the additional effect of the initial postflop refinement, rather than attributing every change to it. Final quality flags separately report the conditional target and full-parent non-increase relative to the initial joint policy. Failures remain in the artifact. A zero final-postflop budget omits stage five and reports conditional quality immediately after preflop feedback.

This is an alternating research experiment. It implements neither a safe-subgame gadget nor a convergence guarantee for six-player poker. Its output is `VALIDATION_ONLY`; it does not change the playable trainer pack or deploy infrastructure. The declared ranges, action menu, sparse physical-flop betting and multiway checkdowns remain the model's limits.

## Reproduction

From the repository root in PowerShell:

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxContinuationFeedbackStudyMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json .local/sixmax-feedback-plan.json 711,712 500 300 500 300 2 711 2 0.05 --plan-only'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxContinuationFeedbackStudyMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json docs/data/sixmax-feedback-seed-711.json 711 500 300 500 300 2 711 2 0.05'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxContinuationFeedbackStudyMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json docs/data/sixmax-feedback-seed-712.json 712 500 300 500 300 2 711 2 0.05'
```

Arguments are source, output, 1–3 distinct training seeds, joint iterations [1, 3000], initial postflop iterations [1, 500], preflop iterations [1, 3000], final postflop iterations [0, 500], histories [1, 4], flop-selection seed, flops per history [1, 4], and a positive finite conditional-gap target. The optional final flag is `--plan-only`. Planning exports metadata and exact cost with empty runs. Source input is limited to 16 MiB; source aliases and over-budget requests are rejected before any output mutation. Exports omit wall time for deterministic reproduction and identify source/spot hashes, coverage and all independent budgets.

Builds and solver runs must not write to the same compiled class directory simultaneously. Independent seed runs may use separate processes and output paths after compilation finishes; the game and mutable traversal caches are not shared across processes.

## Measured paired results

The [seed 711 artifact](data/sixmax-feedback-seed-711.json) and [seed 712 artifact](data/sixmax-feedback-seed-712.json) use the same four-deal, two-history, two-flop model as the [preceding width study](sixmax-flop-width-study.md). Each has 12 compatible deal/flop pairs, 1,358,137 complete-tree states and 372,785 information sets. Both reproduce the preceding study's completed joint and first postflop-refinement policy hashes exactly. Budgets are 500 joint, 300 initial postflop, 500 preflop and 300 final postflop iterations.

| Stage | Seed 711 parent NashConv (bb) | Seed 711 maximum conditional gap (bb) | Seed 712 parent NashConv (bb) | Seed 712 maximum conditional gap (bb) |
| --- | ---: | ---: | ---: | ---: |
| Completed joint policy | 0.028866909 | 17.131929837 | 0.034315505 | 14.897512586 |
| Matched preflop control, unrefined postflop | 0.019082854 | 17.140908081 | 0.018977187 | 14.898909857 |
| Initial postflop refinement, preflop unchanged | 0.028863301 | 0.013494021 | 0.034310818 | 0.013270315 |
| Preflop feedback, refined postflop rows fixed | 0.019167324 | 0.573331984 | 0.019238030 | 0.537943545 |
| Final postflop refinement at changed ranges | 0.019167167 | 0.011197594 | 0.019237835 | 0.011209824 |

Preflop feedback invalidates the earlier conditional target on both seeds: the raised-pot `5d 9s Qc` branch grows from about 0.013bb to 0.54–0.57bb. No postflop row changed in that stage; the reached private distribution did. The final postflop pass is therefore necessary to meet this study's 0.05bb conditional target. Both final candidates also pass parent non-increase relative to the original joint policy, with about 33.60% and 43.94% lower NashConv **in this same declared finite game**.

The control is crucial to interpretation. It has a slightly **lower** full-parent NashConv than even the final refined candidate, by 0.000084312bb and 0.000260648bb, while failing the conditional target by a large margin. Most of the parent improvement over joint training comes from fresh exhaustive preflop solving. The extra refinement improves conditional play but does not outperform the control on every metric; this is evidence against assuming monotonic alternating improvement or presenting one score as a general GTO certificate.

Each feedback stage replaces 8,305 preflop rows and preserves all 364,480 postflop rows. The maximum preflop frequency difference from the matched control is 0.021793 and 0.052293 (2.18 and 5.23 percentage points). These are **unweighted maxima across all preflop information sets**, including rare and off-policy rows, not an average or root-opening-frequency change. Each 500-iteration exhaustive preflop solve visits 153,987,000 states and 81,168,000 terminals without sampling public chance. The expensive postflop expectation is integrated once before those repeated preflop traversals.

| Learned reach | Seed 711 before feedback | Seed 711 after feedback | Seed 712 before feedback | Seed 712 after feedback |
| --- | ---: | ---: | ---: | ---: |
| Selected-history probability | 0.007269004 | 0.007777338 | 0.012470262 | 0.007932961 |
| Selected physical-flop probability | 0.000001218150 | 0.000001251434 | 0.000002175094 | 0.000001286606 |

Final postflop refinement preserves these new preflop rows and hence this reach. Sparse physical betting probability still limits what the full-parent score can say about local postflop accuracy. Published-artifact tests verify source and preceding-policy provenance, vector conservation, row accounting, projected/full EV agreement, score arithmetic and flags without repeating the long training runs in CI.

## Verification and next gate

Tests compare projected profile values with full connected evaluation under different preflop policies, retain weighted off-policy private support and blocked boards, distinguish different public histories with the same active players, and verify immutable postflop rows after feedback. Posterior-changing and revived-history tests independently re-evaluate candidate conditional quality. CLI tests cover byte-identical reproduction, optional final refinement, matched control identity, planning without training, invalid budgets and source preservation.

The next gate is repeated whole alternating rounds with explicit quality stop/rejection rules on the same declared game: a preflop update must not inherit the old posterior's conditional certificate, and a final candidate must pass both independent checks. Broader private-card and physical-board coverage still follows. This stage alone does not justify publishing general GTO charts.
