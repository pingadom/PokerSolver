# Fresh six seat sources from weighted ranges

This proposed backend workflow connects range notation, complete private support,
bounded physical payoffs, fresh exhaustive CFR and a finite-model deviation audit.
It produces a complete validation-only source pack without changing website lessons.
The owner cleared this revision as `2919507` on 2026-11-19.

## Planning before physical execution

`SixSeatRangeSource.plan(request)` compiles all six ranges, requires every declared
hand to have legal support and constructs the existing mandatory-checkdown research
spot. Original private/public-state caps still apply. It then plans explicit exact
or shared-board payoffs within the existing payoff budgets.

A separate CFR bound uses the complete public tree. If D is the legal private-world
count and S is the public-state count, each exhaustive alternating player pass
visits 1 + D*S nodes, including the initial chance root. For I iterations, the six
players require 6*I*(1+D*S) visits. This workflow permits at most 10,000 iterations
and 200 million CFR node visits, with lower caller budgets. Over-budget source
plans reject before any physical payoff execution or CFR solve.

The budget counts CFR traversal visits. It excludes initial tree construction,
regret-clipping arithmetic, best-response audits, pack validation, serialization,
wall time and cumulative repeated runs. Existing finite-game state caps still
bound each game. These are separate physical and traversal budgets, not a global
CPU or memory quota.

A sampled source needs at least two board trials per world because the existing
source-pack format requires that minimum. The earlier raw payoff diagnostic can
use one trial; it cannot be promoted into a source solve. Exact mode retains its
zero trial/seed configuration and full physical board enumeration.

## Fresh solving and owned payoff reuse

`Plan.execute()` evaluates its owned complete payoff plan, builds the existing
game from those rows and runs a fresh exhaustive vanilla or CFR+ solver. No chance
sampling, inactive-player pruning, old strategy rows or continuation checkpoint is
introduced. Actual CFR statistics must match the planned node visits and report
zero sampled-chance nodes and zero inactive-pruned roots.

`Plan.solveWith(ownedPayoffs)` can reuse an already executed immutable payoff
result when its full payoff-plan identity matches. A changed seed, method, trials
or private input rejects. Physical values can serve a different betting/rake model
when the private payoff source is identical, but the target game, policy and
quality are recomputed. Each solve starts fresh and has its own traversal budget;
reusing payoffs does not reuse a policy or evaluate additional boards.

The result includes a complete existing-format `SixMaxPreflopSolutionPack`, fresh
CFR statistics and a full per-player unilateral-deviation report. Serialization
runs the existing pack validator, which checks complete information sets/payoffs
and recomputes quality from saved values. Publication remains VALIDATION_ONLY.

The reported NashConv measures deviation gains in this specified finite payoff
game. For sampled payoffs, it audits the sampled-value game, not unknown true
poker values. A small gap does not remove board estimation error or the model's
mandatory-checkdown/bounded-range/betting-menu limitations. Independent payoff,
strategy and educational qualification remain necessary before trainer admission.

## Private captured request CLI

The CLI accepts three arguments:

```text
com.pokerlab.solver.SixSeatRangeSourceMain plan INPUT.json NEW-plan.json
com.pokerlab.solver.SixSeatRangeSourceMain solve INPUT.json NEW-source.json
```

A small reproducible request is:

```json
{
  "id": "range-source-control",
  "ranges": ["AcAd", "KcKd", "QcQd", "JcJd", "TcTd", "9c9d"],
  "preflightLimits": {"maximumJointDeals": 64, "maximumCandidateChecks": 1000000},
  "rules": {"stackBb": 2, "smallBlindBb": 0.5, "raiseToBb": [2]},
  "rake": {"fraction": 0, "capBb": 0, "noFlopNoDrop": true},
  "payoff": {
    "method": "SHARED_BOARD_MONTE_CARLO",
    "trialsPerDeal": 4,
    "seed": "711",
    "budget": {"maximumBoards": 4, "maximumHandEvaluations": 24}
  },
  "iterations": 2,
  "variant": "CFR_PLUS",
  "maximumCfrNodeVisits": 200000000,
  "generatedAt": "2026-11-18T20:00:00Z"
}
```

This is a two-big-blind implementation control, not a recommended poker strategy
or realistic solved cash-game source. Supply an ISO instant as generatedAt; it is
caller metadata, not an independently attested execution or approval time.

The input is captured once with a bounded read of at most 64 KiB plus one overflow
detection byte. All later work uses the captured typed request. Duplicate, trailing,
unknown, missing, fractional-integer and wrong-token inputs reject. Seeds must be
canonical signed decimal strings. Existing outputs, input paths and aliases reject
before solving. After complete validation and serialization, CREATE_NEW preserves
any concurrent creator's file. A disk failure can still leave a partial new file.
The output directory must already exist; publication is not transactional.

`pokerlab-six-seat-range-source/v1` reports the captured-byte hash, semantic source/
payoff/spot identities, planned work and execution/quality results. Plan-only mode
runs no physical boards or CFR. The existing-format source pack is stored as an
opaque `sourcePackJson` string, preserving its legacy numeric long seed without
converting it through JavaScript numbers. Use the existing Java pack reader to
restore/audit that string. The separate source-pack hash binds the full canonical
pack; a generatedAt change alters that pack hash but not the model-plan identity.

Both trainer admission and grading authority are false. Full source policies,
payoffs and all concealed private cards make this output private/offline. No HTTP
route or new website question is added, and exported JSON cannot manufacture the
privately constructed owned runtime result.

## Jackson boundary fix and interview evidence

A regression exposed an application integration mistake: the shared mapper's
ALLOW_COERCION_OF_SCALARS flag does not reject numeric values targeting String.
Jackson [documents that String exception](https://github.com/FasterXML/jackson-databind/wiki/Mapper-Features).
A standalone local probe on the installed 2.21.5 version reproduces numeric zero
becoming a string, then verifies explicit Textual coercion rules reject integer,
floating-point and boolean tokens while preserving Long.MIN_VALUE as text.

The new source decoder configures those guards explicitly and rejects numeric
enum ordinals. Regression inputs cover numeric/float/boolean seeds, numeric ranges,
variant/method ordinals, fractional integers and ordinary schema corruption.
This is a concrete JSON integration and regression-testing example for the project;
it is documented library behavior, not a claimed upstream library bug or external
contribution. Existing mappers, dependencies and published artifacts are unchanged.

## Validation and remaining scope

All seventeen new Java cases and fifty-eight retained input/payoff cases passed,
for 75 selected cases without skips. Controls include both exact and sampled source
execution, a twelve-world correlated source, owned-payoff reuse compared with the
existing builder, exact node-budget edges, immutable output and opaque seed round
trips. Every source is complete enough to pass the existing pack rebuild/audit.
The final suite runs six source/builder CFR controls; this is algorithm composition
and finite-model validation, not a convergence study or quality-qualified lesson.

A separate private CLI source capture is checked with Python for request/pack
hashes, planned versus actual node counts, full payoff inventory, signed seed
preservation and the sum of all six deviation gains. This does not independently
prove best-response optimality; that algorithm retains its existing controls.

Spotless, compilation and packaging passed. All existing Java sources, 441 data
files, 28 solver resources, 402 CI assignments, dependencies and workflow limits
remain unchanged. Two new suites auto-discover; full PR CI follows owner clearance.
Broader realistic support, independent error/quality and content qualification,
postflop integration and public trainer admission remain separate. AWS stays paused.

## Exact payoff comparison

Use the [payoff comparison](six-seat-payoff-comparison.md) to reassess a fixed
sampled policy under exact boards with the same finite game and private prior.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`c19e89959751af0abb94dc65fcbbe937ae9e44f8` and cleared PR creation on
2026-11-19 (Europe/London), with “cleared c19e899”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
