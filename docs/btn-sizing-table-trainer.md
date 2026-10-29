# BTN quarter-pot table practice

The four qualified responses from the [joint sizing study](size-menu-btn-quarter-study.md)
now have a separate playable page at `#btn-sizing-flop`. It uses the
[pinned cached API](size-menu-btn-cached-api.md); the existing twenty-question
v7 collection and its review format remain intact.

## Practice flow

Choose one of four BTN hands, then fold or call BB's quarter-pot bet. The table
shows all six seats: BTN is the blue hero, BB is red, and the four preflop folds
are dimmed. BB's last action appears under that player. Stacks, contributions,
the board, pot and 1.625 bb call price come from the owned public state.

Both buttons show EV and loss intervals immediately for study. Looking at
values records no attempt. Clicking a move, or using `1` for fold / `2` for call,
requests its cached grade. Feedback appears directly below the buttons, followed
by next-hand, retry, review and reset controls. The hand picker and next-hand
navigation retain each hand's latest feedback; next wraps through the four hands.

Sessions allow 128 attempts and distinguish attempts from distinct hands
practised. Device storage is keyed by the complete study hash. Reload restores
only validated grades. Reset clears this study's session without removing other
trainer histories. Storage failures leave the current session usable in memory.
Reviews preserve order and repetitions, support JSON download/import, and check
imported choices against the server before replacing the current session.

The page rejects stale or inconsistent metadata, tables, EVs, grades and review
counts. Only the complete four-hand inventory for the published hash is accepted.
Duplicate JSON keys, unknown fields, non-finite values, invalid indices, excessive
attempts and cross-study imports cannot become feedback. Pending requests block
duplicate actions. Changing the URL aborts the old request and discards late
results. Keyboard shortcuts ignore modifiers, repeats and focused form controls.

## Running it

Normal startup leaves this research API disabled. For the local Docker stack:

```sh
docker compose -f docker-compose.yml -f docker-compose.trainer-btn-sizing.yml up --build
```

Open the frontend on port 8080 with `#btn-sizing-flop` after API readiness.
The overlay mounts only the original manifest, request and compressed report
read-only. It explicitly enables startup using these environment settings:

```text
TRAINER_SIZE_MENU_BTN_ENABLED=true
TRAINER_SIZE_MENU_BTN_MANIFEST_PATH=/absolute/path/to/the/manifest.json
TRAINER_SIZE_MENU_BTN_STUDY_HASH=c55d598cedc094e836d2d4f4f5ca97897721218f9bc557a9830cc75b45fac5bc
```

Spring CLI properties from the API document also work. One complete physical
replay and two shared reference runs occur at startup; questions, grades and
reviews then use the immutable cache. No solver work happens on a button click.
The startup budget and qualification gates are unchanged.

For a Vite frontend against a separately running API, set
`POKERLAB_BTN_SIZING_API_TARGET=http://127.0.0.1:PORT` in the frontend's local
environment. The exact route is proxied separately; existing trainer proxy
settings are unaffected. Deep links can include `?study=HASH&hand=0` through `3`.

## What the numbers mean

EV is terminal chip profit from the start of the hand, including chips already
committed. Folding's roughly -3 bb value does not mean losing another 3 bb when
clicking fold. Loss compares a forced action with the best legal move across the
supported strategy set; the limit is 0.01 bb. Displayed intervals round outwards.

The model has two active players at a six-seat table, one supplied joint prior,
one physical flop, a jointly solved but restricted size menu, no rake and exact
checkdown after the flop action. This page does not supply general range charts,
strategy frequencies or six-active-player GTO claims. Research admission remains
false. The new route keeps its own study and source indices instead of silently
appending these questions to a different betting game's collection.

## Verification

The UI fixture was captured from all four questions and eight legal grades of
one real, owned startup through the production controller. Its canonical
eight-choice review has four within-limit and four outside-limit outcomes.
No new numerical source generation or fabricated display values are used.

Client tests corrupt pins, public table state, EV/loss values, classifications,
review grades/counts/order and JSON shape. Page tests cover every hand/move,
on-table feedback, retries, navigation, keyboard guards, pending/aborted requests,
restore/import/export, storage failures and the full 128-attempt session.
`scripts/smoke-size-menu-btn.py` checks the actual HTTP protocol, OpenAPI request
schemas, all eight grades, ordered/repeated/empty/maximum reviews, exports and
invalid requests. A separate CI container job verifies default absence followed
by real opt-in startup with the same published source and original budgets.
