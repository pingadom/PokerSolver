# Broader physical-board capacity: design and admission gates

This is the next model design, not an implemented increase to the current solver's limits. The current history-specific model remains capped at 600 literal history/board revelations, 2,000 observations, one million complete states, 8MiB payoff tables, 64MiB policies and 32MiB reports. Its artifact identities and trainer admission rules remain unchanged. AWS deployment remains paused.

## Why accuracy is no longer the only blocker

With six private hands removed, each hidden world has `C(40,3) = 9,880` possible flops. A literal board has conditional probability at most `1/9,880`, including after averaging over the full private posterior. For `R` history/board revelations, their reach divided by all heads-up reach is therefore at most `R/9,880`. Each selected history's reach is bounded by the all-heads-up denominator.

The current 600-revelation ceiling is at most 6.072874% coverage. The unchanged trainer target is **25% retained heads-up coverage**, after every quality, materiality and decision-stability gate. It needs at least **2,470 revelations even under the optimistic bound**. That is a necessary condition, not a feasible menu size: blockers, seat diversity, weak private ranges and unstable decisions can require substantially more. A passing sample of 32 cases cannot stand in for an exhaustive retained-coverage measurement.

The owned bounded maxmin solver addresses local convergence for two active players. It does not enlarge board support, introduce realistic ranges or solve a general six-player cash game. Independent action-EV screening remains necessary even when root NashConv is effectively zero.

## Proposed sparse observation model

The current global observation palette is reused across all selected histories and private worlds. A board revealed at one history creates zero-count palette entries elsewhere. The broader model should store a **separate observation support for each public history**, retaining the complete joint private-world distribution.

1. Keep the original rank/texture observations as each history's fallback support.
2. Assign each literal revelation a stable `(public-history, physical-board)` identity. Index it only in that history's support. Store nonzero `(world, observation)` counts and exact active-pair payouts; absent entries mean zero chance, never an estimated payoff.
3. Subtract literal counts and payouts from the corresponding history/world rank complement. Reject negative counts or payouts. Summing the literals and complements must exactly recover every original rank-table marginal.
4. Enumerate only supported chance outcomes, in a declared canonical order. The complete-state count must count actual supported states rather than a rectangular upper approximation. Reports must distinguish supported, reached and material cases.
5. Bind source, full private-world support, parent table, per-history support, exact payouts and chance order into a **new model identity**. Do not reuse old namespaces, checkpoint schemas or solution hashes.

Information sets must still contain only the acting player's cards, public history and revealed observation. Sparse indexes must not expose the underlying joint world. There is no automatic suit canonicalization: the present ranges are not invariant under arbitrary suit permutations. Any later symmetry compression needs a proved source-range/private-world automorphism and a replayable mapping.

## Capacity proposal to benchmark

These are proposed experiment ceilings, not approved runtime settings or promises of capacity:

| Dimension | First broader experiment | Evidence required before adoption |
| --- | ---: | --- |
| Selected heads-up public histories | Existing six | Legal, reachable histories; multiple seat pairs represented |
| Joint private worlds | Existing twelve | Full correlated support; no marginal-product substitution |
| Literal history/board revelations | Up to 8,192 total | Measured eligible and retained coverage against all heads-up reach |
| Exact payoff work | At most `R × W × 666` runouts | Actual blocker-aware enumeration counts, elapsed time and payout conservation |
| Complete supported states | Measure before choosing a new cap | Structural count agrees with independent traversal |
| Policy/payoff/report bytes | Measure before choosing new byte caps | Decompressed size checked before write and during bounded read |
| Heap | Existing 2GiB replay budget | Peak live heap and allocation measurements on full replay |
| CI | Existing 35-minute backend job | Full exact-head suite passes with practical runner headroom |

At 8,192 revelations and twelve worlds, the deliberately loose enumeration bound is 65,470,464 runouts. It is a work estimate, not a measured runtime. The optimistic coverage bound is about 82.9%; materiality and stability still determine actual admission. If the sparse experiment cannot fit the existing heap and CI budgets, choose a smaller declared experiment or design a separate offline evidence workflow with explicit checks. Do not quietly relax caps or remove old replay tests.

## Runtime and evidence design

Snapshot immutable local game topology once. Cache structural action/own-history metadata independently of policy. Cache scalar terminal utilities only under a complete payoff/model identity, without changing chance order or summation semantics. The existing owned maxmin reduction is limited to 64 pure plans per active player, 1,000 tree nodes, depth 32, 4,096,000 profile node visits and 10,000 simplex pivots. More private combos can exceed these plan limits exponentially; that needs a separately validated sequence-form solver rather than a raised normal-form cap.

Full diagnostics currently duplicate before/after conditional reports. A new compact schema could store immutable support and predecessor hashes once, plus changed-case evidence and independently reproduced summaries. Replay must still reconstruct **every** reached conditional case and the full parent best responses. Omitting unchanged rows from disk must never mean omitting their checks. Old reports remain fully replayable.

Persist only exact generated or exactly replayed payoff tables. A raw JSON record must not construct an opaque validated solver result. Rejected optimization/admission attempts retain their diagnostic report and export no trainer policy. Declare CFR iterations, LP pivots, matrix profiles, traversal visits and independent reference budgets separately.

## Implementation sequence

1. **Sparse support prototype and conservation tests.** Begin with the present 530-board menu. Demonstrate identical literal/fallback counts, payouts, hidden-world posteriors and utilities; record supported-state and byte savings. Preserve the existing model as a compatibility control.
2. **Capacity sweep.** Try declared menus at 1,024, 2,048, 4,096 and then 8,192 revelations only where preceding benchmarks fit. Persist the actual selection and counter evidence, not extrapolated quality or coverage. Keep public-history/seat-pair coverage visible.
3. **Fresh joint studies in the new game.** Rebuild complete information-set support and train fresh policies. A transferred strategy can be a named initialization experiment, not a newly trained checkpoint or admission certificate.
4. **Local quality and action-EV evidence.** Repair eligible bounded games with the owned solver; use independently solved references, fixed-question-posterior EV comparisons, cross-mixture regret and posterior-distance checks. All retained cases need actual evidence. A sampled screen grants credit only to its passing sample.
5. **Trainer admission and API integration.** Require the existing parent/local/materiality/stability and 25% retained-coverage gates, measured over all heads-up reach. Build an opaque admitted pack and verify hidden-card boundaries, deterministic question replay and grade EVs before connecting it to the table UI.

The first achievable deliverable is the sparse 530-board compatibility prototype with measured resource savings. Broader ranges, rake, multiple flop bet sizes, turn/river decisions and true multiway equilibrium remain separate model milestones.
