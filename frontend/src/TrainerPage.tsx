import { useEffect, useState } from "react";
import {
  postTrainer,
  trainerRequest,
  type GradedQuestion,
  type PriorAction,
  type SessionQuestion,
  type TrainerAction,
  type TrainerFeedback,
  type TrainerMetadata,
  type TrainerReview,
} from "./trainerApi";

const seats = ["UTG", "HJ", "CO", "BTN", "SB", "BB"];
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
  const query = new URLSearchParams({ seed, pack: packHash });
  window.history.replaceState(null, "", `#trainer?${query}`);
}

function actionLabel(action: PriorAction) {
  switch (action.kind) {
    case "POST_SMALL_BLIND":
      return "posts small blind";
    case "POST_BIG_BLIND":
      return "posts big blind";
    case "RAISE_TO":
      return `raises to ${action.amountBb} bb`;
    case "FOLD":
      return "folds";
  }
}

const money = (amount: number) => `${amount >= 0 ? "+" : ""}${amount.toFixed(2)} bb`;
const frequency = (value: number) => `${(100 * value).toFixed(1)}%`;

export default function TrainerPage() {
  const [seed, setSeed] = useState(initialSeed);
  const [metadata, setMetadata] = useState<TrainerMetadata | null>(null);
  const [index, setIndex] = useState(0);
  const [question, setQuestion] = useState<SessionQuestion | null>(null);
  const [feedback, setFeedback] = useState<TrainerFeedback | null>(null);
  const [answers, setAnswers] = useState<TrainerAction[]>([]);
  const [review, setReview] = useState<TrainerReview | null>(null);
  const [error, setError] = useState("");
  const [stale, setStale] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    trainerRequest<TrainerMetadata>("", { signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        setMetadata(loaded);
        if (params().get("pack") && params().get("pack") !== loaded.packHash) {
          setStale(true);
          setError("This saved link belongs to an older solution. Start a new session to use the current pack.");
        } else {
          setLocation(seed, loaded.packHash);
        }
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(cause instanceof Error ? cause.message : "The trainer is unavailable.");
      });
    return () => controller.abort();
    // Load one immutable pack when the page mounts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!metadata || stale || review) return;
    const controller = new AbortController();
    setQuestion(null);
    setFeedback(null);
    setError("");
    trainerRequest<SessionQuestion>(`/sessions/${seed}/questions/${index}`, {
      signal: controller.signal,
    })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        if (loaded.packHash !== metadata.packHash) {
          setStale(true);
          setError("The solution changed. Start a new session.");
        } else setQuestion(loaded);
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(cause instanceof Error ? cause.message : "Could not load this decision.");
      });
    return () => controller.abort();
  }, [metadata, seed, index, stale, review]);

  async function answer(action: TrainerAction) {
    if (!metadata || !question || busy || feedback) return;
    setBusy(true);
    setError("");
    try {
      const graded = await postTrainer<GradedQuestion>("/sessions/grade", {
        sessionSeed: seed,
        index,
        packHash: metadata.packHash,
        action,
      });
      if (graded.question.packHash !== metadata.packHash ||
          graded.question.question.heroCombo !== question.question.heroCombo)
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
      setIndex(index + 1);
      return;
    }
    setBusy(true);
    setError("");
    try {
      const result = await postTrainer<TrainerReview>("/sessions/review", {
        sessionSeed: seed,
        packHash: metadata.packHash,
        actions: answers,
      });
      if (result.packHash !== metadata.packHash) throw new Error("The solution changed. Start a new session.");
      setReview(result);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Could not review this session.";
      if (message.includes("Solution pack has changed") || message.includes("The solution changed")) setStale(true);
      setError(message);
    } finally {
      setBusy(false);
    }
  }

  async function restart() {
    if (!metadata) return;
    setBusy(true);
    try {
      const latest = await trainerRequest<TrainerMetadata>("");
      const next = freshSeed();
      setLocation(next, latest.packHash);
      setMetadata(latest);
      setSeed(next);
      setIndex(0);
      setAnswers([]);
      setFeedback(null);
      setReview(null);
      setStale(false);
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not start a new session.");
    } finally {
      setBusy(false);
    }
  }

  const spot = question?.question;
  return (
    <main className="trainer-page">
      <header className="trainer-topbar">
        <a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a>
        <a className="trainer-back" href="#solver">← Equity Lab</a>
      </header>
      <div className="trainer-container hand-play-container">
        <div className="trainer-title-row hand-play-title">
          <div>
            <p className="eyebrow">SOLVER DEMO / PREFLOP</p>
            <h1>Face the five-bet</h1>
            <p className="trainer-intro">Ten decisions in one precisely defined six-seat cash spot. You play UTG; BTN is the only other active player.</p>
          </div>
          <span className="trainer-status">VALIDATION ONLY</span>
        </div>

        <div className="trainer-disclosure hand-play-disclosure" role="note">
          This uses synthetic ranges and a no-rake, all-in decision tree. Its EVs describe this saved model, not general 100bb cash strategy.
        </div>

        {error && <div className="trainer-error" role="alert">{error}{!metadata && <p>Enable the local research trainer and load the exact validation pack to practise.</p>}</div>}
        {stale && metadata && <button className="trainer-button" disabled={busy} onClick={() => void restart()}>Start with current solution</button>}
        {!metadata && !error && <p className="trainer-loading" role="status">Loading solution pack…</p>}

        {metadata && !stale && (
          <div className="trainer-layout">
            <section className="trainer-card trainer-play" aria-label="Preflop decision">
              {review ? (
                <>
                  <div className="trainer-card-heading"><span>SESSION COMPLETE</span><strong>10 / 10</strong></div>
                  <h2>Your review</h2>
                  <p className="trainer-summary">Total EV loss: <strong>{review.totalEvLossBb.toFixed(2)} bb</strong> · Average: {review.averageEvLossBb.toFixed(2)} bb per decision</p>
                  <ol className="trainer-review-list">
                    {review.attempts.map((attempt) => (
                      <li key={attempt.question.index}>
                        <details>
                          <summary><span>{attempt.question.index + 1}. {attempt.question.question.heroCombo}</span><span>{attempt.feedback.selectedAction.toLowerCase()} · {attempt.feedback.evLossBb.toFixed(2)} bb lost</span></summary>
                          <p>Shove EV {money(attempt.feedback.shoveEvBb)} ({frequency(attempt.feedback.shoveFrequency)}) · Fold EV {money(attempt.feedback.foldEvBb)} ({frequency(attempt.feedback.foldFrequency)})</p>
                        </details>
                      </li>
                    ))}
                  </ol>
                  <button className="trainer-button" disabled={busy} onClick={() => void restart()}>New session</button>
                </>
              ) : !spot ? (
                <p className="trainer-loading" role="status">{error ? "Decision unavailable. Try a new session." : "Loading decision…"}</p>
              ) : (
                <>
                  <div className="trainer-card-heading"><span>DECISION {index + 1} OF {metadata.sessionLength}</span><span>{spot.effectiveStackBb} bb stacks</span></div>
                  <div className="trainer-progress" aria-hidden="true"><span style={{ width: `${(index + 1) / metadata.sessionLength * 100}%` }} /></div>
                  <div className="trainer-table" aria-label="Six-seat table">
                    {seats.map((seat) => {
                      const previous = [...spot.priorActions].reverse().find((action) => action.seat === seat);
                      const active = seat === spot.heroSeat || seat === spot.opponentSeat;
                      return <div key={seat} className={`trainer-seat ${seat === spot.heroSeat ? "hero" : seat === spot.opponentSeat ? "opponent" : "folded"}`}>
                        <strong>{seat}<span>{seat === spot.heroSeat ? "You · to act" : seat === spot.opponentSeat ? "Opponent" : "Out"}</span></strong>
                        <small>{previous ? actionLabel(previous) : active ? "In hand" : "Folded"}</small>
                      </div>;
                    })}
                  </div>
                  <div className="trainer-hand-row"><div><span className="trainer-kicker">YOUR HAND · {spot.heroSeat}</span><div className="trainer-cards">{spot.heroCombo.split(" ").map((card) => <span key={card}>{card}</span>)}</div></div><div className="trainer-pot"><span>Current pot</span><strong>{spot.potBb.toFixed(1)} bb</strong></div></div>
                  <p className="trainer-context">You have committed {spot.heroCommittedBb} bb. BTN has committed {spot.opponentCommittedBb} bb. Do you shove the remaining stack or fold?</p>
                  <div className="trainer-actions">
                    {spot.legalActions.map((action) => <button key={action} type="button" className={`trainer-button ${action === "FOLD" ? "secondary" : ""}`} disabled={busy || Boolean(feedback)} onClick={() => void answer(action)}>{action === "SHOVE" ? "Shove" : "Fold"}</button>)}
                  </div>
                  {feedback && <div className="trainer-feedback" aria-live="polite">
                    <h3>Decision feedback</h3>
                    <p>You chose {feedback.selectedAction.toLowerCase()}. EV loss: <strong>{feedback.evLossBb.toFixed(2)} bb</strong>.</p>
                    <div className="trainer-ev-grid"><div><span>Shove</span><strong>{money(feedback.shoveEvBb)}</strong><small>Solver frequency {frequency(feedback.shoveFrequency)}</small></div><div><span>Fold</span><strong>{money(feedback.foldEvBb)}</strong><small>Solver frequency {frequency(feedback.foldFrequency)}</small></div></div>
                    <p className="trainer-explanation">Folding gives up the {spot.heroCommittedBb} bb already committed. Shoving can win when BTN folds; if BTN calls, the saved model evaluates every possible board. BTN’s actual cards stay hidden, so these EVs average over its assumed range.</p>
                    {Math.abs(feedback.shoveEvBb - feedback.foldEvBb) < 0.1 && <p>These actions are nearly tied in this model. The precise mix is sensitive to the assumed range weights.</p>}
                    {Math.abs(feedback.shoveEvBb - feedback.foldEvBb) >= 0.1 && Math.abs(feedback.shoveEvBb - feedback.foldEvBb) < 1 && <p>The action EVs are within 1 bb here. Range changes can reverse a close preference.</p>}
                    <button className="trainer-button" disabled={busy} onClick={() => void advance()}>{index === metadata.sessionLength - 1 ? "Review session" : "Next decision"}</button>
                  </div>}
                </>
              )}
            </section>

            <aside className="trainer-card trainer-reference" aria-label="Spot details">
              <h2>Spot details</h2>
              <dl className="trainer-facts"><div><dt>Format</dt><dd>6-max cash · heads-up at decision</dd></div><div><dt>Stack</dt><dd>{metadata.effectiveStackBb} bb</dd></div><div><dt>Rake</dt><dd>None in this model</dd></div><div><dt>Payoffs</dt><dd>Exact board enumeration</dd></div><div><dt>Measured game gap</dt><dd>{metadata.estimatedGameGapBb.toFixed(6)} bb</dd></div></dl>
              {spot && <><h3>How we got here</h3><ol className="trainer-history">{spot.priorActions.map((action, item) => <li key={item}><strong>{action.seat}</strong> {actionLabel(action)}</li>)}</ol></>}
              <h3>Assumed exact combos</h3>
              <p>These are the model’s possible hands, not the opponent’s dealt cards.</p>
              <div className="trainer-range"><strong>UTG · {metadata.heroRange.length}</strong><div>{metadata.heroRange.map((combo) => <span key={combo}>{combo}</span>)}</div></div>
              <div className="trainer-range"><strong>BTN · {metadata.opponentRange.length}</strong><div>{metadata.opponentRange.map((combo) => <span key={combo}>{combo}</span>)}</div></div>
              <p className="trainer-boundary">The solver considers shove/fold here and BTN call/fold after a shove. Other bet sizes, rake and later streets are outside this exercise.</p>
              <a className="trainer-lab-link" href="#new-simulation">Open Equity Lab ↗</a>
              <p><a className="trainer-lab-link" href="#river">Try the river research drill ↗</a></p>
              <p className="trainer-boundary">Equity Lab compares specified hands at showdown. Strategy EV here averages BTN’s range and possible actions.</p>
            </aside>
          </div>
        )}
      </div>
    </main>
  );
}
