import { useEffect, useState } from "react";
import HandTable from "./HandTable";
import {
  postFlop,
  flopRequest,
  type FlopAction,
  type FlopCard,
  type FlopHandFeedback,
  type FlopHandSnapshot,
  type FlopMetadata,
} from "./flopApi";

const actionName: Record<FlopAction, string> = {
  k: "Check", b: "Bet", c: "Call", f: "Fold",
};
const rankSymbol: Record<string, string> = {
  TWO: "2", THREE: "3", FOUR: "4", FIVE: "5", SIX: "6", SEVEN: "7",
  EIGHT: "8", NINE: "9", TEN: "T", JACK: "J", QUEEN: "Q", KING: "K", ACE: "A",
};
const suitSymbol: Record<string, string> = {
  CLUBS: "♣", DIAMONDS: "♦", HEARTS: "♥", SPADES: "♠",
};
const cardText = (card: FlopCard) =>
  `${rankSymbol[card.rank] ?? card.rank}${suitSymbol[card.suit] ?? card.suit}`;
const money = (value: number) => `${value >= 0 ? "+" : ""}${value.toFixed(2)} bb`;
const frequency = (value: number) => `${(value * 100).toFixed(1)}%`;
const feedbackActions = (item: FlopHandFeedback): FlopAction[] =>
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
    `#flop-hand?${new URLSearchParams({ seed, hero: String(heroPlayer), pack: packHash })}`);
}

function currentSituation(snapshot: FlopHandSnapshot) {
  const history = snapshot.street === "FLOP" ? snapshot.flopHistory
    : snapshot.street === "TURN" ? snapshot.turnHistory : snapshot.riverHistory;
  const actor = snapshot.heroPlayer === 0 ? "First player" : "Second player";
  if (history === "" || history === "k") return `${actor}: check or bet?`;
  return `${actor}: call or fold?`;
}

export default function FlopHandPage() {
  const [seed, setSeed] = useState(initialSeed);
  const [heroPlayer, setHeroPlayer] = useState<0 | 1>(initialPlayer);
  const [metadata, setMetadata] = useState<FlopMetadata | null>(null);
  const [snapshot, setSnapshot] = useState<FlopHandSnapshot | null>(null);
  const [actions, setActions] = useState<FlopAction[]>([]);
  const [error, setError] = useState("");
  const [stale, setStale] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    flopRequest<FlopMetadata>("", { signal: controller.signal })
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
          setError(cause instanceof Error ? cause.message : "Flop research is unavailable.");
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
    postFlop<FlopHandSnapshot>("/hands/replay", {
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

  async function answer(action: FlopAction) {
    if (!metadata || !snapshot || busy || snapshot.complete) return;
    const nextActions = [...actions, action];
    setBusy(true);
    setError("");
    try {
      const next = await postFlop<FlopHandSnapshot>("/hands/replay", {
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
      const latest = await flopRequest<FlopMetadata>("");
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

  const board = snapshot ? [...snapshot.flop,
    ...(snapshot.turn ? [snapshot.turn] : []),
    ...(snapshot.river ? [snapshot.river] : [])] : [];
  return (
    <main className="trainer-page">
      <header className="trainer-topbar">
        <a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a>
        <a className="trainer-back" href="#solver">← Solver project</a>
      </header>
      <div className="trainer-container hand-play-container">
        <div className="trainer-title-row hand-play-title">
          <div>
            <p className="eyebrow">SOLVER RESEARCH / PARTIAL HAND</p>
            <h1>Play from flop to river</h1>
            <p className="trainer-intro">One hidden opponent hand stays in play across all three streets. Choose your actions and review their EV against the saved solver policy.</p>
          </div>
          <span className="trainer-status">VALIDATION ONLY</span>
        </div>
        <div className="trainer-disclosure hand-play-disclosure" role="note">
          This is a synthetic, no-rake game with one bet size per street and two active players. The ranges are assigned at the flop; they do not model how the hand reached it or ordinary 6-max cash play.
        </div>
        {error && <div className="trainer-error" role="alert">{error}{!metadata && <p>Enable the local flop research API and load its saved pack to play.</p>}</div>}
        {stale && metadata && <button className="trainer-button" disabled={busy} onClick={() => void newHand()}>Start with current solution</button>}
        {!metadata && !error && <p className="trainer-loading" role="status">Loading flop solution…</p>}
        {metadata && !stale && <div className="trainer-layout">
          <section className="trainer-card trainer-play" aria-label="Partial hand">
            {!snapshot ? <p className="trainer-loading" role="status">{error ? "Hand unavailable. Try a new hand." : "Dealing a hand…"}</p> : <>
              <div className="trainer-card-heading"><span>{snapshot.street} · {snapshot.complete ? "HAND COMPLETE" : "YOUR DECISION"}</span><span>{heroPlayer === 0 ? "First player" : "Second player"}</span></div>
              <HandTable street={snapshot.street} heroPlayer={heroPlayer} heroCombo={snapshot.heroCombo}
                opponentCombo={snapshot.opponentCombo} heroRemainingStackBb={snapshot.heroRemainingStackBb}
                potBb={snapshot.potBb} complete={snapshot.complete} showdown={snapshot.showdown}
                publicActions={snapshot.publicActions}
                board={[
                  ...board.map((card) => ({ text: cardText(card), red: card.suit === "HEARTS" || card.suit === "DIAMONDS" })),
                  ...(!snapshot.turn && !snapshot.complete ? [{ text: "?", label: "Turn not yet dealt" }] : []),
                  ...(!snapshot.river && !snapshot.complete ? [{ text: "?", label: "River not yet dealt" }] : []),
                ]} />
              {!snapshot.complete && <>
                <h2>{currentSituation(snapshot)}</h2>
                <div className="trainer-actions">{snapshot.legalActions.map((action) => <button key={action} type="button" className={`trainer-button ${action === "k" || action === "f" ? "secondary" : ""}`} disabled={busy} onClick={() => void answer(action)}>{actionName[action]}</button>)}</div>
              </>}
              {snapshot.publicActions.length > 0 && <details className="hand-history-details"><summary>Full betting history · {snapshot.publicActions.length} actions</summary><ol className="hand-history" aria-label="Public betting history">{snapshot.publicActions.map((event, index) => <li key={index}><span>{event.street}</span> {event.player === 0 ? "First" : "Second"} player {actionName[event.action].toLowerCase()}s</li>)}</ol></details>}
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
            <dl className="trainer-facts"><div><dt>Players</dt><dd>First vs second</dd></div><div><dt>Starting pot</dt><dd>{metadata.potBb} bb</dd></div><div><dt>Starting stack</dt><dd>{metadata.remainingStackBb} bb</dd></div><div><dt>Bet sizes</dt><dd>{metadata.flopBetBb} bb flop · {metadata.turnBetBb} bb turn · {metadata.riverBetBb} bb river</dd></div><div><dt>First range</dt><dd>{metadata.firstRange.join(" / ")}</dd></div><div><dt>Second range</dt><dd>{metadata.secondRange.join(" / ")}</dd></div><div><dt>Rake</dt><dd>None</dd></div><div><dt>Best-response gap</dt><dd>{metadata.gameGapBb.toFixed(6)} bb</dd></div></dl>
            <div className="trainer-actions"><button className="trainer-button secondary" disabled={busy} onClick={() => void newHand(0)}>New hand as first player</button><button className="trainer-button secondary" disabled={busy} onClick={() => void newHand(1)}>New hand as second player</button></div>
            <p className="trainer-boundary">The opponent follows the saved mixed strategy. Their cards stay hidden until showdown. Turn and river cards come from the full unblocked deck.</p>
            <a className="trainer-lab-link" href="#solver">Return to solver project ↗</a>
          </aside>
        </div>}
      </div>
    </main>
  );
}
