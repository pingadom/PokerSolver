# Supplied-joint preflop connected to flop betting

PokerLab now re-solves the supplied 24-world preflop game with strategic betting on one selected physical flop. Both players can check or bet, and the player facing the bet can fold or call. There is at most one bet in that flop round. Turn and river check down exactly; all unselected flops also check down exactly. This is a bounded two-player research game at a six-seat table, with no rake. It is not a general six-player cash equilibrium or a new public trainer pack.

The [request](data/supplied-four-button-hand-connected-flop-request.json) retains all four BTN hands, three BB hands and two folded-HJ variants from the earlier [reach example](sixmax-question-reach-bounds.md). The selected flop is `2s 8s Ts`, and the bet is half the current pot, rounded to a millionth of a big blind and capped at the remaining effective stack. Calling preflop to 3, 9, 22 or 50bb creates four distinct continuations. Each retains its actual pot, commitments, remaining stacks and full public history. Postflop seat order starts with SB/BB where live; it does not follow seat enum order.

## Physical handoff and chance

`SixMaxSuppliedFlopHandoff` captures an owned, immutable boundary from the supplied game. It retains all six physical hands and each original deal index. Folded hands remain blockers; worlds that collide with the selected flop are excluded only from that flop's support. Original private chance at the connected parent remains unchanged. An all-blocked flop, an unfinished decision, a fold win or an all-in boundary cannot create this handoff.

The physical flop probability is `1 / C(40,3) = 1/9880` for every compatible world. The parent draws its private world once, plays preflop and then draws this selected flop or the residual checkdown branch. It never redraws private hands from a normalized postflop root. The handoff separately reports the original joint weight, physical flop mass and normalized private support given only the physical flop. Those support weights are not a policy-reached posterior. Source six-player history reach remains `UNAVAILABLE_NO_SOURCE_POLICY`.

With twelve dealt cards and three flop cards removed, each compatible world has `C(37,2) = 666` unordered turn/river runouts. `ExactDeadCardFlopShowdown` enumerates both seven-card hands and records integer wins/losses/ties. This leaves the existing 658,008-board preflop count type unchanged. The saved game reconstructs 15,792,192 source boards and performs 63,936 additional flop runout enumerations across its four independently captured boundaries. Both kinds of work are reported explicitly.

Residual checkdown requires careful counting. Each unordered five-card board has ten possible unordered flop partitions. For each outcome, the residual count is therefore `10 * fullPreflopCount - selectedFlopCount`, with denominator `10 * 658008 - 666 = 6579414`. Weighting the selected checkdown by `1/9880` and the residual by `9879/9880` recovers the original checkdown utility for every seat, world and call branch. Treating `666/658008` as the flop probability would overweight this flop tenfold.

All terminals settle literal chip profit for six seats, keep folded contributions and return uncalled bets. A flop bet followed by a fold cannot win the bettor's own uncalled chips as extra profit. Stack caps, an SB/BTN continuation and partially blocked support have dedicated controls.

## Owned solve and local reach

The complete game contains 1,321 states, 40 BTN and 33 BB information sets, and 89/73 sequences. It fits the existing 20,000-state, 64-information-set and 129-sequence limits. Preflop is re-solved with these continuations included; no frozen continuation values are substituted for strategic play.

Original-order revised simplex rejected the first connected example with a singular basis. The new explicit `FiniteTwoPlayerAffineSequenceForm.solveAscendingL1Rows` entry point selects the existing stable row-order representation before optimization. It does not retry a rejected solve, drop coefficients, change chance, increase limits or loosen tolerance. Duals are mapped back to the original LP rows and the original primal/dual certificates are checked. Its separate algorithm identity preserves the default affine solver and all older report formats.

The [saved report](data/supplied-four-button-hand-connected-flop-report.json.gz) has BTN value about `0.51929118674596 bb` and a full-game independent best-response gap about `6.65e-15 bb`. The report also checks postflop quality separately. Candidate action likelihoods are applied to the original joint worlds, and a conditional posterior is permitted only when the candidate boundary's reach given the physical flop is at least `0.0001`. This is a candidate-policy research floor, not a full-security-face reach certificate or trainer qualification.

The BB-call continuation meets that floor and has zero measured conditional best-response gap. The other three continuations have only numerical trace mass, approximately `4.19e-21`, `7.49e-21` and `9.24e-38` from the supplied root including flop chance. Their raw world masses remain in the report, but their conditional posterior and local quality are omitted. A tiny global gap must not be used to claim that these effectively unreached branches supply reliable training strategies.

Only owned solving or complete physical replay constructs a private result handle. Parsed report JSON cannot manufacture one. Publication remains `CONNECTED_SINGLE_FLOP_RESEARCH_ONLY` and `trainerAdmission=false`.

## Independent checks and commands

The stdlib [checker](../scripts/check-supplied-connected-flop.py) independently evaluates each fixed-flop hand by the best of its 21 five-card subsets. It reconstructs all 1,321 literal game states, pot settlements and action keys, checks perfect recall, and computes pure information-set best responses against the saved candidate for both players. It also reconstructs candidate world reach and the supported local flop quality, and verifies suppression of tiny-reach posteriors. It runs no optimizer or LP solver. The [receipt](data/supplied-four-button-hand-connected-flop-independent-check.json) binds its source and exact input/report bytes.

That checker uses byte-pinned, previously physically replayed preflop counts. It independently regenerates all 15,984 selected-flop runouts and 31,968 hand evaluations, but does not independently regenerate the source's 15.8 million boards or certify the LP matrices. Java replay regenerates the complete source and flop payoffs, re-solves both owned LPs and compares every report field. These scopes remain explicit in the receipt.

With Java 21 and the solver runtime classpath:

```text
com.pokerlab.solver.SixMaxSuppliedFlopBettingStudyMain solve <request.json[.gz]> <new-report.json[.gz]>
com.pokerlab.solver.SixMaxSuppliedFlopBettingStudyMain replay <request.json[.gz]> <report.json[.gz]>
```

```sh
python scripts/check-supplied-connected-flop.py docs/data/supplied-four-button-hand-connected-flop-request.json docs/data/supplied-four-button-hand-connected-flop-report.json.gz --output <new-receipt.json>
python -m unittest discover -s scripts -p 'test_check_supplied_connected_flop.py'
```

Stored and expanded JSON are bounded to 8MiB, duplicate keys/nonfinite values are rejected, and writes are exclusive. Physical replay rejects altered counts even if a caller recomputes the surrounding hashes. Corruption controls also cover chance renormalization, policy changes, wrong residual multiplicity, changed priors/pots, admission and manufactured tiny-reach posteriors.

The next gate is robust action feedback for a supported connected decision: complete-face action EV/loss bounds, adequate reach, fresh independent reference policies and physical replay before any trainer admission. Full turn/river betting, wider flop coverage, realistic ranges/rake and genuinely multiplayer strategy remain separate milestones. AWS deployment remains paused.
