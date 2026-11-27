# Exact showdown reuse across suit relabelings

`EXACT_SUIT_REUSE` reduces the physical cost of exact six seat payoffs when
ordered deals differ only by a global relabeling of suits. Each legal world
retains its own chance weight and policy information sets.

The planner tries all 24 permutations on all twelve hole cards. The same
permutation applies to every seat and every card, including folded seats.
It preserves ranks, seat order and cross-seat suit overlap. A global
renaming maps each legal board to another legal board with matching seat
ranks and shares for every showdown subset. The first world in canonical
input order supplies the payoff rows for its class.

The existing cap now applies to the representative evaluations in this
explicit mode. Work reports continue to list logical worlds and subset
rows. `plannedEvaluatedDeals` and `boardsFromEvaluatedDeals` report
representative work. `boardsFromPerDealEstimates` sums each original world's
trial count and can exceed the physical budget. Each stored row uses the
full 658,008-board estimate with zero sampling error.

## Offline workflow

Plan or evaluate with `SixSeatPayoffWorkMain` and the `exact-suit-reuse` method.

```text
com.pokerlab.solver.SixSeatPayoffWorkMain plan NEW-plan.json exact-suit-reuse 7896096 47376576 AA KK QcQd JcJd TcTd 9c9d
```

To run a source solve through `SixSeatRangeSourceMain`, set
`payoff.method` to `EXACT_SUIT_REUSE` in the private request. Use
`payoff.trialsPerDeal: 0` and `payoff.seed: "0"` as for direct exact
enumeration. The request and plan identities record reuse. The existing
source pack represents the complete exact payoff table with its ordinary
`EXACT_ENUMERATION` method. Keep the surrounding report for execution
provenance and because it contains private range data.

Direct exact and sampled modes remain available with their existing
configuration. There is no automatic selection or fallback. The current
source comparison CLI plans the direct exact reference. An owned reused
exact reference may also be passed to its Java assessment method.

## Validation

An independent experimental run enumerated all 36 worlds and all 12
representatives for the example above. Across 2,052 mask rows the maximum
difference in individual shares was 2.98e-14. Both sources use exhaustive
boards. Their numerical shares may differ slightly from summation order.

The source runner reproduced the 36-world example at 100 bb with a single
3 bb raise target, 5% rake capped at 1 bb and 100 CFR+ iterations. It used
7,896,096 physical boards, preserved 23,688,288 logical board exposures and
emitted all 2,052 subset rows. The policy contains 1,564 strategy rows. The
finite-model deviation sum was 0.001967 bb after 29,376,600 CFR visits.

All 41 selected Java cases passed, including four new cases covering
canonicalization, independent budgets, a nonuniform prior and the payoff
CLI. The complete source pack passed the existing audit. This example
uses artificial private ranges and mandatory checkdown. Its output is
validation-only and contains concealed hands.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`4533b09ca264e53232eb9bf70d744f2c1e12c4e3` and cleared PR creation on
2026-11-27 (Europe/London), with “cleared 9fa8968, 47b79c9, 4533b09”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
