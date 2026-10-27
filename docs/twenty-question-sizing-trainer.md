# Twenty flop decisions across bet sizes

The v7 research collection preserves every original v6 question at indices 0–17 and appends two separately qualified BB responses at 18–19. On 6d Qc Td after BB checks and BTN bets 6.5bb into 6.5bb, BB99 calls and AcJc folds across the supported strategy face. The public question has a 13bb pot and 6.5bb to call. This is a supplied-prior, two-active-player game at a six-seat table, with no rake and one flop bet followed by exact checkdown.

## Ownership and replay

`SixMaxSizingFlopCollectionLoader` snapshots all 21 dependencies, validates exact byte/report/collection pins and the declared startup reservation, replays all eight source qualifications, then checks that the entire dependency snapshot is unchanged. Changed paths, aliases, escaping files, prices, prior pins or budgets fail before a collection is granted authority. Stored JSON is data; only replayed source results can grade.

The original eighteen-question game and the pot-size source share a declared prior. They retain different bet-size strategies, security faces, EVs, report identities and private grading capabilities. The typed wrapper dispatches each question back to its original cache. A board or card label cannot transfer a grade across games.

Startup reserves 8 source replays, 16 fresh reference runs and 1,452,000 CFR iterations. Generation, heavy validation contexts and the native preview have separate declared reservations. Grades, inspections, reviews and exports perform zero additional LP/CFR work. Original numerical, solver and accuracy caps are unchanged.

## API and table

The separate default-off route is `/api/v1/trainer/research/flop-interval-collection-v7`. Enable `TRAINER_SIZING_FLOP_COLLECTION_ENABLED`, provide the v7 manifest path and its complete collection hash, or use:

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-sizing-flop-collection.yml up --build -d
```

The local opt-in mounts 21 source files read-only. `scripts/smoke-sizing-flop-collection.py` checks default absence separately, then the real enabled startup, 20 questions, 40 legal grades, exact canonical export, request rejection and review bounds. The v6 endpoint remains unchanged.

The UI is at `#interval-sizing-flop`; `?question=18` opens the new BB99 decision after validating current metadata. The six-seat table shows hero and villain roles, the previous check and bet, the pot/call price, action EV and loss on the buttons, navigation and feedback. Inspecting EVs does not record an attempt. The new source is labelled Pot-sized bet. Each same-report hand retains a separate question identity.

Reviews allow at most 100 attempts and 1MiB of stored/expanded JSON. The v7 protocol checks the complete public inventory and every feedback field, rejects duplicate keys and older-version imports, and submits only pinned indices/actions to the backend. Coverage counts distinct questions separately from repeated attempts. Device storage, import/export, cancellation, retry and keyboard controls use a v7 storage key; old v6 practice remains separate.

`POKERLAB_SIZING_FLOP_API_TARGET` configures the Vite proxy for a separately enabled API. Local visual verification uses an isolated build directory and native preview port, preserving existing browser sessions and preview services. AWS deployment remains paused.

## Verification

The independent Python checker composes the complete original v6 proofs with independent pot-size physical/qualification evidence, checks the new manifest and predeclared generation budget, reconstructs all 20 public questions, and regrades every canonical choice. It runs no optimizer. The canonical 40-attempt review contains 20 within-limit and 20 outside-limit actions, with robust move coverage 4 folds, 1 check, 11 calls and 4 bets.

CI retains all existing tests and smoke jobs, adds the new opt-in smoke, and requires a complete disjoint backend inventory. Manifest v6 assigns the added full-source solver/API replay suites to lighter existing shards; no class is removed and the original 35-minute backend-job bound remains unchanged. The new smoke has its own 30-minute bound, including at most 25 minutes for startup.

This extends the bounded research trainer; it does not establish general six-active-player cash GTO or unrestricted postflop play.
