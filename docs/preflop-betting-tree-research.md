# Bounded six-seat preflop betting rules

`SixMaxPreflopBetting` is the next solver foundation after the forced-shove fixtures. It advances all six seats in UTG, HJ, CO, BTN, SB, BB order, starting from posted 0.5bb/1bb blinds. A configured menu limits raise-to amounts; the reference 100bb game offers 3, 10, 22, 40 and 100bb. The engine enforces turn order, total commitment, minimum full raises, no action after folding, and the big blind's option to check when no one raises. A raise reopens action for every other live seat. All chip amounts use integer millionths of a big blind internally.

Each immutable state exposes committed chips, live seats, pot, amount to call, legal actions and public history. It distinguishes three outcomes:

| Outcome | What the model knows |
| --- | --- |
| Everyone else folds | The remaining seat wins the pot, including folded blind money; literal net chip profit is available. |
| All live seats commit 100bb | The preflop betting round ends at an all-in showdown. An equity/payoff model is still needed to value it. |
| Two or more live seats finish below 100bb | The state is `POSTFLOP_CONTINUATION_REQUIRED`. It has no invented terminal EV. |

The existing UTG-versus-BTN five-bet spot now replays its action history through these rules at construction. It still has the same content hash, saved solution and restricted shove/fold continuation. The stronger validation rejects histories that the earlier contribution-only check could accept, such as swapping the HJ and CO actions or making a below-minimum re-raise. The reference history leaves UTG facing an 18bb call in a 63.5bb pot. If UTG shoves and BTN folds, UTG's net win is 41.5bb; if BTN calls, the final pot is 201.5bb. Those amounts match [ADR-004](decisions/ADR-004-preflop-validation-game.md).

This is **not a new solved six-max strategy**. The rule tree does not yet assign starting ranges by position, rake, unequal stacks, side pots, or a postflop continuation value to call/check branches. The next solver step is to define a defensible preflop-to-postflop transition with range and action conditioning, then benchmark its size and error before adding ordinary 100bb opens or calls to training packs. The current postflop validation packs use synthetic ranges assigned at their starting street, so they cannot be plugged into these branches without that work.

Run `mvn -pl solver -am test` for the betting-order, pot-accounting, terminal-boundary and saved-pack regression tests. The full solver suite exercises all committed validation packs after the stricter history check.
