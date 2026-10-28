# Size-menu policy qualification

## Declaration before generation

The experiment retains the PR200 request: BB 9d 9h, initial flop decision after
calling, the same 24-world prior, twelve blockers, four preflop boundaries,
6d Qc Td, BB quarter/half-pot and BTN pot after a check. Every action and every
policy assessment is retained. No other hand, board, menu or history is searched.

One complete owned conditional study is generated. Its two fresh 80k/160k CFR+
references are reused for assessment, without training another pair. Numerical
work remains one physical game, four parent solve calls / eight parent LPs,
thirteen interval LPs and 240k reference iterations, with no retry or fallback.
Local tests and CI each reserve one additional complete study replay. Negative
policy controls perform assessment only, with no card evaluation, LP or training.

The new qualification stage performs no LP or CFR work. It independently checks
the primary and both reference policies: full-parent best response, actual reached
flop quality, actual question reach, hero-past-cancelled counterfactual reach,
membership in the original opponent security face, forced action utilities and
losses, all reported intervals and the selected decision regret. Complete policy
coverage and normalized finite action probabilities are required.

Unchanged gates: 0.001 bb parent/local gap, 0.0001 conditional reach floor,
0.01 bb selected-decision regret and move-loss threshold, 1e-8 security slack
and the original certificate tolerance. Offline qualification also requires a
robust move within the limit and a robust move outside it. Ambiguous actions
remain strategy-dependent. Passing policy checks alone cannot bypass that rule.

Serialized reports cannot construct an owned qualification capability. Only an
owned complete solve or replay may issue one, and only if every original gate
passes. Public trainer admission remains false; no loader, API, UI or grading
question is added by this research milestone.

The independent checker reconstructs the underlying physics, all thirteen interval
certificates, three complete policy assessments, physical and conditional masses,
all nine forced-action values/losses, regret and every qualification decision.
It performs no optimization or CFR training. All prior data bytes stay unchanged.

## Results

The single declared generation succeeded in about 77 seconds. All three policies
passed every quality, reach, security-face inclusion, forced-action interval and
regret check. The independent checker verified all nine action values/losses and
reproduced the complete rejection list.

The source is rejected for offline drill use solely because it has no robust move
outside the 0.01 bb loss limit. Quarter-pot remains within the limit; check and
half-pot remain strategy-dependent. No qualification capability is issued and no
question is added to the trainer. This is a recorded negative result, not a failed
numerical solve or a reason to change the admission rules.

The report is about 1.87 MB expanded, below the unchanged 8 MiB cap, and embeds the
byte-preserved PR200 conditional source. The primary/reference selected regrets
are approximately 2.8e-16, 2.1e-10 and 5.3e-11 bb. Their actual conditional reach
is approximately 1/3; hero-past-cancelled reach remains exactly 1/3 within floating
point tolerance.

## Reproduction and next scope

`SixMaxSizeMenuFeedbackMain solve request report.json.gz` creates a new complete
owned qualification report; `replay request report.json.gz` repeats the owned
source generation and requires complete report equality. Parsed report claims
cannot issue a capability. Existing reports and qualification paths are untouched.

The next useful scope is BTN responding to BB's quarter-pot bet at this same
boundary, covering all four supplied BTN hands in a separately declared study.
That may provide meaningful call/fold contrasts, but no outcome is assumed and
no such hands were searched here. Preserve all original gates and ambiguous
results rather than forcing this 99 decision into the trainer.

## Local validation fixture correction

The first local validation replay passed the owned source and eleven controls,
but one negative test incorrectly assumed the primary opponent's check response
made hero checking costly. That move was near-optimal for that policy. A corrected
negative fixture selects half-pot against an opponent who folds quarter-pot and
calls half-pot. Assessment only, using the saved physical counts, measured a
0.000427 bb full-parent gap and 3.59 bb selected-decision regret: exactly the
small-global-gap / large-local-error case the control is meant to detect.

One additional complete local validation replay is reserved for this fixture
repair and an all-fold zero-boundary control. The numerical algorithm, source
input, generation report, limits and gates are unchanged; no numerical solve
failed and no generation is repeated. Negative controls issue no capability.
