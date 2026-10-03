# Six-seat policy-derived flop handoff

`SixMaxPolicyFlopTransition` takes a complete public preflop history and the fixed six-seat strategy, then constructs the actual reached joint hand distribution. It closes the input gap in the older manually conditioned two-range handoff. It does **not** replace mandatory checkdown with solved postflop betting or change trainer eligibility.

For each legal six-hand deal, its reached mass is the initial chance weight multiplied by the saved probability of every observed action at the appropriate information set. Fold actions count too. The constructor validates seat order, legal actions, strategy coverage and probability sums, rejects zero-reach histories, and normalizes the posterior in log space. A history whose floating-point unconditional probability underflows still retains a finite log probability and a usable conditional hand distribution.

The posterior keeps all six hands together. Separate marginal ranges can be inspected, but their product is not a substitute for the joint distribution: it could introduce blocked hand pairs or lose correlations induced by folds and prior betting. For empirical private-deal games, the handoff retains `EMPIRICAL_JOINT_DEALS` labelling; exact public-card enumeration does not certify the sampled private support.

## Flop and continuation accounting

Only completed heads-up, non-all-in pots with equal live commitments and no effective rake are supported. The handoff carries the full pot, including folded chips, and both remaining stacks. Postflop order is SB, BB, UTG, HJ, CO, BTN, restricted to the two survivors. A BTN open to 3bb followed by a BB call starts with a 6.5bb pot, 97bb behind and BB acting first. A UTG limp with BB checking instead starts with 2.5bb and 99bb behind.

All twelve hole cards remain removed from the physical deck, including the folded players' cards. A deal therefore admits `C(40,3) = 9,880` unordered flops. A particular flop's probability is the posterior mass compatible with all three board cards divided by 9,880. Observing the flop renormalizes the compatible joint deals; it can also remove a folded-seat hand from the posterior. An impossible flop is rejected. Seeded flop and turn/river sampling draws from these conditional deals and never returns a folded card to the deck.

`FlopState.exactCheckdown()` supplies a baseline for future continuation comparisons. For each compatible six-hand deal it enumerates `C(37,2) = 666` unordered turn/river pairs, evaluates the two live hands, splits ties, and computes each seat's chip utility as its pot share minus its preflop commitment. Folded-seat losses remain in the six-seat utility vector, whose sum is zero. This is an exact **checkdown** baseline conditional on the declared hand distribution. It supplies no betting strategy, continuation equilibrium, or guarantee for other ranges.

## Saved-policy reach audit

`SixMaxPreflopContinuationAudit` traverses the fixed strategy and reports the mass of fold wins, all-in showdowns and checkdown leaves, with checkdown mass broken down by the number of surviving players. It ranks reached heads-up public histories by unconditional probability. Up to twenty examples can receive a reproducible sampled flop and exact conditional checkdown values; the CLI exports ten. Sampling a few examples does not average over every flop or measure an abstraction error.

The committed [audit JSON](data/sixmax-policy-flop-reach.json) binds the complete source pack hash `2ce2adc9a9cb69179e36f6de3d223b40542d9588da61c58ff66fc9d241b57dcf`, the spot hash, mandatory-checkdown rule, source NashConv and seed 711. On this tiny, synthetic two-deal source:

| Endpoint | Fixed-policy probability |
| --- | ---: |
| Uncontested pot | 97.044628% |
| All-in showdown | 0.003453% |
| Mandatory checkdown, any live-seat count | 2.951919% |
| Heads-up checkdown | 1.677833% |
| Three-player checkdown | 1.272908% |

There are 191 reached heads-up public histories. The largest, about 1.652151% of all play, is UTG limp / HJ fold / CO fold / BTN fold / SB fold / BB check. This dominance is evidence about the synthetic source game; it is not a realistic cash-game frequency estimate. More than 97% of this fixture ends in a fold win, so simply attaching a few postflop examples would not make its ranges representative.

Run from the repository root in PowerShell:

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPolicyFlopAuditMain' '-Dexec.args=solver/src/test/resources/six-seat-full-round-pack.json docs/data/sixmax-policy-flop-reach.json 711'
mvn -q -pl solver -am spotless:check verify
```

The CLI strictly reloads the saved pack, caps the input at 16 MiB, binds the result to the whole artifact and refuses to overwrite the source pack. Output maps are sorted for reproducibility. It neither trains a new policy nor changes any trainer pack.

Tests cover analytic action-conditioned posteriors, folded blockers, correlations between surviving hands, total probability over every physical unordered flop, deterministic sampling, an unbeatable flopped royal flush, exact runout counts, pot conservation, invalid histories, missing/zero strategy reach, invalid boards, unsupported rake and rare-history numerical stability. Integration tests replay the committed saved policy and verify CLI provenance and source preservation.

The next continuation milestone is a postflop betting game whose chance root consumes this correlated six-hand posterior and whose information sets expose only the acting player's hand and public board. It must keep folded cards hidden, preserve chip accounting across the handoff, and measure how its solved betting values differ from the exact checkdown baseline. Broader input ranges and independent strategic validation remain required before general cash-game training content can be published.
