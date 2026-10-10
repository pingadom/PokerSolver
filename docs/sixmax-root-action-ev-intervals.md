# Root-action EV ambiguity and robust decision diagnostics

PokerLab can now bound a root move's EV across globally near-optimal opponent strategies, using its own bounded LP solver. A tiny whole-game error does not imply a unique EV for an unplayed alternative. Opponent continuations in branches that the equilibrium avoids can change that alternative's value without materially changing the equilibrium value.

This is `APPROXIMATE_SECURITY_FACE_DIAGNOSTIC_ONLY`, with `trainerAdmission=false`. It does not replace the existing two-reference feedback screen, export a trainer policy, or qualify the richer scenario for practice. The earlier accepted and rejected studies retain their identities and replay unchanged. No upstream library bug was identified: this is an equilibrium-selection issue in the declared game.

## Saved physical example

The [decision request](data/supplied-root-five-target-aj-decision-request.json) and [complete report](data/supplied-root-five-target-aj-decision-report.json) extend the existing 27-world BTN/BB supplied-prior fixture to the staged targets `[3,9,22,50,100]` bb. UTG/HJ/CO fold, BTN opens to 3bb, SB folds and BB holds A♣J♣. There are three hands for each active player and three possible folded HJ hands. Every world's twelve dealt cards remain excluded from the 658,008 legal boards. The exact payoff work remains 17,766,216 boards / 35,532,432 hand evaluations, enumerated once per complete decision report.

The opponent security slack is explicitly **0.00000001bb** above BB's owned whole-game upper value. All reported bounds are numerical certificates with the unchanged owned LP tolerance of 1e-8. They are not symbolic bounds over the exact Nash set.

| BB move | Incremental EV interval (bb) | Conservative decision-loss interval (bb) |
| --- | ---: | ---: |
| Fold | approximately 0 | 1.273941–1.273941 |
| Call | 1.273941–1.273941 | 0–0 |
| Raise to 9bb | 0.195534–0.728954 | 0.544987–1.078407 |

Calling is best throughout the declared numerical opponent face, although raising has no useful single EV. The conservative loss bounds combine individual action intervals; they are **not** claimed to be tight jointly optimized loss extrema. For chosen move `s`, the lower bound is `max(0, max(other lower EV) - upper EV(s))`, and the upper bound is `max(0, max(other upper EV) - lower EV(s))`. The selected move is excluded from the comparison because its loss relative to itself is exactly zero.

A separate [single-action request](data/supplied-root-five-target-aj-raise-request.json) and [report](data/supplied-root-five-target-aj-raise-report.json) retain all seven LP controls for the raise. Its upper LPs have 21 variables and 22 constraints; the lower epigraph has 22 variables and 28 constraints. The batch uses 160 pivots and about 3.62 million charged LP arithmetic units, within the existing limits. No shared cap or numerical tolerance was increased.

## Owned algorithm

`FiniteTwoPlayerRootActionIntervals` copies and validates the complete game once. All later solves and independent best responses use that immutable snapshot. It requires exactly two active constant-sum actors, preserves fixed inactive-seat utilities, and supports either actor as hero. It recompiles the oriented terminal payoff matrix when hero is the original second actor; subtracting matrix entries from the constant sum would incorrectly assign payoff to unsupported sequence pairs.

Write the affine sequence-form utility as `c0 + a·z + b·w + zᵀDw`, with hero constraints `Pz ≤ p`, opponent constraints `Qw ≤ q` and nonnegative variables. The opponent security face includes nonnegative dual variables `μ` and constraints:

```text
Dw - Pᵀμ ≤ -a
Qw ≤ q
b·w + p·μ ≤ hero global upper value + explicit security slack - c0
```

Each feasible opponent realization therefore has a dual upper certificate on hero's global best-response value. The diagnostic conditions on the selected root information set's **chance-only** joint prior and forces the selected move. It enumerates at most 64 complete hero continuation plans, choosing one action per information set across all hidden worlds. Each plan is linear in the opponent realization. The upper interval endpoint maximizes every plan separately and takes the largest optimum. The lower endpoint minimizes the maximum of all plans using an epigraph; a derived utility shift handles losing continuations without introducing a free LP variable.

Every witness reconstructs the original opponent flow, converts it to behavior, independently checks whole-game security, and recomputes the conditional information-set best response. The latter must match the compiled complete-plan maximum within 1e-8. This catches invalid linear reductions and behavior changes caused by tiny parent realizations. Original LP primal/dual points, certificates, flow residuals, policy hashes, all continuation coefficients and charged work remain in the report.

The new compiler retains the existing 8-million-unit limit. All interval LPs share at most 10,000 pivots and four billion arithmetic units. The complete decision wrapper shares those interval budgets across every move and enumerates physical payoffs once. Baseline affine solves retain their separately declared original compiler and LP budgets. Existing tree, sequence, information-set, dimension and byte limits still apply. Work/numerical failures return no result handle.

**Only questions before every player action are supported.** Later posteriors depend on opponent realization, so this linear reduction cannot be reused for them. Missing, illegal, inactive, later and excessive-plan requests fail. Mandatory postflop checkdown, supplied conditional beliefs and unavailable source-history reach remain explicit. This adds no six-player Nash guarantee, realistic cash range provenance, rake or physical postflop coverage.

## Offline commands and independent verification

Build with Java 21/Maven, then use the solver/engine/Jackson classpath:

```text
com.pokerlab.solver.SixMaxSuppliedRootActionIntervalsMain
  solve|replay <request.json[.gz]> <report.json[.gz]>

com.pokerlab.solver.SixMaxSuppliedRootDecisionIntervalsMain
  solve|replay <request.json[.gz]> <report.json[.gz]>
```

Solve requires a new output path. Strict JSON/gzip replay requires a distinct, matching request, reenumerates every physical board and recomputes all owned LPs, original flows, security checks, conditional responses and summary bounds. Changed counts still fail after their payoff hash is recomputed; edited LP/EV numbers also fail.

The optional [independent oracle](../scripts/check-supplied-root-intervals.py) uses NumPy/SciPy to build a literal **729-by-5,832** complete-plan matrix and six forced-action continuation payoffs. These optional tools are not solver runtime or CI dependencies. The check reuses saved integer showdown counts but calls none of PokerLab's betting, LP, sequence-form or best-response implementations. It solves the same numerical security face and independently evaluates every owned witness against all 5,832 opposing pure plans. The [saved oracle summary](data/supplied-root-five-target-aj-independent-check.json) agrees with both endpoints within 2e-8; analytic JVM tests independently cover unique and nonunique opponents, hidden-world aggregation, either hero orientation, negative utilities, strict replay and bounded work.

```text
python scripts/check-supplied-root-intervals.py \
  docs/data/supplied-root-five-target-aj-decision-report.json \
  <new-oracle-summary.json>
```

Next design and validate an admission policy for ambiguity-aware feedback, with separate treatment of EV intervals and robust action quality. Later decisions need an explicit action-conditioned posterior design. Existing admission rules remain unchanged until that work is complete; broader useful scenarios and eventual full cash-poker scope remain on the roadmap. AWS deployment is still paused.
