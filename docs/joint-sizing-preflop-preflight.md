# Preflop continuation planning in the joint sizing game

## Declared scope

Inventory every preflop information set and every legal move in the existing
jointly solved size-menu request. Keep its complete supplied joint worlds,
folded-card blockers, staged raises, physical flop support and all flop sizes.
Do not select hands or actions based on an EV result.

This milestone is structural preflight only. Reconstruct a payoff-free tree,
collect each hero's distinct future information sets after each forced move,
and calculate the exact Cartesian continuation-plan product used by the current
interval compiler. Compare that product with its unchanged 64-plan limit.
Keep over-limit moves in the inventory. Plans count one action per information
set across all compatible hidden worlds, including off-policy branches; they
must not choose actions separately for each hidden deal.

Planning and replay enumerate zero poker boards, evaluate zero hands, run zero
LPs and train zero CFR iterations. Measured physical work is an estimate of a
future solve, distinct from work performed by this planner. No numerical
generation or physical replay is reserved or required for this milestone.
Passing this one structural limit does not establish reach, compiler capacity,
numerical feasibility, quality, EV bounds or trainer qualification.

The report binds the complete canonical source request, publishes all preflop
public contexts and retains each question's supporting original world indices.
The displayed prior chance mass is not an on-policy or counterfactual question
reach. No strategy is used. Public context contains the hero's cards and six
public seats; opponent and folded private cards remain outside that context.
The research source request still contains its explicitly supplied private worlds.

Use strict bounded JSON, exclusive new output files, immutable reports and a
private constructed result. Replay rebuilds the entire structural report and
requires exact equality. Parsed flags cannot authorize grading. All old source
data, numerical modes, limits, trainer protocols and preview assets stay unchanged.

An independent Python checker will build the fixed supplied tree using literal
preflop commitments and flop transitions, without calling Java's betting engine,
reading solved strategies or importing its numerical checkers. It will verify
all source worlds, public prices, menus, information-set aggregation, exact plan
products, classifications, complete inventories and zero-work declarations.

## Results

The complete payoff-free tree has 1,609 states and the same 89 information sets
as the existing joint game. It contains 17 preflop questions and 48 moves. All
24 original physical worlds remain in the preflop inventory. The independent
checker reproduces every public commitment, additional-chip action price,
continuation menu and exact plan product.

Forty-one moves fit the 64-plan bound. Seven do not: the root BB raise for each
of its three hands requires 1,296 Cartesian plans, and BTN's first re-raise for
each of its four hands requires 192. These counts include distinct future
information sets across mutually exclusive hero branches, exactly as the current
compiler enumerates them. They are representation costs, not a claim that poker
requires that many strategically distinct plans or that the decisions have no EV.

Ten questions have complete menus within this one bound. The other seven retain
all actions and their rejection status. `Result.requireCompleteMenuWithinPlanLimit`
rejects those incomplete menus before a consumer starts physical or numerical
work. An individually cheap fold/call cannot authorize the whole menu. Passing
this guard does not certify conditional reach or any solver/educational gate.

The planner performs zero board enumerations, hand evaluations, LP solves or CFR
iterations. Its estimated physical budget describes a future solve; it is not
performed work. Chance-only private-hand mass is explicitly distinguished from
question reach, which cannot be measured without a policy. No new strategy,
conditional utility, grade or playable question is published.

## Reproduction and validation

After the usual Java 21/Maven build, run
`com.pokerlab.solver.SixMaxSizeMenuPreflopPlanMain` on the solver runtime classpath:

```text
plan docs/data/supplied-flop-size-menu-request.json new-plan.json[.gz]
replay docs/data/supplied-flop-size-menu-request.json docs/data/supplied-size-menu-preflop-plan.json
```

Outputs are exclusive and input/output aliases reject. Replay is structural;
it does not repeat the earlier expensive physical source study. The independent
checker is `scripts/check-size-menu-preflop-plan.py`; its pinned receipt is
`docs/data/supplied-size-menu-preflop-plan-independent-check.json`.

Eleven Java tests cover complete replay, agreement with published joint-game
menus, all over-limit paths, complete-menu guards, hidden-world aggregation,
off-policy branches, prices/history/privacy, weighted chance masses, changed
folded-card blockers, immutable ownership, strict readers, gzip and exclusive
outputs. Six Python test methods corrupt inventories, future decisions, prices,
plan products, work, scope and admission. No numerical source generation or
physical replay is hidden in these tests.

## Next solver work

Use this inventory to design a separately declared preflop interval study. Keep
the seven over-limit moves recorded; changing their menu or silently raising the
cap would change the experiment. A new representation that avoids enumerating
unreachable hero branches would need independent payoff/continuation equivalence
tests and measured work limits, and may still exceed the original cap. The ten
structurally complete menus also need every original reach, numerical, policy
and educational check before they can become trainer content.

This remains two active players at six public seats, with supplied tiny joint
ranges, one selected physical flop and checkdown elsewhere. Broader preflop
coverage, realistic cash ranges/rake, later streets and six-active-player
equilibrium remain separate work. The existing trainers stay stable and AWS
deployment remains paused.
