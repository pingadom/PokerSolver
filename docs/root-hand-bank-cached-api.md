# Three-hand cached trainer API

The owned preflop root bank now has an opt-in research trainer backend with three
private-hand questions and nine cached grades. Sevens, nines and suited ace-jack
retain separate question indices and values. Mixed-hand reviews preserve every
submitted choice, repeat and ordinal, and count distinct questions correctly.
The cache/API milestone itself added no frontend page. The subsequent
[table integration](root-hand-bank-table-trainer.md) is awaiting owner review.

The source remains the original supplied joint game with two active players at
six seats and a selected-flop continuation. This does not establish unrestricted
six-player GTO or action frequencies. Call is within the 0.01 BB loss limit, fold
outside it, and raise-to-9 remains strategy dependent for every supplied hand.
Unresolved choices have their own count and are never silently marked correct.
Trainer admission remains false.

## Source and startup

The canonical study pin is
`e333144323609afd2d5fd47daf7b2636b56647d3f080c579d1491c10afc8e84d`.
It identifies the typed bank report; its stored gzip hash is separately pinned
as `eb31446fa68413b12cfea51d2ac6e52356de91437c8068faf997a2a40491cadd`.
The manifest pins the request and original physical parent bytes too.

`SixMaxRootHandBankDrillLoader` captures all four regular, distinct local files,
checks strict manifest/request/report identity before numerical work, reconstructs
the owned physical bank once, compares the complete report, then rechecks captured
file identities and bytes. Dependencies must be basenames in the manifest directory;
symbolic links, hard-link aliases, changed bytes and substituted sources are rejected.
The report DTO cannot construct an owned study result or cache. The cache requires
all three ordered hand outcomes to support complete feedback; it cannot silently
reduce a partially rejected bank to fewer questions.

One startup reserves one physical game, up to four parent-kernel calls/eight parent
LPs, fifteen envelope LPs and two fresh reference runs totalling 240,000 iterations.
The original per-hand compiler, pivot, arithmetic and numerical limits are unchanged.
After startup, immutable questions, all nine grades and reviews use cached values;
they do not read source files, solve LPs or train CFR. Startup has an appreciable
compute cost and is explicitly disabled by default.

## Enable locally

The API accepts these operator settings:

| Environment variable | Value |
| --- | --- |
| `TRAINER_ROOT_HAND_BANK_ENABLED` | `true` |
| `TRAINER_ROOT_HAND_BANK_MANIFEST_PATH` | Absolute path to `docs/data/supplied-root-hand-bank-drill-manifest.json` |
| `TRAINER_ROOT_HAND_BANK_STUDY_HASH` | The complete canonical pin above |

The same settings are Spring properties under `pokerlab.trainer.root-hand-bank-`:
`enabled`, `manifest-path` and `study-hash`. Missing or incorrect startup settings
fail closed. Without opt-in, no owned cache bean or trainer endpoint is exposed.
Earlier nines, batch and sevens APIs retain their own settings and pins.

The dedicated Compose override mounts the four pinned inputs read-only:

```text
docker compose -f docker-compose.yml -f docker-compose.trainer-root-hand-bank.yml up --build -d
python scripts/smoke-root-hand-bank.py
```

This is local/container configuration. AWS deployment remains paused.

## HTTP contract

Base route: `/api/v1/trainer/research/root-hand-bank-drill`.

| Method and path | Request | Result |
| --- | --- | --- |
| `GET` base | None | Source pin, model, 3 questions, 9 grades, limits and startup reservation |
| `GET /questions/{sourceCaseIndex}` | `studyHash` query | Public context for 0 = sevens, 1 = nines, 2 = suited ace-jack |
| `POST /grade` | `studyHash`, `sourceCaseIndex`, `action` | One cached hand/action grade |
| `POST /review` | `studyHash`, ordered `choices` | Server-derived grades, ordinals and counts |
| `POST /review/export` | Same choices | Canonical UTF-8 LF JSON download |

Every successful response uses `Cache-Control: no-store`. Legal action strings are
`fold`, `call`, `raise:9.0`; question indices are integral 0–2. Reviews accept at most
128 choices; request bodies at most 32 KiB. Unknown, duplicate, missing, coerced or
null fields and caller-supplied EVs, classifications or counts are rejected. OpenAPI
uses distinct `RootHandBankGradeRequest`, `RootHandBankSubmittedChoice` and
`RootHandBankReviewRequest` names with the correct index range.

For example, a mixed review body can contain:

```json
{
  "studyHash": "e333144323609afd2d5fd47daf7b2636b56647d3f080c579d1491c10afc8e84d",
  "choices": [
    {"sourceCaseIndex": 2, "action": "call"},
    {"sourceCaseIndex": 0, "action": "raise:9.0"},
    {"sourceCaseIndex": 1, "action": "fold"},
    {"sourceCaseIndex": 2, "action": "raise:9.0"}
  ]
}
```

It reviews three distinct questions, one within-limit choice, one outside-limit
choice and two strategy-dependent choices. Repeated choices remain separate rows.
The server derives those results from the cached bank, not supplied grade fields.
Exports use `pokerlab-root-hand-bank-review-<pin-prefix>.json`.

## EV and hidden information

The backend adds the posted 1 BB to absolute terminal-profit EV exactly once to
produce current-decision EV. It leaves loss bounds and classifications unchanged.
Consumers must preserve these server values. Public questions contain only the
hero hand, empty preflop board, six-seat public context and complete action menu.
Opponent/folded private cards, selected future board, policies and certificates are
not included in the public projection.

## Validation and remaining checks

The source fixture and 11-attempt mixed review were captured from the real native
HTTP API. The export contains 6,657 UTF-8 LF bytes with SHA-256
`e093e39c2c1cfe5b625b929640f7e843873473e1cd5a6b119d136b74b8f33ff2`.
An independent checker verifies every public question, all nine shifted grades,
unchanged losses/ambiguity, mixed-review order/counts and canonical export against
the original bank. It also rechecks the source certificates/physics without LP or
CFR work. Corruption tests cover cross-hand substitutions and byte/type boundaries.

The cache and HTTP integration suites each perform one declared owned startup.
Loader guard tests reject invalid inputs before numerical work. Two native starts
were used: the first revealed copied OpenAPI schema names; a separately declared
second start verified corrected final controller names and range constraints.
No numerical rejection was retried, saved source report replaced, or kernel budget
increased. Native HTTP used explicit Spring properties; Compose CI verifies the
environment-variable wiring. PR #232 passed all 30 CI jobs, including the new
root-hand-bank container smoke, before merging. Docker was unavailable locally;
the container check ran in CI.

The CI change retains the existing 29 jobs and adds a separate 30th container job
for this source, plus an independent projection-checker command. Earlier classes,
test assignments, checker steps, fixture bytes and dependency versions are retained.
The subsequent table integration mixes these three public question indices while
retaining their source pin, hero cards, inline EVs and canonical reviews. Its new
frontend code requires a separate review.

## Review status

The project owner cleared code revision `afb22aa3ed11992c2d6fed91cfdb6a24aae130f8`
on 2026-11-08 (Europe/London), with “cleared afb22aa”. This confirmation applies
only to that revision. PR #232 passed all 30 checks and merged; Code Review History
retains this and earlier approvals. The subsequent table UI requires its own clearance.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`afb22aa3ed11992c2d6fed91cfdb6a24aae130f8` and cleared PR creation on
2026-11-08 (Europe/London), with “cleared afb22aa”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
