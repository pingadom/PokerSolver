# Direct root minimax lower endpoint

## Scope declared before implementation

Implement a separate, research-only calculation of the minimum, over the complete
opponent security face, of the hero's optimal continuation value after one forced
root action. The question must precede every player action; only original chance
events can determine its reach. Use the existing exact reduced continuation-plan
compiler, original sequence projections and complete global security constraints.

Use one epigraph LP for this minimum. Do not perform an upper-endpoint LP for
each continuation and do not introduce fractional normalization when the root
denominator is constant. Keep the original 64-plan, eight-million compiler,
10,000-pivot, four-billion arithmetic and 512-row/column LP limits. The owned
ascending refined parent retains its separate original limits. The endpoint uses
the existing explicit all-phase refined LP representation, without retry/fallback.

The result is a bracket around the *minimum value*, not the full action EV
interval. Its upper bracket does not bound the action against every opponent
strategy. Return the original LP matrix/RHS/objective, primal/dual certificate,
original-flow and behavioral best-response checks, reduced plan counts, chance
support and charged work. An opaque owned result carries no grading authority.

This milestone permits analytic control games only. Controls cover both hero
orientations, dominated actions, matching pennies, shared hidden information,
negative and positive utilities, non-unit chance reach, self-unreachable plan
choices, independent opponent branches, root/reach/plan/work rejection and
immutability. Compare small controls with the existing complete root/conditional
interval implementations and independent closed-form or exhaustive calculations.
No physical poker solve, new source report, hand/range/menu/board/policy search,
source rewrite, cache update, API or UI change is authorized by this declaration.

One full PR CI validation retains every existing physical replay reservation.
New tests run analytic games only. Preserve all 382 existing numerical artifacts,
the old failed exact raise, the unresolved public raise feedback, existing gates
and frozen previews. AWS deployment remains paused. A later physical BB99 study
requires its own declaration and measured result; no speedup or tighter physical
raise bound is claimed from this analytic implementation.

Retain five fixed analytic captures for independent checking: dominated actions
and shared matching-pennies continuations for each hero orientation, plus a
three-reduced-plan continuation control. Their parent and endpoint certificates
must be checked against independently reconstructed original matrices, with exact
binary64 rational arithmetic. Java must replay the complete saved audits. The
capture adds no physical game or poker-source generation. It is separate from
all 382 existing numerical study artifacts.

## Analytic validation

The controls distinguish `min_opponent max_continuation` from minimizing each
hero plan separately. In matching pennies with a safe outside option, each pure
continuation can lose 1, while the minimum optimal continuation value is 0 at a
shared half-and-half opponent mixture. The full action's upper endpoint is +1;
the new minimum bracket near 0 is therefore explicitly unsuitable as that upper
endpoint. Both hero orientations are checked.

A continuation with four Cartesian maps reduces to three own-reachable plans,
retaining every opponent action and a closed-form minimum of -8/3. Independent
opponent branches retain their product of hero choices. The original plan cap
accepts 64 reduced plans and rejects 65 without increasing the 16-action limit.

The saved five-control fixture includes both original parent witnesses, the
original epigraph problem and its witness. The Python checker reconstructs the
literal trees' payoffs, flow projections, complete security faces, reduced-plan
coefficients, epigraph matrices, posterior weights and behavioral best responses.
It checks ten parent certificates and five endpoint certificates using exact
rational arithmetic on the original binary64 values. It performs no LP solve,
CFR run or physical poker evaluation. Java replays each complete saved audit.
Corruption controls cover false scope/admission, modified games, parent values,
matrices, objectives, primal/dual points, missing plans, flow, reach, bounds and work.

```powershell
mvn -pl solver -am test "-Dtest=RootMinimaxTest,FiniteTwoPlayerRootActionIntervalsTest,FiniteTwoPlayerConditionalActionIntervalsTest,ReducedHeroContinuationsTest" "-Dsurefire.failIfNoSpecifiedTests=false"
python scripts/check-root-minimax.py
python -m unittest discover -s scripts -p test_check_root_minimax.py
```

The new kernel exposes `solve(game, rootQuestion)` and a research audit with an
owned result. It has no physical study CLI, cache, grading capability or public
trainer route. The current preflop trainer continues using its existing pinned
conservative bounds, including the unresolved raise. A physical source study
and reference-policy assessment remain separate future work.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`8aa7ebeaf89795100679d27ba0b8c40fbae53868` and cleared PR creation on
2026-11-06 (Europe/London), with “cleared 8aa7ebe”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
