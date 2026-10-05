# Connected continuation selection across active seat pairs

The connected solver can now choose reached histories by active-seat diversity and board-conditioned hand mass. The default remains reach-first, preserving the historical menus and checkpoint identities. Add `--diverse-pairs 0.05` to require at least two exact combos with probability at least 5% for **each** active player on **every** selected physical flop.

This is an offline curriculum selection rule on the source policy. It does not impose a poker strategy, prune private worlds, certify convergence, or admit a pack to the trainer. Retained-policy beliefs are reported separately and can change during training.

## Selection and accounting

1. Validate complete source preflop rows and the eight-private-deal cap.
2. Enumerate source-policy terminal reach; inspect the highest-reach 20 heads-up histories, with deterministic tie breaking. The candidate limit is explicit in the Java settings and report.
3. In reach order, skip an active-seat pair already selected. Generate the same seeded physical flop menu the reach-first audit assigns to that original rank.
4. Condition the complete joint reached distribution on each physical board, retaining folded hands as blockers. Aggregate each active player's own-hand marginal. Reject a history if either player lacks two combos meeting the threshold on any selected board.
5. Keep the first requested number of eligible, distinct pairs. Fail if the candidate window cannot supply them. Apply the unchanged compatible-deal/flop and complete-tree budgets; fail rather than trim support or silently fall back to another menu.

All original source private deals remain in the connected preflop chance root. On an individual flop, incompatible deals disappear because of physical card removal, not because their policy reach or combo weight is small. The full six-seat parent audit still checks every player's unilateral deviations. Unselected histories, flops and multiway pots keep the existing mandatory-checkdown continuation.

Reports use `six-max-alternating-continuation-study/v3`. `selectionAudit` includes the settings, original reach rank, seats, public history, source reach, disposition, and the board-conditioned active hand marginals for evaluated candidates. Duplicate pairs are recorded without recomputing their boards. The scan stops when the requested menu is complete; it does not claim to assess every reached history. Historical v1/v2 reports remain readable with a null selection audit.

Checkpoint format is unchanged. Resume reconstructs the declared menu from the source and options and requires exact equality with the saved selections and budget before replacing output. Restoring a reach-first checkpoint with diverse options therefore fails when those menus differ.

## Eight-deal preflight

With the existing exact eight-deal source, seed 711, two histories and one flop per history, the selected original reach ranks are **1 and 5**:

| Active seats | Source history reach | Physical flop | Compatible private worlds |
| --- | ---: | --- | ---: |
| BB / BTN | 0.0320639992 | 3c 4h Ks | 8 |
| CO / BTN | 0.0001302950 | 3h Ac Ad | 4 |

CO opens to 3bb, BTN calls, and the other seats fold in the second history. The pot is 7.5bb and each active player has 97bb remaining. Its source board-conditioned CO marginal is approximately 60.26% Ah Kh / 39.74% Qh Th; BTN is approximately 75.08% 8h 8s / 24.92% Js Ts. The board blocks the folded BB's Ac Jc hand. It does not remove either active player's hand. Histories at ranks 2 and 3 duplicate BB/BTN; rank 4 fails the active hand-mass condition.

The complete connected tree has **12 compatible deal/flop pairs and 1,409,473 states**, within the existing 16-pair / 2-million-state study budget. Lower source reach means this menu is for coverage; it is not the menu capturing the most total reach. Quality scores must be compared against the initial policy in this same declared game, not against scores from a different continuation menu.

Reproduce the no-training plan from the repository root (quote the `-Dexec.args` argument in PowerShell):

```text
mvn -q -pl solver exec:java -Dexec.mainClass=com.pokerlab.solver.SixMaxAlternatingContinuationStudyMain "-Dexec.args=docs/data/sixmax-eight-deal-source-pack.json .local/sixmax-diverse-pair-plan.json .local/sixmax-diverse-pair-policy.json 711 500 300 500 300 1 2 711 1 0.05 0.000001 --plan-only --diverse-pairs 0.05"
```

Remove `--plan-only` to execute the declared training and quality-gated round. Keep complete policy checkpoints in ignored `.local/`; publish compact audit evidence separately.

The model still has tiny private ranges, no rake, one bet size per postflop street, no postflop raises and no multiway postflop betting. The source hand-mass rule is not a replacement for the conditional and whole-parent quality gates or for later realistic cash-game validation.

A wider one-history/two-flop preflight also exercises the rule across boards: rank 1 passes on its first flop but its second flop contains 8s and leaves BTN with only Js Ts. It is rejected. Rank 5 retains material hands on both its boards and is selected instead. The selector never replaces a sampled board just to force eligibility.

## Paired whole-round results

The [seed 711 report](data/sixmax-diverse-pair-alternating-seed-711.json) and [seed 712 report](data/sixmax-diverse-pair-alternating-seed-712.json) use the identical declared menu, 500 joint / 300 initial conditional iterations, and one round of 500 exhaustive preflop / 300 conditional postflop iterations. Both accept the whole round and stop at `ROUND_LIMIT_REACHED`.

| Seed | Initial parent NashConv (bb) | Accepted parent NashConv (bb) | Initial maximum conditional gap (bb) | Accepted maximum conditional gap (bb) |
| --- | ---: | ---: | ---: | ---: |
| 711 | 0.027213766 | 0.008657585 | 0.006626742 | 0.004226265 |
| 712 | 0.032554094 | 0.008592600 | 0.003279597 | 0.005960909 |

Each accepted policy improves its own same-game parent score materially and meets the 0.05bb conditional target. Seed 712's conditional maximum increases relative to its initial policy, so this is not an improvement in every metric. After preflop feedback alone, the changed-range conditional maxima are 4.411449703bb / 0.132319612bb, both above the target; the final postflop re-solve is necessary before acceptance. Changing the continuation menu changes the game; these scores cannot establish greater accuracy than the historical reach-first study. Two single-round trials do not establish convergence.

The CO/BTN branch's final conditional gaps are 0.001467186bb and 0.001467187bb. Both final board-conditioned CO marginals remain approximately 60.32% Ah Kh / 39.68% Qh Th, and BTN approximately 76.53% 8h 8s / 23.47% Js Ts. These describe the retained policies; they were not imposed as a final strategy constraint.

Each preflop stage freezes sixteen exact six-seat utility vectors, including the four private worlds incompatible with the CO/BTN betting flop. It visits 307,971,000 states and 162,336,000 terminals with exhaustive chance. Both final policies preserve all 234,256 explicit rows; initial sampled traversal visits 219,550 / 220,162 rows, with explicit completion of the remaining 14,706 / 14,094 recorded before refinement.

Full checkpoints stay in ignored `.local/` and are approximately 61.00 / 60.98 MiB. Their saved hashes match the retained audits: `f40dc45ebcec3eb8461f8a10b5bfcbe513ab760b9662855b5c63177910a77a00` and `53c64c19fd3f4ef20b9b7f5842ff5dec1f707b005e8ac572f7c125738658f0b0`. The different row count and file size reflect the changed physical support and board, not a compression or algorithm-performance claim.

Tests reconstruct source selection and board-conditioned beliefs independently, check exact source/menu/budget binding, recompute acceptance decisions, verify policy hash chains and all-six-seat payoff accounting, and reconcile physical branch reach with the retained audit. They do not repeat long CFR training in CI. No new trainer pack is admitted by this evidence.
