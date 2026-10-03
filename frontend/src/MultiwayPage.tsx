import { useEffect, useState } from "react";
import PokerTable, { type TablePlayer } from "./PokerTable";
import {
  multiwayRequest, postMultiway,
  type MultiwayAction, type MultiwayFeedback, type MultiwayGradedQuestion,
  type MultiwayMetadata, type MultiwayQuestion, type MultiwayReview,
} from "./multiwayApi";

const params = () => new URLSearchParams(window.location.hash.split("?")[1] ?? "");
const money = (amount: number) => `${amount >= 0 ? "+" : ""}${amount.toFixed(2)} bb`;
const frequency = (value: number) => `${(100 * value).toFixed(1)}%`;
const rangeSummary = (counts: number[] = []) =>
  counts.length === 0 ? "limited exact combos" : counts.every((count) => count === counts[0])
    ? `${counts[0]} exact combos per seat`
    : `${counts.join("/")} exact combos by seat`;

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

function initialPlayer() {
  const value = Number(params().get("player"));
  return Number.isInteger(value) && value >= 0 && value <= 5 ? value : 0;
}

function setLocation(seed: string, player: number, packHash: string) {
  window.history.replaceState(null, "", `#multiway?${new URLSearchParams({ seed, player: String(player), pack: packHash })}`);
}

function tablePlayers(metadata: MultiwayMetadata, question: MultiwayQuestion): TablePlayer[] {
  return metadata.seats.map((seat, index) => {
    const aggressor = seat === question.aggressorSeat;
    const action = question.priorResponses.find((response) => response.seat === seat)?.action;
    return { seat, stack: `${metadata.stacksBb[index]} bb stack`,
      contribution: "Starting stack", folded: action === "FOLD", allIn: aggressor,
      status: action === "FOLD" ? "Out of hand" : action === "CALL" || aggressor ? "In hand" : "Waiting",
      move: aggressor ? "raise" : action === "CALL" ? "call" : action === "FOLD" ? "fold" : "waiting",
      moveLabel: aggressor ? "Shoved" : action === "CALL" ? "Called" : action === "FOLD" ? "Folded" : "Yet to act",
    };
  });
}

export default function MultiwayPage() {
  const [seed, setSeed] = useState(initialSeed);
  const [player, setPlayer] = useState(initialPlayer);
  const [metadata, setMetadata] = useState<MultiwayMetadata | null>(null);
  const [index, setIndex] = useState(0);
  const [question, setQuestion] = useState<MultiwayQuestion | null>(null);
  const [feedback, setFeedback] = useState<MultiwayFeedback | null>(null);
  const [answers, setAnswers] = useState<MultiwayAction[]>([]);
  const [review, setReview] = useState<MultiwayReview | null>(null);
  const [error, setError] = useState("");
  const [stale, setStale] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    multiwayRequest<MultiwayMetadata>("", { signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        setMetadata(loaded);
        if (params().get("pack") && params().get("pack") !== loaded.packHash) {
          setStale(true);
          setError("This saved link belongs to an older solution. Start a new session.");
        } else setLocation(seed, player, loaded.packHash);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(cause instanceof Error ? cause.message : "Six-seat research is unavailable.");
      });
    return () => controller.abort();
    // Bind the page to one immutable pack on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!metadata || stale || review) return;
    const controller = new AbortController();
    multiwayRequest<MultiwayQuestion>(`/sessions/${seed}/questions/${index}?player=${player}`, {
      signal: controller.signal,
    })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        if (loaded.packHash !== metadata.packHash || loaded.sessionSeed !== seed ||
            loaded.index !== index || loaded.playerFilter !== player) {
          setStale(true);
          setError("The solution changed. Start a new session.");
        } else setQuestion(loaded);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(cause instanceof Error ? cause.message : "Could not load this decision.");
      });
    return () => controller.abort();
  }, [metadata, seed, player, index, stale, review]);

  async function answer(action: MultiwayAction) {
    if (!metadata || !question || feedback || busy) return;
    setBusy(true);
    setError("");
    try {
      const graded = await postMultiway<MultiwayGradedQuestion>("/grade", {
        sessionSeed: seed, index, player, packHash: metadata.packHash, action,
      });
      if (graded.question.packHash !== metadata.packHash || graded.question.sessionSeed !== seed ||
          graded.question.index !== index || graded.question.playerFilter !== player ||
          graded.question.actingSeat !== question.actingSeat ||
          graded.question.heroCombo !== question.heroCombo)
        throw new Error("The solution changed. Start a new session.");
      setFeedback(graded.feedback);
      setAnswers((current) => [...current.slice(0, index), action]);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Could not grade this decision.";
      if (message.includes("Solution pack has changed") || message.includes("The solution changed")) setStale(true);
      setError(message);
    } finally {
      setBusy(false);
    }
  }

  async function advance() {
    if (!feedback || !metadata || busy) return;
    if (index < metadata.sessionLength - 1) {
      setQuestion(null);
      setFeedback(null);
      setError("");
      setIndex(index + 1);
      return;
    }
    setBusy(true);
    setError("");
    try {
      const result = await postMultiway<MultiwayReview>("/review", {
        sessionSeed: seed, player, packHash: metadata.packHash, actions: answers,
      });
      if (result.packHash !== metadata.packHash || result.attempts.length !== metadata.sessionLength)
        throw new Error("The solution changed. Start a new session.");
      setReview(result);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Could not review this session.";
      if (message.includes("Solution pack has changed") || message.includes("The solution changed")) setStale(true);
      setError(message);
    } finally {
      setBusy(false);
    }
  }

  async function newSession(nextPlayer = player) {
    if (!metadata || busy) return;
    setBusy(true);
    try {
      const latest = await multiwayRequest<MultiwayMetadata>("");
      const nextSeed = freshSeed();
      setLocation(nextSeed, nextPlayer, latest.packHash);
      setMetadata(latest);
      setSeed(nextSeed);
      setPlayer(nextPlayer);
      setIndex(0);
      setAnswers([]);
      setFeedback(null);
      setReview(null);
      setQuestion(null);
      setStale(false);
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not start a new session.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="trainer-page preflop-page">
      <header className="trainer-topbar">
        <a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a>
        <a className="trainer-back" href="#solver">← Solver project</a>
      </header>
      <div className="trainer-container hand-play-container">
        <div className="trainer-title-row hand-play-title">
          <div>
            <p className="eyebrow">SOLVER RESEARCH / SIX SEATS</p>
            <h1>Face the shove</h1>
            <p className="trainer-intro">UTG is all-in. Decide whether to call or fold as the remaining seats respond in order.</p>
          </div>
          <span className="trainer-status">VALIDATION ONLY</span>
        </div>
        <div className="trainer-disclosure hand-play-disclosure" role="note">
          {metadata?.packSchema === "multiway-side-pot-pack/v1"
            ? `Synthetic ranges (${rangeSummary(metadata.rangeComboCounts)}), unequal stacks and side pots, no rake or further betting. This is a six-seat solver test, not a general preflop chart.`
            : `Synthetic ranges (${metadata ? rangeSummary(metadata.rangeComboCounts) : "limited combos"}), equal stacks, no rake or further betting. This is a six-seat solver test, not a general preflop chart.`}
        </div>
        {error && <div className="trainer-error" role="alert">{error}{!metadata && <p>Enable the local multiway research API and load its exact pack to practise.</p>}</div>}
        {stale && metadata && <button className="trainer-button" disabled={busy} onClick={() => void newSession()}>Start with current solution</button>}
        {!metadata && !error && <p className="trainer-loading" role="status">Loading six-seat solution…</p>}
        {metadata && !stale && <div className="trainer-layout preflop-layout">
          <section className="trainer-card trainer-play multiway-play" aria-label="Six-seat decision">
            <div className="multiway-mode">
              <label htmlFor="multiway-player">Practice seat</label>
              <select id="multiway-player" value={player} disabled={busy} onChange={(event) => void newSession(Number(event.target.value))}>
                <option value={0}>Mix responding seats</option>
                {metadata.seats.slice(1).map((seat, offset) => <option key={seat} value={offset + 1}>{seat} only</option>)}
              </select>
            </div>
            {review ? <>
              <div className="trainer-card-heading"><span>SESSION COMPLETE</span><strong>{metadata.sessionLength} / {metadata.sessionLength}</strong></div>
              <h2>Your review</h2>
              <p className="trainer-summary">Total EV loss: <strong>{review.totalEvLossBb.toFixed(2)} bb</strong> · Average: {review.averageEvLossBb.toFixed(2)} bb per decision</p>
              <ol className="trainer-review-list">{review.attempts.map((attempt) => <li key={attempt.question.index}><details>
                <summary><span>{attempt.question.index + 1}. {attempt.question.actingSeat} · {attempt.question.heroCombo}</span><span>{attempt.feedback.selectedAction.toLowerCase()} · {attempt.feedback.evLossBb.toFixed(2)} bb lost</span></summary>
                <PokerTable players={tablePlayers(metadata, attempt.question)} heroSeat={attempt.question.actingSeat}
                  heroCombo={attempt.question.heroCombo} potBb={attempt.question.potBb} toCallBb={attempt.question.callCostBb}
                  selectedMove={{ kind: attempt.feedback.selectedAction === "CALL" ? "call" : "fold", label: attempt.feedback.selectedAction === "CALL" ? "Call" : "Fold" }} />
                <p>Call EV {money(attempt.feedback.callEvBb)} ({frequency(attempt.feedback.callFrequency)}) · Fold EV {money(attempt.feedback.foldEvBb)} ({frequency(attempt.feedback.foldFrequency)})</p>
              </details></li>)}</ol>
              <button className="trainer-button" disabled={busy} onClick={() => void newSession()}>New session</button>
            </> : !question ? (
              <p className="trainer-loading" role="status">{error ? "Decision unavailable. Try a new session." : "Loading decision…"}</p>
            ) : <>
              <div className="trainer-card-heading"><span>DECISION {index + 1} OF {metadata.sessionLength}</span><span>{metadata.packSchema === "multiway-side-pot-pack/v1" ? "Unequal stacks" : `${metadata.stackBb} bb stacks`}</span></div>
              <div className="trainer-progress" aria-hidden="true"><span style={{ width: `${(index + 1) / metadata.sessionLength * 100}%` }} /></div>
              <PokerTable players={tablePlayers(metadata, question)} heroSeat={question.actingSeat}
                heroCombo={question.heroCombo} potBb={question.potBb} toCallBb={question.callCostBb}
                selectedMove={feedback ? { kind: feedback.selectedAction === "CALL" ? "call" : "fold", label: feedback.selectedAction === "CALL" ? "Call" : "Fold" } : undefined}>
                <h2>{question.actingSeat}: call or fold?</h2>
                <div className="trainer-actions preflop-actions">{question.legalActions.map((action) => <button key={action} type="button" className={`trainer-button poker-action move-${action.toLowerCase()} ${feedback?.selectedAction === action ? "selected" : ""}`} disabled={busy || Boolean(feedback)} onClick={() => void answer(action)}>{action === "CALL" ? "Call" : "Fold"}</button>)}</div>
              </PokerTable>
              {metadata.packSchema === "multiway-side-pot-pack/v1" && <p className="multiway-side-pot-note">A short caller can win the main pot while deeper callers contest side pots. Your EV accounts for every possible later call or fold.</p>}
              {feedback && <div className="trainer-feedback" aria-live="polite">
                <h3>Decision feedback</h3>
                <p>You chose {feedback.selectedAction.toLowerCase()}. EV loss: <strong>{feedback.evLossBb.toFixed(2)} bb</strong>.</p>
                <div className="trainer-ev-grid"><div><span>Call</span><strong>{money(feedback.callEvBb)}</strong><small>Solver frequency {frequency(feedback.callFrequency)}</small></div><div><span>Fold</span><strong>{money(feedback.foldEvBb)}</strong><small>Solver frequency {frequency(feedback.foldFrequency)}</small></div></div>
                <p className="trainer-explanation">These EVs average over hidden joint hands compatible with your cards and the prior calls or folds. They are not the payoff against one revealed deal.</p>
                <button className="trainer-button" disabled={busy} onClick={() => void advance()}>{index === metadata.sessionLength - 1 ? "Review session" : "Next decision"}</button>
              </div>}
            </>}
          </section>
          <aside className="trainer-card trainer-reference" aria-label="Six-seat game details">
            <h2>Game details</h2>
            <dl className="trainer-facts">
              <div><dt>Game</dt><dd>Forced UTG shove · call/fold response</dd></div>
              <div><dt>Ranges</dt><dd>{rangeSummary(metadata.rangeComboCounts)}</dd></div>
              <div><dt>Stacks</dt><dd>{metadata.packSchema === "multiway-side-pot-pack/v1" ? "By seat, shown at the table" : `${metadata.stackBb} bb each`}</dd></div>
              {metadata.packSchema === "multiway-side-pot-pack/v1" && <div><dt>Side pots</dt><dd>Settled by each caller's contribution</dd></div>}
              <div><dt>Dead money</dt><dd>{metadata.deadMoneyBb} bb</dd></div>
              <div><dt>Rake</dt><dd>None</dd></div>
              <div><dt>Payoffs</dt><dd>Exact board enumeration</dd></div>
              <div><dt>Measured deviation</dt><dd>{metadata.nashConvBb.toFixed(6)} bb</dd></div>
            </dl>
            <p className="trainer-boundary">Only the acting seat's cards are shown. All six hands are dealt with card removal, including folded seats. The solver covers this restricted response tree, not opens, re-raises or postflop betting.</p>
            <a className="trainer-lab-link" href="#trainer">Try the two-player preflop drill ↗</a>
          </aside>
        </div>}
      </div>
    </main>
  );
}
