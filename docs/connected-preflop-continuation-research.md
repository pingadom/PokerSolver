# Connected BTN-versus-BB preflop and postflop research game

`ButtonBigBlindContinuationGame` is PokerLab's first **single CFR tree** that includes both an ordinary preflop open/call decision and its postflop continuation. UTG, HJ, CO and SB are fixed to fold in a six-seat, 100bb, 0.5bb/1bb, no-rake game. BTN chooses fold or open to 3bb; BB chooses fold or call. A call produces a 6.5bb pot with 97bb behind and BB first to act on the flop. Both players can check/bet or call/fold on each subsequent street, using the existing three-street game. Fold payoffs come from the six-seat betting engine; the three-street utility uses the same zero-sum centering for the folded SB's 0.5bb.

Unlike the [range-conditioned handoff](preflop-flop-transition-research.md), the preflop action probabilities here are **learned in the same game tree** as the postflop strategy. The model starts from prior exact-combo ranges. CFR reaches the flop only through the BTN-open/BB-call path, so the policy's reach weights condition the postflop ranges without hand-entered action likelihoods. Each player's information sets include their own cards and the public board/action history, never the opponent's cards. The declared flop and turn candidates form a deliberately restricted chance model; the river is sampled from every remaining card.

The reproducible fixture uses BTN `Ac Ad` at weight 0.25 and `Kh Qh` at weight 1, against BB `Jc Jd` and `As Ks` at weight 1 each. It declares only the `2c 7d Th` flop and `3s`/`4s` turns, with 2bb, 4bb and 8bb bet sizes. Its game hash is `7678c298dce63f40c7d97d52ef027f6453b001025228c8dfccec413284c15ed4`. On one local run, 300 CFR+ iterations took about 11 seconds and gave a **0.012559bb exact-game best-response gap**. The resulting BTN `Kh Qh` open frequency was about 49%; BB `Jc Jd` called about 44%. Those values describe only this synthetic, restricted game. They are not recommendations for real 6-max cash play.

Run the research demo on Windows after compiling:

```powershell
mvn -q -pl solver -am -DskipTests compile
java -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.ButtonBigBlindResearchMain 300
```

The constructor caps `prior BTN combos × prior BB combos × declared flops × declared turns` at 128 to keep this scalar research path bounded. A stable SHA-256 game hash binds the prior ranges, fixed preflop rules, public chance candidates and postflop bet sizes; reordering the same inputs does not change it. Tests cover preflop chip accounting, hidden-card information sets, flop blockers, chance normalization, connected-street solving, mixed preflop frequencies, declining best-response gap and game identity.

This is still **validation-only**. Four seats have exogenous folds, and the candidate flop/turn deck excludes nearly all real runouts. The measured gap is against that abstract game; it says nothing about error from omitted boards, synthetic ranges, missing rake or restricted bet sizes. The next scaling step is to compare representative flop abstractions with a full-deck reference, then expand prior ranges and decision branches within a measured compute budget. A trainer pack must retain the game definition and both convergence and abstraction-error evidence.

The [connected chance audit](connected-chance-abstraction-audit.md) now makes that comparison. The starting menu has more than 1bb of range-weighted forced-check-down bias; a seeded menu search reduces in-sample bias but fails on unseen combos. This rules out promoting either menu to training content despite its abstract-game best-response gap.
