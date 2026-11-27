# Trainer action audit against exact showdowns

The private six-seat range-source CLI can compare the action feedback from
a shared-board Monte Carlo source with an exact showdown reference. It uses
the same weighted six-seat deals, preflop tree and saved policy on both sides.
Only the showdown payoff table changes.

For each deterministic question seed, both trainers replay the policy from
the same deal prior. The audit requires the same acting seat, hero cards,
public action history and legal actions. It groups repeated prompts, keeps
the first seed and occurrence count, and grades each distinct prompt once
per payoff model. The grade conditions on the hero hand and the public
action likelihoods, marginalizing all compatible hidden hands. Future
decisions follow the frozen sampled policy in both evaluations.

For each action, EV loss is the best legal action EV minus that action's EV,
in big blinds. An action is acceptable when its loss is at most the
configured loss threshold. A prompt is stable when it has the same set of
acceptable actions under both payoff models and the largest absolute
per-action loss difference stays within the configured drift limit.

## Run the audit locally

Use a private request with `payoff.method` set to
`SHARED_BOARD_MONTE_CARLO`. The existing request still supplies the sampled
budget and CFR settings. Once the solver and engine have been built and
their runtime dependencies are on the Java classpath, invoke:

```text
com.pokerlab.solver.SixSeatRangeSourceMain compare-trainer INPUT.json NEW-output.json MAX-EXACT-BOARDS MAX-EXACT-HANDS exact|exact-suit-reuse DRAWS FIRST-SEED ACCEPTABLE-LOSS-BB MAX-LOSS-DRIFT-BB
```

The final argument limits the absolute action loss drift. Both loss limits
accept finite nonnegative values. Use 1 to 512 draws from consecutive signed
64-bit seeds. The exact plan must fit its own budget before either side
evaluates boards. Direct exact work enumerates every deal. Suit reuse charges
only the representatives of global suit classes and retains every logical
deal in the exact payoff table. Keep the audit output private. It includes
the source range request, plus each prompt's cards and betting line.

## Local validation

A weighted, 36-world fixture ran with six seats, 100 bb stacks, a single
3 bb raise target, mandatory checkdown and 5% rake capped at 1 bb. A 200
iteration CFR+ solve produced the frozen policy for each sampled budget. Each
row uses question seeds 0 through 511 and payoff seed 711. The loss threshold
was 0.01 bb and the maximum permitted drift was 0.02 bb. The exact reference
covered 36 logical worlds with 14 evaluated representatives and 9,212,112
physical boards.

| Sampled boards per deal | Stable prompts | Changed acceptable sets | Largest loss drift |
| --- | ---: | ---: | ---: |
| 64 | 0 of 48 | 7 | 0.58168 bb |
| 8,192 | 31 of 45 | 1 | 0.04849 bb |
| 65,536 | 46 of 46 | 0 | 0.01483 bb |

The same 512 seeds can reach different prompts across rows because each
sample size trained a different policy. Each comparison uses its own policy
on both sides. The results apply to the tested policy-reached prompts in
this finite game. They establish neither exhaustive game coverage nor an
equilibrium guarantee for full 6-max cash. The audit is validation-only.
It grants neither publication nor grading authority.

Another fixture allowed two hands at each of the six seats, with card
removal leaving 16 weighted legal worlds and eight exact representatives.
Using 65,536 sampled boards per deal and the same thresholds, 25 of 27
prompts were stable. The largest loss drift was 0.01260 bb, and both failed
prompts changed acceptable actions. The sampling budget alone therefore
cannot establish stable feedback across spots.

A separate one-world control exercised the direct exact path at 658,008
boards. The local checks passed 43 Java cases. An independent report check
recomputed action acceptability, maximum drift, stability, seat counts,
request hashes and exact work.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`4533b09ca264e53232eb9bf70d744f2c1e12c4e3` and cleared PR creation on
2026-11-27 (Europe/London), with “cleared 9fa8968, 47b79c9, 4533b09”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
