# Six-seat weighted range preflight

This proposed backend step connects weighted poker notation to the existing six-seat
mandatory-checkdown research model. It completely enumerates card-legal joint
private hands within explicit limits before any physical payoff construction or
strategy solving. The owner reviewed this revision and cleared PR creation on 2026-11-18.
The confirmation below identifies the exact approved revision; full PR CI follows.

## Complete physical prior

`SixSeatRangePreflight.plan(expressions, limits)` requires six explicit ranges in
UTG, HJ, CO, BTN, SB, BB order. The approved weighted range compiler supplies each
seat's canonical physical hands and binary64 per-combo frequencies. All six seats
remain physical blockers, including seats that might fold later.

The planner visits the complete independent product, rejecting pairs of hands that
share any card. For each legal world its unnormalized mass is the product of all
six frequencies. Normalization happens only after enumeration completes. Because
card removal couples seats, the returned per-seat marginals must never be multiplied
back together to reconstruct the joint prior.

For example:

```text
UTG  4c4d
HJ   Kd9d,AhJh
CO   QhTh,AhKh
BTN  JsTs,8s8h
SB   6c5c
BB   AcJc,7d7h
```

These inputs contain sixteen nominal combinations and twelve legal joint worlds.
HJ AhJh and CO AhKh each have positive marginal probability but cannot occur
together. All twelve worlds are equiprobable; HJ Kd9d has marginal probability
2/3 and CO AhKh 1/3. Changing HJ AhJh to weight 0.5 leaves the same twelve worlds
with total unnormalized mass ten. A literal Cartesian-product test checks every
weighted probability, separately from the prefix enumeration implementation.

## Work limits and rejection

The unchanged exact game's limit is 64 legal joint worlds. Preflight also limits
candidate prefix checks to 1,000,000. `Limits` permits reducing either number, not
increasing it. Every candidate costs one check before conflict testing, including
blocked candidates. Successful work reports completed counts and both limits.
This bounds impossible products as well as products with many legal worlds.

The planner rejects an over-budget input without returning a partial result and
never truncates support or falls back to sampling. Encountering a 65th legal world
reports 64 stored complete worlds and a legal-world lower bound of 65. A candidate
limit reports the number actually checked and the completed-world lower bound.
These are work diagnostics, not a complete world-count estimate. An entirely
impossible input is a separate rejection.

Log weights and maximum rescaling prevent common tiny frequency scales from
underflowing before normalization. Positive worlds whose final binary64 probability
would become zero cause an explicit rejection. These probabilities remain floating
point estimates of the declared input product, not rational certificates. The log
legal weight mass is reported rather than an underflow-prone product mass.

## Support and research integration

Results are immutable and privately constructed. Each seat includes every declared
combo's marginal probability, its supported count and explicit unsupported keys.
Zero-support hands remain visible in both original ranges and support accounting;
they are not silently pruned. `toResearchSpot(id, rules, rake)` refuses any unsupported
declared combo and delegates to the unchanged research-spot validation. Existing
public-state and private/public-product limits can still reject a complete plan.

The versioned input identity binds ordered seat range hashes and prior scope. It
ignores notation differences and successful work budgets. It excludes betting rules,
rake and solver configuration; the research spot has its own separate identity.
A plan is not a strategy, a solved range chart or trainer admission. Research spot
validation uses an analytic equal-share control; physical showdown evidence and
strategy quality still require the established solve/qualification pipeline.

## Offline export

On the existing engine/solver runtime classpath, invoke:

```text
com.pokerlab.solver.SixSeatRangePreflightMain NEW-output.json "4c4d" "Kd9d,AhJh" "QhTh,AhKh" "JsTs,8s8h" "6c5c" "AcJc,7d7h"
```

The directory must exist and the output must be new. All input planning and JSON
serialization complete before `CREATE_NEW` writes the file. Existing files and
hard-link aliases are preserved. Validation, support overflow and impossible inputs
create no output file. Filesystem write failures can still leave an incomplete new
file; there is no transactional publication guarantee.

The `pokerlab-six-seat-range-preflight/v1` report includes all six full compiled
ranges, range identities, input identity, complete legal worlds, log mass, marginal
support and work. Nominal product size is a decimal string, since the maximum
1,326-to-the-sixth product exceeds JavaScript's exact integer range but fits a
signed Java long. Both trainer admission and grading authority are false.

This file contains concealed hands for all six seats. Keep it in offline/private
research tools. Never return its world inventory as a public trainer question.
No HTTP route or website lesson is added by this change.

## Validation and remaining work

All nineteen new Java tests pass: thirteen planner/integration controls and six
CLI/IO controls. All twenty-two existing range compiler tests also pass. The
weighted twelve-world plan agrees with every existing game chance outcome, using
684 analytic equal-share subset calls; this is not physical showdown evidence.
A separate local Python Cartesian/Decimal check of the actual CLI export matches
all twelve joint probabilities and every seat marginal. It does not use the Java
planner or its normalization routine.
Exact-budget completion, blocked-prefix costs, impossible inputs, extra-world
witnesses, tiny common weights, relative-probability underflow, unsupported hands,
semantic identity, immutable results and non-overwriting output are covered.

Spotless, compilation and packaging with tests skipped passed. No existing solver
or engine Java sources, dependencies, data fixtures, resources or CI job limits
change. No CFR, LP solving or physical board enumeration runs in these tests.
The new test classes join the existing automatically discovered CI shards after
owner clearance; full PR CI follows that clearance. Broader range sampling/error accounting,
physical payoff budgets, strategy solving, independent quality checks and trainer
admission remain separate work. AWS deployment remains paused.

## Proposed physical payoff follow up

The [payoff work planner](six-seat-payoff-work.md) consumes this owned complete
input, calculates exact or explicitly sampled physical board costs, and can execute
a complete private payoff table within its separate budgets. It preserves the
input prior and unsupported-hand reporting. The owner cleared this later revision as `87673cb`; it does not qualify
strategies or publish trainer questions.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`87673cb036635e75d85b7e6c59409b6fb1aea3d4` and cleared PR creation on
2026-11-18 (Europe/London), with “I reviewed 87673cb — clear it”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
