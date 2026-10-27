# Joint flop size-menu research milestone

## Declaration before numerical generation

The first experiment keeps the original 24 equally weighted joint deals, all twelve
dealt-card blockers, all four preflop call boundaries, and the physical 6d Td Qc flop.
BB acts first with check, quarter-pot and half-pot. Following a check, BTN can check or bet
pot. Every bet permits fold or call followed by exact checkdown. There are no
flop raises, rake, range pruning, new boards, stack changes or selected histories.
The asymmetric menu is an explicit abstraction, not a full cash-game solution.

The existing fixed-size game is a physical carrier only: it computes the original
preflop counts, selected-flop counts, and exact complementary chance event. Its
policy and betting EVs are never loaded. The size-menu game has a distinct model,
information-set namespaces and size-tagged histories. Preflop strategies and all
flop choices are solved together in this new game.

Expected dimensions, checked before enumeration: 1,609 states; BTN 56 information
sets / 121 sequences; BB 33 / 85. Both players having both sizes would require
137 BTN sequences and must be rejected by the unchanged 129-sequence cap.
Other unchanged limits include 64 information sets per actor, 20,000 states,
depth 32, 8,000,000 affine-reduction work units and all owned-LP tolerances and
work limits. Rounded or stack-capped size aliases are rejected before evaluation.

Generation reservation: one physical carrier and one new game; one call to
`solveAscendingRefinedDirections` (two original primal/dual LP certificates);
then, only if that owned solve succeeds, two fresh CFR+ runs of 80,000 and
160,000 iterations, each with an independent complete-game best-response audit.
No optimization retry, fallback algorithm or automatic range/board/menu change
is allowed. Failure is retained as a diagnostic and grants no trainer admission.
The reference policy gap target is the existing 0.001 bb full-parent threshold;
observed failures are reported, never hidden by relaxing the target.

Physical work per generation: 15,792,192 full preflop boards / 31,584,384 hand
evaluations, plus 63,936 fixed-flop runouts / 127,872 evaluations across four
boundaries. Tests reserve one additional physical carrier, one owned solve and
the same two fresh references locally and once in CI. A singleton-menu control
reuses the test carrier without reevaluation or another solve. Corruption tests
perform no optimization. Independent Python verification reconstructs the entire
literal tree and physical fixed-flop counts, checks both LP certificates, and
audits all three policies; it performs no optimization or CFR training.

The study is research-only (`trainerAdmission=false`). Full-parent quality is
not a bound on individual conditional flop errors: the flop has probability
1/9,880. No new trainer grades, lesson collection or UI are admitted here.
Conditional security-face intervals and trainer qualification remain later work.

## Reproduction

Build with Java 21 using `mvn -B -pl solver -am spotless:check install -DskipTests`.
Run `com.pokerlab.solver.SixMaxSuppliedFlopSizeMenuMain` on the solver and engine
classpath with Jackson dependencies. It accepts:

```text
preflight docs/data/supplied-flop-size-menu-request.json budget.json
solve docs/data/supplied-flop-size-menu-request.json report.json.gz
replay docs/data/supplied-flop-size-menu-request.json docs/data/supplied-flop-size-menu-report.json.gz
```

Preflight creates its output exclusively and does no evaluator or solver work.
Solve also rejects an existing output or an input/output alias before expensive
work. Replay rebuilds physics, solves once, runs both references and requires
exact report equality. A deserialized report cannot create an owned `Result`.

## Preflight correction (before any numerical attempt)

The initially proposed half-pot/pot menu was rejected structurally: at the
50 bb boundary both sizes cap to the same 50 bb all-in. The request was changed
to quarter-pot/half-pot, leaving every other input unchanged. Both players having
two sizes still exceeds the sequence cap. This correction occurred before any
physical payoff generation, LP attempt or reference training.

## Results

The one declared generation succeeded in about 80 seconds on the development
machine. It solved the complete 1,609-state game with 89 information sets and
produced two independently checked original LP certificates. The Java full-game
best-response gap is 4.09e-15 bb; fresh 80k and 160k CFR+ references measured
2.09e-9 and 5.22e-10 bb respectively. The independent Python reconstruction agrees
within the unchanged numerical tolerances.

The saved report includes both primal/dual witnesses, every physical handoff and
residual count, all three complete policies, work counters and best-response
reports. The independent checker regenerates 15,792,192 full boards using the
separate mask evaluator, checks fixed-flop counts against its best-of-21 evaluator,
reconstructs every size-tagged edge and terminal payoff, verifies perfect recall
through sequence-flow conservation, rebuilds the projected payoff, and checks both
original LP certificates and all three policy best responses. It performs no
optimization or training. Its byte-pinned receipt is
`docs/data/supplied-flop-size-menu-independent-check.json`.

Thirteen Java tests cover exact physical replay, singleton-menu equivalence,
size/stack alias rejection, private-card isolation, public prices, refunds and
called payoffs, unchanged limits, fresh reference pruning, strict readers and
exclusive outputs. Seven Python tests include source, card, chance, recall,
policy, payoff, LP witness, work-counter and admission corruptions.

## Next scope

Extend conditional security-face reach and action bounds to this new action tree,
then qualify candidate decisions against both fresh reference policies. Only after
that should a new trainer collection expose the additional sizing buttons. Neither
the existing twenty-question collection nor its cached grades have changed. The
larger roadmap still needs wider ranges, more betting actions, additional streets
and genuine multiway solving; this two-active-player abstraction does not complete
those goals.
