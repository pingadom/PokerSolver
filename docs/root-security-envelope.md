# Root security envelopes

## Declaration before source generation

Implement a separate conservative outer-bound mode for questions before every
player action. Keep the old exact/reduced modes, all source worlds, actions,
opponent security face, numerical gates and artifacts unchanged. No fallback or
larger work cap is used for PR #207's failed raise.

For root question q with fixed chance mass p, let sigma be one complete pure hero
policy. Select its action of greatest probability at every hero information set
in the owned parent policy, breaking ties in the original legal-action order.
This derived policy is identified separately; it is not the parent's mixed policy
or a newly claimed equilibrium. Certify its global guarantee L against the entire
opponent flow polytope. The original face F bounds the hero best response by U+s.
Perfect recall and explicit separation of q's future hero information sets from
other root observations permit a unilateral deviation only in q's subtree. Thus

```text
V_a(tau) <= v_q(sigma,tau) + (U + s - L) / p, for every tau in F.
```

Maximize the fixed-policy conditional value over F once, then add that allowance.
For each move, minimizing its forced-action fixed-policy continuation over F gives
a lower bound on optimal continuation. Intersect the upper bound with that action
subtree's literal maximum terminal utility (with original safety padding).
These bounds may be loose and need not be attained by any optimal continuation.
Their schema, witnesses and owned results must remain separate from exact endpoint
audits. A negative guarantee gap, unsupported root, failed original flow/behavior
check or any work/numerical limit rejects; no weaker mode is substituted.

Before real-source generation, verify against exhaustive pure strategies and
opponent mixtures in small finite games, including either hero orientation,
constant-sum offsets, shared hidden worlds, poor fixed policies, unattained outer
bounds, conditional optimal continuation, imperfect recall, non-root questions,
invalid policies, positive/zero reach and reduced work controls. No mixed-policy
rounding is claimed: the fixed policy is explicitly pure, and its guarantee is
certified even if greedy selection makes it poor.

The one source experiment is the same BB99 root with fold/call/raise to 9 bb in
the unchanged supplied joint size-menu game. Attempt the complete envelope once.
Do not search another hand, model, policy, slack, target or mode after results.
Reserve one physical game, one shared parent/two fresh 80k/160k references (240k
iterations total), plus one envelope parent solve: at most two parent calls/four
parent LPs. The three-action envelope uses at most five additional LPs, sharing
the original 10,000-pivot/four-billion-arithmetic budget and eight-million compiler
budget. State, sequence, information-set, dimension and 8 MiB stored/expanded
limits remain unchanged. Reserve one generation, one local full validation replay
and one replay per CI run. Retain failures; no retry, cap increase or replacement
case is allowed.

Root probability uses the original joint prior, with the original 0.0001 positive
reach gate. It is not divided by selected-flop probability. Utilities remain
absolute terminal profit; add BB's committed 1 bb for current-decision EV.
Published evidence must independently reconstruct the original parent, fixed pure
policy, global and conditional coefficients, both LP domains, original primal/dual
certificates, flow/behavior, full-parent security, fixed-policy values, optimal
continuation inequalities and work. No new trainer admission, policy qualification
or grading capability follows from having complete outer bounds.

## Declared result

The single generation completed in about 82 seconds, including physical evaluation
and the two fresh parent references. All three moves have conservative bounds:

| BB99 move | Lower terminal profit (bb) | Upper terminal profit (bb) |
| --- | ---: | ---: |
| Fold | -1.000000010 | -0.999999990 |
| Call | 0.785779190 | 0.785779283 |
| Raise to 9 bb | -6.359434212 | 0.785779283 |

These use the same original joint root mass of 1/3 across eight hidden worlds,
not the probability of seeing the selected flop. Add 1 bb to both ends for
current-decision EV. The raise interval is deliberately broad: a poor fixed
continuation still gives a valid lower bound. It does not establish that call is
uniquely best, nor that either raise endpoint is attainable. No grades, question
qualification or trainer capability are added.

The five LPs consumed 153 pivots and 471,304,900 charged arithmetic units, within
the unchanged shared limits of 10,000 and 4,000,000,000. Compilation consumed
1,120,512 of 8,000,000 units. The report is 65,958 stored bytes and 858,830 expanded
bytes, below both 8 MiB caps. The original 1,609-state parent, 89 information sets,
all actions/worlds and every previous data artifact remain intact. This is a
two-active-player supplied-range research game at a six-seat table, not a general
six-player equilibrium solver.

## Verification and reproduction

`FiniteTwoPlayerRootSecurityEnvelope` owns the generic calculation. Analytic tests
enumerate complete hero strategies and opponent mixtures, including nonoptimal
fixed policies and bounds that are not attained. `SixMaxRootEnvelopeStudy` owns
the physical report, and `RootEnvelopeStudyTest` performs one complete deterministic
physical replay per test run. The global guarantee's witness may lie outside the
security face; only the other four witnesses are required to lie inside it.

The separate Python checker reconstructs the literal original game, selects all
33 pure hero actions from the owned baseline, rebuilds each affine objective and
both LP domains, and checks the five original primal/dual certificates with the
existing exact-binary64 residual checker. It independently reconstructs original
flows and behavioral policies, evaluates fixed utilities and optimal information-set
continuations, and verifies the full-game deviation inequalities. It performs no
new LP optimization or CFR training. Canonical Java state/policy hashes and the
entire saved report are covered by the full owned replay. Eight corruption-test
methods exercise domain, coefficient, flow, policy, reach, scope, menu and work
claims. Old algorithms, qualification gates and checkers are unchanged.

```powershell
mvn -pl solver -am test "-Dtest=RootSecurityEnvelopeTest,RootEnvelopeStudyTest" "-Dsurefire.failIfNoSpecifiedTests=false"
python scripts/check-root-security-envelope.py
python -m unittest discover -s scripts -p test_check_root_security_envelope.py
```

The CLI accepts `solve <request.json> <new-report.json.gz>` or
`replay <request.json> <existing-report.json.gz>` through
`com.pokerlab.solver.SixMaxRootEnvelopeMain`. Inputs and outputs must be distinct;
generation never overwrites a report. Reproduction is validation of this fixed
case, not permission to search new cases or change limits. Source artifacts are
`docs/data/supplied-root-envelope-request.json`,
`docs/data/supplied-root-envelope-report.json.gz`, and the independently checked
`docs/data/supplied-root-envelope-independent-check.json`.

## Next boundary

The next scoped step is to assess whether conservative complete-menu EV/loss bounds
can satisfy the existing reach, whole-face, reference-policy and decision-separation
gates. Passing this research calculation alone does not satisfy them. Keep broad
or ambiguous decisions rejected. Any wider hand/range/model study needs a separate
declared budget; the old exact raise's retained work failure cannot be relabelled
as a successful endpoint solve.
