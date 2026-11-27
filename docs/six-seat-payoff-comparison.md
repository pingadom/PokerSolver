# Auditing sampled preflop payoffs against exact boards

A six seat policy can have small deviation gains on the sampled payoffs used to
train it while leaving larger gains on fully enumerated board payoffs.
`SixSeatPayoffComparison` scores the same frozen policy on both games. It uses the
same rules, rake, legal private worlds and chance weights for each assessment.

## Planning and execution

Use the existing standalone source runner with the sampled JSON request in the
[range source guide](six-seat-range-source.md). Supply a board budget and a hand
evaluation budget for the exact reference. Each exact world has 658,008 boards
and six hand evaluations per board.

```text
com.pokerlab.solver.SixSeatRangeSourceMain compare INPUT.json NEW-audit.json 658008 3948048
```

The input must select `SHARED_BOARD_MONTE_CARLO`. The runner checks the source
and exact reference budgets before executing either. It then trains one sampled
policy and exhaustively enumerates the reference payoffs. It audits that policy
against best responses under both payoff models. Existing output paths are
rejected. The report contains the captured request and can include concealed
cards in its ranges. Keep it in private research storage. It grants no trainer
admission or grading authority.

For multiple sampled sources with the same weighted ranges, use
`planExactReference`, execute that reference once, and call `assess` with each
owned source and the same exact result. The comparison verifies the payoff
methods, the full weighted range identity and matching chance outcomes.
Assessing an existing pair requires no physical board evaluation or CFR solve.

## Interpreting deviation shifts

For each seat, the comparison visits every modeled terminal in every legal
world and records the minimum and maximum of exact utility minus sampled
utility. With the prior, rules and policy held fixed, any strategy's expected
utility shift is an average of terminal shifts. It therefore lies between those
two extrema. This also bounds the shift in best response value even when the
best response changes. The difference between the extrema bounds the absolute
change in unilateral deviation gain, subject to floating point rounding.

The report also gives the measured profile EV, best response EV and deviation
gain in each game. Sampled terminal standard error is a separate diagnostic.
The transfer calculation covers this finite mandatory-checkdown model and its
provided ranges. It does not certify a broader model or a public lesson.

## Matched local comparison

The [twelve world example](six-seat-range-preflight.md) was run with a two big
blind stack, half a big blind small blind, a raise target of two big blinds, no
rake, seed `711` and 200 CFR+ iterations per sampled policy. Each policy was scored
against the same exact reference, which evaluated 7,896,096 boards. Each
comparison covered all 8,352 modeled world and terminal pairs.

| Samples per world | Sampled payoff NashConv (bb) | Exact payoff NashConv (bb) | Maximum sampled terminal SE (bb) |
| ---: | ---: | ---: | ---: |
| 64 | 0.001127 | 0.053267 | 0.736698 |
| 1,024 | 0.000529 | 0.009837 | 0.169656 |
| 8,192 | 0.001062 | 0.002005 | 0.059235 |

The 64-board run illustrates why the training game's gap alone is insufficient.
The differences in this run are measured on one range set and seed. Other
ranges, stacks, raise menus and seeds need their own checks. The Java controls
cover exact revaluation, terminal inventory and per-seat bounds, rejecting
a mismatched prior, preflight rejection and the private CLI.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`9fa89685cb28dd13d5bdbac26072eaa775bf8acc` and cleared PR creation on
2026-11-27 (Europe/London), with “cleared 9fa8968, 47b79c9, 4533b09”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
