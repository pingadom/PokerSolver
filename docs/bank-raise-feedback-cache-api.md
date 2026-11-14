# Owned two hand raise feedback cache and API

This backend serves the original BB77 and AcJc feedback as immutable questions,
six cached grades and ordered reviews. It builds on qualification revision
`62a8a68d32d3e1e0e729dcee0dab7c1ff55cb785`, separately cleared by the owner.
The owner cleared cache/API revision `cc53f13` on 2026-11-14. It remains research-only;
trainer admission is false and both raises remain strategy dependent.

## Startup and ownership

The manifest fixes the qualification request, feedback report, original minimum
and original bank by stored-byte SHA-256, plus the typed feedback identity
`60f8eb975a963ebdf3964cf4edb72354b8e0841e01bc5eb3a3a333e5bc9b0343`.
The loader captures the manifest and all four sources before replay. It enforces
regular same-directory files, caps, distinct physical files, exact source pins,
complete nested lineage and qualification, then replays the owned qualification.
After replay it compares the complete report and rechecks every captured path,
file key, size, timestamp and byte sequence. Parsed reports and JSON snapshots
cannot create the private cache result. Only the qualified owned result can.

The complete startup budget is one physical game, four parent LPs, two endpoint
LPs and two fresh references totalling 240,000 iterations. Existing endpoint
compiler, pivot and arithmetic limits are unchanged. Startup is deliberately
expensive; questions, grades and reviews do no IO or numerical solving afterwards.
The question and grade objects remain usable when every startup file is removed.

## Public contract

Base path: `/api/v1/trainer/research/bank-raise-feedback-drill`.
Metadata gives the exact `studyHash`, available source IDs `[0,2]`, two questions,
six cached grades, the EV scope and the declared startup budget. IDs are original
bank source indices, not offsets into a two-item array. Source index 1 belongs to
the earlier bank and is rejected here.

| Route | Input | Result |
| --- | --- | --- |
| GET base path | None | Metadata and explicit source IDs |
| GET `/questions/{sourceCaseIndex}` | Exact `studyHash` query | Hero cards, empty board, public six-seat context, three moves |
| POST `/grade` | `studyHash`, `sourceCaseIndex`, `action` | Matching cached grade |
| POST `/review` | `studyHash`, ordered `choices` | Every attempt and server-derived counts |
| POST `/review/export` | Same choices | Stable UTF-8 JSON download |

Each choice contains only `sourceCaseIndex` and one of `fold`, `call`,
`raise:9.0`. Bodies are capped at 32 KiB. Missing, duplicate, unknown, null,
coerced and trailing values fail. Submitted EVs, classifications and saved
review counts are not accepted. The maximum review is 128 attempts; empty and
repeated choices are supported. All responses use `Cache-Control: no-store`.

Public EV bounds add the original posted 1 BB exactly once, converting terminal
profit to current-decision profit. Loss bounds and classifications do not shift.
The sevens and AJs raise loss upper bounds remain about 1.1078041 and 3.5953334 BB.
Public questions contain the hero cards and current table, without opponent
cards, future board cards, private worlds, policies or certificates. Cached grades
remain separate for both original hands; the old bank source keeps its identity.

## Local opt in

All new routes and the startup bean are absent by default. Enable through the
separate overlay after starting the normal local Compose stack:

```text
docker compose -f docker-compose.yml -f docker-compose.trainer-bank-raise-feedback.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.trainer-bank-raise-feedback.yml up -d --no-deps api
python scripts/smoke-bank-raise-feedback.py
```

The overlay mounts five distinct read-only files and sets
`TRAINER_BANK_RAISE_FEEDBACK_ENABLED`, `TRAINER_BANK_RAISE_FEEDBACK_MANIFEST_PATH`
and `TRAINER_BANK_RAISE_FEEDBACK_STUDY_HASH`. The application also supports those
environment variables directly. Existing research flags and routes are unchanged.

## Evidence and validation

Local Java checks passed 23 cases: 13 loader/cache controls, seven real MVC
controller cases sharing one physical startup replay, and three disabled/startup
configuration controls. The loader suite has one full owned replay. One separate
owned cache capture produced the public fixture. These are the three declared
local owned replays; there was no numerical retry or new source generation.
Compilation and Spotless passed across the solver/API reactor.

The independent checker reuses the unchanged pinned qualification checker, then
derives every public question and grade, all source pins, the complete startup
budget and exactly one EV shift. Its 13 corruption controls and all six smoke
controls passed. The smoke runner checks all five disabled routes, metadata,
OpenAPI choice schemas, both public questions, all six grades, ordered/repeated/
maximum reviews, stable exports and strict input failures. Its readiness loop
has a fixed ten-minute deadline. Mocked smoke checks passed locally; a Docker
executable is unavailable locally, so actual Compose startup and OpenAPI execution
are pending PR CI after clearance.

CI keeps all 30 jobs and all 382 previous Java class assignments. The new solver
class is discovered on solver-4. The new owned MVC replay is explicitly assigned
to the lighter services-1 group; the disabled class also uses services-1. All 15
backend CI accounting controls pass, retaining the previous 14 and adding an
explicit assignment check. The existing root-hand-bank
smoke job retains every earlier step and adds the separate opt-in and native HTTP
smoke. The independent step gains the new checker and 13 tests. No timeout,
numerical tolerance or kernel limit is increased. All 433 earlier data files,
original producers, kernels and dependencies are preserved.

## Scope and next boundary

This is the original supplied-range, fixed-prefix game with two active players
at six seats, folded-card blockers and selected-flop continuation. It does not
establish unrestricted six-player equilibrium, whole-hand action frequencies,
attained full-action maxima or general complete-hand play. The frontend still
uses its existing bank source. Next integrate this separate pinned menu into the
table UI while retaining the previous source. AWS deployment remains paused.

## Review status

The owner cleared this cache/API revision with “cleared cc53f13” on 2026-11-14.
The confirmation below identifies exactly the reviewed code changes.

<!-- pokerlab-user-code-review:start -->
Code review confirmed: the project owner reviewed code changes at
`cc53f13ce75c053c298fe32c5a0d3fb67663cd2d` and cleared PR creation on
2026-11-14 (Europe/London), with “cleared cc53f13”. This confirmation applies to
that code revision. See the project's Code Review History for the approval record.
<!-- pokerlab-user-code-review:end -->
