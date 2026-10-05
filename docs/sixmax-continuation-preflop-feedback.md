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

## Verification and next gate

Tests compare projected profile values with full connected evaluation under different preflop policies, retain weighted off-policy private support and blocked boards, distinguish different public histories with the same active players, and verify immutable postflop rows after feedback. A posterior-changing test independently re-evaluates the candidate's conditional quality. CLI tests cover byte-identical reproduction, optional final refinement, matched control identity, planning without training, invalid budgets and source preservation.

The next gate is repeated alternating rounds with explicit quality stop/rejection rules on the same declared game, followed by broader private-card and physical-board coverage. This stage alone does not justify publishing general GTO charts.
