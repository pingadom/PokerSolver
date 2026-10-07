# Reached rank/texture decisions and parent witnesses

The six-seat rank-aware solver now has a separately versioned, read-only audit for every selected history and supported public flop signal. It computes conditional best responses, retains folded-card blockers and independently checks that the local responses form legal unilateral deviations in the complete parent game. The saved 100- and 500-iteration policies are unchanged. Both remain `VALIDATION_ONLY`; this audit does not publish new trainer questions or certify real-world poker GTO.

## Why the parent score needs a second check

The [rank-aware study](sixmax-rank-texture-continuation.md) reports parent-game NashConv: the sum of six players' unilateral deviation gains from the start of the complete declared game. A rare history or rare public signal receives little weight in that score. Its local strategy can remain poor even when the aggregate score is small.

The new `SixMaxRankTextureConditionalAudit` measures the same model's reached local decisions. It neither adds actual suits to the observation nor changes the game or its source ranges. A large local gap is an optimization problem within the declared abstraction. The separate [named-board witnesses](sixmax-rank-texture-continuation.md#exact-board-witnesses-and-remaining-suit-error) measure information/payoff differences caused by that abstraction. These are distinct limitations and need different remedies.

## Posterior construction

First rebuild and validate the complete checkpoint against the exact source pack, payoff table, selected histories and sizing. Foreign, illegal or missing policy rows are rejected. For each selected history, extract the policy's original preflop rows and condition the original six-hand joint distribution on every observed action. Folded seats' physical cards remain part of each world.

For public signal `s`, world `d` has unnormalized mass:

```text
P(d | history) * physicalFlops(d, s) / 9,880
```

Summing those masses gives `P(s | history)`. Dividing by the sum gives the posterior root distribution of the conditional game. This uses full joint worlds, not a product of independently inferred ranges. Own-information-set keys, legal actions and terminal chip accounting delegate to the original rank-aware game. Each player still sees only its own hand, public rank/texture signal and action history.

An `AUDITED` history includes every palette signal. A signal with no physically compatible reached private world has `NO_REACHED_PRIVATE_SUPPORT`, zero probability and no quality estimate. A history excluded by the policy has `ZERO_POLICY_REACH` and no conditional rows. Neither case removes any off-path support from the parent policy. Positive probabilities that become subnormal during history, private-world, signal or joint reach calculations are rejected explicitly; they must not be labelled impossible or silently rounded to zero. A reached history's signal probabilities must partition chance within `1e-12`.

Offline rows include active-seat marginals and counts of hands with at least 5% posterior mass. These are descriptive diagnostics, not trainer payloads or a replacement for physical-board content screening. The 0.01bb gap threshold also counts findings only; it does not introduce a new admission rule.

## Independent parent embedding

For each conditional game, the information-set best-response algorithm selects a pure response separately for each seat. It aggregates hidden-world chance and opponent reach before choosing an action, so it cannot select a different action merely because an opponent's private cards differ.

The audit combines each player's conditional responses across all reached history/signal cases. Those cases have disjoint postflop information-set keys. It rejects any overlap or attempt to change preflop decisions. For each player separately, replace only that player's postflop rows in a copy of the complete learned parent policy. Keep all opponents' rows, every preflop row and unsupported/off-path decisions intact.

Directly evaluate that unilateral policy in the complete parent game. Its gain must agree with:

```text
sum over reached cases:
    P(history) * P(signal | history) * localDeviationGain(player)
```

The agreement tolerance is `1e-9`bb. Each embedded gain must also be bounded by that player's independently computed unrestricted parent best-response gain. This checks the chance conditioning, player-key prefixes and policy assembly through a different evaluation path. The six modified policies are individual witnesses; combining them into one jointly changed strategy would not preserve these unilateral guarantees. No witness is exported as a trained policy or assigned a new equilibrium label.

The report stores the six gain vectors, directly evaluated utilities, signed embedding errors, response information-set counts and SHA-256 hashes of the response-action maps. It binds the exact model, source pack, source spot, table, game, solution, algorithm, traversal mode, iteration budget and complete-state count.

## Saved measurements

Both reports use the original six histories, twelve private worlds, 1,182 signals and 888,205-state model. This is a read-only follow-up to the existing studies, with no new training.

| Measurement | 100 iterations | 500 iterations |
| --- | ---: | ---: |
| Audited reached history/signal cases | 7,092 | 7,092 |
| Largest conditional NashConv, bb | 2.623499900 | 2.621840609 |
| Largest gap with two 5%-mass hands per active seat, bb | 2.580454549 | 2.577989744 |
| Cases above the descriptive 0.01bb threshold | 2,998 | 2,677 |
| Cases with two 5%-mass hands per active seat | 4,305 | 4,340 |
| Sum of reach-weighted local deviation gains, bb | 0.005327399895 | 0.000227389794 |
| Unrestricted parent NashConv, bb | 0.029739390931 | 0.001372972994 |
| Maximum absolute parent embedding error, bb | below 1e-15 | below 1e-15 |

The compressed reports are saved for [100 iterations](data/sixmax-staged-rank-texture-conditional-100.json.gz) and [500 iterations](data/sixmax-staged-rank-texture-conditional-500.json.gz). Each report is approximately 0.86MB compressed and is fully recomputed in regression tests. No policy checkpoint, payoff table or earlier parent-only study report was relabelled.

At 500 iterations, the worst signal is ranks `(4,7,10)` with `DISTINCT_MONOTONE`, in the history UTG fold, HJ fold, CO raise to 3bb, BTN fold, SB fold, BB raise to 9bb, CO call. The history probability is `0.0001107719053`; the signal probability given that history is about `0.000007921216`. Its 2.62184bb conditional gap therefore contributes very little to the parent score. This signal has only one hand above 5% for each active seat. Other rare signals still have gaps above 2.57bb with two material hands per seat.

The materially reached CO-open/BTN-re-raise history has probability `0.3556059242`. Its maximum local gap is `0.0381429340`bb, with six signals above 0.01bb. The other common CO-open/BB-call history reaches `0.3099787593` and has three signals above 0.01bb. These observations guide future work; they are not a full-deck quality guarantee or an admission decision.

## Independent checks and failure handling

Dedicated tests force a known all-tie continuation in which both seats wrongly fold to a bet. The two conditional unilateral gains are analytically 3.25bb each, and the embedded parent gains agree. Separate tests change a folded player's action likelihood, verify physical signal probabilities, distinguish unsupported signals from zero policy reach and reject underflow, incomplete policies, foreign rows and changed report claims.

For each saved budget, tests enumerate every pure own-information-set plan for all six seats in the five largest-gap cases. They directly evaluate each plan rather than reuse the best-response selector. The checks include active seats with multiple private hands. This catches illegal hidden-world conditioning or ignored player-prefix overrides. Enumeration is bounded at 256 plans per seat per case.

Failure-path testing also found and fixed a Windows file-handle leak in the shared gzip artifact loader and the older six-texture checkpoint reader. `GZIPInputStream` can throw while constructing its header reader, before its own try-with-resources variable exists. Both loaders now own the raw input stream as a separate resource, so a malformed header releases the file immediately. Regression tests reject empty/truncated/invalid headers and atomically replace the file after each failure. This was our resource-management bug, not an upstream library defect.

## Reproduction

Run from the repository root after installing the solver's local Maven dependencies. Audit exports are individually atomic, deterministic gzip or plain JSON. Raw, compressed and expanded reports are capped at 32MiB. Inputs and outputs must be distinct normalized files, including hardlinks.

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxRankTextureConditionalAuditMain' '-Dexec.args=audit docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-rank-texture-policy-500.json.gz .local/rank-conditional-500.json.gz'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxRankTextureConditionalAuditMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-rank-texture-policy-500.json.gz docs/data/sixmax-staged-rank-texture-conditional-500.json.gz'
mvn -q -pl solver -am test '-Dtest=SixMaxRankTextureConditional*Test,SixMaxRankTextureArtifactIoTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

`replay` strictly deserializes the bounded saved report, rebuilds the bound complete policy and recomputes every case and parent witness. Any changed summary, posterior, quality value, response hash or metadata is rejected. It does not write a policy, resume training, launch a page-triggered solve or expose hidden hands through the trainer API.

For an interview: explain why a small aggregate best-response score can conceal bad rare decisions, derive the joint action/card posterior, and describe the independent pure-plan and full-game witnesses that validate the result. The remaining solver milestones are bounded actual-suit observation, better local quality across important decisions, physical-board content selection, then broader ranges, multiway betting, rake and later streets.
