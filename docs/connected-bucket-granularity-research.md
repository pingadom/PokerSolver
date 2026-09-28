# Connected-game board granularity study

The [disjoint-range connected fixture](connected-range-validation.md) has a second, deliberately coarser observation mode. `COARSE_BOARD_BUCKETS` keeps the physical deck, legal betting tree, action histories and terminal chip payoffs of `BOARD_BUCKETS`. It groups each public street by the player's made-hand category and whether the board is paired. The existing mode additionally distinguishes broad suit and straight features and a high-card band. Both modes retain the player's own exact cards and the earlier street observations. The new mode has its own game hash and is research-only; neither profile is published to the trainer.

Reducing buckets raises the number of observations available per decision, but it also hides information that can affect equity, blockers and future actions. A count of visited information sets cannot decide whether that trade is acceptable.

## Common-reach coverage

`PhysicalBoardObservationCoverage` samples 50,000 legal private deals and physical board runouts. It chooses uniformly among legal actions at each decision, then maps **the same reached first BB decision on each street** into both observation modes. Uniform actions are a controlled reach policy, not a candidate poker strategy. The support threshold is 20 observations in one bucket, roughly the total needed for ten observations on each side of an alternating discovery/held-out split. It is only a descriptive threshold, not a precision guarantee.

| Seed | Street | Reached states | Fine buckets | Fine states supported | Coarse buckets | Coarse states supported |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 42 | Flop | 12,537 | 76 | 98.2% | 16 | 100.0% |
| 42 | Turn | 7,902 | 942 | 60.3% | 120 | 93.0% |
| 42 | River | 4,881 | 2,927 | 1.5% | 576 | 57.9% |
| 43 | Flop | 12,552 | 79 | 98.2% | 16 | 100.0% |
| 43 | Turn | 7,774 | 949 | 60.1% | 119 | 93.8% |
| 43 | River | 4,845 | 2,930 | 0.9% | 567 | 53.5% |

The common reach isolates the observation mapping: the game sees the same cards and actions under both modes, so the support increase is not an artifact of one trained policy folding earlier. It does not establish that the added samples describe strategically interchangeable states.

## River information-loss counterfactual

`PhysicalRiverAliasAudit` gives a direct, deliberately narrow test of whether merged physical boards can call for different actions. It forces a BTN open, BB call, and checks through flop and turn on sampled legal full-deck boards. At BB's first river decision, compare checking (BTN checks back) with an 8bb bet (BTN always calls). For each BB hand and board, it evaluates showdown **exactly against every legal BTN combo in the synthetic prior**, removing hands blocked by the board. The incremental value of betting is `8bb × (conditional win probability − conditional loss probability)`. A positive margin favors betting in this fixed-response model; a negative margin favors checking. It does not model BTN's real river response, prior strategic reach, or equilibrium.

An observation bucket is *conflicted* if sampled physical boards mapped to it have both positive and negative margins. The audit also measures the sample-weighted betting value lost when one action must be chosen for the whole bucket, relative to choosing separately on each physical board. This is an empirical information-loss diagnostic for the fixed-response decision, **not** exploitability or actual-game EV.

| Seed | Non-tie boards | Fine boards in conflicted buckets | Coarse boards in conflicted buckets | Fine observation loss | Coarse observation loss |
| --- | ---: | ---: | ---: | ---: | ---: |
| 42 | 46,941 | 81.1% | 97.9% | 0.893bb | 1.033bb |
| 43 | 46,985 | 80.6% | 98.7% | 0.882bb | 1.026bb |

These results show that **both** observations lose relevant information in this counterfactual. Coarsening adds about 0.14bb of empirical observation loss at the fixture's 8bb river bet. It cannot be justified solely by the large support gain. The sample estimates use the same boards for both mappings; they are not a statistical certification on new ranges or a full-game quality bound.

## Learned-policy check

At 5,000 vanilla chance-sampled CFR iterations, both modes were trained on the same nine-combo synthetic range and audited with the existing independent preflop and sample-split BB flop/turn/river procedures. The coarse runs visited **4,174** information sets for each seed, versus **109,846** and **107,316** for the fine runs at seeds 42 and 43. Coarse BTN pocket aces opened **99.7%** and **99.6%**; the corresponding fine values were **99.4%** and **99.3%**. The coarse runs opened BTN `7c7d` about **59%**, versus **36.1%** and **34.7%** in the fine runs. Those frequencies are behavior, not a quality ranking.

The learned coarse policy's BB river audit placed **78.0%** and **78.8%** of reached states in buckets with at least ten held-out observations, versus **9.1%** and **8.7%** for the fine policies. These percentages use *different learned reaches* and therefore do not isolate the effect of bucketing; the common-reach table above does. The coarse audit still finds selected actions whose held-out values are worse than the learned mixture, with substantial sampling uncertainty. Increased support alone has not resolved strategic error or quantified the cost of aliasing distinct physical boards. The preflop one-decision audit also cannot replace a full-game best-response bound.

Reproduce after compiling from the repository root:

```powershell
mvn -q -pl solver -am -DskipTests compile
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalBoardObservationCoverage 50000 42 20
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalBoardObservationCoverage 50000 43 20
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalRiverAlias 50000 42
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkPhysicalRiverAlias 50000 43
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedRangeValidation 5000 42 5000 50000 4 coarse
java -Xmx2g -cp 'solver\target\classes;engine\target\classes' com.pokerlab.solver.BenchmarkConnectedRangeValidation 5000 43 5000 50000 4 coarse
```

The fine mode keeps hash `25e28ef764e7972baa54710b37d78d3f839a60863b0849f76642abe9beb4ebbf`; the coarse mode has hash `6e2a88be7a3dbe371895148be5858317d0d57eccfea7222322171b2a4af2d46d`. The next quality gate is a broader range and a strategic response audit under realistic continuation policies, with enough held-out samples to evaluate later-street choices. Neither mode is ready for a 6-max chart or a mixed-street trainer pack.
