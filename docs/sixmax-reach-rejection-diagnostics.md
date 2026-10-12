# Certified explanations for rejected questions

The solver now preserves evidence when a conditional question fails its full-security-face reach requirement. The existing rejection and trainer admission rules remain unchanged. This is an offline diagnostic, with a separate request/report format and physical replay; it does not publish another practice question.

## Why more private hands did not produce another question

The saved [request](data/supplied-four-button-hand-zero-reach-request.json) adds BTN AsQs to the earlier three-hand model. It explicitly declares four BTN hands, three BB hands and two folded HJ variants: 24 equally weighted joint worlds. All six dealt hands still remove their cards from every board. This is a different supplied prior, not an equivalent reduction of the previous 27-world model. The complete four-by-three-by-three alternative would enumerate 23,688,288 boards and fails the unchanged 20-million-board cap before enumeration.

The bounded model has 361 complete states, 25 sequences per actor, 15,792,192 exact boards and 31,584,384 hand evaluations. BTN AsQs faces BB's raise to 9bb, after opening to 3bb. Security slack is 0.0001bb and the minimum question reach is 0.01.

An opponent that calls every BB hand immediately has an independently calculated hero best response of **315337/607392 = 0.5191655471260734bb**, equal to the certified game value. It needs zero security slack. Every BTN strategy reaches the same mandatory checkdown against that opponent, and the later raise-to-9 question has exactly zero reach. Thus positive reach cannot hold over the entire allowed security face. A low whole-game gap alone cannot make this question valid.

## What the diagnostic contains

`FiniteTwoPlayerConditionalActionIntervals.Rejected.reachFailure()` returns optional evidence only after a successful owned minimum-reach LP and independently checked behavioral reconstruction. It includes the original primal point, dual vector, certificate, opponent realization flow, reconstructed policy hash, full-game hero best response, unnormalized hidden-world masses, projected denominator, conservative dual lower bound, baseline and work accounting. No posterior is divided by zero; no conditional EV is invented.

Two statuses distinguish the cause:

- `WITNESS_BELOW_REQUIRED_FLOOR`: the checked opponent has reach below the declared floor by more than certificate tolerance.
- `CONSERVATIVE_BOUND_BELOW_REQUIRED_FLOOR`: the padded lower bound cannot certify the floor, while the witness is not demonstrably below it at that tolerance. For example, exact reach 0.5 fails a floor of 0.5 when the certified lower bound is 0.5 minus 1e-8.

The [saved report](data/supplied-four-button-hand-zero-reach-report.json.gz) has the first status, a zero-reach witness and a conservative lower bound of -1e-8. That small negative bound is numerical padding, not a negative probability. The minimum-reach diagnostic consumes one LP within the original shared budgets; it does not retry with larger limits. Numerical, reconstruction and work failures never become reach certificates. Existing successful interval reports retain their formats and values.

`SixMaxSuppliedReachRejection` only creates a result after the owned reach rejection. A positive-reach solve, an unrelated error or an invalid target creates no saved failure. Parsed JSON is evidence to replay, not a solved capability. Trainer admission is always false, and supplied source-history reach remains unavailable.

## Commands and physical replay

With Java 21 and the solver/engine/Jackson runtime classpath, use:

```text
com.pokerlab.solver.SixMaxSuppliedReachRejectionMain diagnose
  docs/data/supplied-four-button-hand-zero-reach-request.json
  <new-report.json[.gz]>

com.pokerlab.solver.SixMaxSuppliedReachRejectionMain replay
  docs/data/supplied-four-button-hand-zero-reach-request.json
  docs/data/supplied-four-button-hand-zero-reach-report.json.gz
```

The request/report identities are `pokerlab-supplied-reach-rejection-{request,report}/v1`. Requests specify the literal information set and a legal action used by the failed conditional-interval attempt; reach is measured before that action. Strict JSON, expanded/stored 8MiB limits, distinct paths and exclusive output creation apply. Replay regenerates every physical board, the affine baseline and the minimum-reach LP, then compares every report field. Altered certificates and altered physical counts fail even after rehashing the counts.

## Independent checks

The [stdlib Python checker](../scripts/check-supplied-zero-reach.py) reconstructs the original 26-by-23 LP from the saved projections and recomputes every primal/dual inequality, objective, gap and residual. It separately checks realization conservation, all six selected masses and literal pot settlement over the 24 integer-count worlds. The BB-call-all witness proves that the nonnegative reach minimum is zero without another LP solve. Java replay independently regenerates counts and projections; the Python checker is scoped to this saved counterexample.

```sh
python scripts/check-supplied-zero-reach.py \
  docs/data/supplied-four-button-hand-zero-reach-request.json \
  docs/data/supplied-four-button-hand-zero-reach-report.json.gz \
  --output <new-check.json>
python -m unittest discover -s scripts -p 'test_check_supplied_zero_reach.py'
```

The [recorded check](data/supplied-four-button-hand-zero-reach-independent-check.json) binds the request, compressed/expanded report and checker source bytes. CI runs the positive control and corruption controls for the original point, dual value, behavioral mass, admission identity, duplicate keys and non-finite numbers. Failed controls write no receipt; existing receipts cannot be overwritten. Java tests cover both hero orientations, padding-only failure, budget exhaustion, physical replay, tampered rehashed counts and separation from trainer requests.

Broader work must explicitly address unreachable histories and strategic postflop continuation. Intersecting the security face with a positive-reach constraint would quantify over a different opponent set and needs a separate model and validation. Changing synthetic priors merely to produce more questions would not establish ordinary six-max cash coverage. AWS deployment remains paused.
