import { useEffect, useState } from "react";
import {
  postTurnRiver,
  turnRiverRequest,
  type TurnRiverAction,
  type TurnRiverCard,
  type TurnRiverHandFeedback,
  type TurnRiverHandSnapshot,
  type TurnRiverMetadata,
} from "./turnRiverApi";

const actionName: Record<TurnRiverAction, string> = {
  k: "Check", b: "Bet", c: "Call", f: "Fold",
};
const rankSymbol: Record<string, string> = {
  TWO: "2", THREE: "3", FOUR: "4", FIVE: "5", SIX: "6", SEVEN: "7",
  EIGHT: "8", NINE: "9", TEN: "T", JACK: "J", QUEEN: "Q", KING: "K", ACE: "A",
};
const suitSymbol: Record<string, string> = {
  CLUBS: "♣", DIAMONDS: "♦", HEARTS: "♥", SPADES: "♠",
};
const cardText = (card: TurnRiverCard) =>
  `${rankSymbol[card.rank] ?? card.rank}${suitSymbol[card.suit] ?? card.suit}`;
const money = (value: number) => `${value >= 0 ? "+" : ""}${value.toFixed(2)} bb`;
const frequency = (value: number) => `${(value * 100).toFixed(1)}%`;
const feedbackActions = (item: TurnRiverHandFeedback): TurnRiverAction[] =>
  "k" in item.actionEvBb ? ["k", "b"] : ["c", "f"];
const params = () => new URLSearchParams(window.location.hash.split("?")[1] ?? "");

function freshSeed() {
  const words = crypto.getRandomValues(new Uint32Array(2));
  return BigInt.asIntN(64, (BigInt(words[0]) << 32n) | BigInt(words[1])).toString();
}

function initialSeed() {
  const stored = params().get("seed");
  if (stored && /^-?\d+$/.test(stored)) {
    const value = BigInt(stored);
    if (value >= -(2n ** 63n) && value < 2n ** 63n) return value.toString();
  }
  return freshSeed();
}

function initialPlayer(): 0 | 1 {
  return params().get("hero") === "1" ? 1 : 0;
}

function setLocation(seed: string, heroPlayer: 0 | 1, packHash: string) {
  window.history.replaceState(null, "",
    `#turn-river-hand?${new URLSearchParams({ seed, hero: String(heroPlayer), pack: packHash })}`);
}

function currentSituation(snapshot: TurnRiverHandSnapshot) {
  const history = snapshot.street === "TURN" ? snapshot.turnHistory : snapshot.riverHistory;
  const actor = snapshot.heroPlayer === 0 ? "First player" : "Second player";
  if (history === "" || history === "k") return `${actor}: check or bet?`;
  return `${actor}: call or fold?`;
}

export default function TurnRiverHandPage() {
  const [seed, setSeed] = useState(initialSeed);
  const [heroPlayer, setHeroPlayer] = useState<0 | 1>(initialPlayer);
  const [metadata, setMetadata] = useState<TurnRiverMetadata | null>(null);
  const [snapshot, setSnapshot] = useState<TurnRiverHandSnapshot | null>(null);
  const [actions, setActions] = useState<TurnRiverAction[]>([]);
  const [error, setError] = useState("");
  const [stale, setStale] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    turnRiverRequest<TurnRiverMetadata>("", { signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        setMetadata(loaded);
        if (params().get("pack") && params().get("pack") !== loaded.packHash) {
          setStale(true);
          setError("This hand link belongs to an older solution. Start a new hand.");
        } else setLocation(seed, heroPlayer, loaded.packHash);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(cause instanceof Error ? cause.message : "Turn-river research is unavailable.");
      });
    return () => controller.abort();
    // Load one immutable pack when the page mounts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!metadata || stale) return;
    let active = true;
    setSnapshot(null);
    setError("");
    postTurnRiver<TurnRiverHandSnapshot>("/hands/replay", {
      seed, packHash: metadata.packHash, heroPlayer, actions: [],
    })
      .then((loaded) => {
        if (!active) return;
        if (loaded.packHash !== metadata.packHash || loaded.seed !== seed ||
            loaded.heroPlayer !== heroPlayer)
          throw new Error("The hand solution changed. Start a new hand.");
        setSnapshot(loaded);
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : "Could not load the hand.");
      });
    return () => { active = false; };
  }, [metadata, seed, heroPlayer, stale]);

  async function answer(action: TurnRiverAction) {
    if (!metadata || !snapshot || busy || snapshot.complete) return;
    const nextActions = [...actions, action];
    setBusy(true);
    setError("");
    try {
      const next = await postTurnRiver<TurnRiverHandSnapshot>("/hands/replay", {
        seed, packHash: metadata.packHash, heroPlayer, actions: nextActions,
      });
      if (next.packHash !== metadata.packHash || next.seed !== seed ||
          next.heroPlayer !== heroPlayer || next.heroCombo !== snapshot.heroCombo ||
          next.feedback.length !== nextActions.length)
        throw new Error("The hand solution changed. Start a new hand.");
      setActions(nextActions);
      setSnapshot(next);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Could not play this action.";
      if (message.includes("Solution pack has changed") || message.includes("solution changed"))
        setStale(true);
      setError(message);
    } finally {
      setBusy(false);
    }
  }

  async function newHand(player: 0 | 1 = heroPlayer) {
    if (!metadata) return;
    setBusy(true);
    try {
      const latest = await turnRiverRequest<TurnRiverMetadata>("");
      const nextSeed = freshSeed();
      setLocation(nextSeed, player, latest.packHash);
      setMetadata(latest);
      setHeroPlayer(player);
      setSeed(nextSeed);
      setActions([]);
      setSnapshot(null);
      setStale(false);
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not start a new hand.");
    } finally {
      setBusy(false);
    }
  }

  const board = snapshot ? [...snapshot.turnBoard, ...(snapshot.river ? [snapshot.river] : [])] : [];
  return (
    <main className="trainer-page">
      <header className="trainer-topbar">
        <a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a>
        <a className="trainer-back" href="#turn-river">← Single-decision drill</a>
      </header>
      <div className="trainer-container">
        <div className="trainer-title-row">
          <div>
            <p className="eyebrow">SOLVER RESEARCH / PARTIAL HAND</p>
            <h1>Play from turn to river</h1>
            <p className="trainer-intro">One hidden opponent hand stays in play as the betting and public board move forward. Your decisions are graded from the same saved solver policy.</p>
          </div>
          <span className="trainer-status">VALIDATION ONLY</span>
        </div>
        <div className="trainer-disclosure" role="note">
          This is a synthetic, no-rake, single-bet game with two active players. It does not model how the hand reached the turn or claim to solve ordinary 6-max cash play.
        </div>
        {error && <div className="trainer-error" role="alert">{error}{!metadata && <p>Enable the local turn-river research API and load its saved pack to play.</p>}</div>}
        {stale && metadata && <button className="trainer-button" disabled={busy} onClick={() => void newHand()}>Start with current solution</button>}
        {!metadata && !error && <p className="trainer-loading" role="status">Loading turn-river solution…</p>}
        {metadata && !stale && <div className="trainer-layout">
          <section className="trainer-card trainer-play" aria-label="Partial hand">
            {!snapshot ? <p className="trainer-loading" role="status">{error ? "Hand unavailable. Try a new hand." : "Dealing a hand…"}</p> : <>
              <div className="trainer-card-heading"><span>{snapshot.street} · {snapshot.complete ? "HAND COMPLETE" : "YOUR DECISION"}</span><span>{heroPlayer === 0 ? "First player" : "Second player"}</span></div>
              <div className="river-board" aria-label={`${snapshot.street.toLowerCase()} board`}>
                {board.map((card) => <span key={`${card.rank}-${card.suit}`} className={card.suit === "HEARTS" || card.suit === "DIAMONDS" ? "red" : ""}>{cardText(card)}</span>)}
                {!snapshot.river && !snapshot.complete && <span className="unrevealed" aria-label="River not yet dealt">?</span>}
              </div>
              <div className="trainer-hand-row"><div><span className="trainer-kicker">YOUR HAND</span><div className="trainer-cards">{snapshot.heroCombo.split(" ").map((card) => <span key={card}>{card}</span>)}</div></div><div className="trainer-pot"><span>Current pot</span><strong>{snapshot.potBb.toFixed(1)} bb</strong></div></div>
              <p className="trainer-context">Your remaining stack: {snapshot.heroRemainingStackBb.toFixed(1)} bb.</p>
              {snapshot.publicActions.length > 0 && <ol className="hand-history" aria-label="Public betting history">{snapshot.publicActions.map((event, index) => <li key={index}><span>{event.street}</span> {event.player === 0 ? "First" : "Second"} player {actionName[event.action].toLowerCase()}s</li>)}</ol>}
              {!snapshot.complete && <>
                <h2>{currentSituation(snapshot)}</h2>
                <div className="trainer-actions">{snapshot.legalActions.map((action) => <button key={action} type="button" className={`trainer-button ${action === "k" || action === "f" ? "secondary" : ""}`} disabled={busy} onClick={() => void answer(action)}>{actionName[action]}</button>)}</div>
              </>}
              {snapshot.feedback.length > 0 && <div className="trainer-feedback" aria-live="polite">
                <h3>Your decisions</h3>
                {snapshot.feedback.map((item, index) => <div className="hand-feedback-item" key={index}>
                  <p><strong>{item.street} · {actionName[item.selectedAction]}</strong> · EV loss {item.evLossBb.toFixed(2)} bb</p>
                  <div className="trainer-ev-grid">{feedbackActions(item).map((action) => <div key={action}><span>{actionName[action]}</span><strong>{money(item.actionEvBb[action])}</strong><small>Solver frequency {frequency(item.actionFrequency[action])}</small></div>)}</div>
                </div>)}
                <p className="trainer-explanation">Each EV loss is for one decision against the saved policy. Adding these losses is not a full-hand exploitability measure.</p>
              </div>}
              {snapshot.complete && <div className="trainer-feedback" aria-live="polite">
                <h3>{snapshot.showdown ? "Showdown" : "Hand ended by a fold"}</h3>
                {snapshot.showdown && <p>Opponent hand: <strong>{snapshot.opponentCombo}</strong></p>}
                <p>Centered result for your hand: <strong>{snapshot.heroCenteredResultBb === null ? "Unavailable" : money(snapshot.heroCenteredResultBb)}</strong>.</p>
                <p className="trainer-explanation">This result uses half the starting pot as zero. It is one sampled hand outcome, separate from the action EV feedback.</p>
                <button className="trainer-button" disabled={busy} onClick={() => void newHand()}>Play another hand</button>
              </div>}
            </>}
          </section>
          <aside className="trainer-card trainer-reference" aria-label="Partial-hand game details">
            <h2>Game details</h2>
            <dl className="trainer-facts"><div><dt>Players</dt><dd>First vs second</dd></div><div><dt>Starting pot</dt><dd>{metadata.potBb} bb</dd></div><div><dt>Starting stack</dt><dd>{metadata.remainingStackBb} bb</dd></div><div><dt>Bet sizes</dt><dd>{metadata.turnBetBb} bb turn · {metadata.riverBetBb} bb river</dd></div><div><dt>Rake</dt><dd>None</dd></div><div><dt>Best-response gap</dt><dd>{metadata.gameGapBb.toFixed(6)} bb</dd></div></dl>
            <div className="trainer-actions"><button className="trainer-button secondary" disabled={busy} onClick={() => void newHand(0)}>New hand as first player</button><button className="trainer-button secondary" disabled={busy} onClick={() => void newHand(1)}>New hand as second player</button></div>
            <p className="trainer-boundary">The opponent follows the saved mixed strategy. Their exact cards stay hidden until showdown; a fold does not reveal them. The river comes from the unblocked deck.</p>
            <a className="trainer-lab-link" href="#turn-river">Return to isolated decisions ↗</a>
          </aside>
        </div>}
      </div>
    </main>
  );
}
