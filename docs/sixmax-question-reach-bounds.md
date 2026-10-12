# Certified question reach bounds

The earlier [reach-rejection diagnostic](sixmax-reach-rejection-diagnostics.md) proves that an opponent can keep a question below its required reach. `FiniteTwoPlayerReachBounds` now also certifies the maximum reach on that same complete numerical opponent security face. This distinguishes a question that some opponents bypass from one that no opponent on the declared face reaches often enough.

The separate diagnostic neither adds a reach constraint nor computes conditional EVs. Hero's earlier actions are fixed and their common reach cancels; the reported quantity includes the chance of hero's shown hand and the opponent's public actions. It is not the frequency of a history under a six-player source policy.

## Owned calculation and classifications

One immutable game snapshot supplies the baseline affine solve, original flows, projections, payoff matrix and question denominator. Two owned LPs minimize and maximize that denominator on the original full security face. Both endpoints reconstruct the opponent's original realization flow and behavior, independently check full-game hero best responses, and compare every unnormalized hidden-world mass. Original primal/dual points, certificates and conservative dual bounds are retained. No posterior is divided by zero.

Both endpoints share the existing compiler, pivot and arithmetic limits. A failed second LP or failed reconstruction creates no result; an LP failure is never labelled a proof of infeasibility. The baseline retains its separate existing budgets. Reach inspection does not enumerate hero continuation plans or solve action EVs, so the action-interval continuation-plan cap is irrelevant to this diagnostic. The selected legal action identifies the request; reach is measured before that action.

| Status | Evidence |
| --- | --- |
| `CERTIFIED_ALL_BELOW_REQUIRED_FLOOR` | Padded dual upper bound is below the floor. No opponent on this face can satisfy the requested reach constraint. |
| `CERTIFIED_ALL_AT_OR_ABOVE_REQUIRED_FLOOR` | Padded dual lower bound meets the floor. This is reach evidence only, not question qualification. |
| `VERIFIED_WITNESSES_ON_BOTH_SIDES` | Checked witnesses lie on opposite sides of the floor by more than certificate tolerance. |
| `UNRESOLVED_AT_CERTIFICATE_TOLERANCE` | The conservative interval crosses the floor without decisive opposite-side witnesses, including an exact boundary affected by padding. |

The original fast minimum-only rejection and successful action-interval formats remain separate. Only an owned solve constructs a private result handle. Parsed audits are evidence to replay, not solver or trainer capabilities. Trainer admission is always false.

## Four-hand physical example

The [request](data/supplied-four-button-hand-reach-bounds-request.json) uses exactly the earlier four-BTN-hand, three-BB-hand, two-folded-HJ-variant prior. The new [report](data/supplied-four-button-hand-reach-bounds-report.json.gz) regenerates all 24 joint worlds: 15,792,192 physical boards and 31,584,384 hand evaluations. All six dealt hands remain blockers. The baseline and minimum witness match the previous rejection artifact.

For BTN AsQs facing BB's raise to 9bb, the declared security slack is 0.0001bb. Minimum reach is zero; the maximum witness is about `0.0000394280367682`. The padded certified upper bound is `0.0000394380367693`, or **0.0039438%**. This is below even the smallest supported floor of `0.0001` (**0.01%**) and well below the request's `0.01` (**1%**). Therefore adding any currently supported positive-reach constraint to this particular security face would leave an empty opponent set. It would not repair the question.

The independent [stdlib checker](../scripts/check-supplied-reach-bounds.py) reconstructs both original 26-by-23 LPs, checks every primal/dual inequality and objective, reconstructs both original flows and all selected masses, and verifies the conservative bounds. Separately it checks a literal exact-rational BTN strategy against all 18 complete BB plans for each of three BB hands. Actual pot, fold and checkdown settlement gives a guaranteed `315337/607392` bb. BB's call-all strategy gives the same value against every BTN strategy, providing the reverse bound. This proves the saved game's value with rational arithmetic and no additional optimization or LP solver.

The [receipt](data/supplied-four-button-hand-reach-bounds-independent-check.json) binds the checker, request and compressed/expanded report bytes. The Python check uses saved board counts and projections; Java replay regenerates both. Neither check establishes realistic cash ranges or an ordinary six-max equilibrium.

## Commands and verification

With Java 21 and the solver runtime classpath:

```text
com.pokerlab.solver.SixMaxSuppliedReachBoundsMain assess <request.json[.gz]> <new-report.json[.gz]>
com.pokerlab.solver.SixMaxSuppliedReachBoundsMain replay <request.json[.gz]> <report.json[.gz]>
```

Request/report identities are `pokerlab-supplied-reach-bounds-{request,report}/v1`. Strict JSON, stored/expanded 8MiB limits, distinct paths and exclusive output creation apply. Full replay rebuilds the physical game, baseline and both original LP endpoints and compares every field.

```sh
python scripts/check-supplied-reach-bounds.py docs/data/supplied-four-button-hand-reach-bounds-request.json docs/data/supplied-four-button-hand-reach-bounds-report.json.gz --output <new-receipt.json>
python -m unittest discover -s scripts -p 'test_check_supplied_reach_bounds.py'
```

Analytic tests compare literal signal plans in both hero orientations, constant reach, security-limited reach, tolerance boundaries and shared work exhaustion. Physical tests cover both endpoints, positive-question diagnostics, legacy separation, exclusive plain/gzip replay, source-byte binding and rehashed-count tampering. Eleven independent-checker tests cover both points, dual values, behavioral masses, classification, admission, alternate pot rules, changed counts, malformed numbers/keys and byte caps. Failures create no receipt.

The next model gate is bounded strategic continuation for this supplied joint prior, drawing on the existing connected postflop studies while retaining counterfactual private support, public-history recall and measured chance coverage. Frozen continuation values alone do not certify the complete strategic game. Realistic ranges, rake and genuinely multiplayer strategy remain separate work; AWS deployment is paused.
