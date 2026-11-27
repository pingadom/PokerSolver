# Coverage audit for preflop trainer feedback

The six-seat source CLI can audit action losses across every prompt with
positive reach under a frozen sampled policy. The audit uses the same
weighted deals and game tree with sampled and exact payoff tables.

A prompt consists of the acting seat, the hero's hole cards, the public
betting history and the legal actions. For each private deal, the traversal
multiplies the deal's prior by the saved policy probabilities on the public
line. These weights sum to the decision reach for each prompt. A 0.001 reach
floor includes prompts reached in at least 0.1% of deals played under that
policy. Each prompt's action values average only the deals compatible with
the shown cards and public history.

The traversal computes continuation utilities for every seat in both payoff
models, once per private-deal tree state. It evaluates a legal action with
zero policy frequency for the EV of choosing that action. Decisions reached
only through zero-frequency actions are outside positive-reach coverage.

A prompt is stable when both models agree on the actions within the chosen
loss threshold of the best action and the worst per-action loss drift is at
most the chosen drift limit. Totals include every audited prompt. Detailed
output includes up to 32 unstable prompts, ranked by reach, with conditional
action losses and acceptable sets.

## Run the audit locally

Use the same private sampled request as the [seed replay audit](six-seat-trainer-action-audit.md).
With the solver and its runtime dependencies on the Java classpath, invoke
the following command.

```text
com.pokerlab.solver.SixSeatRangeSourceMain compare-trainer-coverage INPUT.json NEW-output.json MAX-EXACT-BOARDS MAX-EXACT-HANDS exact|exact-suit-reuse MINIMUM-DECISION-REACH ACCEPTABLE-LOSS-BB MAX-LOSS-DRIFT-BB
```

Use a minimum reach from 0 to 1, inclusive. Both loss limits accept finite
nonnegative big-blind values. The exact work budget must pass before either
side evaluates boards. Keep the output private. Its request contains the
six ranges, and its detailed failure rows contain hero combos.

## Local validation

The controls use the existing 65,536-board sampled sources and the bounded
exact suit-reuse reference in each spot. They use an acceptable action loss
of 0.01 bb and a maximum action loss drift of 0.02 bb.

| Fixture | Minimum reach | Audited prompts | Stable prompts | Changed acceptable sets |
| --- | ---: | ---: | ---: | ---: |
| 36 weighted worlds | 0 | 1,762 | 1,707 | 14 |
| 36 weighted worlds | 0.001 | 56 | 56 | 0 |
| 16 worlds with all seats varying | 0 | 1,328 | 1,326 | 2 |

The seed replay saw 46 prompts in the first fixture. The full audit found
55 unstable prompts among 1,762. Each unstable prompt had reach below
one in 100 million, and all 56 prompts at the 0.1% reach floor passed.
In the all-seats-varying fixture, one failed prompt had 53.3% reach.
The two failures changed the acceptable action sets even though the
maximum loss drift stayed under 0.02 bb across all 1,328 prompts.

A separate positive-reach replay reproduced the total and per-seat prompt
counts. Where failure rows also appeared in the seed replay, every legal
action loss matched within 1e-12 bb. All 45 selected Java cases passed,
including a control with a nonuniform private prior and checks for the
reach floor.

Coverage is limited to the fitted policy and the bounded preflop checkdown
model. The calculation checks the sampled payoffs' effect on feedback for
this policy. It grants neither publication nor grading authority.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`9fa89685cb28dd13d5bdbac26072eaa775bf8acc` and cleared PR creation on
2026-11-27 (Europe/London), with “cleared 9fa8968, 47b79c9, 4533b09”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
