# BTN facing the quarter-pot bet in the joint sizing game

## Declaration before generation

This study includes all four supplied BTN hands in sorted order: 2c 3c, Ah Kh,
As Qs and Qh Th. BTN faces BB's quarter-pot bet at the initial flop after BB
called preflop. The same 24-world joint prior, all twelve dealt blockers, all
four preflop boundaries, 6d Qc Td and quarter/half-pot BB menu remain in the
complete solved game. No other board, hand, menu or history is searched.

Every hand has both fold and call attempted once using the existing owned v10
physical-flop endpoint algorithm. Endpoint rejections are retained separately;
neither the failed action nor its hand is dropped. Successful utility certificates
are retained even if the other action fails; loss bounds and classifications
require both actions and are withheld for incomplete menus. No fallback mode, retry,
threshold adjustment or range pruning is allowed. A case needs both endpoints
before qualification, and every original policy and action-separation gate.

Reservation per generation or full replay: one physical game, one shared parent
study with exactly two fresh 80k/160k CFR+ references (240k total), plus eight
conditional action attempts. Each endpoint solves its own parent audit on the
same immutable tree. Thus at most nine parent solve calls / eighteen parent LPs
and thirty-two interval LPs are reserved. No extra references are trained per
hand. Local validation and each CI run reserve one complete replay. Assessment
and negative controls perform no extra LP solves or CFR training.

Physical work remains 15,792,192 full preflop boards / 31,584,384 evaluations and
63,936 selected-flop runouts / 127,872 evaluations. Existing caps remain 1,609
states in this game, 129 sequences and 64 information sets per actor, the same
8,000,000 reduction-work limit and all original LP budgets/tolerances. Reports
remain capped at 8 MiB stored and expanded.

The BTN posterior retains BB's preflop call AND size-specific quarter-pot bet
likelihood. Neither can cancel as a past BTN action. Public context, actual
question reach and hero-past-cancelled reach are derived from owned edges.
The full parent opponent security face retains every original world and action;
only measurement roots are normalized by the owned physical-flop event.

Qualification uses unchanged 0.001 bb global/local quality, 0.0001 reach,
0.01 bb selected regret and move loss, 1e-8 security slack and original
certificate tolerances. A robust within-limit AND outside-limit move are both
required. Private per-case capabilities can only follow owned full generation
or replay. Public trainer admission stays false; no UI/API/loader is added here.

Independent checks cover complete physical reconstruction, both parent LP
certificates, every successful conditional certificate, per-witness flow,
posterior, global and conditional best response, all three policies per complete
case, every rejection and every qualification decision. The checker performs no
optimization or training. All 360 previous data files remain byte-unchanged.

## Results

The single declared generation succeeded in about 82 seconds. All eight fold/call
endpoint attempts succeeded, with thirty-two independently checked interval LP
certificates and twelve passing primary/reference policy assessments. All four
cases qualify for offline use under the original gates; no rejection was removed.

For this exact supplied prior and game abstraction, 2c 3c has a robust fold, while
Ah Kh, As Qs and Qh Th have robust calls. The opposite choices have conservative
loss lower bounds of approximately 0.978, 3.833, 6.188 and 6.236 bb respectively.
The robust calls have loss upper bounds below 0.001 bb. These values are scoped
to this model, not a general range chart or a completed six-active-player solver.

The source report is about 3.93 MB expanded, below the unchanged 8 MiB cap. It
shares one physical parent and the same two fresh reference runs across all four
hands. The underlying parent exactly matches the previously published joint
sizing source. Every witness retains both BB call and quarter-pot likelihoods.

The offline bank exposes four questions and eight immutable certified grades.
Canonical reviews preserve source indices, repeated attempt order and counts;
all eight legal choices produce four within-limit and four outside-limit grades.
The bank requires the exact study hash and owned per-case capabilities. It adds
no numerical work when creating grades or reviewing a session.

## Reproduction and next step

`SixMaxSizeMenuBtnMain solve request report.json.gz` performs complete owned
source generation. `replay request report.json.gz` reconstructs physics, the
shared fresh references, all eight endpoints and every qualification decision,
requiring complete report equality. Outputs must be new and requests are strict.
`SixMaxSizeMenuBtnDrill.create(owned)` builds the offline cache from that owned
result; parsed reports or caller-selected eligibility flags cannot build it.

Next, add a pinned startup loader and cached default-disabled API for the four
questions, grades and canonical reviews, then connect the table UI. Preserve the
current twenty-question collection and old browser preview until the new path
has its own complete startup and HTTP verification.

## Offline cache addition before validation

The completed owned study can now build an immutable offline question bank,
eight precomputed legal grades, and bounded session reviews. Each lookup requires
the exact study hash and preserves the original source case index. Only owned
qualified-case capabilities can populate it; parsed reports cannot. Review order,
repeated attempts and counts are retained, with a 128-attempt cap. This adds no
LP, CFR or physical evaluation work and uses the already reserved validation
replay. No public API, loader or UI is enabled by this addition.
