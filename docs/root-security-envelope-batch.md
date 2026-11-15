# Shared parent root envelope batch

The new backend solves up to eight distinct, chance-only root questions using one
owned ascending-refined parent. Every question keeps the existing conservative
full-menu envelope, complete fixed hero policy, whole opponent security face,
original prior reach and independently checked behavioral witnesses. The batch
reuses the parent rather than solving it again for each question. It creates no
trainer capability and does not change any existing mathematical source or grade.

## Parent reuse and work limits

The caller's finite game is captured once into the checked immutable snapshot.
All questions pass the original root, separation and 0.0001 reach checks before
parent solving. One parent call performs its original two LPs. Each envelope then
accepts that private owned result only when its ascending-refined algorithm and
captured snapshot hash match. A deserialized audit or a parent from another game
cannot create or substitute the result handle.

Compiler units, endpoint pivots and endpoint arithmetic are cumulative across
preflight and all questions: 8,000,000 compiler units, 10,000 endpoint pivots and
4,000,000,000 endpoint arithmetic units. Each question receives only the remainder.
The parent's original compiler and LP work are recorded separately. A batch can
mix the two active actor labels, preserve nonzero constant sums and keep request
order. Every menu retains all original legal actions. At most eight distinct
questions are accepted. Caller lists and returned collections are copied or frozen.

The completed audit includes both original parent primal/dual witnesses, one
shared baseline, every envelope and cumulative counters. Any invalid question,
numerical failure or exhausted budget aborts before returning a result. Failed
kernels do not expose complete spent-work inventories, so no such count is claimed.
No fallback solver, larger cap, partial success or trainer admission is added.

The original standalone envelope entry points retain their algorithm, arithmetic,
policy selection, witnesses and work accounting. Their helper now also supports
the new owned-parent path. The nine existing envelope tests are unchanged. In the
standalone-versus-batch comparison only the declared remaining compiler limit is
allowed to differ; every other output field must match exactly.

## Analytic evidence

Three frozen controls cover both actor orientations, constant sums 0 and 7, and
four mixed roots at six seats. Eight complete menus use six shared-parent LPs
rather than sixteen standalone parent LPs: ten parent LP solves are avoided across
those controls. This is a solve-count comparison, not a runtime speedup or a claim
about physical poker startup. Endpoint work remains 32 LPs. The independent
checker reconstructs literal trees, original flow conservation and affine
projections, payoff matrices, security faces, fixed-policy forms and bounds.
It checks all six parent and 32 endpoint original LP certificates using rational
arithmetic on stored binary64 values, then groups hidden states for behavioral
best responses. Policy hashes, support hashes, original reach and cumulative
counter inventories are checked. It performs no LP optimisation or CFR training.

The first analytic capture is preserved with SHA-256
`1e5a3f11df5f765b4e12318e790f91cd99b356abe0da73add0a47c9cb7849c15`.
Java tests replay the complete captured audits rather than regenerating the file.
The first test run incorrectly compared the intentionally reduced work cap with
the standalone cap; the comparison was corrected to ignore only that declared
limit. All bounds, certificates, witnesses and actual charged work still compare
exactly. The independent checker separately handles Java's binary64 JSON notation
and the envelope's own flow normalization when checking the pinned policy hashes.
Neither correction changed a numerical solve or the first capture.

Local validation passed 16 new Java controls, all nine unchanged envelope tests
and 18 independent corruption tests. Added controls include the eight-question
maximum, one capture of a live caller, both actors, nonzero constant sums,
reordered questions, private provenance, insufficient reach, late questions and
compiler/pivot/arithmetic exhaustion between questions. Independent controls
reject modified physics, parent and endpoint certificates, incomplete menus,
policy/flow/hash forgery, optimistic bounds, reset counters and false admission.
Compilation and Spotless passed. Initial isolated reactor setup omitted the three
unused sibling POMs; supplying those unchanged POMs fixed setup before test solving.

## Reproduce

Use Java 21 and the existing Maven dependencies:

```text
mvn -pl solver -am -Dtest=RootSecurityBatchTest,RootSecurityEnvelopeTest -Dsurefire.failIfNoSpecifiedTests=false test
python scripts/check-root-security-batch.py
python -m unittest discover -s scripts -p test_check_root_security_batch.py
```

The new independent commands are additive in the existing solver-3 CI step.
All 30 jobs, timeouts, old commands, dependencies and prior class assignments
remain. The new Java test is discovered by the existing inventory. No physical
poker replay, fresh reference training or source regeneration was run locally for
this slice; all 436 existing data files and old test resources stay unchanged.
The new analytic resource is separate from those physical source reports.

## Next boundary and review

This is infrastructure for covering more root observations with less repeated
parent work. It supports two active constant-sum decision makers, including at a
six-seat table. It does not solve six-active-player equilibrium, realistic rake,
a richer betting model, attained full-action maxima or coherent full-hand play.
A read-only survey of the existing three-hand bank's completed audits gives
3,366,363 compiler units including the new preflight, 468 endpoint pivots and
1,440,054,300 endpoint arithmetic operations. Those historical counts fit the
new cumulative caps. This survey runs no LP or CFR and does not substitute for
an owned batch replay. The next step is an explicitly declared physical hand-bank
integration with complete replay, source identity, work inventory and qualification. Existing
trainer sources keep their identities and do not automatically use this batch.
AWS deployment remains paused. The owner cleared this code as `df4101e` on 2026-11-14 (Europe/London).
The approval below applies to that exact revision.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`df4101ea7b0e3b11467bd1d28c2f59c2768e3725` and cleared PR creation on
2026-11-14 (Europe/London), with “cleared df4101e”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
