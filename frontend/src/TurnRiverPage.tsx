import { useEffect, useState } from "react";
import {
  postTurnRiver,
  turnRiverRequest,
  type TurnRiverAction,
  type TurnRiverCard,
  type TurnRiverFeedback,
  type TurnRiverMetadata,
  type TurnRiverQuestion,
  type TurnRiverQuestionResponse,
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

function setLocation(seed: string, packHash: string) {
  window.history.replaceState(
    null, "", `#turn-river?${new URLSearchParams({ seed, pack: packHash })}`,
  );
}

function situation(question: TurnRiverQuestion) {
  const history = question.street === "TURN" ? question.turnHistory : question.riverHistory;
  const bet = question.street === "TURN" ? question.turnBetBb : question.riverBetBb;
  const street = question.street.toLowerCase();
  switch (history) {
    case "": return `The first player acts on the ${street}.`;
    case "k": return `The first player checked. The second player can check behind or bet ${bet} bb.`;
    case "b": return `The first player bet ${bet} bb. The second player faces a call or fold.`;
    case "kb": return `The first player checked, then the second player bet ${bet} bb. The first player faces a call or fold.`;
    default: return `${question.street} decision`;
  }
}

export default function TurnRiverPage() {
  const [seed, setSeed] = useState(initialSeed);
  const [metadata, setMetadata] = useState<TurnRiverMetadata | null>(null);
  const [question, setQuestion] = useState<TurnRiverQuestionResponse | null>(null);
  const [feedback, setFeedback] = useState<TurnRiverFeedback | null>(null);
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
          setError("This link belongs to an older turn-river solution. Start a new question.");
        } else setLocation(seed, loaded.packHash);
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
    const controller = new AbortController();
    setQuestion(null);
    setFeedback(null);
    setError("");
    turnRiverRequest<TurnRiverQuestionResponse>(`/questions/${seed}`, { signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        if (loaded.question.packHash !== metadata.packHash) {
          setStale(true);
          setError("The turn-river solution changed. Start a new question.");
        } else setQuestion(loaded);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(cause instanceof Error ? cause.message : "Could not load a decision.");
      });
    return () => controller.abort();
  }, [metadata, seed, stale]);

  async function answer(action: TurnRiverAction) {
    if (!metadata || !question || busy || feedback) return;
    setBusy(true);
    setError("");
    try {
      const graded = await postTurnRiver<TurnRiverFeedback>("/grade", {
        seed, packHash: metadata.packHash, action,
      });
      const spot = question.question;
      if (graded.packHash !== metadata.packHash || graded.heroCombo !== spot.heroCombo ||
          graded.street !== spot.street || graded.turnHistory !== spot.turnHistory ||
          graded.riverHistory !== spot.riverHistory ||
          (graded.river ? cardText(graded.river) : null) !==
          (spot.river ? cardText(spot.river) : null))
        throw new Error("The turn-river solution changed. Start a new question.");
      setFeedback(graded);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Could not grade this decision.";
      if (message.includes("Solution pack has changed") || message.includes("solution changed"))
        setStale(true);
      setError(message);
    } finally {
      setBusy(false);
    }
  }

  async function restart() {
    if (!metadata) return;
    setBusy(true);
    try {
      const latest = await turnRiverRequest<TurnRiverMetadata>("");
      const next = freshSeed();
      setLocation(next, latest.packHash);
      setMetadata(latest);
      setSeed(next);
      setQuestion(null);
      setFeedback(null);
      setStale(false);
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not start a new question.");
    } finally {
      setBusy(false);
    }
  }

  const spot = question?.question;
  const board = spot ? [...spot.turnBoard, ...(spot.river ? [spot.river] : [])] : [];
  const actingSeat = spot?.actingPlayer === 0 ? "First player" : "Second player";
  return (
    <main className="trainer-page">
      <header className="trainer-topbar">
        <a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a>
        <a className="trainer-back" href="#trainer">← Preflop trainer</a>
      </header>
      <div className="trainer-container">
        <div className="trainer-title-row">
          <div>
            <p className="eyebrow">SOLVER RESEARCH / TURN + RIVER</p>
            <h1>Turn and river decisions</h1>
            <p className="trainer-intro">Practise one decision from a two-street game solved by PokerLab. A river card is dealt between betting rounds.</p>
          </div>
          <span className="trainer-status">VALIDATION ONLY</span>
        </div>
        <div className="trainer-disclosure" role="note">
          These exact-card ranges are synthetic and have no documented path from earlier streets. The game has one bet size per street, no raises and no rake. Feedback applies only to this model.
        </div>
        {error && <div className="trainer-error" role="alert">{error}{!metadata && <p>Enable the local turn-river research API and load its saved pack to practise.</p>}</div>}
        {stale && metadata && <button className="trainer-button" disabled={busy} onClick={() => void restart()}>Start with current solution</button>}
        {!metadata && !error && <p className="trainer-loading" role="status">Loading turn-river solution…</p>}
        {metadata && !stale && <div className="trainer-layout">
          <section className="trainer-card trainer-play" aria-label="Turn or river decision">
            {!spot ? <p className="trainer-loading" role="status">{error ? "Decision unavailable. Try a new question." : "Loading decision…"}</p> : <>
              <div className="trainer-card-heading"><span>{spot.street} DECISION</span><span>{actingSeat} to act</span></div>
              <div className="river-board" aria-label={`${spot.street.toLowerCase()} board`}>
                {board.map((card) => <span key={`${card.rank}-${card.suit}`} className={card.suit === "HEARTS" || card.suit === "DIAMONDS" ? "red" : ""}>{cardText(card)}</span>)}
                {!spot.river && <span className="unrevealed" aria-label="River not yet dealt">?</span>}
              </div>
              {spot.street === "RIVER" && <p className="trainer-context">Turn: {spot.turnHistory === "kk" ? "both players checked" : "a bet was called"}.</p>}
              <h2>{situation(spot)}</h2>
              <div className="trainer-hand-row"><div><span className="trainer-kicker">YOUR HAND · {actingSeat}</span><div className="trainer-cards">{spot.heroCombo.split(" ").map((card) => <span key={card}>{card}</span>)}</div></div><div className="trainer-pot"><span>Current pot</span><strong>{spot.potBb.toFixed(1)} bb</strong></div></div>
              <p className="trainer-context">Bet size this street: {spot.street === "TURN" ? spot.turnBetBb : spot.riverBetBb} bb. The opponent's cards remain hidden.</p>
              <div className="trainer-actions">{spot.legalActions.map((action) => <button key={action} type="button" className={`trainer-button ${action === "f" || action === "k" ? "secondary" : ""}`} disabled={busy || Boolean(feedback)} onClick={() => void answer(action)}>{actionName[action]}</button>)}</div>
              {feedback && <div className="trainer-feedback" aria-live="polite">
                <h3>Decision feedback</h3>
                <p>You chose {actionName[feedback.selectedAction].toLowerCase()}. EV loss: <strong>{feedback.evLossBb.toFixed(2)} bb</strong>.</p>
                <div className="trainer-ev-grid">{spot.legalActions.map((action) => <div key={action}><span>{actionName[action]}</span><strong>{money(feedback.actionEvBb[action])}</strong><small>Solver frequency {frequency(feedback.actionFrequency[action])}</small></div>)}</div>
                <p className="trainer-explanation">EV uses equal shares of the starting pot as the zero point. It accounts for the observed opponent actions and river card; later choices follow the saved solution.</p>
                {Math.abs(feedback.actionEvBb[spot.legalActions[0]] - feedback.actionEvBb[spot.legalActions[1]]) < 0.1 && <p>The actions are nearly tied in this model. The exact mix is not a general poker rule.</p>}
                <button className="trainer-button" disabled={busy} onClick={() => void restart()}>Next decision</button>
              </div>}
            </>}
          </section>
          <aside className="trainer-card trainer-reference" aria-label="Turn-river game details">
            <h2>Game details</h2>
            <dl className="trainer-facts"><div><dt>Order</dt><dd>First vs second player</dd></div><div><dt>Starting pot</dt><dd>{metadata.potBb} bb</dd></div><div><dt>Starting stack</dt><dd>{metadata.remainingStackBb} bb</dd></div><div><dt>Bet sizes</dt><dd>{metadata.turnBetBb} bb turn · {metadata.riverBetBb} bb river</dd></div><div><dt>Rake</dt><dd>None</dd></div><div><dt>Best-response gap</dt><dd>{metadata.gameGapBb.toFixed(6)} bb</dd></div></dl>
            <h3>Assumed exact combos</h3>
            <p>Possible hands in the model, not the opponent's dealt cards.</p>
            <div className="trainer-range"><strong>First player · {metadata.firstRange.length}</strong><div>{metadata.firstRange.map((combo) => <span key={combo}>{combo}</span>)}</div></div>
            <div className="trainer-range"><strong>Second player · {metadata.secondRange.length}</strong><div>{metadata.secondRange.map((combo) => <span key={combo}>{combo}</span>)}</div></div>
            <p className="trainer-boundary">River cards are drawn exactly from the remaining deck. Earlier-street ranges, raises and other bet sizes are outside this saved game.</p>
            <a className="trainer-lab-link" href="#river">Try the fixed-board river drill ↗</a>
          </aside>
        </div>}
      </div>
    </main>
  );
}
