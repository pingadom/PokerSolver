# Content review for the focused preflop demo

The [exact diverse validation pack](../solver/src/test/resources/diverse-validation-pack.json) is used for the first playable trainer demo. It is a 100bb, no-rake six-seat table history with only UTG and BTN still active. UTG has committed 22bb and faces BTN's raise to 40bb. UTG can shove or fold. If UTG shoves, BTN can call or fold. The pack contains eight exact UTG combos, seven exact BTN combos and 47 unblocked matchups. It remains `VALIDATION_ONLY` because the ranges and single betting branch are synthetic.

The server recomputes each answer from the saved solution. Folding has −22bb chip EV for UTG: the already committed 22bb is lost. Shoving can win BTN's committed chips when BTN folds; when BTN calls, UTG's share of the full pot is based on exhaustive legal board runouts. The reported EV for a hero combo averages over the possible BTN combos and its saved call/fold strategy. Showing one BTN hand or one simulated showdown would answer a different question.

Manual checks against the committed pack:

| UTG combo | Shove EV | Fold EV | Reading within this model |
| --- | ---: | ---: | --- |
| `Ac Ad` | +60.77bb | −22.00bb | Clear shove. |
| `6h 7h` | −31.90bb | −22.00bb | Clear fold. |
| `Ah Kh` | −21.9995bb | −22.00bb | Essentially tied; the displayed 73.5% shove mix is not a stable lesson. |
| `Ks Qs` | −22.83bb | −22.00bb | Fold by 0.83bb in the saved assumptions; this preference reverses under some ±25% range-weight changes. |

The pack's best-response gap is about 0.000149bb and exact payoffs have zero board-sampling error. Those measures support consistency *inside the stated game*. They do not establish that the ranges, bet sizes or game branch represent ordinary cash poker. [The range-sensitivity probe](preflop-range-sensitivity.md) shows `Ah Kh` moving from 24% to 98% shove frequency and `Ks Qs` changing preferred action under one-at-a-time weight perturbations. The interface therefore presents EVs as model results, describes close decisions cautiously, and never calls a low-frequency action inherently wrong.

Before promoting this content beyond a validation demo, a poker reviewer must inspect the action history, range provenance, legal alternatives and realistic rake/cap assumptions. A broader preflop solution also needs postflop continuation values for calls and small raises. The eight included combos cannot support a full 13×13 strategy chart.
