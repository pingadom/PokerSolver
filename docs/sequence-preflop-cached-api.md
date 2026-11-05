# Cached sequence-flow preflop API

## Declaration before startup replays

Adopt exactly the qualified sequence-flow feedback cleared at bbb54f0, stored
SHA-256 `013686942f9d630dbcaad65673afed0263b73d864b6f27e27c161998887ec52b`.
Keep the original source, legacy minimum, physical sequence minimum and request
byte pins. Derive their canonical identities from the frozen typed reports before
replay; this identity derivation performs no optimization and grants no authority.
No numerical source report is generated or modified. Capture all bounded regular
files once before replay and recheck identities, size, timestamps and bytes after.
Reject path escapes, symbolic sources, hard-link aliases and caller-refreshed pins.

Add captured-DTO overloads to the two sequence study entry points, preserving
their existing algorithms, requests, limits and complete report equality. Pin the
captured legacy and sequence reports' canonical identities before physical work.
DTO parsing alone cannot issue an owned qualification. Only a complete physical
replay and its private qualified capability can construct the immutable cache.
No path is reopened during that replay; loader reads are confined to capture and
post-replay integrity checks. Every HTTP request uses cached objects only.

Reserve one enabled native HTTP startup to capture public fixture/review artifacts,
one shared local solver-loader replay and one shared local API test-context replay.
Also reserve one local regression replay each of the existing physical sequence
study and feedback suites to validate the captured-entry refactor. No additional
generation, numerical retry, fallback, source/tolerance/representation search or
work-limit change is reserved. Each replay retains one physical game, two parent
calls/four parent LPs, at most one new sequence endpoint and two fresh references
with 240k total iterations. Do not re-solve the legacy minimum. The disabled API
test context performs zero replays. Reserve three new replays per full CI run:
solver-loader suite, API suite and dedicated production-container startup; existing
study/replay classes remain enabled with their existing complete-CI reservations.
Retain the original 20k nodes, depth32, 64 infosets/129 sequences per actor, 512 LP
rows/columns, 8m compiler units, 10k pivots, 4b endpoint arithmetic, 1e-8 certificate/
security tolerance, .0001 reach, .001 quality, .01 regret/loss and 8 MiB stored and
expanded source caps. Malformed-source controls must fail before physical work.

Publish a separate default-off research API and a typed manifest, public fixture,
canonical review and independent receipt under `supplied-sequence-preflop-drill`.
Expose the original six-seat BB99 root only: two active players, empty board, pot
4.5 bb and 2 bb to call. Hide villain cards, future board, policies, certificates,
internal information-set keys and legacy comparisons. Shift terminal-profit EVs
by the original committed 1 bb exactly once; keep every loss bound, classification
and full-action ceiling. Raise stays strategy dependent. Public trainer admission
remains false. Bound ordered/repeated session reviews to128 choices and canonical
UTF-8 LF exports. Strict JSON accepts only the exact source pin, case0 and legal
choices; reject missing/null/unknown/duplicate/coerced/nonfinite/oversized inputs.

Add independent projection/canonical-export checks, corruption tests, real HTTP
smoke controls, explicit OpenAPI request schemas and a required dedicated Docker
CI job with default absence and exactly one enabled replay. Preserve all existing
CI jobs, classes, cases and timeouts. Native capture is not Docker evidence; the
new production-container smoke remains pending until PR CI after owner review.
Preserve all398 existing data files, prior failures, original trainer routes/pins/
fixtures, frontend, frozen previews and interview document. AWS remains paused.
This remains one bounded two-active-player root at a six-seat table, not a broad
six-active-player GTO solution. No frontend route is included in this change.

## Review status

The project owner cleared code revision `1bb65a9` for PR creation on 2026-11-05
(Europe/London), with “cleared 1bb65a9”. This applies only to that revision.
Fresh full PR CI, including the required container smoke, and its evidence audit
must pass before merging. Material code changes require renewed review.


## Backend contract

Enable the separate research API with `TRAINER_ROOT_SEQUENCE_PREFLOP_ENABLED=true`,
`TRAINER_ROOT_SEQUENCE_PREFLOP_MANIFEST_PATH` and
`TRAINER_ROOT_SEQUENCE_PREFLOP_STUDY_HASH`. The exact published study pin is
`586e0c0777ca9fa8f48e957f126a8f24c8b9382ba9da1442f684fdcb5f40ff21`.
Absent opt-in exposes no new routes or solver bean. Existing root/preflop and
minimum-informed trainers retain their separate configuration, pins and data.

Routes use `/api/v1/trainer/research/root-sequence-preflop-drill`:

| Method and path | Behaviour |
| --- | --- |
| GET base | Pinned metadata, six seats, two active players and startup reservation |
| GET `/questions/0?studyHash=...` | BB99, empty public board, current table and move bounds |
| POST `/grade` | Exact source pin, integer case zero and one legal action |
| POST `/review` | Reconstruct up to 128 ordered/repeated choices using cached grades |
| POST `/review/export` | The same review as deterministic UTF-8 JSON with LF and final newline |

Every successful response uses `Cache-Control: no-store`. JSON request bodies
have a 32 KiB acceptance cap and reject missing, null, duplicate, unknown, coerced,
nonfinite and stale inputs. Old root/minimum pins cannot select the new source.
Clients submit choices; server-derived EVs, classifications and review totals
cannot be supplied as grading authority. OpenAPI has explicit, distinct request
schemas for grades and reviews, including the export request.

The manifest captures six regular files: itself, feedback request/report, original
envelope, physical sequence minimum and retained legacy minimum. Each dependency
has its original hard-coded stored byte pin. The entire feedback report also has
a hard-coded canonical identity. The two captured solver entry points check the
legacy minimum canonical identity
`6040e8e54a11fbe6737a454ae014eca7f054bbb91ac9f4000eecdad41ae13238`
and sequence minimum identity
`231336105c0f6f477a7a81d43fd0b57b29adbe83c2cf0531a7ce5412702b1fce`
before physical work. Even a parsed, internally consistent counter edit cannot
mint qualification. Both public path entry points retain their byte pins and
delegate to the same captured-input implementation.

After exact owned feedback reproduction, the typed cache keeps only public
question and grade objects. It adds the original committed 1 bb once to utility
bounds; losses and classifications are unchanged. It retains full-action ceilings
from the envelope instead of treating a minimum bracket as a full-action EV.

| Move | Current-decision utility bounds (bb, approximate) | Loss bounds (bb, approximate) | Classification |
| --- | --- | --- | --- |
| Fold | 0 to 0 | 1.7857791803 to 1.7857792930 | Outside limit |
| Call | 1.7857791903 to 1.7857792830 | 0 to .0000000928 | Within limit |
| Raise to 9 | 1.2111418188 to 1.7857792830 | 0 to .5746374641 | Strategy dependent |

The frozen source-derived fold bounds include the original approximately 1e-8
certificate tolerance around zero. Full binary64 values remain in the fixture
and API; this table rounds for readability. The tiny differences from the legacy
minimum remain in the source evidence. No additional shift changes losses.

## Verification completed locally

One declared localhost API capture loaded the production controller/configuration,
performed one owned replay, captured the public fixture and canonical export,
and passed actual HTTP checks for all grades, ordered/repeated/empty/max reviews,
strict requests and OpenAPI. The temporary hidden process was stopped. The
captured fixture SHA-256 is
`3f2df9e3286858029c8857904ac98988cb251158f502220e7823a0caa4c4c0e0`.

The focused Java checks passed 62 tests without failures or skips: 11 loader guards,
8 cached-drill tests, 6 enabled API tests, 3 new disabled API tests, 3 existing
disabled API controls, and the original 12 physical sequence/19 feedback tests.
The existing physical and feedback reports reproduced completely. Exactly the
declared five local owned replays were used: native capture, cache suite, enabled
API context, existing physical suite and existing feedback suite. Invalid-source
and disabled contexts perform no physical solve. No numerical report was generated.

The independent projection checker verifies the original sequence/legacy source
certificates before reconstructing public metadata, question, all three grades,
the commitment shift and canonical export bytes. Ten corruption/positive tests
and five smoke-control tests passed. Ten immutable Python helpers are SHA-pinned;
the checker performs zero fresh LP or CFR solves. An initial checker-only Python
name-shadowing error was fixed and its failed log retained; this required no new
source replay or evidence regeneration.

CI retains every existing job, test and timeout and adds the projection checker
plus one dedicated production-container job. Its 27 expanded jobs include all
360 backend classes; new tests are assigned automatically by the existing
inventory. The new cache suite runs on solver-5 and the enabled API suite on
services-1. The read-only Compose overlay mounts exactly the six declared files.

Docker is not installed in the local environment. The dedicated container smoke
and full CI remain pending until a PR is permitted after explicit owner clearance.
Native HTTP evidence is not production-container validation.

```text
docker compose -f docker-compose.yml -f docker-compose.trainer-root-sequence-preflop.yml up --build -d
python scripts/smoke-sequence-preflop.py
python -m unittest discover -s scripts -p test_check_sequence_preflop_drill.py
python -m unittest discover -s scripts -p test_smoke_sequence_preflop.py
```

All 398 previous numerical/source data artifacts, existing trainer fixtures,
frozen previews and the protected interview document remain unchanged. This
change supplies the backend for later UI adoption; it adds no frontend route or
general six-active-player equilibrium claim. The supplied future-flop model and
small private support remain the original explicit research limitations.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`1bb65a99098bfc1088a017b9cb4c8e824c339e25` and cleared PR creation on
2026-11-05 (Europe/London), with “cleared 1bb65a9”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
