import { useCallback, useEffect, useRef, useState } from "react";
import LaterFlopIntervalDecisionTable, { laterFlopActionLabel as label, laterFlopClassificationLabel } from "./LaterFlopIntervalDecisionTable";
import { type LaterFlopAction } from "./laterFlopIntervalApi";
import {
  LaterFlopIntervalProtocolError, LaterFlopIntervalRequestError, laterFlopIntervalRequest, parseLaterFlopIntervalFeedback,
  parseLaterFlopIntervalMetadata, parseLaterFlopIntervalQuestion, parseLaterFlopIntervalReview,
  type LaterFlopIntervalAttempt, type LaterFlopIntervalFeedback, type LaterFlopIntervalMetadata,
  type LaterFlopIntervalQuestion, type LaterFlopIntervalReview,
} from "./laterFlopIntervalApi";
import "./intervalTrainer.css";
import "./flopInterval.css";
import { intervalEqual } from "./intervalTrainerApi";
import { intervalRange } from "./intervalTrainerFormatting";

function downloadReview(review: LaterFlopIntervalReview) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(review, null, 2) + "\n"], { type: "application/json" }));
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `pokerlab-later-flop-interval-review-${review.question.reportHash.slice(0, 12)}.json`;
  anchor.click();
  URL.revokeObjectURL(url);
}

export default function LaterFlopIntervalPage() {
  const [generation, setGeneration] = useState(0);
  const [metadata, setMetadata] = useState<LaterFlopIntervalMetadata | null>(null);
  const [question, setQuestion] = useState<LaterFlopIntervalQuestion | null>(null);
  const [feedback, setFeedback] = useState<LaterFlopIntervalFeedback | null>(null);
  const [attempts, setAttempts] = useState<LaterFlopIntervalAttempt[]>([]);
  const [review, setReview] = useState<LaterFlopIntervalReview | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [fatal, setFatal] = useState(false);
  const operation = useRef<AbortController | null>(null);
  const pending = useRef(true);
  const saved = useRef<LaterFlopIntervalAttempt[]>([]);

  const clear = useCallback(() => {
    operation.current?.abort(); pending.current = true; saved.current = [];
    setBusy(true); setError(""); setFatal(false); setMetadata(null); setQuestion(null);
    setFeedback(null); setAttempts([]); setReview(null);
  }, []);

  useEffect(() => {
    const changed = () => {
      clear();
      setGeneration((value) => value + 1);
    };
    window.addEventListener("hashchange", changed);
    return () => { window.removeEventListener("hashchange", changed); operation.current?.abort(); };
  }, [clear]);

  useEffect(() => {
    const controller = new AbortController();
    operation.current = controller; pending.current = true; saved.current = [];
    const pin = new URLSearchParams(window.location.hash.split("?")[1] ?? "").get("report");
    void (async () => {
      const loaded = parseLaterFlopIntervalMetadata(await laterFlopIntervalRequest("", controller.signal));
      if (controller.signal.aborted) return;
      setMetadata(loaded);
      if (pin !== null && pin !== loaded.reportHash) throw new LaterFlopIntervalProtocolError();
      const q = parseLaterFlopIntervalQuestion(await laterFlopIntervalRequest("/question", controller.signal), loaded);
      if (controller.signal.aborted) return;
      window.history.replaceState(null, "", `#interval-flop-response?report=${loaded.reportHash}`);
      setQuestion(q);
    })().catch((cause: unknown) => {
      if (controller.signal.aborted) return;
      setError(cause instanceof Error ? cause.message : "Could not load the interval drill.");
      setFatal(cause instanceof LaterFlopIntervalProtocolError || cause instanceof LaterFlopIntervalRequestError && cause.status === 400);
    }).finally(() => {
      if (!controller.signal.aborted) { pending.current = false; setBusy(false); }
    });
    return () => controller.abort();
  }, [generation]);

  async function submit(action: LaterFlopAction) {
    if (pending.current || !question || feedback || fatal || saved.current.length >= 100) return;
    pending.current = true; setBusy(true); setError(""); setReview(null);
    const controller = new AbortController(); operation.current = controller;
    try {
      const graded = parseLaterFlopIntervalFeedback(await laterFlopIntervalRequest("/grade", controller.signal,
        { reportHash: question.reportHash, action }), question, action);
      if (controller.signal.aborted) return;
      if (saved.current.length && !intervalEqual(saved.current[0].feedback.alternatives, graded.alternatives))
        throw new LaterFlopIntervalProtocolError();
      const next = [...saved.current, { index: saved.current.length, action, feedback: graded }];
      saved.current = next; setAttempts(next); setFeedback(graded);
    } catch (cause) {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "Could not grade this move. Please try again.");
        setFatal(cause instanceof LaterFlopIntervalProtocolError || cause instanceof LaterFlopIntervalRequestError && cause.status === 400);
      }
    } finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }

  async function loadReview() {
    if (pending.current || !metadata || !question || fatal) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    const captured = saved.current.slice();
    try {
      const result = parseLaterFlopIntervalReview(await laterFlopIntervalRequest("/review", controller.signal,
        { reportHash: question.reportHash, actions: captured.map((a) => a.action) }), metadata, question, captured);
      if (!controller.signal.aborted) setReview(result);
    } catch (cause) {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "Could not review these attempts. Please try again.");
        setFatal(cause instanceof LaterFlopIntervalProtocolError || cause instanceof LaterFlopIntervalRequestError && cause.status === 400);
      }
    } finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }

  useEffect(() => {
    const key = (event: KeyboardEvent) => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey ||
        (event.target instanceof HTMLElement && (event.target.isContentEditable || event.target.closest("input, textarea, select, button, a, summary")))) return;
      const action = question?.context.legalActions[Number(event.key) - 1];
      if (/^[1-9]$/.test(event.key) && action) { event.preventDefault(); void submit(action); }
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  });

  const reset = () => {
    clear();
    window.history.replaceState(null, "", "#interval-flop-response");
    setGeneration((value) => value + 1);
  };

  return <div className="trainer-page preflop-page interval-page flop-interval-page later-flop-interval-page">
    <header className="trainer-topbar"><a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a><a className="trainer-back" href="#interval-flop">Initial flop drill</a><a className="trainer-back" href="#solver">← Solver project</a></header>
    <main className="trainer-container hand-play-container">
      <div className="trainer-title-row hand-play-title"><div><p className="eyebrow">FLOP RESPONSE · TWO ACTIVE PLAYERS</p>
        <h1>Facing a flop bet</h1><p className="trainer-intro">BB checked. BTN bet. Choose fold or call, then compare the supported EV and loss ranges.</p></div></div>
      <p className="trainer-disclosure hand-play-disclosure" role="note">Research drill · Supplied hand beliefs, no rake, one flop bet then exact turn/river checkdown.</p>
      {error && <div className="trainer-error" role="alert"><p>{error}</p>
        <button type="button" className="trainer-button" disabled={busy} onClick={reset}>{fatal ? "Load current report" : question ? "Reload drill" : "Retry loading"}</button></div>}
      {!question && !error && <p role="status">Loading the qualified interval report…</p>}
      {question && !fatal && <section className="trainer-card trainer-play preflop-play" aria-label="Physical flop interval decision">
        <LaterFlopIntervalDecisionTable question={question} feedback={feedback} knownMoves={attempts[0]?.feedback.alternatives} attemptCount={attempts.length} busy={busy}
          submit={(action) => void submit(action)} controls={<>
          <div className="interval-controls">
            {feedback && attempts.length < 100 && <button className="trainer-button poker-next" disabled={busy} onClick={() => { setFeedback(null); setReview(null); setError(""); }}>Try another move</button>}
            <button className="trainer-button" disabled={busy} onClick={() => void loadReview()}>Review {attempts.length} {attempts.length === 1 ? "attempt" : "attempts"}</button>
            {attempts.length >= 100 && <button className="trainer-button" disabled={busy} onClick={reset}>Start over</button>}
          </div>
          {attempts.length >= 100 && <p role="status">Attempt limit reached. Review and export your attempts, or load the current report to start again.</p>}
          </>}>
          {review && <section className="interval-review" aria-label="Attempt review">
            <h2>Same question · {review.attempts.length} attempts</h2>
            <p>Within limit: {review.counts.withinLimit} · Outside limit: {review.counts.outsideLimit} · Strategy-dependent: {review.counts.strategyDependent}</p>
            <ol>{review.attempts.map((attempt) => <li key={attempt.index}>{label(attempt.action, question)} · {laterFlopClassificationLabel(attempt.feedback.selected)} · Loss {intervalRange(attempt.feedback.selected.lowerLossBb, attempt.feedback.selected.upperLossBb)}</li>)}</ol>
            <button className="trainer-button" onClick={() => downloadReview(review)}>Download replayable review</button>
            <p>The server checked these explicit attempts. This JSON can be replayed with the later flop interval trainer CLI.</p>
          </section>}
        </LaterFlopIntervalDecisionTable>
      </section>}
    </main>
  </div>;
}
