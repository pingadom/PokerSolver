# Resolvable pricing with refined endpoint solves

The owned solver now solves the predeclared `2c 3c` BTN decision facing a BB bet
on `8d Kc Kd`. This is a new, explicitly selected research mode. It replaces six
J9s worlds in the earlier supplied 24-world support, regenerates the physical
game, and computes both legal actions against the complete opponent security
face. Two players are active at the six-seat table.

| Action | Certified terminal-profit EV (bb) | Conservative decision loss (bb) |
| --- | --- | --- |
| Fold | −3.00000001 to −2.99999999 | 0 to 0.00000002 |
| Call | −5.24977471 to −5.24949976 | 2.24949975 to 2.24977472 |

These are absolute hand profits, including prior commitments. The approximately
2.25bb loss is the cost of choosing call over the best available action in this
restricted game. The report remains `trainerAdmission=false`.

## Why a separate algorithm was needed

The previous v5 endpoint LP solver rejects the captured 86-constraint,
81-variable call lower-bound problem with `Unresolved positive LP reduced cost`.
An independently generated SciPy/HiGHS witness satisfies the original primal,
dual and objective-gap conditions within the unchanged 1e-8 tolerance. The
rejection is numerical; it is not evidence that the physical problem is infeasible.
The original matrix and oracle witnesses are frozen as new test fixtures.

`BoundedLinearProgram.solveResolvableRefinedBasis` explicitly combines the existing
resolvable-cost pricing with ascending L1 row ordering and fixed compensated
basis, dual and direction refinement. A resolvably positive phase-II reduced cost
can enter even when it is below the older fixed 1e-10 pricing threshold. The
roundoff test, strict computed ratio comparisons, two refinement passes and
original-problem certificate checks remain in force. This is binary64 simplex,
with independently checked residuals, not rational optimization.

The LP certificate has a distinct `/v6` identity. Physical conditional endpoints
use `/v8` and report schema `/v3`. The parent still uses the original affine v2 /
LP v4 algorithm. Old v5 LPs and v7 endpoint requests retain their prior behavior,
including rejection of this weak-hand example. No mode retries another mode.

All original limits remain: 512 variables/constraints, 1,000,000 coefficient
magnitude, 10,000 pivots, four billion charged arithmetic units and the 1e-8
certificate tolerance. Successive endpoint LPs share their action's cumulative
budget. The frozen full-study budget reserves one generation, three parent solves,
eight endpoint certificates, at most 80,000 pivots, 32 billion arithmetic units
and 40 million compiler units. Full reconstruction uses 550 pivots, 711,528,705
arithmetic units and 2,507,232 compiler units. Later replay checks reproduce the
study separately; they are not retries after a rejected generation.

## Independent evidence

- All 298 owned LP results are frozen and reproduced in Java: 292 independent
  oracle controls, three older captured endpoints, the near-ratio regression,
  the new captured weak-hand LP and a one-variable small-cost regression.
- A standard-library Python checker uses exact fractions of every original
  binary64 coefficient and witness. The largest dual residual is about
  4.18e-10 and largest objective gap about 8.16e-11, below 1e-8. It also checks the
  independent weak-hand oracle and per-solve/cumulative work limits.
- A separate standard-library Java reference, with no PokerLab engine imports,
  enumerates all 15,792,192 five-card boards and 15,984 selected-flop runouts
  across the new 24 worlds. It preserves all six hands' blockers. Every count
  matches the reconstructed report. Rank controls cover all nine categories
  and 512 seeded samples against literal best-of-21 rankings. The Python
  physical helper additionally reproduces every fixed-flop count using its
  best-of-21 evaluator.
- The physical checker reconstructs the complete 1,321-state betting tree,
  73 information sets, residual settlements, both original parent LPs and pure
  best responses. Its measured parent NashConv is about 1.22e-15.
- All eight endpoint witnesses are checked against rebuilt full security faces,
  original flows, positive complete-path reach, physical posteriors and literal
  public context. The new lower witness also passes exact-fraction checks on
  the originally captured LP. Corruption tests exercise cards/counts, policies,
  witnesses, EV/loss bounds, reach, work, protocol and input limits.

The independent reference is a validation tool, not a second production solver.
It does not run an optimizer. Existing data files and the playable ten-question
collection are unchanged.

## Reproduction

Use Java 21 and the repository's normal Maven classpath for these CLI entry points:

```text
com.pokerlab.solver.SixMaxRefinedConditionalStudyMain solve docs/data/supplied-weak-hand-resolvable-conditional-request.json <new-report.json.gz>
com.pokerlab.solver.SixMaxRefinedConditionalStudyMain replay docs/data/supplied-weak-hand-resolvable-conditional-request.json docs/data/supplied-weak-hand-resolvable-conditional-report.json.gz
```

```sh
mvn -pl solver -am test -Dtest=ResolvableRefinedLpTest,ResolvableRefinedConditionalStudyTest -Dsurefire.failIfNoSpecifiedTests=false
python -m unittest discover -s scripts -p 'test_check_resolvable_refined_lp.py'
python -m unittest discover -s scripts -p 'test_check_weak_hand_resolvable_study.py'
```

The checked-in receipts bind the exact inputs, stored/expanded reports, budgets
and checker sources. Output-generating commands require a new output path.

## Known boundary and next milestone

The new weak-hand game's `As Qs` after-check boundary has insufficient certified
reach. Converting its near-zero sequence masses to a behavioral counterexample
also fails the existing ratio check. The new mode returns a typed numerical
rejection without a report, posterior or retry. It does not claim to have
constructed an insufficient-reach witness. A regression preserves that boundary;
behavioral tolerances have not been widened.

Next, freeze fresh 80k/160k full-game reference budgets and qualify this specific
fold decision for training. Reference inclusion, full-face security, positive
reach, selected-decision regret, all-action feedback and educational loss gates
must all pass before a new owned trainer source exists. A later collection must
use a new identity because this source's supplied worlds differ from the current
collection. More connected betting streets, rake and general six-player cash
strategy remain later work. AWS deployment remains paused.

For an interview: this milestone demonstrates isolating a numerical rejection,
checking it against an independent oracle, versioning the algorithm without
changing old results, and validating the final poker result from physical cards
through the full betting tree and original LP witnesses. It is an improvement to
PokerLab's own solver; no third-party library bug is claimed.
