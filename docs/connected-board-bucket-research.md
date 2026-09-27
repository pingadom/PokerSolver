# Connected-game board-bucket experiment

The [physical-deck connected game](physical-deck-connected-research.md) now has a separate `BOARD_BUCKETS` information mode. Its private deals, all legal flop/turn/river draws, betting actions, and terminal chip payoffs are unchanged. Only each player's **observation** of a public board changes: a bucket combines coarse made-hand strength, whether the board is paired, broad suit and straight potential, and a high-card band. It uses that player's own cards and public cards, never the opponent's hidden cards. Turn and river information sets retain the preceding street buckets and action histories, so the abstract player remembers its earlier observations. The mode has a separate game hash and is research-only.

The bucket is intentionally simple and lossy. It can group boards with different future equities and blockers. A reduced information-set count is therefore a scaling result, **not** proof of a good abstraction. `PhysicalConnectedStrategyAudit` draws new physical runouts from an independent seed, follows the learned average policies, and records the fraction of reached decisions with no learned action distribution; those decisions use a declared uniform fallback. `PhysicalConnectedPreflopDeviationAudit` estimates what each player would gain by switching only its BTN fold/open or BB fold/call choice while holding both players' later policies fixed. The BB audit conditions its hidden-hand weights on the observed BTN open. Both audits sample actual cards and report sampling uncertainty for continuation values. Neither computes a full-game best response.

At **5,000 vanilla chance-sampled CFR iterations**, training seed 42, 20,000 held-out self-play trials (seed 43), and 5,000 deviation trials per private deal (seed 44):

| Observation mode | Visited information sets | BTN AA open frequency | Estimated BTN AA gain from best preflop action | Reached decisions using uniform fallback |
| --- | ---: | ---: | ---: | ---: |
| Exact public cards | 1,009,194 | 1.3% | 1.276bb | 0.9% |
| Board buckets | 70,862 | 99.3% | 0.022bb | 0.4% |

For the exact-card profile, BTN's AA open continuation is estimated at **+1.043bb** in BTN utility versus **-0.250bb** for folding, with a **0.144bb** approximate 95% sampling half-width on the opening value. The near-always-fold policy is a concrete failure at this compute budget. Bucketing removes that particular failure, but the bucketed BTN still has an estimated **0.224bb** one-decision improvement with KhQh, and the BB values have meaningful sampling uncertainty. The separate self-play BB values (+0.250bb exact, -0.568bb bucketed) are **not** quality rankings: each profile plays against itself and can change both players' behaviour. The fallback percentages are conditional on reached decisions, so the exact profile's frequent early folds also make them poor standalone coverage measures.

The aces result persists with training seeds **41** and **43** at the same 5,000-iteration budget: exact-card BTN opens AA about **1.3%** in both runs, while the bucketed BTN opens it **99.1%** and **99.5%**. The bucketed KhQh one-decision gain remains about **0.20bb** and **0.18bb** in those runs. These additional runs use 10,000 held-out self-play trials and 3,000 deviation trials per private deal. They establish repeatability on this fixture, not accuracy on new ranges.

Reproduce after compiling from the repository root:

```powershell
mvn -q -pl solver -am -DskipTests compile
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedBoardBuckets 5000 42 20000 5000
```

The [disjoint-range follow-up](connected-range-validation.md) now tests three new combos per seat and a sample-split audit of the first BB flop action. Aces remain sensible, while a medium pair and some flop observations remain unstable. The next quality gate is a much broader, independently justified range and a strategic response estimate spanning later streets. This bucket policy is neither serialized nor admitted to the trainer. Rake, endogenous actions from the other four seats, more bet sizes, and realistic hand ranges also remain outside this connected fixture.
