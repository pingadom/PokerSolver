# First bounded river continuation model

PokerLab now solves a **fixed-board, heads-up river betting game** with its own CFR+ implementation. This is the first later-street solver path required by the broader GTO trainer plan. A local [river research drill](../frontend/src/RiverPage.tsx) exercises its saved decisions. The synthetic model is not a reviewed postflop lesson or a valid continuation value for ordinary preflop calls.

The [validation spot](../solver/src/main/java/com/pokerlab/solver/RiverValidationSpot.java) is BB versus BTN at a six-seat table after other players have left the hand. BB acts first, respecting postflop position. The public board is `2c 3d 4h 8s 9c`; the starting pot is 20bb, both players have 80bb remaining, and the only bet size is 10bb. Ranges are three exact synthetic combos per player. There is no rake. The previous street's actions and range origins are deliberately unspecified, so this solution cannot be attached to the preflop demo as if it were a coherent hand.

```text
BB: check ─ BTN: check → showdown
          └ BTN: bet 10 ─ BB: call → showdown
                            └ BB: fold → BTN wins pot
BB: bet 10 ─ BTN: call → showdown
           └ BTN: fold → BB wins pot
```

The game removes board-blocked and mutually blocked hole-card pairs before normalizing their weighted chance probabilities. For each legal pair, showdown is evaluated exactly on the fixed five-card board. It uses a centered, zero-sum chip utility: winning or losing the existing pot without a called bet gives ±10bb, while winning or losing after a called 10bb bet gives ±20bb. A checked showdown can tie at 0bb. This centering subtracts half the starting pot from each player's future-chip-profit view; it changes neither optimal actions nor the best-response gap.

The saved [river validation pack](../solver/src/test/resources/river-validation-pack.json) records the spot hash, full strategy at every exact-combo information set, 3,000 CFR+ iterations and a **0.000956bb** exact-game best-response gap. The loader checks the schema, timestamp, spot hash, legal actions, strategy probabilities and recomputed gap. A separate full-content hash identifies the artifact. Tests independently enumerate pure best responses for this small game and verify that the reported bounds match.

`RiverDecisionEvaluator` computes action EV at any of the four public decision histories. It conditions the hidden opponent range on actions already observed: for example, BTN facing a BB bet sees a posterior weighted by BB's saved betting frequencies. BB's own earlier check is held fixed when grading BB's response to a BTN bet. One-action EV loss compares the chosen action with the best action while later decisions follow the saved strategy. `RiverResearchTrainer` samples reproducible questions over all reachable exact-combo information sets and grades only from the loaded pack.

Manual checks of the committed pack (EVs use the centered convention above):

| Decision | Action EVs | Interpretation inside this model |
| --- | --- | --- |
| BB holds `Th Ts`, acts first | Check −6.6674bb; bet −7.1653bb | Checking saves about 0.50bb against the saved BTN policy. |
| BB holds `6s 7s`, faces BTN's bet | Call −20bb; fold −10bb | Folding is clearly better for this exact air combo. |
| BTN holds `Kc Kd`, faces BB's bet | Call −9.9997bb; fold −10bb | Essentially tied; the 89.4% call frequency is not a stable lesson. |
| BB holds `Ah As`, acts first | Check +16.6681bb; bet +16.6669bb | Near tied in this synthetic game; the displayed 50/50 mix should not be generalized. |

These examples reinforce the product boundary: a small exact-game gap validates the strategy *for this tree and these weights*, while many action EV differences are tiny. The local UI flags near ties rather than treating one mixed action as a categorical mistake.

The API is off by default. For local research, set `TRAINER_RIVER_RESEARCH_ENABLED=true` and `TRAINER_RIVER_RESEARCH_PACK_PATH` to the absolute path of the saved pack, or use the local trainer Compose overlay. It loads the pack once at startup, rejects an oversized file or a gap above 0.05bb, and never solves on a request. `GET /api/v1/trainer/research/river` returns the game metadata; `GET /api/v1/trainer/research/river/questions/{seed}` returns a deterministic question without EVs or opponent cards; `POST /api/v1/trainer/research/river/grade` accepts `{"seed":"42","packHash":"…","action":"k"}` using one of the question's legal action codes (`k` check, `b` bet, `c` call, `f` fold). The server rejects stale packs, illegal actions and client-supplied EVs.

The local website route `#river` shows the public board, current acting seat, exact hero cards and legal actions. Feedback then reveals both action EVs, frequencies and one-action EV loss. It warns when actions are nearly tied and keeps the synthetic-range boundary visible. The drill samples one decision at a time; it does not imply the earlier streets were played according to this pack.

The next research work is to define a plausible river reach state from earlier streets, add alternate bet sizes and rake, then introduce turn and flop chance nodes with measured abstraction/payoff error. Until those links exist, this pack proves a later-street solver and grading contract only. It is not a general GTO river strategy, a mixed-street curriculum or a preflop continuation model.
