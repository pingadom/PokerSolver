# BTN half-pot response coverage

## Declaration before generation

Attempt all four supplied BTN hands, in sorted source order: 2c 3c, Ah Kh,
As Qs and Qh Th. Each faces BB's already modeled half-pot bet after `|BB:call`
on 6d Qc Td. Attempt fold and call exactly once for each hand. No other bet,
board, history, range or loss limit is searched. The complete joint game retains
all 24 worlds, twelve dealt blockers, four preflop boundaries, BB check/quarter/
half-pot choices and BTN's check/pot-sized choice after BB checks.

The reservation for one generation or complete replay is one physical game,
one shared parent study/two fresh CFR+ references (80,000 and 160,000 iterations),
and eight conditional endpoint attempts on the same immutable tree. Each endpoint
has its own parent audit: at most nine parent solve calls/eighteen parent LPs and
thirty-two interval LPs. References are shared, with 240,000 fresh iterations in
total. Exactly one generation, one local validation replay and one replay per
CI run are reserved. Pure negative controls and cache construction add no solves.

Keep the original work and numerical limits: 1,609 states for this game,
129 sequences/64 information sets per actor, 8,000,000 reduction-work units,
the existing LP limits and tolerances, and 8 MiB stored/expanded report caps.
Physical work remains 15,792,192 full preflop boards/31,584,384 hand evaluations
plus 63,936 selected-flop runouts/127,872 evaluations.

Retain typed endpoint failures and every hand. A successful utility remains
visible even if its other action fails. Incomplete menus have no loss bounds,
classifications, policy qualification or drill capability. No retry, fallback,
threshold change, world pruning or replacement question is allowed.

Reuse the existing utility/endpoint records, conservative loss calculation,
classification and qualification gates from the quarter-pot study. Old sources
and all 364 previous data files stay unchanged. New report/request schemas and
private half-pot capabilities prevent confusing these contexts with quarter-pot
results. Serialized flags do not confer authority.

Qualification keeps 0.001 bb global/local quality, 0.0001 question reach,
0.01 bb selected regret and move loss, 1e-8 security slack and original certificate
tolerances. It requires both a robust within-limit move and a robust outside-limit
move, plus every original primary/reference policy gate. BB's preflop call and
size-specific half-pot likelihood remain in actual and counterfactual posteriors.

Public context must follow the owned edge: BB bets 3.25 bb, the pot becomes
9.75 bb and BTN has 3.25 bb to call. Hero has committed 3 bb; BB has committed
6.25 bb. All six public seats remain visible and no private opponent cards are
published in a drill. This is two-active-player research, not general six-player
GTO; public trainer admission remains false. API/UI integration is separate work.

Independent verification reconstructs the complete physical parent, all successful
LP certificates/posteriors, forced action values, policy assessments, gate reasons
and the complete case inventory without optimization or training. Rejected
numerical attempts remain diagnostics, not claims of infeasibility.

## Results

The one declared generation completed in about 80 seconds. All eight fold/call
attempts returned `INSUFFICIENT_REACH`. Each carries an original minimum-reach LP
certificate and a security-valid opponent realization with zero question reach.
The conservative lower bounds are approximately -1e-8 after the original safety
padding; the behavioral reach itself is zero. These are distinct quantities.

Independent verification checks eight negative-reach certificates using exact
arithmetic on the original binary64 coefficients, all 48 unnormalized world
masses, affine/behavioral realization agreement and full-parent best responses.
It retains BB's preflop call and half-pot action likelihoods. The same physical
parent, both parent LPs and all three parent policies are checked independently.
No optimizer or new CFR run is used by that checker.

A zero-reach counterexample means the full declared strategy set cannot support
the required positive reach for these conditional questions. It does not mean
every opponent avoids a half-pot bet, that half-pot betting is impossible, or
that either response has zero EV. Conditioning a zero-mass witness would invent
a posterior. The report therefore contains no conditional utility/loss grades,
no per-question policy qualification, and no half-pot trainer capabilities.
The primary and two shared fresh policies remain in the complete parent report.

The existing quarter-pot source still qualifies all four hands and remains
available in the current trainer. This half-pot result is a coverage boundary
with independently checked counterexamples, not an additional playable pack.

## Reproduction

`SixMaxSizeMenuHalfBtnMain solve request report.json.gz` attempts the complete
four-hand study. `replay request report.json.gz` performs one complete owned
physical replay and requires exact report equality. Outputs must be new and
requests use strict JSON. `scripts/check-size-menu-half-btn.py` verifies the
published rejection evidence without optimization or training. Its fixed-study
contract rejects invented positive outcomes, discarded failures or copied
quarter-pot grades. Local Java tests reserve one full replay; corruption tests
and context/provenance checks add no numerical solves.
