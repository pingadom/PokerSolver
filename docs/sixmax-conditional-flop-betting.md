# Conditional flop betting from a six-seat policy

`SixMaxHeadsUpFlopGame` adds a solved betting continuation to the [policy-derived six-seat handoff](sixmax-policy-flop-handoff.md). It consumes the actual joint six-hand posterior after a completed heads-up preflop history and a known flop. It does not reconstruct independent marginal ranges or return folded cards to the deck.

This is a bounded conditional research game: either player can check or make one configured flop bet, and a bet can be called or folded to. There are no flop raises. A called bet or two checks lead to mandatory turn/river checkdown. A bet may consume the full remaining stack, but cannot exceed either live stack. Rake and multiway flop betting remain unsupported. The source preflop strategy is frozen; it was solved with checkdown continuation and has not been re-solved for this new betting game.

## Chance, information and chip accounting

The initial chance node uses the correlated deals supplied by `FlopState`. Opponent and folded-seat holdings affect terminal payoffs, but never enter an acting player's information set. A decision key contains the public preflop history, flop, positions, pot, stack and bet size, that player's own combo, and public flop actions. Deals with the same own hand and public observations share a decision even if other hands differ. A blocked surviving-hand pair remains absent rather than being recreated by multiplying marginal ranges.

For each compatible six-hand deal, the payoff table enumerates all `C(37,2) = 666` unordered remaining boards. All twelve hole cards and the flop stay removed. Because there are no later betting decisions, integrating turn/river chance into terminal expected utility is exact for this declared model. Payoffs are prepared once; CFR iterations do not repeat hand evaluation. With a called bet `b`, the showdown pot becomes `P + 2b`, and each live player pays another `b`. A folded-to bet is returned to its bettor, so it adds no net contribution to the settled result. Folded-seat preflop losses remain in every six-seat utility vector, whose sum is zero.

The two live players' actual chip utilities sum to the folded seats' preflop commitments `F`. To use the existing zero-sum CFR interface, the game subtracts `F/2` from each live player's utility. Their centered utilities are opposites, and this constant shift changes no action preference or best-response gap. Reports and decision EVs restore the constant and show actual chip utilities from the start of the hand. The `bestResponse` report's profile/bound values remain centered; its `gap` is invariant to this shift.

`SixMaxHeadsUpFlopDecisionEvaluator` conditions on the requested player's hand and public betting history. Opponent action probabilities update the joint posterior. The player's own prior moves are forced, allowing an off-policy branch to be inspected without using its zero strategy frequency to discard it. Conditioning uses log weights, so a rare private deal followed by a rare opponent action can still yield valid conditional EVs when their direct product underflows. Missing strategies, invalid probabilities, impossible histories, terminal requests and zero opponent reach are rejected. There is no implicit uniform-policy fallback.

## Reproducible solve audit

`SixMaxFlopContinuationAudit` reuses the saved-policy reach audit to select the most probable heads-up preflop histories. Each receives one seeded physical flop, a fresh exhaustive CFR+ solve, exact information-set best responses, conditional first-player action EVs/frequencies, and six-seat values compared with exact checkdown. The solve has no sampled payoff error. An empirical source remains labelled `EMPIRICAL_JOINT_DEALS`; enumerating its remaining boards does not make its private support exact.

The [committed export](data/sixmax-conditional-flop-betting.json) binds source pack hash `2ce2adc9a9cb69179e36f6de3d223b40542d9588da61c58ff66fc9d241b57dcf`, source spot hash and the source's `MANDATORY_CHECKDOWN` rule. It stores all ten conditional strategies, solver budgets, public game settings, quality bounds, root decisions and baseline comparisons. It is an audit artifact with `VALIDATION_ONLY` status, not a trainer-admitted solution pack. The CLI caps its source file at 16 MiB, limits each solve to 100,000 iterations, sorts output maps, and refuses to overwrite the source. A source with no reached heads-up flop histories cannot be reported as a zero-gap solve.

With seed 711, 5,000 CFR+ iterations and a half-pot bet capped at the remaining stack:

| First / second seat | Flop | Pot / bet (bb) | Joint deals | Conditional gap (bb) | First player's change from checkdown (bb) |
| --- | --- | ---: | ---: | ---: | ---: |
| BB / UTG | 3c 7s 9d | 2.5 / 1.25 | 2 | 0.000000069 | +0.259009 |
| BB / UTG | 5s 8c Qc | 6.5 / 3.25 | 1 | 0.000000631 | -0.849100 |
| SB / UTG | 2s 3c 5s | 7 / 3.5 | 1 | 0.000000707 | -0.740992 |
| UTG / CO | 7d 9d Jc | 7.5 / 3.75 | 2 | 0.000000174 | +0.889640 |

The largest gap across the ten conditional games is about **0.000000707 bb**. Their first-player values change by as much as about **0.89 bb** from checkdown. These are tiny synthetic games with one or two posterior deals and four or six information sets. Several effectively have a single possible opponent holding. The results demonstrate the continuation implementation and the effect of betting; they are not representative cash-game strategy results. Ten sampled flops do not average over all 9,880 possible flops per six-hand deal, measure the error of a board abstraction, or certify the source six-player strategy under changed continuation values.

Run from the repository root in PowerShell:

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxFlopContinuationAuditMain' '-Dexec.args=solver/src/test/resources/six-seat-full-round-pack.json docs/data/sixmax-conditional-flop-betting.json 711 5000 0.5'
mvn -q -pl solver -am spotless:check verify
```

Tests cover all terminal betting paths against an unbeatable flopped-royal-flush control, six-seat chip conservation, constant-sum centering, exact checkdown equivalence, physical folded-card removal, correlated live-hand support, private information-set grouping, deterministic CFR+, exact best-response bounds, conditional opponent-action EVs for both players, forced own actions, rare-history underflow, invalid policies and inputs, stack-sized bets, real saved-policy replay and byte-reproducible CLI output with source preservation.

The [conditional turn/river continuation](sixmax-conditional-turn-river-betting.md) now extends this correlated chance model through public flop actions, a shown turn, two connected betting rounds and all 36 physical rivers per compatible deal. It carries called contributions and caps bets at remaining stacks, including all-in runouts. These are still separate conditional solves: the next integration step is to re-solve the flop with the later-street values in a single tree, then connect those values back into a re-solved six-seat preflop policy with broad flop coverage and a measured chance/abstraction method. More realistic ranges, broader action menus and separate multiway/rake validation remain publication gates.
