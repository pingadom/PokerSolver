# Physical-hand decision values and budget stability

PokerLab now screens individual hand decisions from the accepted [balanced conditional derivative](sixmax-suit-conditional-refinement.md). This produces reproducible research evidence for potential trainer questions. It does **not** publish a lesson pack, certify general 6-max GTO, or change the playable trainer. Every report has `publicationStatus = VALIDATION_ONLY` and `trainerAdmission = false`.

## What a question means

A question identifies one selected preflop history, one literal three-card flop, the acting seat, its actual two cards and the public flop action prefix. The four decision prefixes are the initial decision, after a check, facing a bet, and after checking and facing a bet. The model permits check/bet and fold/call, with one declared bet size and mandatory turn/river checkdown. Other histories and multiway continuations keep their original assumptions.

For each question, retain the original full joint private worlds, including folded players' blockers. Multiply their board-conditioned weights by the saved probabilities of **every preceding public action**, then condition on the actor's own hand and normalize. This is an on-policy posterior; an action path with zero policy reach is labelled `ZERO_POLICY_REACH` and receives no EV. Structurally valid counterfactual actions at a reached question still receive values. Positive probability underflow, missing strategy rows, duplicate worlds and malformed posterior roots fail explicitly.

The reported action EV assumes **optimal remaining hero decisions against the fixed opponent policy**, within this bounded betting model. Hero pure plans must use the same action in the same information set across every private world; the evaluator cannot inspect the opponent's cards. It enumerates at most 256 hero plans. EV is measured from the current decision: preflop commitments are sunk and added back to terminal utility, so folding has EV zero. Uncalled bets are returned.

The report separates three quantities:

- **Decision regret:** best action with optimal hero continuation minus the saved policy's actual continuation EV.
- **Root mixture regret:** best action EV minus the saved root frequencies applied to optimal action EVs.
- **Continuation regret:** the difference between that optimally continued mixture and actual saved continuation.

Decision regret equals the latter two quantities combined. For example, checking can be valuable if followed by a call, while checking and then folding loses that continuation value. Presenting only the saved continuation EV as the value of checking would hide this distinction. EVs remain model-dependent, rather than general advice for an unrestricted poker hand.

## Candidate and stability gates

Candidates must observe physical flop cards, have preflop history reach at least 0.0001, have conditional NashConv at most 0.001bb, and have at least two combos with probability at least 5% for **each** active player. The standard screen selects the 32 largest joint history/board reach candidates, with stable menu/observation tie order. A one-combo option exists for synthetic accounting tests; the saved poker report uses two.

For each selected case, train fresh exhaustive CFR+ references from zero regrets at budgets 500 and 1000, with literal fixed-utility pruning. These are independent budget runs, **not independent random samples**, resumed training, or extra iterations added to the derivative. Each reference records its own strategy hash, local best-response quality and work counts. None of its rows are assembled into the parent or exported as another joint checkpoint.

A primary question is material when its prefix has probability at least 1% conditional on the board and its own hand has probability at least 5% conditional on that prefix. Every material question must meet all of these checks:

| Check | Limit |
| --- | --- |
| Primary total decision regret | 0.01bb |
| Each fresh reference's joint local NashConv | 0.001bb |
| Every action's primary/reference EV difference | 0.01bb |
| Primary frequencies' regret under reference action EVs | 0.01bb |
| Reference frequencies' regret under primary action EVs | 0.01bb |
| Full private-world posterior total variation | 0.01 |
| Reference question reach | Positive |

Reference action EVs are calculated on the **fixed primary question posterior**, isolating the effect of transferring the opponent policy. Separately, each reference's own on-policy question posterior is recalculated and compared by total variation. These two scopes must not be confused: transferred EVs are not reference on-policy equilibrium diagnostics. Local NashConv is independently measured on the original board-conditioned joint game.

Frequency differences are reported but do not independently reject a question: different mixes of equal-EV actions can be strategically equivalent. A whole case is retained only when both active seats have material questions and every material question passes. Nonmaterial and zero-reach rows remain visible as diagnostics; they are not marked stable.

## Saved result

`docs/data/sixmax-staged-suit-decision-stability-500.json.gz` binds the existing balanced derivative, its complete before/after report, and its fresh predecessor by canonical SHA-256 hashes. Its model and table identities remain unchanged.

| Measurement | Result |
| --- | ---: |
| Physical history/board cases considered | 804 |
| Eligible cases | 148 |
| Excluded for insufficient material combos | 499 |
| Excluded for primary local gap | 157 |
| Cases selected / fully retained | 32 / 23 |
| Material decisions / individually stable | 148 / 127 |
| Material decisions in fully retained cases | 94 |
| Decisions failing action EV stability | 9 |
| Decisions failing posterior stability | 12 |
| Largest observed action EV difference | 0.13159050bb |
| Largest private posterior total variation | 0.13583707 |
| Largest primary material decision regret | 0.00133625bb |
| Eligible physical reach, as probability of the whole game | 0.00424946671 |
| Retained selected physical reach, as probability of the whole game | 0.00082545841 |

The selected preflop histories have total reach 0.66621850571. The eligible physical cases therefore cover only **0.638% of selected-history reach**, and the retained sample covers **0.124%**. These denominators are explicitly the six selected histories, not all possible heads-up histories. The selected-history fraction is an upper bound on the corresponding all-heads-up fraction because its denominator is smaller. Neither comes close to the previously declared 25% content target. The 116 eligible but unselected cases have not passed this screen; their reach is not counted as retained.

The 64 fresh reference solves visit 12,960,000 nodes, evaluate 5,760,000 terminals and prune 2,304,000 literal inactive-utility nodes. Sampled chance nodes and baseline corrections are both zero. The compressed report is about 58KB; reports are capped at 8MiB, cases at 64, reference budgets at four runs of at most 1000 iterations, and private posterior worlds at twelve. The complete parent retains its one-million-state cap.

Derived artifact hash: `45f35acc95440b71500983fb391a650b5e285cc844b77f9c60dfdc4d3ab41ee5`.
Derived report hash: `12389b420ad725e34fbfd48869b6f4e342b5ac5a1ada8d7f6a33c9830b7c5f7f`.
Predecessor checkpoint hash: `9387b3eca90b0d1d35b806a6c5d893a0957c6ff03535fc9f5df313eefcf47e9b`.

## Reproduce and verification

From the repository root:

```powershell
mvn -q -pl solver -am install -DskipTests
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxSuitDecisionStabilityMain' '-Dexec.args=screen docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-policy-500.json.gz docs/data/sixmax-staged-suit-conditional-balanced-500-policy.json.gz docs/data/sixmax-staged-suit-conditional-balanced-500-report.json.gz .local/new-decision-screen.json.gz'
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxSuitDecisionStabilityMain' '-Dexec.args=replay docs/data/sixmax-staged-three-nine-source-pack.json docs/data/sixmax-staged-rank-texture-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-payoffs.json.gz docs/data/sixmax-staged-suit-refinement-policy-500.json.gz docs/data/sixmax-staged-suit-conditional-balanced-500-policy.json.gz docs/data/sixmax-staged-suit-conditional-balanced-500-report.json.gz docs/data/sixmax-staged-suit-decision-stability-500.json.gz'
mvn -q -pl solver -am test '-Dtest=SixMaxOneBetDecisionValuesTest,SixMaxSuitDecisionStabilityTest,SixMaxSuitConditionalRefinementArtifactTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

The CLI rejects normalized or hard-linked aliases among all seven paths, requires a new screen output, and first replays the **entire** conditional derivative. The screen API accepts only that class's opaque validated result. Screen replay checks derived lineage, reproduces selection, every fresh solve, EV, posterior comparison, failure and reach total, then requires complete record equality. Strict bounded JSON/gzip handling rejects corrupted or inconsistent reports. Reading raw JSON alone does not certify its claims.

CI reuses the already replayed balanced derivative to avoid repeating its costly parent audit, then reproduces the saved screen. Independent closed-form pot/share calculations check every reached primary action in all 32 cases. Synthetic tests separately prove own-card/action conditioning, accounting and future-call optimization, zero reach, missing support, underflow, equal-EV frequency changes, genuine EV/posterior failure, settings caps and evidence tampering.

## Next milestone and interview explanation

The backend can now explain which particular action and hand failed a quality check, rather than presenting a low aggregate error as adequate feedback. The next substantial blocker is a declared observation/runtime design that covers useful material physical hands under affordable memory and solve cost. Keep the coverage and stability gates intact before constructing an admitted physical-flop lesson pack. Broader ranges, realistic rake, multiway betting and later-street continuation remain separate validated expansions. AWS deployment remains paused.

For an interview, derive a private-world posterior after a public bet, show fold/call EV accounting, explain information-set-consistent pure plans, and use the measured 0.1316bb EV drift as an example of why independent budget checks matter. Explain the distinction between a useful research screen and an admitted trainer: 23 stable cases demonstrate reproducible local progress, while their limited reach still prevents a broad release claim.
