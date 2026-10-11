import { useCallback, useEffect, useRef, useState } from "react";
import PokerTable, { type TableMove } from "./PokerTable";
import { actionLabel, type PreflopAction } from "./sixMaxPreflopApi";
import {
  IntervalProtocolError, IntervalRequestError, intervalEqual, intervalRequest, parseIntervalFeedback,
  parseIntervalMetadata, parseIntervalQuestion, parseIntervalReview,
  type IntervalAttempt, type IntervalFeedback, type IntervalMetadata, type IntervalMove,
  type IntervalQuestion, type IntervalReview,
} from "./intervalTrainerApi";
import "./intervalTrainer.css";
import { intervalRange } from "./intervalTrainerFormatting";

const tone = (action: PreflopAction): TableMove => action.startsWith("raise:") ? "raise" : action as TableMove;
const label = (action: PreflopAction, question: IntervalQuestion) => {
  const hero = question.table.players.find((p) => p.seat === question.hero)!;
  return actionLabel(action, hero.committedBb + hero.remainingStackBb);
};
export const classificationLabel = (move: IntervalMove) => move.classification === "WITHIN_LIMIT" ? "Within limit"
  : move.classification === "OUTSIDE_LIMIT" ? "Outside limit" : "Strategy-dependent";
function downloadReview(review: IntervalReview) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(review, null, 2) + "\n"], { type: "application/json" }));
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `pokerlab-interval-review-${review.question.reportHash.slice(0, 12)}.json`;
  anchor.click();
  URL.revokeObjectURL(url);
}

export default function IntervalPreflopPage() {
  const [generation, setGeneration] = useState(0);
  const [metadata, setMetadata] = useState<IntervalMetadata | null>(null);
  const [question, setQuestion] = useState<IntervalQuestion | null>(null);
  const [feedback, setFeedback] = useState<IntervalFeedback | null>(null);
  const [attempts, setAttempts] = useState<IntervalAttempt[]>([]);
  const [review, setReview] = useState<IntervalReview | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [fatal, setFatal] = useState(false);
  const operation = useRef<AbortController | null>(null);
  const pending = useRef(true);
  const saved = useRef<IntervalAttempt[]>([]);

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
      const loaded = parseIntervalMetadata(await intervalRequest("", controller.signal));
      if (controller.signal.aborted) return;
      setMetadata(loaded);
      if (pin !== null && pin !== loaded.reportHash) throw new IntervalProtocolError();
      const q = parseIntervalQuestion(await intervalRequest("/question", controller.signal), loaded);
      if (controller.signal.aborted) return;
      window.history.replaceState(null, "", `#interval-preflop?report=${loaded.reportHash}`);
      setQuestion(q);
    })().catch((cause: unknown) => {
      if (controller.signal.aborted) return;
      setError(cause instanceof Error ? cause.message : "Could not load the interval drill.");
      setFatal(cause instanceof IntervalProtocolError || cause instanceof IntervalRequestError && cause.status === 400);
    }).finally(() => {
      if (!controller.signal.aborted) { pending.current = false; setBusy(false); }
    });
    return () => controller.abort();
  }, [generation]);

  async function submit(action: PreflopAction) {
    if (pending.current || !question || feedback || fatal || saved.current.length >= 100) return;
    pending.current = true; setBusy(true); setError(""); setReview(null);
    const controller = new AbortController(); operation.current = controller;
    try {
      const graded = parseIntervalFeedback(await intervalRequest("/grade", controller.signal,
        { reportHash: question.reportHash, action }), question, action);
      if (controller.signal.aborted) return;
      if (saved.current.length && !intervalEqual(saved.current[0].feedback.alternatives, graded.alternatives))
        throw new IntervalProtocolError();
      const next = [...saved.current, { index: saved.current.length, action, feedback: graded }];
      saved.current = next; setAttempts(next); setFeedback(graded);
    } catch (cause) {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "Could not grade this move. Please try again.");
        setFatal(cause instanceof IntervalProtocolError || cause instanceof IntervalRequestError && cause.status === 400);
      }
    } finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }

  async function loadReview() {
    if (pending.current || !metadata || !question || fatal) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    const captured = saved.current.slice();
    try {
      const result = parseIntervalReview(await intervalRequest("/review", controller.signal,
        { reportHash: question.reportHash, actions: captured.map((a) => a.action) }), metadata, question, captured);
      if (!controller.signal.aborted) setReview(result);
    } catch (cause) {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "Could not review these attempts. Please try again.");
        setFatal(cause instanceof IntervalProtocolError || cause instanceof IntervalRequestError && cause.status === 400);
      }
    } finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }

  useEffect(() => {
    const key = (event: KeyboardEvent) => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey ||
        (event.target instanceof HTMLElement && (event.target.isContentEditable || event.target.closest("input, textarea, select, button, a, summary")))) return;
      const action = question?.legalActions[Number(event.key) - 1];
      if (/^[1-9]$/.test(event.key) && action) { event.preventDefault(); void submit(action); }
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  });

  const reset = () => {
    clear();
    window.history.replaceState(null, "", "#interval-preflop");
    setGeneration((value) => value + 1);
  };

  return <div className="trainer-page preflop-page interval-page">
    <header className="trainer-topbar"><a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a><a className="trainer-back" href="#solver">← Solver project</a></header>
    <main className="trainer-container hand-play-container">
      <div className="trainer-title-row hand-play-title"><div><p className="eyebrow">ONE QUALIFIED QUESTION · TWO ACTIVE PLAYERS</p>
        <h1>Play the preflop interval</h1><p className="trainer-intro">Choose a move, then compare its EV and loss across the supported strategies.</p></div></div>
      <p className="trainer-disclosure hand-play-disclosure" role="note">Research drill · Supplied hand beliefs, no rake, mandatory postflop checkdown.
        This is one scenario at a six-seat table. Source-history reach is unavailable; general GTO trainer admission remains off.</p>
      {error && <div className="trainer-error" role="alert"><p>{error}</p>
        <button type="button" className="trainer-button" disabled={busy} onClick={reset}>{fatal ? "Load current report" : question ? "Reload drill" : "Retry loading"}</button></div>}
      {!question && !error && <p role="status">Loading the qualified interval report…</p>}
      {question && !fatal && <section className="trainer-card trainer-play preflop-play" aria-label="Interval research decision">
        <PokerTable heroSeat={question.hero} heroCombo={question.heroCards} potBb={question.table.potBb} toCallBb={question.table.toCallBb}
          selectedMove={feedback ? { kind: tone(feedback.selected.action), label: label(feedback.selected.action, question) } : undefined}
          players={question.table.players.map((player) => {
            const move = player.lastAction;
            return { seat: player.seat, status: player.status === "FOLDED" ? "Out of hand" : player.status === "ALL_IN" ? "All-in" : "In hand",
              stack: `${player.remainingStackBb} bb behind`, contribution: `${player.committedBb} bb in pot`,
              folded: player.status === "FOLDED", allIn: player.status === "ALL_IN",
              move: move?.kind === "FOLD" ? "fold" : move?.kind === "RAISE_TO" ? "raise" : move?.kind === "CALL" ? "call" : move?.kind === "CHECK" ? "check" : move ? "blind" : "waiting",
              moveLabel: !move ? "Yet to act" : move.kind === "FOLD" ? "Folded" : move.kind === "CHECK" ? "Checked"
                : move.kind === "RAISE_TO" ? `Raised to ${move.amountBb} bb` : move.kind === "CALL" ? `Called to ${move.amountBb} bb` : `Posted ${move.amountBb} bb`,
            };
          })}>
          <p className="interval-prompt">{feedback ? "Your move and its supported ranges" : "BB · Your decision"}
            <span>{attempts.length} / 100 attempts · Loss limit {question.maximumLossBb} bb</span></p>
          <div className="trainer-actions preflop-actions">
            {question.legalActions.map((action, index) => {
              const value = feedback?.alternatives.find((move) => move.action === action);
              return <button key={action} type="button" aria-label={label(action, question)}
                aria-describedby={value ? `interval-value-${index}` : undefined}
                className={`trainer-button poker-action move-${tone(action)}${feedback?.selected.action === action ? " selected" : ""}${value ? " graded" : ""}`}
                disabled={busy || !!feedback || attempts.length >= 100} onClick={() => void submit(action)}>
                <span className="poker-action-label"><kbd aria-hidden="true">{index + 1}</kbd>{label(action, question)}</span>
                {value && <span className="poker-action-value" id={`interval-value-${index}`}>
                  <strong>EV {intervalRange(value.lowerEvBb, value.upperEvBb)}</strong>
                  <small>Loss {intervalRange(value.lowerLossBb, value.upperLossBb)}</small>
                  <small>{classificationLabel(value)}{action === feedback?.selected.action ? " · Your choice" : ""}</small>
                </span>}
              </button>;
            })}
          </div>
          {busy && <p role="status">Checking the requested result…</p>}
          <div className="interval-controls">
            {feedback && attempts.length < 100 && <button className="trainer-button poker-next" disabled={busy} onClick={() => { setFeedback(null); setReview(null); setError(""); }}>Try another move</button>}
            <button className="trainer-button" disabled={busy} onClick={() => void loadReview()}>Review {attempts.length} {attempts.length === 1 ? "attempt" : "attempts"}</button>
            {attempts.length >= 100 && <button className="trainer-button" disabled={busy} onClick={reset}>Start over</button>}
          </div>
          {attempts.length >= 100 && <p role="status">Attempt limit reached. Review and export your attempts, or load the current report to start again.</p>}
          {feedback && <div className="poker-decision-feedback interval-feedback" aria-live="polite">
            <h2>{classificationLabel(feedback.selected)}</h2>
            <p>You chose {label(feedback.selected.action, question).toLowerCase()}. {feedback.selected.classification === "WITHIN_LIMIT"
              ? `Its loss stays within ${question.maximumLossBb} bb across the supported strategies.`
              : feedback.selected.classification === "OUTSIDE_LIMIT" ? `Its loss exceeds ${question.maximumLossBb} bb across the supported strategies.`
                : `Its loss can fall on either side of ${question.maximumLossBb} bb. The supported strategies do not give a single verdict.`}</p>
            <p>Decision EV: <strong>{intervalRange(feedback.selected.lowerEvBb, feedback.selected.upperEvBb)}</strong> · Loss: <strong>{intervalRange(feedback.selected.lowerLossBb, feedback.selected.upperLossBb)}</strong></p>
            <details><summary>Read the ranges and exact endpoints</summary>
              <p>EV is incremental to this decision, with optimal hero continuation against each fixed opponent strategy. Loss bounds compare moves jointly; subtracting separate EV endpoints does not reproduce them. Displayed endpoints are rounded outwards. These are supported strategy ranges, not sampling error or policy frequencies.</p>
              <p>Supplied conditional hand beliefs do not establish how often the preceding action history occurs. Checkdown postflop is required by this model.</p>
              <dl>{feedback.alternatives.map((move) => <div key={move.action}><dt>{label(move.action, question)}</dt>
                <dd>EV [{String(move.lowerEvBb)}, {String(move.upperEvBb)}] bb; loss [{String(move.lowerLossBb)}, {String(move.upperLossBb)}] bb</dd></div>)}</dl>
            </details>
          </div>}
          {review && <section className="interval-review" aria-label="Attempt review">
            <h2>Same question · {review.attempts.length} attempts</h2>
            <p>Within limit: {review.counts.withinLimit} · Outside limit: {review.counts.outsideLimit} · Strategy-dependent: {review.counts.strategyDependent}</p>
            <ol>{review.attempts.map((attempt) => <li key={attempt.index}>{label(attempt.action, question)} · {classificationLabel(attempt.feedback.selected)} · Loss {intervalRange(attempt.feedback.selected.lowerLossBb, attempt.feedback.selected.upperLossBb)}</li>)}</ol>
            <button className="trainer-button" onClick={() => downloadReview(review)}>Download replayable review</button>
            <p>The server checked these explicit attempts. This JSON can be replayed with the interval trainer CLI.</p>
          </section>}
        </PokerTable>
        <details className="interval-context"><summary>Public action history and report</summary>
          <ol>{question.history.map((move, index) => <li key={index}>{move.seat}: {label(move.action, question)}</li>)}</ol>
          <p>Report <code>{question.reportHash}</code></p><p>Security slack {question.securitySlackBb} bb · Minimum conditional reach {question.minimumReach}</p>
        </details>
      </section>}
    </main>
  </div>;
}
