# Physical BB99 raise minimum

## Declaration before numerical generation

Use the original qualified root request and pinned report only. Keep BB 9d 9h,
the fixed public-history prefix, every joint world/blocker, selected physical
flop and residual, all five preflop raise targets and the original postflop menu.
Attempt only raise-to-9 once with the direct root minimax kernel. No hand, range,
history, board, action, policy, tolerance, representation or work-limit search,
retry or fallback is permitted after the result is observed.

The previous qualified report is canonical hash
`cc20afe1dee2584956fba058a4cbac5e421886b6bc98e91ff2c602666173a658`, with stored
SHA-256 `e3bf6895b71c24dd5202d09e402ee413d9d40d35cce660d9d5edfce5e2d1823d`.
Capture its bounded bytes before parsing and verify both pins. Rebuild one owned
physical game and parent, including the two fresh 80k/160k references. Require
the entire parent report to reproduce the pinned source. The new minimum's
parent audit and both parent certificates must match that same owned parent.

Reserve one generation, one complete local validation replay shared by the new
study test class, and one new replay per full CI run. Each uses one physical game,
two parent solve calls/four parent LPs, at most one endpoint LP, and two fresh
references/240k iterations. Preserve the original 8m compiler, 10k pivot, 4b LP
arithmetic, 64 reduced-plan and 8 MiB stored/expanded caps. The known original
raise continuation has 1,296 Cartesian maps and 52 reduced plans; keep them all.
No native API startup or new browser source replay is reserved.

Retain either the owned minimum audit or a typed numerical failure. A bracket
around the minimum is not the full action EV interval. Do not use its upper
bracket as an all-opponent-strategy ceiling. Assess the owned primary policy and
both fresh references with the unchanged whole-parent/public-root .001 quality,
.0001 reach, .01 selected-regret/loss and 1e-8 face/numerical gates. For an included
policy, its forced raise value must be above the new minimum's lower bound; it
need not be below the upper bracket on that minimum.

Save separate request/report/check artifacts. An independent checker must verify
the original source, parent, physical tree, original epigraph coefficients and
primal/dual certificate, full face, reach/posterior, reduced-plan coverage,
behavioral best responses and policy-assessment result without LP/CFR optimization.
Its independent pure-plan coverage oracle is bounded to the original 1,296 maps;
the production compiler still retains its 64 reduced-plan cap.
Preserve every previous numerical artifact, the old exact raise work failure,
the existing trainer's pinned bounds/feedback, numerical kernels and work limits.
This study grants no grading capability, cache/API/UI update or six-player GTO
claim. AWS deployment remains paused.


## Result of the declared run

The single physical generation succeeded with the original inputs, kernels and
caps. No generation retry, fallback, limit increase or replacement scenario was
used. The saved report is `docs/data/supplied-root-minimum-report.json.gz`;
its request and independent check are adjacent files with the same prefix.

| Terminal-profit quantity | bb |
| --- | ---: |
| Previous conservative raise lower bound | -6.359434212217635 |
| New minimum lower bracket | 0.21114181882276112 |
| Attained minimum witness | 0.2111418288227532 |
| New minimum upper bracket | 0.2111418388227532 |
| Lower-bound improvement | 6.570576031040396 |

These are terminal-profit values. The existing trainer adds the already committed
1 bb when displaying the value from the current decision. This study does not
change that trainer, its pinned source, feedback, classifications or HTTP routes.
The upper bracket encloses the *minimum*, not every opponent policy's raise value.
For example, the primary policy's forced-raise value is 0.78577920025289 bb, above
that bracket, and correctly passes the lower-bound check.

All 52 reduced continuation plans remain present, covering the original 1,296
Cartesian maps and eight BB99 worlds. The endpoint required one LP, 263 pivots,
2,045,249,458 arithmetic units and 1,556,197 compiler units. The original 10,000
pivot, four-billion arithmetic and eight-million compiler ceilings were retained.
The stored report is 71,503 bytes; expanded it is 922,071 bytes.

The original whole parent and both fresh reference policies reproduce the pinned
qualified source. Forced-raise utilities for primary, 80k and 160k policies are
0.78577920025289, 0.2111418297145661 and 0.21114182904570566 bb respectively.
All three retain the original whole-parent/public-root quality, face, reach and
regret gates and have zero minimum lower-bound violation.

## Verification and limits

`RootMinimumStudyTest` shares one complete physical replay across its tests;
that replay compares the entire saved report and parent, including both fresh
reference policies. The independent Python checker rebuilds the original physical
tree and epigraph, covers all continuation maps, checks the primal and dual with
exact binary64 rational arithmetic, and verifies the witness through behavioral
best responses. It performs no LP optimization or CFR training. Its corruption
controls exercise source identity, scopes, parent face, support, plans, matrix,
certificate, minimum bracket, work totals and policy outcomes.

Run the bounded replay with Java 21 and the project's Maven dependencies:

```text
mvn -pl solver -Dtest=RootMinimumStudyTest test
python -m unittest discover -s scripts -p test_check_root_minimum.py
```

The command entry point is `com.pokerlab.solver.SixMaxRootMinimumMain`, with
`solve|replay request pinned-source report`. Generation refuses an existing output;
source bytes and canonical identity are pinned before constructing a physical
game. Replays require complete report equality. A typed numerical failure publishes
no minimum, comparison or support claim. Parsed research evidence cannot construct
an owned result or grant trainer admission.

This is an improvement to one original two-active-player preflop root at a six-seat
table. It is not a six-active-player solution or a complete action-value interval.
The older exact interval's raise `LP:WORK_LIMIT` remains intact. A later milestone
may evaluate whether this checked lower bound can improve a separately qualified
trainer envelope; that requires its own declared scope, complete-menu loss bounds,
policy checks and admission tests. AWS deployment remains paused.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`cc53f13ce75c053c298fe32c5a0d3fb67663cd2d` and cleared PR creation on
2026-11-14 (Europe/London), with “cleared cc53f13”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
