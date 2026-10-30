# Reduced hero continuation plans

## Declaration before numerical generation

Add a separately selected preflop-root interval mode for the existing jointly
solved size-menu game. Keep every world, blocker, public move, physical chance
event, payoff, opponent strategy and original numerical gate. Existing modes and
published reports must replay unchanged.

The new representation specifies a hero action only at information sets reachable
under that plan's own earlier choices. All opponent and chance branches remain.
Shared hero information sets across hidden worlds receive one common choice;
their parent hero sequence must agree (perfect recall). The old Cartesian mode
continues to enumerate its complete maps, including unused hero entries.

Before any LP, compute the reduced plan count from the hero dependency forest:
multiply independent information-set counts at a sequence; sum over that hero's
legal actions at an information set. Reject above the unchanged 64-plan bound
before allocating choices. Charge traversal, counting and map work to the
existing eight-million-unit compiler budget. No game action is removed.

Verify equivalence independently by enumerating the old full Cartesian maps in
small controls and the fixed 1,296-plan research case, erasing only self-unreachable
entries, and checking every original opponent-sequence payoff coefficient. This
offline oracle has a declared 1,296-plan ceiling and performs no optimization.
The production compiler retains its 64-plan cap and never enumerates 1,296 maps.

The one numerical study is BB 9d 9h at the original preflop root, with all three
moves (fold, call, raise to 9 bb) attempted once. No other hand, range, history,
menu, board, loss limit or mode is searched after seeing results. The expected
reduced products are 1, 4 and 52; old Cartesian products are 1, 6 and 1,296.
This is a research interval study, without grading capabilities or trainer admission.
Terminal-profit bounds remain separate from EV measured at the decision (add BB's
already committed 1 bb); folding therefore has terminal utility -1 bb and current
decision EV zero. Question reach is in original joint-prior units, without scaling
by or conditioning on the selected physical flop.

Reserve one physical game, one shared parent/two fresh 80k and 160k references
(240,000 iterations total), and three interval attempts on that same game. Each
attempt keeps its original separate parent audit: at most four parent solve calls,
eight parent LPs, and 66 interval LPs (4 + 7 + 55). Each attempt retains the original
10,000-pivot/four-billion-arithmetic LP budget and eight-million compiler budget;
no shared limit is raised. Stored and expanded reports remain capped at 8 MiB.
Physical board/runout work and the 1,609-state tree remain the original request's
measured budgets. One generation, one complete local validation replay and one
replay per CI run are reserved. Pure representation controls add no physical solves.

Retain typed failures for every attempted move. Incomplete action menus have no
loss classifications, policy qualification or grading authority. No retry, fallback,
threshold change, opponent-face restriction or replacement case is allowed.
An independent checker must verify the complete original parent, all original LP
certificates, reach/posteriors, reduced-plan coverage and coefficients, full-parent
security, forced-action best responses, work totals and complete move inventory.

## Results

The one declared generation completed in about 84 seconds. Fold and call produced
complete intervals; raise to 9 bb returned `LP:WORK_LIMIT` with the original
`LP arithmetic work budget exceeded` diagnostic. Its reduced forest fits 52
plans, but the LP stage exhausts its separate four-billion-unit arithmetic bound.
No retry, fallback, larger cap or replacement question was used.

The representation improvement is exact: the independent oracle enumerates all
1 + 6 + 1,296 original Cartesian plans and obtains 1 + 4 + 52 reduced plans.
Every erased plan retains all original opponent-sequence payoff coefficients.
The raise uses about 96% fewer pure plans; this is not an end-to-end speed claim,
and it does not imply that the entire numerical calculation fits its work limits.

BB99's fold terminal-profit bounds are approximately [-1.00000001, -0.99999999] bb;
call bounds are [0.7857791903, 0.7857792403] bb. Add the existing 1 bb commitment
to express these from the decision. The positive root reach is approximately 1/3
in original joint-prior units. A comparison with fold does not establish call as
best across the complete menu: raise still has no certified interval.

The independent checker reconstructs the unchanged physical parent and its two
original LP certificates, all three parent policies, eleven completed endpoint
certificates, the original security face, child-sum behavioral reconstruction,
root posteriors, global security and forced-action best responses. It performs
no LP optimization or CFR training. The rejected raise has no complete witness
audit; neither a certified raise EV nor an observed total of 66 LP solves is
claimed. Sixty-six was the declared maximum, not a measured completed count.

The compressed report is 111,375 bytes and expands to 1,426,340 bytes, below the
unchanged caps. It retains all three moves and the failed raise. There are no
complete-menu loss classifications, question qualifications, grading capabilities
or new trainer questions. Existing modes, reports and playable trainers remain
unchanged.

## Reproduction and validation

Build with the usual Java 21/Maven workflow and run
`com.pokerlab.solver.SixMaxReducedPreflopMain` on the solver runtime classpath:

```text
solve docs/data/supplied-reduced-preflop-request.json new-report.json.gz
replay docs/data/supplied-reduced-preflop-request.json docs/data/supplied-reduced-preflop-report.json.gz
```

Outputs are exclusive; input/output aliases and existing outputs reject before
physical work. Strict JSON and canonical LF UTF-8 are retained. Replay rebuilds
the physical game, both fresh references and each attempted action, including the
same work-limit rejection, and requires complete report equality.

Eight representation tests cover hidden worlds, both hero orientations, exact
coefficient preservation for every full plan, independent opponent branches,
perfect recall, immutable canonical maps and unchanged work/64-plan limits.
The 22 existing analytic root/conditional controls also pass. Nine study tests
reserve one complete physical replay and check saved report equality, original
parent/reference equality, the actual owned 52-plan forest, old-mode rejection,
root chance versus flop units, incomplete-menu withholding, phase provenance,
strict readers and output protection. Six Python test methods corrupt original
coefficients, reduced maps, certificates, posteriors, prices, scope, work and the
retained raise failure.

## Next numerical work

Investigate a separately declared root-specific formulation or reuse of LP work
for a family of objectives on the same face. The root denominator is constant;
the generic conditional compiler still introduces fractional normalization.
Any optimization needs independent endpoint/certificate equivalence controls,
new measured budgets and a separately identified mode. Do not rerun or relabel
this failed raise under a larger cap. Complete all three action bounds and the
original policy/educational qualification gates before adding a root trainer.

This is two-active-player research at six seats, with the existing tiny supplied
ranges and physical-flop/checkdown abstraction. It is not a general six-player
cash equilibrium, and the work-limit failure is not a third-party library bug.
AWS deployment remains paused.
