# Certified negative reach at the reconstruction boundary

The explicit v9 research mode converts four previously numerical after-check rejections into independently checked **insufficient-reach witnesses**. The complete security face contains a valid opponent policy that gives each selected question zero counterfactual reach. A conditional EV and posterior cannot be defined for that policy, so these questions remain unavailable for full-face interval training.

This improves the solver's diagnosis. It adds no trainer questions and makes no claim about a general six-active-player equilibrium. The existing eleven-question website and its source policies remain unchanged.

## Reproduced numerical failure

The frozen original problem in `docs/data/captured-near-zero-minimum-reach-lp.json` has 79 variables and 82 constraints. Its original binary64 coefficients, primal point and dual are retained byte for byte from the first diagnostic capture. The capture's later attempt to serialize an internal map failed; the saved LP was complete before that diagnostic error. It is not represented as a successful original end-to-end capture or an upstream library bug.

The original v8 conversion divides every child realization by its parent before normalization. At BB's 9d9h information set after the preflop raise chain 9/22/50/100 bb, the parent is `3.066424196519426e-10` and its fold child is `3.0664245615488384e-10`. Their absolute difference is only `3.650294126530209e-17`, but their ratio is `1.0000001190407424`, outside the unchanged `1 + 1e-8` probability gate.

Exact fractions of the original binary64 LP data give maximum primal violation `2.256439601538234e-16`, zero dual violation and zero duality gap. The LP certificate passes its existing absolute tolerance. The probability conversion is the failure boundary.

## Explicit reconstruction protocol

`PHYSICAL_FLOP_CERTIFIED_REACH_ALGORITHM` selects v9 before any solve. It retains the original v8 parent, LP engine, security face, reach objective, tolerances and positive-reach endpoint/posterior conversion. There is no retry or fallback from a rejected v8 solve.

Only the negative minimum-reach witness uses `NegativeReachBehavior`:

1. Validate every original coordinate for finiteness, original bounds and root mass. Only negative rounding within the existing absolute tolerance may be converted to zero.
2. Check every original signed conservation equation and its nonnegative conversion under the same `1e-8` tolerance.
3. Normalize each nonnegative child row by its own sum. A row with exactly zero child mass receives the deterministic uniform distribution; this does not make its original path reachable.
4. Independently multiply the complete perfect-recall history for every action, reconstructing every original realization coordinate. Reject if any accumulated discrepancy exceeds the same absolute tolerance. This prevents individually acceptable local residuals from accumulating into a materially different policy.
5. Run the existing full-parent hero best response against the emitted opponent policy, require original global security, compare every original and behavioral world mass, and compare total behavioral reach against the original affine objective and LP certificate.

Additional reconstruction work is charged to the unchanged compiler budget. Policy updates are committed only after reconstruction succeeds. The rejection audit is separately labelled `FULL_SECURITY_FACE_MINIMUM_REACH_CHILD_SUM_ORIGINAL_FLOW_SECURITY_CHECKS/v2`. It contains original LP point/dual, projections, full-parent evidence and unnormalized path masses. It contains no posterior, conditional action value or trainer admission.

## Complete eight-case result

The new coverage request uses schema v2, explicitly bound to endpoint v9; old coverage schema v1 remains bound to v8. Conditional successes have report schema v4. Old v8 requests, reports and numerical failures are preserved.

The physical model remains the separately supplied weak-range game: 24 joint six-hand worlds, two active players, board **8d Kc Kd**, BB call preflop, a 6.5 bb flop pot, one possible half-pot bet and exact checkdown. These supplied beliefs differ from the original ten-question collection.

| BTN holding | After BB checks | Facing BB bet |
|---|---|---|
| 2c 3c | Witnessed zero reach | Fold within 0.01 bb |
| Ah Kh | Witnessed zero reach | Call within 0.01 bb |
| As Qs | Witnessed zero reach | Call within 0.01 bb |
| Qh Th | Witnessed zero reach | Call within 0.01 bb |

Each after-check witness has reach `0`, conservative certified lower bound `-1e-8` and required floor `0.0001`. Its full-parent hero best response is `-0.041505228791422974` bb, within the unchanged security limit. Maximum original realization discrepancy is `3.650294126530209e-17`. The four facing-bet reports are exactly equal to the original v8 results after changing only their explicit algorithm/schema identities, including all endpoint arithmetic, policies, posteriors and work charges.

The report hash is `5e2963854792ab98037c2d9e6735ca76e38a48316ad9f7f2e282d6a83c77b6e0`. Generation reserved one physical game, eight case attempts, at most sixteen action computations and twenty-four parent solves, zero fresh CFR runs and zero retries. This bounds calls; it is not a complete measured inventory of failed internal work. Both report forms retain the existing 8 MiB cap.

## Verification and replay

The new checker rebuilds the physical parent, all 15,792,192 preflop boards, the literal 1,321-state tree and its parent certificates. It derives each rejected original security face and full-path denominator, checks four original certificates with exact binary64 fractions, rebuilds every opponent realization and full-parent best response, and checks the six unnormalized world masses for each question. It also verifies all 32 positive endpoint certificates through the existing independent checks. It performs zero LP or CFR solves.

Run from the repository root:

```text
python scripts/check-certified-reach-coverage.py --output <new-receipt.json>
python -m unittest discover -s scripts -p test_check_certified_reach_coverage.py
SixMaxRefinedFlopCoverageStudyMain replay docs/data/supplied-certified-reach-flop-coverage-request.json docs/data/supplied-certified-reach-flop-coverage-report.json.gz
```

The Java command needs compiled solver dependencies on its classpath. Replay rebuilds and compares all eight cases. The existing v8 replay still reproduces its four numerical rejections. Inspection alone remains an unverified stored snapshot.

Java regressions cover the actual captured ratio, tiny and zero mass, nonfinite/negative flows, root and conservation failures, accumulated recall drift, work exhaustion and version/provenance corruption. Python controls corrupt original certificates, realization/affine coordinates, reach/status, full-parent evidence, world masses, action values, ordering and admission. No numeric threshold or existing CI limit was raised.

## Next useful milestone

Fresh-reference qualification is still required for the three additional facing-bet candidates before publishing lessons. After-check variety needs a separately declared game whose complete security face supports those questions, or a separately defined training objective; discarding zero-reach opponent strategies would change the stated problem. Broader supplied ranges, boards, rake, later betting and six-active-player solving remain separate model work.
