# Fixed-utility pruning and exact named-board diagnostics

This follow-up adds capacity to the six-seat solver and measures a concrete limitation of its coarse public-board model. Both paths are offline. Saved studies remain `VALIDATION_ONLY`; the exact-board content gate, trainer admission and paused AWS deployment are unchanged.

## Exact inactive-player pruning

`MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY` opts into the game's existing `inactivePlayerUtility` guarantee. A game may return a value only when the target never acts again and every descendant terminal has that same literal payoff under **every action and chance outcome**. For a folded seat in the texture game this is its negative committed preflop amount. A hand-strength estimate, a checkdown equity, a profile value or a chance baseline cannot supply this guarantee.

The alternating solver visits a suffix separately for each target. Once that target has folded, traversing the remaining decisions updates neither its regrets nor its average strategy: those updates occur only at the target's own decisions. Returning the fixed payoff therefore preserves its ancestor action values. Each other player's own pass still traverses its complete legal decision support. The hook is consulted after terminal validation; ordinary terminal settlement keeps its existing checks. Non-finite guarantees fail immediately.

Every existing constructor defaults to `NONE` and never consults the hook. The four-field `Statistics` record is unchanged. A separate `inactiveUtilityPrunedNodes()` counter counts skipped **suffix roots**, rather than omitted descendants, and resets at each solve. Differences in node visitation measure work saved; they are not a new game-quality score.

In exhaustive traversal, algebraically equal suffix values can differ by floating-point summation order. Policies can therefore have different canonical hashes. In sampled traversal, pruning can also change which random draws are consumed. The literal payoff still receives its sampled ancestors' proposal/baseline corrections; no suffix importance multiplier is invented after the suffix has been integrated exactly. Same-seed policy equality is not a sampling correctness condition.

The texture CLI accepts an optional final `NONE` or `FIXED_UTILITY`. Existing commands and saved policies retain `CFR_PLUS`; opt-in studies record `CFR_PLUS_FIXED_UTILITY_PRUNING`. Both algorithms share the same game hash when source, payoff table, menu and sizing match. The new algorithm has a distinct policy hash and is not silently relabelled as the original solve. Both load paths require complete policy support and strict identities.

`SixMaxTexturePruningAuditMain` independently reloads an original/pruned checkpoint pair, requires the same game and iteration budget, and computes maximum action-frequency difference, uniform information-set mean total variation, profile utilities and each player's unilateral deviation gains. Frequency agreement within `1e-10` is reported as a diagnostic, not assumed. The mean gives every information set equal weight; it is not reach-weighted strategic loss. The exact best-response audit measures the declared texture game only.

## Actual-board witnesses

`SixMaxTextureBoardAuditMain` accepts one to twenty-four explicit, distinct flops. These are named witnesses, **not a random sample or an exhaustive scan of all boards**. Reordered duplicate cards/boards, incomplete policies, changed identities, malformed notation and input/output aliases fail before writing. Exports are atomic. The report binds the original source, exact texture table, complete checkpoint policy and game, including its solver algorithm.

For each reached selected public history, the tool retains the joint six-hand action-conditioned distribution. Conditioning on a named board removes any world blocked by a card in any seat's hand, including folded seats, then renormalizes the remaining weights. Each compatible world enumerates exactly `37 choose 2 = 666` turn/river pairs. The report includes hidden private-world rows only as offline diagnostic evidence; these rows must not become trainer observations.

Three first-to-act checkdown shares distinguish the information losses:

| Quantity | Private-world posterior | Conditional showdown equity |
| --- | --- | --- |
| `exactFirstShareGivenBoard` | Actual named board | Actual board, all 666 runouts |
| `textureFirstShareUsingBoardPosterior` | Actual named board | Existing coarse texture table |
| `textureFirstShareGivenTexture` | Only the texture signal | Existing coarse texture table |

Let these quantities be `E`, `B` and `T`. The report records signed differences `E-B` (runout/payoff abstraction), `B-T` (posterior information hidden by the texture) and `E-T` (their total). Within a private world, ranks and suit identity can change the conditional equity even when texture is the same. Across worlds, a visible card also changes which hands remain possible; a coarse texture only updates each world's weight by its number of matching physical flops.

For pot `P` and the declared stack-capped bet `b`, differences in first-player settlement are `P*(E-T)` after check/check and `(P+2b)*(E-T)` after a call. The committed amount and called bet cancel in the paired difference. These comparisons occur **before postflop action likelihoods**: they are not the EV of the learned check/bet/fold/call buttons. They are neither full-poker exploitability nor a bound on abstraction error over every flop. A large witness is useful evidence of a missing signal; a small witness cannot certify the model.

Boards blocked by every reached world are listed separately. A history with exactly zero policy reach has status `ZERO_POLICY_REACH` and no fabricated conditional measurement. Positive subnormal board/texture masses are rejected rather than silently reported as a confident zero. The parent game and checkpoint still preserve off-path information sets for counterfactual deviations.

## Saved measurements

The fresh [1,000-iteration pruned study](data/sixmax-staged-texture-pruned-study-1000.json) uses the same source, exact table, six histories and half-pot bets as the original broad study. Its [complete checkpoint](data/sixmax-staged-texture-pruned-policy-1000.json.gz) retains all **9,449** information sets across **142,681** complete states. The [independent comparison](data/sixmax-staged-texture-pruning-comparison-1000.json) measures maximum frequency difference **6.92779e-14**, uniform information-set mean TV **1.15372e-17**, and maximum profile/deviation-gain differences **1.11022e-16bb**. Original/pruned own-game NashConv is respectively `0.0002897451039000082` / `0.0002897451038998972`bb. These policies agree within rounding but have different hashes.

The [recorded traversal observation](data/sixmax-staged-texture-pruned-traversal-1000.json) has **516,810,000** visited nodes versus **856,086,000** for the unpruned exhaustive traversal, a **39.63%** reduction. Its counter records **28,800,000** skipped suffix roots. The original work count is independently recoverable as `6 * 1000 * 142681`, and matches the earlier run. End-to-end time was **248.07 seconds**, including menu selection, solving, quality/content audits and export while local Maven tests ran concurrently. This single observation does not establish a portable wall-time speedup or a paired timing comparison.

The pruned policy hash is `4bf62fb01ff00fda24bfdbefec33e14d6d63e6dbc8acaedef131b18209e4ae42`. Its game hash is the original six-history `5c2e67e05ad9c33eebb7a1f4c8839747937ab6dfbd3feffa0f245696c4f81048`. The physical-flop material bound remains **3.60609e-6**, far below `0.25`; the optimization has not created publishable trainer content.

The [saved board-witness report](data/sixmax-staged-texture-board-witnesses-1000.json) compares twelve declared flops on all six selected histories and enumerates **439,560** physical turn/river pairs across the compatible worlds. Its largest total share difference is **0.48954** on `5d 6d 7c` at a BB/CO history with tiny reach. On a materially reached CO/BTN history with a 19.5bb pot, `8c 8d 2h` changes the first actor's share from approximately **0.48365** in the coarse class to **0.00000131** on the actual board: a **−9.43bb conditional checkdown settlement difference**. This is one board's conditional difference, not a full-game loss. The related CO/BTN 7.5bb history on `Ac Kd Qs` also has a visible blocker/posterior component of about **0.08** share, rather than all of the discrepancy coming from runout equity.

Every saved checkpoint/report is replay-tested without retraining or regenerating the texture table. Additional tests independently enumerate a named board with the object-level hand evaluator, verify 37 remaining cards and 666 runouts, remove a world using a folded UTG blocker, exercise nonzero posterior-information differences with an analytically controlled table, and preserve zero-reach and off-path distinctions.

## Reproduction

Run from the repository root. Long generation is separate from read-only replay. The commands write ignored local outputs and reuse the committed exact payoff table.

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTextureStudyMain' '-Dexec.args=solve docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-texture-payoffs.json .local/texture-pruned-policy.json.gz .local/texture-pruned-study.json 1000 1,2,3,4,5,6 0.5 FIXED_UTILITY'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTexturePruningAuditMain' '-Dexec.args=docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-texture-payoffs.json docs/data/sixmax-staged-texture-broad-policy-1000.json.gz .local/texture-pruned-policy.json.gz .local/texture-pruning-comparison.json'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxTextureBoardAuditMain' '-Dexec.args=docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-texture-payoffs.json .local/texture-pruned-policy.json.gz .local/texture-board-witnesses.json 2c3d4h;QcTd2h;8c8d2h;AsKsQs;2s3s4s;2d3d4d;2c2d3h;9c9h9s;AcKdQs;5d6d7c;QdJdTd;AhKhQh'
```

Freeze compiled solver/engine classes before launching long local studies if development or Maven tests will continue concurrently. A live study must never mix old and rebuilt class files.

## Next model gate

Use these explicit witnesses to design a separately versioned public signal that retains strategically relevant ranks and suit information. Declare its chance weights, blockers, information sets and state budget before retraining. Compare independent quality measurements and retained material hand diversity; do not merely split texture names or promote a finite-budget score. Broader credible ranges, multiway continuations, rake and later betting still require their own evidence.

For an interview: the concrete contribution is an exact optional traversal optimization with backwards-compatible provenance, plus a reproducible diagnostic that separates conditional equity error from information lost through private-hand posteriors. This is PokerLab code, not an upstream library bug fix.
