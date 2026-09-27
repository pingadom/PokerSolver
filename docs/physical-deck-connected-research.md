# Physical-deck connected preflop research

`ButtonBigBlindPhysicalDeckGame` keeps BTN's fold/open-to-3bb and BB's fold/call decisions in one CFR tree with the flop, turn and river decisions. UTG, HJ, CO and SB still fold by assumption. The 100bb, no-rake chip accounting comes from `SixMaxPreflopBetting`; a call makes a 6.5bb pot with 97bb behind. The prior hands and 2/4/8bb postflop bet sizes are the same synthetic inputs as the earlier [restricted connected game](connected-preflop-continuation-research.md).

The chance model now uses the **physical deck**. After four private cards are dealt, each of the C(48,3) = **17,296 unordered flops** is equally likely. Each flop has 45 legal turn cards and each turn has 44 legal river cards. Card removal happens before every draw. Private hands remain absent from the opponent's information set. Terminal utilities evaluate the actual seven-card showdown or settle a fold using the same centered chip payoff as the earlier connected game. The full list of flops can be enumerated for an audit, but normal solving draws one by combinatorial index without constructing 17,296 states.

`CfrGame.sampleChanceOutcome` lets large chance nodes provide a direct seeded draw. The default implementation still samples from the validated outcome list, preserving the existing small-game behavior. The physical game implements direct sampling for flop, turn and river; `CfrSolver` enumerates the small four-deal private range and samples later public cards. Tests compare quantile draws with complete chance lists, verify 17,296/45/44 counts and normalization, check a shared runout's payoffs against the restricted game, and confirm reproducible sampled traversals.

`PhysicalConnectedChanceAudit` independently compares seeded, forced-check-down rollouts against `ExactPreflopEquityOracle` on all four fixture matchups. With 5,000 samples per matchup and seed 42, the exact range-weighted BB check-down value is **+0.196069bb**; sampling estimates **+0.229515bb**, an error of **+0.033446bb** with estimated sampling standard error **0.025054bb**. This is consistent with sampling noise. The old one-flop/two-turn game's deterministic check-down error was **+1.251659bb**. These figures test one fixed policy's public-card distribution, **not** the error in learned betting strategy.

With 1,000 vanilla chance-sampled CFR iterations (seed 42), the physical game visited **204,666 information sets** and solving took about **1–2 seconds** after the audit on the development machine. Its game hash is `b5a6cb91d7331cbebc723edc2273fddee789c40ec9f730d09a5f6dee5d91dae8`. The profile is sparse: most physical boards have never been visited, so no full-game best-response gap or valid convergence certificate is available. In particular, the provisional preflop frequencies are unstable and must **not** enter a trainer pack. Full-deck chance removes the handpicked-board bias but exposes the scale of the information-set problem; it does not by itself solve general 6-max preflop poker.

Reproduce from the repository root after compiling:

```powershell
mvn -q -pl solver -am -DskipTests compile
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalConnectedChance 1000 42 5000
```

The next research step is to reduce or share public-board information sets with a measured abstraction, then evaluate strategy quality on independently held-out boards and combos. A trainer artifact needs both a complete policy for its declared game and evidence about abstraction and sampling error; this sparse research profile is deliberately not serialized or served.
