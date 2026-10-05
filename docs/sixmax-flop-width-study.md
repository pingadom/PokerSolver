# Physical-flop width in the connected six-seat solver

`SixMaxFlopWidthStudyMain` expands selected heads-up histories into nested menus of physical flops. It compares declared coverage, complete-tree cost, learned reach and conditional quality. It uses the [four-deal suited/pair/broadway source](sixmax-reached-continuation-study.md#declared-game); every one of the six players still makes preflop decisions in the connected tree.

## What changes

The source policy ranks heads-up non-all-in histories once. Width one uses exactly the preceding study's board at each history. Higher widths retain that board and append distinct boards from the same source-conditioned six-hand distribution, using a deterministic seed stream. Menus remain nested when the requested width changes. A board's probability remains its actual blocker-adjusted physical chance; adding boards does not renormalize them into a restricted deck.

The joint tree retains all counterfactual private deals, including deals with zero source-policy action reach. Sampling a menu from reached hands does not prune those other worlds. Each compatible selected board has probability 1/9,880 per physical six-hand deal; blocked boards have zero probability. Other flops use residual exact mandatory-checkdown payoffs. Folded-seat hands block turn and river cards, which are drawn from all 37 and 36 legal physical cards respectively.

Source reach is reported separately from learned reach. The export always includes an untrained width-one reference, even if only a wider menu is requested. Its physical betting reach supplies the coverage ratio. This avoids treating a change in learned preflop decisions as an increase in board coverage.

## Cost preflight and explicit limits

`SixMaxContinuationStudyBudget` keeps the preceding methods at eight compatible deal/flop pairs and two million complete-tree states by default. The new experiment explicitly opts into sixteen pairs, with the same two-million-state cap. The underlying game still has its own four-private-deal, four-history, eight-flop-per-history and thirty-two-pair construction limits. The study has the tighter budget; it fails instead of trimming hidden deals or selected boards.

The preflight counts an entire connected-tree traversal before policy training. It enumerates preflop and selected-flop chance, including blocked boards and residual-checkdown children. Within this postflop model, legal actions and stack caps depend on public betting history rather than board ranks or hidden hands. All children at one turn/river chance node therefore have the same tree shape: count one representative and multiply by the physical fanout. This skips card-identity enumeration for the count without changing probabilities, utilities or strategy observations.

This is an exact count of visited states for the declared tree, not a count of unique information sets, a RAM bound or a wall-clock forecast. Arithmetic overflow is rejected. Each completed study run independently traverses every state during policy completion and must reproduce the preflight count. Full-walk tests also cover asymmetric board blockers, all-in flops and turn/river stack caps. If future legal actions depend on card ranks, the counting assumption must be changed with the game.

All requested widths are screened by checking the widest menu before training any width. Since the menus are nested, narrower trees cannot add more deal/flop pairs or states. Rejected requests leave the existing output unchanged. `--plan-only` exports coverage and cost with `executionStatus: PLANNED` and empty run lists; successful training exports `COMPLETED`. Neither is a trainer publication.

## Declared wider menu

The source histories remain BTN limping/BB checking and BTN raising to 3bb/BB calling, with the other four seats folding. At flop-selection seed 711 the menus are:

| History | Original physical flop | Added physical flop | Compatible deals for original / added |
| --- | --- | --- | --- |
| BTN call; BB check | 3c 4h Ks | 3d 8s 9h | 4 / 2 |
| BTN raise to 3bb; BB call | 5d 9s Qc | 3s Ac Td | 4 / 2 |

The first added board blocks BTN's 8s 8h combo. The second blocks BB's Ac Jc combo. All four root deals remain in the parent game; physical board conditioning removes incompatible deals only inside that particular continuation. Thus both original branches have uncertainty in both active players' hands, while each added branch has uncertainty in one active player's hand.

| Declared coverage under the frozen source policy | One flop per history | Two flops per history |
| --- | ---: | ---: |
| Selected histories | 2 | 2 |
| Compatible deal/flop pairs | 8 | 12 |
| Complete-tree states | 922,537 | 1,358,137 |
| Selected-history reach | 0.007780298 | 0.007780298 |
| Fraction of heads-up probability selected | 94.86% | 94.86% |
| Physical betting-continuation reach | 0.000000787480 | 0.000001251234 |
| Physical reach relative to one-flop reference | 1.0000 | 1.5889 |

Nominal board count doubles, but actual physical betting coverage rises by 58.89% because of blockers and source-policy posterior weights. The full traversal grows by 435,600 states (47.22%). These are coverage and computational-size measurements, not a strategic accuracy claim. Even the expanded betting mass remains very small; the residual exact checkdown model still dominates physical board chance.

## Training and interpretation

Every requested width and training seed starts a fresh joint solver with the same declared iteration budget. Linear vanilla CFR enumerates private deals and selected flops while sampling later runouts. Equal seeds do **not** produce identical trajectories when the tree changes. Missing strategy rows receive explicit uniform completion, then selected conditional games are solved with exact CFR+ under the frozen learned preflop posterior.

The reusable training runner records traversal counts, completed and missing information sets, learned terminal/history/flop reach, input and candidate policy hashes, every conditional before/after gap, and exact six-player parent best-response values. Conditional refinement preserves preflop rows. Its two checks remain separate: every branch must be refined and meet the conditional target; parent NashConv must not increase beyond 1e-9bb. A failure of either check remains visible.

Parent before/after refinement comparisons within one run use the same declared game. Parent scores across widths refer to different games and are not a paired exploitability improvement. The width-one reference is coverage metadata, not a separately trained baseline. The prior [budget export](data/sixmax-reached-continuation-budgets.json) supplies measured width-one policies at the same 500/300 training budgets, where stated below.

## Reproduction

From the repository root in PowerShell:

```powershell
mvn -q -pl solver -am install '-DskipTests'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxFlopWidthStudyMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json .local/sixmax-flop-width-plan.json 711,712 500 300 2 711 1,2 0.05 --plan-only'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxFlopWidthStudyMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json docs/data/sixmax-flop-width-seed-711.json 711 500 300 2 711 2 0.05'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxFlopWidthStudyMain' '-Dexec.args=docs/data/sixmax-diverse-source-pack.json docs/data/sixmax-flop-width-seed-712.json 712 500 300 2 711 2 0.05'
```

The two committed exports use separate processes for the independent seeds; they can also be trained sequentially into one export by supplying `711,712`. Both use the same immutable coverage and budgets. No wall-time speedup is claimed from the concurrent runs.

Arguments are source, output, 1–3 distinct training seeds, 1–3,000 joint iterations, 1–500 conditional iterations, 1–4 histories, a signed long flop seed, 1–3 strictly increasing widths in [1, 4], and a positive finite conditional target. The optional final flag is `--plan-only`. Requests still must fit the declared pair/state caps. Source input is limited to 16 MiB; an existing output cannot be the source, including an alias to it. Exports omit timing for deterministic reproduction and include source/spot hashes and requested budgets.

## Measured strategy quality

The [seed 711 export](data/sixmax-flop-width-seed-711.json) and [seed 712 export](data/sixmax-flop-width-seed-712.json) each train 500 joint iterations, explicitly complete missing rows, and refine every selected continuation with 300 exact CFR+ iterations. Both independently pass the 0.05bb conditional target and the exact parent non-increase check.

| Joint seed | Maximum conditional gap before (bb) | Maximum gap after (bb) | Parent NashConv before (bb) | Parent NashConv after (bb) | Both checks pass |
| --- | ---: | ---: | ---: | ---: | --- |
| 711 | 17.131930 | 0.013494 | 0.028866909 | 0.028863301 | Yes |
| 712 | 14.897513 | 0.013270 | 0.034315505 | 0.034310818 | Yes |

The added raised-pot flop has the largest before gap: 17.131930bb / 14.897513bb. Refinement brings it to 0.004174bb in both seeds. The maximum after gap instead comes from the original raised-pot flop (0.013494bb / 0.013270bb). Both limped-pot continuations finish below 0.004bb. The preceding width-one study's maxima at the same 500/300 budgets were 0.013945bb / 0.013839bb. The chosen conditional target therefore remains met on the expanded menu; comparisons across these different joint games do not isolate a causal change in parent quality.

Each completed game has 372,785 information sets. Seed 711 visits 347,333 rows and explicitly completes 25,452; seed 712 visits 348,863 and completes 23,922. Conditional refinement replaces 364,480 postflop rows, preserves every one of the 8,305 preflop rows, and retains unsupported counterfactual rows where relevant. The completed-state count agrees with preflight at 1,358,137 for both seeds. Joint training visits 158,223,000 states per seed, versus 4,074,411,000 for full traversal of the same tree over six alternating passes and 500 iterations (25.75 times fewer visits). This compares traversal work, not time to convergence.

Learned selected-history reach is 0.007269004 / 0.012470262, covering 92.36% / 93.79% of learned heads-up mass. Actual learned betting-flop reach is 0.00000121815 / 0.00000217509. Refinement leaves those preflop-dependent reach values unchanged. The difference from the source's 58.89% coverage increase reflects learned policy changes; the export keeps these quantities separate.

## Exact inactive-seat audit shortcut

The connected game now supplies `inactivePlayerUtility` only after a selected, completed preflop history, for seats which have folded and will never act again. Their literal payoff is the negative of their committed chips under every physical board, every subsequent bet and the residual checkdown. Seats which still act, and every earlier preflop decision, receive no shortcut. This includes players who called before folding; their lost commitment is not assumed to be zero or merely their blind.

`MultiPlayerInformationSetBestResponse` can stop its target-player collection and value traversal at such a certified branch. The fixed six-player profile is still evaluated through the full tree, validating strategy rows and computing active-player values. Active seats' full counterfactual best-response searches are also retained. The default game-interface method returns empty, so other games keep the original traversal. Non-finite supplied fixed values are rejected. This method is separate from CFR's approximate control-variate baseline and does not affect CFR training or policy observations.

A comparison with the shortcut disabled checks all six profile values, best-response values, deviation gains and complete response-action maps, while counting fewer terminal-utility queries. It includes blocked flops, mixed earlier preflop decisions, multiple selected histories and a caller who later folds. A separate test checks a seat which is active at one selected history and folded at another, including the later point where it acts. This verifies the exact shortcut against the existing full search, rather than inferring quality from runtime. No universal wall-clock speedup is claimed.

## Validation and remaining scope

Tests independently reconstruct nested menus and their physical betting reach, compare preflight with exhaustive state counts, exercise exact-limit admission and one-state-short rejection, and inspect a planned four-deal wider tree without training. A repeated saved-pack CLI run checks byte-identical output, source preservation, history identity, explicit completion counts, independent quality flags and rejected width requests. The original one-flop study retains its schema and selection behavior.

This remains sparse synthetic research. The source's four physical deals and restricted preflop actions do not represent realistic position ranges; only selected physical flops permit heads-up betting. Multiway non-all-in pots still check down, and the postflop game allows check/bet and call/fold with one configured bet per street. Wider private ranges, postflop raises, multiway betting, full-board coverage and cash rake remain separate solver gates before general GTO trainer publication.
