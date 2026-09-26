import { useEffect, useState } from "react";
import {
  postRiver,
  riverRequest,
  type RiverAction,
  type RiverCard,
  type RiverFeedback,
  type RiverMetadata,
  type RiverQuestionResponse,
} from "./riverApi";

const actionName: Record<RiverAction, string> = {
  k: "Check",
  b: "Bet",
  c: "Call",
  f: "Fold",
};
const rankSymbol: Record<string, string> = {
  TWO: "2", THREE: "3", FOUR: "4", FIVE: "5", SIX: "6", SEVEN: "7",
  EIGHT: "8", NINE: "9", TEN: "T", JACK: "J", QUEEN: "Q", KING: "K", ACE: "A",
};
const suitSymbol: Record<string, string> = {
  CLUBS: "♣", DIAMONDS: "♦", HEARTS: "♥", SPADES: "♠",
};
const cardText = (card: RiverCard) =>
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
    null,
    "",
    `#river?${new URLSearchParams({ seed, pack: packHash })}`,
  );
}

function situation(history: string, firstSeat: string, secondSeat: string, betBb: number) {
  switch (history) {
    case "": return `${firstSeat} acts first on the river.`;
    case "k": return `${firstSeat} checked. ${secondSeat} can check behind or bet ${betBb} bb.`;
    case "b": return `${firstSeat} bet ${betBb} bb. ${secondSeat} faces a call or fold.`;
    case "kb": return `${firstSeat} checked, then ${secondSeat} bet ${betBb} bb. ${firstSeat} faces a call or fold.`;
    default: return "River decision";
  }
}

export default function RiverPage() {
  const [seed, setSeed] = useState(initialSeed);
  const [metadata, setMetadata] = useState<RiverMetadata | null>(null);
  const [question, setQuestion] = useState<RiverQuestionResponse | null>(null);
  const [feedback, setFeedback] = useState<RiverFeedback | null>(null);
  const [error, setError] = useState("");
  const [stale, setStale] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    riverRequest<RiverMetadata>("", { signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        setMetadata(loaded);
        if (params().get("pack") && params().get("pack") !== loaded.packHash) {
          setStale(true);
          setError("This link belongs to an older river solution. Start a new question.");
        } else setLocation(seed, loaded.packHash);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(cause instanceof Error ? cause.message : "River research is unavailable.");
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
    riverRequest<RiverQuestionResponse>(`/questions/${seed}`, { signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        if (loaded.question.packHash !== metadata.packHash) {
          setStale(true);
          setError("The river solution changed. Start a new question.");
        } else setQuestion(loaded);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(cause instanceof Error ? cause.message : "Could not load a river decision.");
      });
    return () => controller.abort();
  }, [metadata, seed, stale]);

  async function answer(action: RiverAction) {
    if (!metadata || !question || busy || feedback) return;
    setBusy(true);
    setError("");
    try {
      const graded = await postRiver<RiverFeedback>("/grade", {
        seed,
        packHash: metadata.packHash,
        action,
      });
      if (graded.packHash !== metadata.packHash ||
          graded.heroCombo !== question.question.heroCombo ||
          graded.publicHistory !== question.question.publicHistory)
        throw new Error("The river solution changed. Start a new question.");
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
      const latest = await riverRequest<RiverMetadata>("");
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
  return (
    <main className="trainer-page">
      <header className="trainer-topbar">
        <a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a>
        <a className="trainer-back" href="#trainer">← Preflop trainer</a>
      </header>
      <div className="trainer-container">
        <div className="trainer-title-row">
          <div>
            <p className="eyebrow">SOLVER RESEARCH / RIVER</p>
            <h1>River decision lab</h1>
            <p className="trainer-intro">A fixed-board heads-up endgame from our own solver. Practise one decision at a time across check, bet, call and fold nodes.</p>
          </div>
          <span className="trainer-status">VALIDATION ONLY</span>
        </div>
        <div className="trainer-disclosure" role="note">
          The ranges are synthetic and have no documented earlier-street path. This is a single-size, no-raise, no-rake research game, not general river advice or a continuation of the preflop lesson.
        </div>
        {error && <div className="trainer-error" role="alert">{error}{!metadata && <p>Enable the local river research API and load its saved pack to practise.</p>}</div>}
        {stale && metadata && <button className="trainer-button" disabled={busy} onClick={() => void restart()}>Start with current solution</button>}
        {!metadata && !error && <p className="trainer-loading" role="status">Loading river solution…</p>}
        {metadata && !stale && <div className="trainer-layout">
          <section className="trainer-card trainer-play" aria-label="River decision">
            {!spot ? <p className="trainer-loading" role="status">{error ? "Decision unavailable. Try a new question." : "Loading river decision…"}</p> : <>
              <div className="trainer-card-heading"><span>RIVER DECISION</span><span>{spot.actingSeat} to act</span></div>
              <div className="river-board" aria-label="River board">
                {spot.board.map((card) => <span key={`${card.rank}-${card.suit}`} className={card.suit === "HEARTS" || card.suit === "DIAMONDS" ? "red" : ""}>{cardText(card)}</span>)}
              </div>
              <h2>{situation(spot.publicHistory, metadata.firstSeat, metadata.secondSeat, spot.betBb)}</h2>
              <div className="trainer-hand-row"><div><span className="trainer-kicker">YOUR HAND · {spot.actingSeat}</span><div className="trainer-cards">{spot.heroCombo.split(" ").map((card) => <span key={card}>{card}</span>)}</div></div><div className="trainer-pot"><span>Starting pot</span><strong>{spot.potBb.toFixed(1)} bb</strong></div></div>
              <p className="trainer-context">The only bet size is {spot.betBb} bb. Choose an action for this exact hand; the opponent's cards remain hidden.</p>
              <div className="trainer-actions">{spot.legalActions.map((action) => <button key={action} type="button" className={`trainer-button ${action === "f" || action === "k" ? "secondary" : ""}`} disabled={busy || Boolean(feedback)} onClick={() => void answer(action)}>{actionName[action]}</button>)}</div>
              {feedback && <div className="trainer-feedback" aria-live="polite">
                <h3>Decision feedback</h3>
                <p>You chose {actionName[feedback.selectedAction].toLowerCase()}. EV loss: <strong>{feedback.evLossBb.toFixed(2)} bb</strong>.</p>
                <div className="trainer-ev-grid">{spot.legalActions.map((action) => <div key={action}><span>{actionName[action]}</span><strong>{money(feedback.actionEvBb[action])}</strong><small>Solver frequency {frequency(feedback.actionFrequency[action])}</small></div>)}</div>
                <p className="trainer-explanation">EV is measured from an equal share of the starting pot. It averages legal opponent hands, adjusted for the public actions already taken; later choices follow the saved solution.</p>
                {Math.abs(feedback.actionEvBb[spot.legalActions[0]] - feedback.actionEvBb[spot.legalActions[1]]) < 0.1 && <p>The actions are nearly tied in this model. The precise mix describes only these synthetic ranges.</p>}
                <button className="trainer-button" disabled={busy} onClick={() => void restart()}>Next river question</button>
              </div>}
            </>}
          </section>
          <aside className="trainer-card trainer-reference" aria-label="River game details">
            <h2>Game details</h2>
            <dl className="trainer-facts"><div><dt>Seats</dt><dd>{metadata.firstSeat} vs {metadata.secondSeat}</dd></div><div><dt>Pot</dt><dd>{metadata.potBb} bb</dd></div><div><dt>Remaining stack</dt><dd>{metadata.remainingStackBb} bb</dd></div><div><dt>Bet size</dt><dd>{metadata.betBb} bb</dd></div><div><dt>Rake</dt><dd>None</dd></div><div><dt>Best-response gap</dt><dd>{metadata.gameGapBb.toFixed(6)} bb</dd></div></dl>
            <h3>Assumed exact combos</h3>
            <p>These are possible hands in the model, not the opponent's dealt cards.</p>
            <div className="trainer-range"><strong>{metadata.firstSeat} · {metadata.firstRange.length}</strong><div>{metadata.firstRange.map((combo) => <span key={combo}>{combo}</span>)}</div></div>
            <div className="trainer-range"><strong>{metadata.secondSeat} · {metadata.secondRange.length}</strong><div>{metadata.secondRange.map((combo) => <span key={combo}>{combo}</span>)}</div></div>
            <p className="trainer-boundary">Checking can face one bet. A bet can be called or folded to. Raises, other bet sizes and earlier streets are outside this saved game.</p>
            <a className="trainer-lab-link" href="#trainer">Return to preflop trainer ↗</a>
          </aside>
        </div>}
      </div>
    </main>
  );
}
