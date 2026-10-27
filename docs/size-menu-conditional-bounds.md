# Conditional bounds for the jointly solved sizing game

## Declaration before generation

The first request selects BB holding 9d 9h at the initial flop decision after
calling preflop. It retains the whole 24-world prior, all four boundaries, all
twelve dealt blockers, 6d Qc Td, the quarter/half-pot BB menu and BTN pot bet after
a check. All three legal actions must be reported, without selecting outcomes.

An owned size-menu entry point invokes the existing explicit v10 conditional
algorithm. It derives the physical event probability from the owned game, never
from a caller scale. Only the measurement roots are divided by that event mass;
the full preflop/flop opponent security face retains every world and action.
Past hero actions cancel by perfect recall. Opponent action probabilities remain
in the witness posterior. The face slack stays 1e-8 bb, minimum conditional reach
0.0001, and all existing numerical, tree, sequence, reduction and work caps stay.

One generation is reserved: one physical game; one parent solve plus two fresh
CFR+ references at 80k/160k (240k total); then one conditional solve for each of
check, quarter-pot and half-pot. Each conditional solve regenerates its owned
parent LP audit on the immutable snapshot (no additional card enumeration or CFR).
This is four parent-solve calls / eight parent LPs, plus at most thirteen interval
LPs: five for check (two hero continuation plans), four for each bet (one plan).
There is no numerical retry, alternate mode, range pruning, changed threshold,
new board, or admitted trainer decision. A failure stops generation and is saved
as a diagnostic, rather than dropping the failed action.

Physical work is the existing 15,792,192 full boards / 31,584,384 evaluations plus
63,936 selected-flop runouts / 127,872 evaluations. Local tests and CI each reserve
one additional complete study replay with the same bounds. Reader/corruption
controls do no extra solving. The independent checker performs no optimization
or CFR; it reconstructs the size-menu tree, payoff and original LP certificates,
conditional face, numerator plans, posterior masses and witness best responses.

These are terminal-utility intervals, not incremental profit from the current
pot. Conservative loss intervals compare all legal actions and may overlap.
Full-parent convergence alone is not conditional quality. This study grants no
trainer capability; fresh reference inclusion and per-decision qualification are
separate subsequent work.

## Results

The single declared generation succeeded in about 75 seconds. All three actions
have independently verified positive reach, all thirteen original interval LP
certificates, eight-world witness posteriors and full-game security checks.
The embedded parent exactly matches the previously published size-menu source,
including both fresh references. The checker also rebuilds physics, the full
1,609-state game and both original parent LP certificates.

For this restricted 99 scenario, quarter-pot has a conservative loss upper bound
of 0.000297 bb. Check and half-pot remain strategy-dependent under the marginal
interval envelopes; neither is labelled definitively wrong. The saved source is
research evidence only and has no grading or trainer capability.

The important distinction is between a converged policy and a justified answer
for every alternative move. Responses to rarely chosen sizes can differ across
opponent strategies that are all globally secure. The intervals retain that
uncertainty. A negative terminal utility can also be the best available action
because previously committed chips are included; it is not an incremental bet EV.

The report is approximately 140 KB compressed / 1.76 MB expanded, below the
unchanged 8 MiB caps. Corruption tests cover size identity, physical-event scaling,
hero-past cancellation, posterior support, the complete security face, numerator
plans, certificate points/duals, work counters and loss bounds.

## Reproduction and next step

`SixMaxSizeMenuConditionalMain solve request report.json.gz` creates a new result
exclusively; `replay request report.json.gz` regenerates the physical parent, fresh
references and every action interval, requiring complete report equality. Requests
and reports use the new size-menu schemas; old fixed-size namespaces are rejected.
The v10 endpoint algorithm and every old report identity remain unchanged.

Next, check each fresh reference policy against the new conditional security face
and all three intervals. Any future trainer source must carry its own qualification
and retain unresolved actions. Wider hero-hand coverage is a separate declared
experiment; no additional hands or bet menus were searched in this generation.
