# Eight-deal connected private support

The connected six-seat solver now accepts up to eight physical joint deals. This expands the preceding four-deal model by making the cutoff player uncertain as well as the button and big blind. All six players still act preflop; selected postflop betting remains heads-up. This is an offline `VALIDATION_ONLY` research model.

## Declared game

The [spot](data/sixmax-eight-deal-spot.json) uses 100bb stacks, 0.5/1bb blinds, raises to 3bb or 100bb, and no rake. The [source pack](data/sixmax-eight-deal-source-pack.json) stores exact showdown estimates for every joint deal and every active subset of at least two seats. Other preflop terminals remain mandatory checkdown.

| Seat | Physical hand support |
| --- | --- |
| UTG | 4c 4d |
| HJ | Kd 9d |
| CO | Ah Kh or Qh Th |
| BTN | Js Ts or 8s 8h |
| SB | 6c 5c |
| BB | Ac Jc or 7d 7h |

The three binary ranges produce eight unblocked deals, each with prior probability 1/8. The source contains 456 payoff entries: eight deals times 57 active subsets. Each estimate enumerates all 658,008 possible five-card boards after twelve private cards are removed. Its 500-iteration CFR+ policy has 9,264 explicit preflop information sets and measured mandatory-checkdown NashConv of 0.009822583bb. This score applies only to the source game, before connected betting is introduced.

The source policy selects its two most reached heads-up non-all-in histories. With flop seed 711 and width one these are:

1. UTG/HJ/CO fold, BTN calls, SB folds, BB checks. Pot 2.5bb; physical flop 3c 4h Ks; requested street bets 1.25/2.5/5bb.
2. UTG/HJ/CO fold, BTN calls, SB folds, BB raises to 3bb, BTN calls. Pot 6.5bb; physical flop 5d 9s Qc; requested street bets 3.25/6.5/13bb.

Both boards are compatible with all eight deals. The tree contains 16 compatible deal/flop pairs and 1,845,073 states. It remains inside the existing 16-pair / 2,000,000-state study budget; limits fail instead of removing private deals or boards. Most physical flops and all multiway continuations retain mandatory checkdown.

The second history differs from the preceding four-deal study because the changed source ranges changed its reached-history ranking. These are different declared games. Scores across them do not measure a convergence improvement; compare candidate and retained policies within the same game.

## Private-belief diagnostics

`SixMaxPrivateSupportAudit` records the source chance model, complete policy hash, physical deal count, each seat's prior marginal and which seats have more than one hand. For every selected board it records:

- Counterfactual compatible deal count, compatible original-prior mass and literal physical-flop probability.
- Policy-conditioned history reach, reached deal count and the joint probability of the history and physical flop.
- Separate counterfactual and reached seat marginals, with an empty reached distribution when a history or board has zero reach. Positive history likelihoods that underflow in the displayed probability still retain log-normalized private beliefs.
- Which folded seats remain uncertain, and distinct root information-set counts for the two active players.

The source audit describes the source policy's reached ranges. The retained audit describes the final retained connected policy, including a rejected candidate's unchanged input. Each report binds its own policy hash. The alternating-study schema is now `six-max-alternating-continuation-study/v2`; committed v1 reports remain readable and simply have no private-support fields.

Marginals are descriptive. Multiplying them would lose physical-card correlations and action likelihoods. Counterfactual support preserves deals with zero policy reach so future preflop deviations are still evaluated. A physical flop has probability 1/9,880 per compatible six-seat deal, not equal probability among the selected boards.

The cutoff's cards remain removed from the deck after it folds. Its fold likelihood can also change its posterior hand distribution. Neither effect reveals the actual cutoff hand to BTN or BB. The audit checks that postflop root keys group by the active player's own hand and public state. Tests separately check concealed folded-card variation across flop, turn and river, weighted blocker probabilities, zero policy reach, positive-likelihood underflow and immutable reports. Root counts alone do not prove all later information sets are correct.

The diagnostics contain offline range beliefs and must not be exposed as joint hidden hands in a trainer. They do not admit the policy to training or establish convergence.

## Reproduction

Build before starting research JVMs. Do not rebuild classes while a study is running.

```powershell
mvn -q -pl solver -am verify
$env:MAVEN_OPTS = '-Xmx5g'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxAlternatingContinuationStudyMain' '-Dexec.args=docs/data/sixmax-eight-deal-source-pack.json .local/sixmax-eight-deal-plan.json .local/sixmax-eight-deal-policy-711.json 711 500 300 500 300 1 2 711 1 0.05 0.000001 --plan-only'
```

Plan-only computes cost and source range diagnostics without training or creating a policy checkpoint. Remove `--plan-only` and choose a separate report path to execute the declared joint training, initial refinement and one whole quality-gated round. Complete policy checkpoints belong in ignored `.local/`; compact audit evidence belongs in `docs/data/`.

## Remaining scope

Eight physical deals are a bounded support experiment, not broad poker ranges. Three seats still have singleton hands. Only two public histories and one physical betting flop per history are connected. There is one bet size per street, no postflop raises, no multiway postflop betting and no rake. Expanding private and board support simultaneously, richer betting, realistic cash assumptions and validated trainer admission remain separate model gates.
