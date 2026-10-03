# Saved full-round six-seat preflop research trainer

The bounded six-seat solver now has an offline artifact and an opt-in trainer API. Unlike the earlier forced-shove call/fold pack, this artifact starts from the blind posts and includes every legal decision in its configured preflop betting tree: folds, calls, checks, raises and responses to raises. A completed non-all-in round still follows the declared **MANDATORY_CHECKDOWN** rule. The artifact remains **VALIDATION_ONLY**.

## Offline generation and saved contract

`SixMaxPreflopResearchSpot` binds all six exact-combo ranges, their weights, stack/blind values, raise targets, rake and the continuation model. It canonicalizes range order, checks physical-card support and enforces the existing deal/public-state caps. Its SHA-256 includes the exact range-product chance model and all serialized assumptions.

`SixMaxPreflopPackBuilder` collects the complete physical-deal × active-player-subset payoff table, solves the full public tree with multi-player CFR, measures six information-set best responses, and writes a versioned `six-max-preflop-checkdown-pack/v1` artifact. The generator supports exact enumeration or shared-board sampling. Sampled artifacts retain their seed, trial counts and reported payoff SE; they are offline research outputs and are rejected by the playable session gate.

The strict JSON loader rejects unknown, missing, duplicate, coerced or trailing fields. Reload uses only persisted showdown estimates. It requires every one of the 57 multi-player subsets per legal six-hand deal, including subsets where UTG has folded; missing, duplicate or foreign payoff keys fail. It rebuilds the betting tree and checks every strategy information set and legal-action probability, then recomputes NashConv and maximum terminal-payoff SE. Exact metadata requires 658,008 boards per deal and zero sampling SE. These are consistency checks on the saved artifact; reloading does not re-enumerate boards to independently prove payoff provenance.

The full pack hash binds its strategy, payoffs and metadata as well as its spot. A session from another artifact is rejected even when the spot assumptions are unchanged.

## Committed exact fixture

The [saved fixture](../solver/src/test/resources/six-seat-full-round-pack.json) fixes UTG to A♠A♥, HJ to K♠K♥, CO to Q♠Q♥, SB to T♠T♥ and BB to 9♠9♥. BTN has equal-weight J♠J♥ or 5♠5♥. Stack depth is 100bb, the small blind is 0.5bb, raises may target 3bb or 100bb, and rake is zero. Both physical deals are legal. Folded hole cards remain removed from the board deck.

| Property | Measured value |
| --- | ---: |
| Legal six-hand deals | 2 |
| Exact boards per deal | 658,008 |
| Saved showdown entries | 114 |
| Public states | 12,832 |
| Saved information sets | 7,089 |
| CFR+ iterations | 500 |
| NashConv in the saved finite game | 0.021102640bb |
| Maximum payoff sampling SE | 0bb |
| Artifact size | 941,061 bytes |

The generation timestamp is `2026-10-03T17:50:22Z`; the full-pack hash is `2ce2adc9a9cb69179e36f6de3d223b40542d9588da61c58ff66fc9d241b57dcf`. Reproduce from the repository root:

```powershell
mvn -q -pl solver -am install
mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.GenerateSixMaxPreflopPack' '-Dexec.args=exact button-mix solver/src/test/resources/six-seat-full-round-pack.json 500 2026-10-03T17:50:22Z'
```

The small deviation and exact board payoffs validate this tiny specified game. They do not validate position-specific starting ranges or real postflop cash-game decisions. The BTN range and mandatory checkdown are synthetic research controls.

The `install` step makes the parent and engine artifacts available to the subsequent solver-only CLI invocation on a fresh checkout. For a custom experiment, serialize a `SixMaxPreflopResearchSpot` with `MultiwayPackJson.writeFullRoundSpot` and pass that JSON path instead of `button-mix`. The generator also accepts `sampled` with trailing board-budget and seed arguments; such packs can be inspected offline but cannot enter the exact-only session API.

## Session and API flow

`SixMaxPreflopDrillSession` draws ten reproducible reached decisions from saved policy trajectories, mixing the seats reached by those trajectories. It admits only an exact-board pack with zero payoff sampling SE and measured NashConv at most 0.05bb. These are numerical research admission criteria, separate from any review of range or continuation realism.

A question reveals the acting seat's cards and prior public actions, pot, amount to call and legal actions. Grading conditions on all compatible hidden deals and prior-action likelihoods; later decisions follow the frozen saved policy. Feedback returns conditional action EVs, selected-action EV loss, strategy frequencies and per-action payoff-SE envelopes. A review recomputes every decision and score from the session seed, full pack hash and exactly ten action strings. Clients cannot supply EVs. HTTP seeds are decimal strings so JavaScript does not lose 64-bit precision.

Questions also contain a public `players` snapshot in seat order. Each entry identifies its seat, status (`ACTING`, `ACTIVE`, `FOLDED` or `ALL_IN`), committed chips, remaining stack and last public move, including blind posts. The backend reconstructs this table with the solver's betting engine and cross-checks actor, pot and call amount. No opponent hole cards enter the snapshot. A frontend can show the entire action table without implementing its own betting accounting.

After starting the usual local dependencies, enable the API directly:

```powershell
$env:TRAINER_SIXMAX_PREFLOP_RESEARCH_ENABLED = 'true'
$env:TRAINER_SIXMAX_PREFLOP_RESEARCH_PACK_PATH = (Resolve-Path 'solver/src/test/resources/six-seat-full-round-pack.json').Path
mvn -pl api -am -DskipTests package
java -jar api/target/api-1.0.0.jar --server.port=18080
```

The existing local trainer Compose overlay also mounts and enables this exact fixture:

```powershell
docker compose -f docker-compose.yml -f docker-compose.trainer.yml up --build -d
python scripts/smoke-full-round-trainer.py
```

CI runs that standard-library HTTP smoke script against the real containerized API. It checks the pack assumptions, exact-board admission, all ten deterministic questions and grades, review totals, hidden-answer omission, no-store headers and stale-hash rejection with a full-width 64-bit seed.

The routes are absent by default and all responses set `Cache-Control: no-store`:

| Route | Purpose |
| --- | --- |
| `GET /api/v1/trainer/research/sixmax-preflop` | Assumptions, exact-payoff provenance, measured quality and pack hash |
| `GET /api/v1/trainer/research/sixmax-preflop/sessions/711/questions/0` | First reached decision; indices 0–9 |
| `POST /api/v1/trainer/research/sixmax-preflop/grade` | Reconstruct and grade one decision |
| `POST /api/v1/trainer/research/sixmax-preflop/review` | Reconstruct all ten answers and compute total/average EV loss |

From a separate PowerShell terminal:

```powershell
$trainerBase = 'http://localhost:18080/api/v1/trainer/research/sixmax-preflop'
$trainerQuestion = Invoke-RestMethod "$trainerBase/sessions/711/questions/0"
$trainerAnswer = @{
  sessionSeed = $trainerQuestion.sessionSeed
  index = $trainerQuestion.index
  packHash = $trainerQuestion.packHash
  action = $trainerQuestion.legalActions[0]
} | ConvertTo-Json
Invoke-RestMethod "$trainerBase/grade" -Method Post -ContentType 'application/json' -Body $trainerAnswer
```

The startup loader requires an explicit file path and enforces a 16 MiB file limit. Tests cover disabled routes, metadata, answer/hidden-card omission, extreme seeds, deterministic review totals, stale artifact hashes, illegal actions, incomplete answers, forged fields, sampled-pack rejection and unconverged exact-pack rejection. Requests use the loaded policy and payoffs; they perform no solve or board simulation. The website is connected through the separate full-round drill described below.

## Website drill and standalone preview

The website exposes the full-round drill at `#sixmax-preflop`, linked from the solver section. Six players sit around an oval table, rotated so the acting hero stays at the bottom while the clockwise seat order and dealer button remain correct. Blue panels identify the hero and red panels identify opponents; each player's labelled move appears underneath. Check uses yellow outlines, call green, raise/all-in red and fold grey. Folded opponents fade and their card backs disappear. Only hero cards are exposed.

Pot and call cost appear on the felt, with legal decision buttons directly beneath the hero. Each full-round seat shows its actual remaining stack and committed chips; the separate call/fold drill uses the same table but explicitly labels its stack figures as starting stacks. Raise buttons specify their total target; the stack-sized raise is labelled all-in. After grading, the hero's badge says “You chose” and the selected button stays highlighted. This does not advance the underlying public snapshot or alter its chips. Desktop and phone layouts keep the table and controls together, with a text colour key and keyboard focus outlines.

After a successful grade, each legal action button displays its conditional EV in bb, policy frequency, frequency bar and any positive payoff standard error. The selected button also says “Your choice”. Button names remain the action labels; accessible descriptions include their displayed values. The buttons stay legible while disabled, so the decision cannot be changed after grading. “Next decision” (or “Review session” on the tenth answer) appears beside the move buttons, and concise EV-loss feedback sits immediately underneath them in the table controls. The longer interpretation is available in a disclosure. The separate six-seat call/fold drill uses the same controls and feedback layout. A compact laptop layout keeps the players and continuation button visible together; phones use two action columns and a full-width continuation button.

Numeric keys select the displayed actions and Enter advances after grading; normal Enter behavior on focused links, buttons and history disclosures is preserved. Advancing clears the previous values and feedback before presenting another unanswered decision. The session review uses server-computed totals and lets each decision expand into its original public table and action values. Replay retains the same full-width seed and artifact. Copied session links start at decision one and reject outdated pack hashes.

The client checks that grade and review responses match the original shown questions, including cards, history, legal actions and every public player state. Failed question/grade requests can be retried; answer EVs appear only after a successful grade. Loading errors and disabled APIs have explicit recovery controls. Unsupported artifact assumptions are rejected before drawing questions.

For this drill alone, no database or queue is needed:

```powershell
mvn -pl api -am -DskipTests package
$env:TRAINER_SIXMAX_PREFLOP_RESEARCH_PACK_PATH = (Resolve-Path 'solver/src/test/resources/six-seat-full-round-pack.json').Path
java -jar api/target/api-1.0.0.jar --research-preview --server.port=18080
```

Start `pnpm dev` in `frontend` in another terminal, then open `http://127.0.0.1:5173/#sixmax-preflop`. The explicit preview launcher imports only this research controller, its validated saved-pack loader and API error handling. It excludes datasource/Flyway startup and simulation/queue services, with loopback binding by default. Other drills and equity simulations require the complete API; omitting the flag preserves normal startup. A real HTTP regression test runs the preview with an unreachable datasource URL to verify this separation.
