# Weighted preflop range notation

This proposed solver input layer expands poker range notation into exact weighted
physical hands. It covers all 169 pair/suited/offsuit classes and all 1,326 possible
two-card combinations, preserving per-combo weights and explicit known-card
blockers. The owner cleared this revision as `dcb58cd` on 2026-11-18.
The approval below identifies the exact reviewed revision; full PR CI follows.

## Accepted grammar

Use commas to form a union. Canonical ranks are `23456789TJQKA`, suits `cdhs`,
and non-pair classes require a high rank first plus `s` or `o`.

| Input | Meaning | Physical combinations |
| --- | --- | ---: |
| `AA` | Every ace pair | 6 |
| `AKs` | Every suited ace-king | 4 |
| `AKo` | Every offsuit ace-king | 12 |
| `TT+` | TT through AA | 30 |
| `A5s+` | A5s through AKs | 36 |
| `KTs-KQs` | KTs, KJs, KQs | 12 |
| `A5s-A2s` | A2s, A3s, A4s, A5s | 16 |
| `AsKh@0.5,AcKd` | Two exact combos with weights 0.5 and 1 | 2 |
| `*` | Every physical two-card hand | 1,326 |

A plus fixes the first rank for non-pairs. Consequently `76s+` means only `76s`;
it does not silently add a diagonal ladder such as 87s/98s. List those classes
explicitly. Intervals may run in either direction but must contain pairs or retain
the same high rank and suitedness. Bare `AK`, diagonal intervals, lowercase ranks,
ambiguous suffixes and malformed terms reject.

`@decimal` applies a frequency weight to every selected physical combo, not a
single mass to the class. `AA@0.5,AKs,AKo@0.25` contains 22 combos and total input
weight ten: three units for AA, four for AKs and three for AKo. Exact physical
combos may represent a partial class. All overlaps reject, including overlaps
between a class and an exact combo, even when their weights agree. Weights are
never summed or clamped to hide an ambiguous input.

Weights must be positive decimal values at most one. Decimal bounds are checked
before binary64 conversion, so a value just above one cannot round down and pass.
Values that underflow to zero reject. Binary64 rounding within the valid interval
is explicit; these are input frequencies rather than arbitrary-precision values.
Zero weights, signs, percentages, scientific notation and non-finite literals are
not accepted. Omit a combo to exclude it. Input is bounded at 32,768 characters
and 1,326 comma-separated terms.

## Blockers and identity

`CompiledRange.conditionOnDeadCards` accepts at most 52 distinct known cards.
It removes conflicting physical hands while retaining each remaining input weight.
The result reports input/blocked/retained counts and weight masses. It performs no
probability renormalization. All-blocked support is explicit; requesting usable
combos from that result fails. Duplicate/null known cards reject.

These conditional weights are not reconstructed joint beliefs. Folded hands remain
physical blockers in a complete six-seat game; this helper does not authorize
removing those seats or multiplying reached marginals into a new prior.

Physical combos are sorted by their existing canonical `WeightedCombo.key`.
Equivalent token order, pair intervals, plus notation and unit-weight spellings
produce identical support and semantic hashes. Hashing includes a versioned scope,
canonical keys and exact `Double.toHexString` weight bits. Known cards accompany
the original range hash in exports. All returned collections are immutable.

## Java and CLI use

`WeightedRangeNotation.compile(expression).combos()` returns the existing
`List<WeightedCombo>` consumed by solver inputs. `coverage()` reports represented
class counts/masses; `handClasses()` provides a stable 13-by-13 grid order.

Using the existing engine/solver runtime classpath:

```text
com.pokerlab.solver.WeightedRangeNotationMain "AA@0.5,AKs,AKo@0.25" .local/range-input.json Ac,Kd
```

The output directory must already exist and the output file must be new. All
inputs are parsed and the JSON is serialized before creating the file. Existing
files, including hard-link aliases, are refused. The report has schema
`pokerlab-weighted-range-input/v1`, with input identity, known cards, all retained
weighted combos and blocker accounting. All-blocked input exports explicit empty
support. Both trainer admission and grading authority are false.

## Integration and validation

All 22 Java tests passed: fourteen notation/physical controls, three existing-game/
sampler integration controls and five CLI/IO controls. The independent literal
52-card-pair oracle confirms the 169-class partition covers every physical hand
exactly once. Blocker counts are checked for all 53 known-card inventory sizes,
against choose(52-n,2). Tests cover per-combo mass, exact-combo partial classes,
semantic identity, overlap/grammar/decimal guards, immutable collections,
all-blocked inputs, deterministic exports and output preservation.

The existing exact six-seat game accepts the compiled singleton ranges in an
analytic equal-share control, with all 57 active-seat subset queries. This oracle
is a control, not physical showdown evidence. The six pair-class ranges have
46,656 possible independent joint deals; the unchanged 64-world cap rejects before
any payoff query. The existing seeded sampler accepts compiled weighted ranges;
two deterministic 32-draw controls remain empirical support, not exact priors.
No CFR, LP solve or physical board enumeration runs in these checks.

Spotless, compilation and packaging with tests skipped passed. All existing solver
and engine source files, 441 data files, 28 resources and dependencies remain
unchanged. All 395 prior CI class assignments and existing job limits remain;
three new classes are automatically discovered. Full PR CI follows owner clearance.

## Scope and next boundary

This adds practical range input, not a solved 169-hand opening chart. It does not
increase the exact game's joint/private/public-state caps, qualify new trainer
questions or change current website lessons. Broader ranges still require a bounded
source model, appropriate sampling/error accounting where used, complete solving
and qualification before teaching decisions. Multiway equilibrium, realistic rake
and coherent full-hand strategy remain broader work. AWS deployment stays paused.

## Proposed six-seat follow-up

The [six-seat preflight](six-seat-range-preflight.md) consumes six compiled ranges
and constructs their complete card-legal joint prior within explicit work/world
limits. It exposes blocker-induced correlations and unsupported hands rather than
multiplying marginals or silently pruning ranges. The owner cleared this follow-up as `1dfc54a`; it does not change this compiler's
grammar or physical partition.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`2919507e9ab099ae802529f6835e94fd41cb6fd7` and cleared PR creation on
2026-11-19 (Europe/London), with “cleared 2919507”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
