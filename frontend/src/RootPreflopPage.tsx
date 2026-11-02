import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import PokerTable, { type TableMove } from "./PokerTable";
import { intervalRange } from "./intervalTrainerFormatting";
import { parseRootGrade, parseRootMetadata, parseRootQuestion, parseRootReview, readRootReview, rootChoices, rootMaximumAttempts,
  rootRequest, rootReviewDocument, rootSessionKey, rootSessionReview, RootProtocolError, RootRequestError,
  type RootAction, type RootAttempt, type RootGrade, type RootMetadata, type RootMove, type RootQuestion, type RootReview } from "./rootPreflopApi";
import "./intervalTrainer.css";
import "./rootPreflop.css";

export const rootActionLabel = (action: RootAction) => action === "raise:9.0" ? "Raise to 9 bb" : action === "call" ? "Call 2 bb" : "Fold";
export const rootVerdict = (move: RootMove) => move.classification === "WITHIN_LIMIT" ? "Within limit"
  : move.classification === "OUTSIDE_LIMIT" ? "Outside limit" : "Unresolved";
const tone = (action: RootAction): TableMove => action === "raise:9.0" ? "raise" : action;

export default function RootPreflopPage() {
  const [generation, setGeneration] = useState(0);
  const [metadata, setMetadata] = useState<RootMetadata | null>(null);
  const [question, setQuestion] = useState<RootQuestion | null>(null);
  const [attempts, setAttempts] = useState<RootAttempt[]>([]);
  const [feedback, setFeedback] = useState<RootGrade | null>(null);
  const [review, setReview] = useState<RootReview | null>(null);
  const [busy, setBusy] = useState(true), [fatal, setFatal] = useState(false);
  const [error, setError] = useState(""), [notice, setNotice] = useState("");
  const operation = useRef<AbortController | null>(null), pending = useRef(true), saved = useRef<RootAttempt[]>([]);
  const clear = useCallback(() => {
    operation.current?.abort(); pending.current = true; saved.current = [];
    setMetadata(null); setQuestion(null); setAttempts([]); setFeedback(null); setReview(null);
    setBusy(true); setFatal(false); setError(""); setNotice("");
  }, []);
  useEffect(() => {
    const changed = () => { clear(); setGeneration(g => g + 1); };
    window.addEventListener("hashchange", changed);
    return () => { window.removeEventListener("hashchange", changed); operation.current?.abort(); };
  }, [clear]);
  useEffect(() => {
    const controller = new AbortController(); operation.current = controller; pending.current = true;
    void (async () => {
      const m = parseRootMetadata(await rootRequest("", controller.signal));
      if (controller.signal.aborted) return;
      const [route, query] = window.location.hash.split("?");
      const params = new URLSearchParams(query ?? "");
      if (route !== "#root-preflop" || [...params.keys()].some(k => k !== "study" || params.getAll(k).length !== 1) ||
        params.has("study") && params.get("study") !== m.studyHash) throw new RootProtocolError();
      const q = parseRootQuestion(await rootRequest(`/questions/0?studyHash=${m.studyHash}`, controller.signal), m);
      if (controller.signal.aborted) return;
      setMetadata(m); setQuestion(q);
      window.history.replaceState(null, "", `#root-preflop?study=${m.studyHash}`);
      try {
        const text = localStorage.getItem(rootSessionKey);
        if (text !== null) {
          const restored = readRootReview(text, m, q);
          saved.current = restored.attempts; setAttempts(restored.attempts);
          setFeedback(restored.attempts.at(-1)?.grade ?? null);
          if (restored.attempts.length) setNotice(`Restored ${restored.attempts.length} saved attempts for this study.`);
        }
      } catch {
        try { localStorage.removeItem(rootSessionKey); } catch { /* Practice remains available without storage. */ }
        setNotice("Saved practice could not be restored. Starting a fresh session.");
      }
    })().catch((cause: unknown) => {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "Could not load preflop practice.");
        setFatal(cause instanceof RootProtocolError || cause instanceof RootRequestError && [400, 404].includes(cause.status));
      }
    }).finally(() => { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } });
    return () => controller.abort();
  }, [generation]);

  function fail(cause: unknown, controller: AbortController) {
    if (controller.signal.aborted) return;
    setError(cause instanceof Error ? cause.message : "The request failed. Please try again.");
    setFatal(cause instanceof RootProtocolError || cause instanceof RootRequestError && [400, 404].includes(cause.status));
  }
  async function submit(action: RootAction) {
    if (pending.current || !question || !metadata || feedback || fatal || saved.current.length >= rootMaximumAttempts) return;
    pending.current = true; setBusy(true); setError(""); setReview(null);
    const controller = new AbortController(); operation.current = controller;
    try {
      const grade = parseRootGrade(await rootRequest("/grade", controller.signal, { studyHash: metadata.studyHash,
        sourceCaseIndex: 0, action }), question, action);
      if (controller.signal.aborted) return;
      const next = [...saved.current, { ordinal: saved.current.length, attempt: { sourceCaseIndex: 0, action }, grade }];
      saved.current = next; setAttempts(next); setFeedback(grade);
      try { localStorage.setItem(rootSessionKey, JSON.stringify(rootSessionReview(metadata, question, next))); }
      catch { setNotice("Your choice is recorded for this visit. Browser storage is unavailable, so reloading will not save it."); }
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  async function sessionReview(exporting = false) {
    if (pending.current || !metadata || !question || fatal) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    const captured = saved.current.slice();
    try {
      if (exporting) {
        const text = await rootReviewDocument(controller.signal, metadata, question, captured);
        if (controller.signal.aborted) return;
        const url = URL.createObjectURL(new Blob([text], { type: "application/json" }));
        const anchor = document.createElement("a"); anchor.href = url;
        anchor.download = `pokerlab-root-preflop-review-${metadata.studyHash.slice(0, 12)}.json`;
        try { anchor.click(); } finally { URL.revokeObjectURL(url); }
      } else {
        const result = parseRootReview(await rootRequest("/review", controller.signal, { studyHash: metadata.studyHash,
          choices: rootChoices(captured) }), metadata, question, rootChoices(captured));
        if (!controller.signal.aborted) setReview(result);
      }
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  useLayoutEffect(() => {
    const key = (event: KeyboardEvent) => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey ||
        (event.target instanceof HTMLElement && (event.target.isContentEditable || event.target.closest("input, textarea, select, button, a, summary")))) return;
      const action = question?.context.actions[Number(event.key) - 1]?.action;
      if (/^[1-3]$/.test(event.key) && action) { event.preventDefault(); void submit(action); }
    };
    window.addEventListener("keydown", key); return () => window.removeEventListener("keydown", key);
  });
  function startOver() {
    if (pending.current) return;
    saved.current = []; setAttempts([]); setFeedback(null); setReview(null); setError(""); setNotice("");
    try { localStorage.removeItem(rootSessionKey); } catch { setNotice("Browser storage could not be cleared. Practice is reset for this visit."); }
  }
  function reload() { clear(); window.history.replaceState(null, "", "#root-preflop"); setGeneration(g => g + 1); }
  return <div className="trainer-page preflop-page interval-page root-preflop-page">
    <header className="trainer-topbar"><a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a><a className="trainer-back" href="#solver">← Solver project</a></header>
    <main className="trainer-container hand-play-container">
      <div className="trainer-title-row hand-play-title"><div><p className="eyebrow">PREFLOP · BB VS BUTTON</p>
        <h1>Defend pocket nines</h1><p className="trainer-intro">The button raised to 3 bb. Compare the EV ranges, then choose your response.</p></div></div>
      <p className="trainer-disclosure hand-play-disclosure" role="note">Research exercise · Six seats, two active players · Supplied ranges, selected-flop continuation model · Raise remains unresolved.</p>
      {error && <div className="trainer-error" role="alert"><p>{error}</p><button className="trainer-button" disabled={busy}
        onClick={fatal || !question ? reload : () => setError("")}>{fatal ? "Load current study" : question ? "Dismiss error" : "Retry loading"}</button></div>}
      {!question && !error && <p role="status">Loading preflop practice…</p>}
      {question && !fatal && <section className="trainer-card trainer-play preflop-play" aria-label="Root preflop research decision">
        <PokerTable heroSeat="BB" heroCombo={question.heroCards} potBb={question.context.potBb} toCallBb={question.context.toCallBb}
          selectedMove={feedback ? { kind: tone(feedback.move.action), label: rootActionLabel(feedback.move.action) } : undefined}
          players={question.context.players.map(player => {
            const last = question.context.history.filter(h => h.seat === player.seat).at(-1)!;
            return { seat: player.seat, status: player.status === "FOLDED" ? "Out of hand" : "In hand", folded: player.status === "FOLDED", allIn: false,
              stack: `${player.remainingStackBb} bb behind`, contribution: `${player.committedBb} bb in pot`,
              move: last.kind === "FOLD" ? "fold" : last.kind === "RAISE_TO" ? "raise" : "blind",
              moveLabel: last.kind === "FOLD" ? "Folded" : last.kind === "RAISE_TO" ? `Raised to ${last.amountBb} bb` : `Posted ${last.amountBb} bb` };
          })}>
          <p className="interval-prompt">BB · {feedback ? "Your choice is recorded" : "Your decision"}<span>{attempts.length} / {rootMaximumAttempts} attempts · Keys 1–3</span></p>
          <div className="trainer-actions preflop-actions">{question.moves.map((move, index) => <button type="button" key={move.action}
            aria-label={rootActionLabel(move.action)} aria-describedby={`root-value-${index}`}
            className={`trainer-button poker-action graded move-${tone(move.action)}${feedback?.move.action === move.action ? " selected" : ""}`}
            disabled={busy || !!feedback || attempts.length >= rootMaximumAttempts} onClick={() => void submit(move.action)}>
            <span className="poker-action-label"><kbd aria-hidden="true">{index + 1}</kbd>{rootActionLabel(move.action)}</span>
            <span className="poker-action-value" id={`root-value-${index}`}><strong>EV {intervalRange(move.lowerEvBb, move.upperEvBb)}</strong>
              <small>{question.context.actions[index].amountBb} bb more committed</small>
              {feedback && <small>{rootVerdict(move)}{feedback.move.action === move.action ? " · Your choice" : ""}</small>}</span>
          </button>)}</div>
          {busy && <p role="status">Request in progress…</p>}
          <nav className="interval-controls root-controls" aria-label="Practice controls">
            {feedback && attempts.length < rootMaximumAttempts && <button className="trainer-button poker-next" disabled={busy} onClick={() => { setFeedback(null); setReview(null); setError(""); }}>Try another move</button>}
            <button className="trainer-button" disabled={busy} onClick={() => void sessionReview()}>Review {attempts.length} {attempts.length === 1 ? "attempt" : "attempts"}</button>
            <button className="trainer-button" disabled={busy} onClick={startOver}>Start over</button>
          </nav>
          {feedback && <div className="poker-decision-feedback interval-feedback" aria-live="polite">
            <h2>{rootVerdict(feedback.move)}</h2><p>You chose {rootActionLabel(feedback.move.action).toLowerCase()}. {feedback.move.classification === "STRATEGY_DEPENDENT"
              ? "The loss range crosses the decision limit. This raise is neither a proven mistake nor a proven safe move."
              : feedback.move.classification === "WITHIN_LIMIT" ? "The loss stays within the study’s decision limit across the supported opponent strategies."
                : "The loss exceeds the study’s decision limit across the supported opponent strategies."}</p>
            <p>Loss {intervalRange(feedback.move.lowerLossBb, feedback.move.upperLossBb)}. This is feedback on this decision, not a played-out hand.</p>
          </div>}
          {attempts.length >= rootMaximumAttempts && <p role="status">Attempt limit reached. Review, download or start over.</p>}
          {notice && <p className="root-session-notice" role="status">{notice}</p>}
          {review && <section className="interval-review" aria-label="Preflop session review"><h2>{review.attempts.length} attempts · One preflop decision</h2>
            <p>Within limit: {review.counts.withinLimit} · Outside limit: {review.counts.outsideLimit} · Unresolved: {review.counts.strategyDependent}</p>
            <ol>{review.attempts.map(a => <li key={a.ordinal}>{rootActionLabel(a.attempt.action)} · {rootVerdict(a.grade.move)} · Loss {intervalRange(a.grade.move.lowerLossBb, a.grade.move.upperLossBb)}</li>)}</ol>
            <button className="trainer-button" disabled={busy} onClick={() => void sessionReview(true)}>Download session review</button>
            <p>Repeated attempts count separately. Unresolved moves have their own count and are not marked correct or incorrect.</p>
          </section>}
        </PokerTable>
        <details className="interval-context"><summary>EV scope, public history and exact bounds</summary>
          <p>EV is profit from this decision, with optimal future hero continuation against each supported opponent strategy. The 1 bb already posted is accounted for once by the server. Loss bounds compare moves jointly; subtracting separate EV endpoints does not reproduce them. Displayed endpoints are rounded outwards.</p>
          <p>This fixed-prefix, supplied-range model uses one selected flop plus residual continuations. It does not establish whole-hand frequencies, a unique best action or six-player GTO. No future board or opponent cards are shown.</p>
          <ol>{question.context.history.map((h, i) => <li key={i}>{h.seat}: {h.kind === "FOLD" ? "Fold" : h.kind === "RAISE_TO" ? `Raise to ${h.amountBb} bb` : `Post ${h.amountBb} bb`}</li>)}</ol>
          <dl>{question.moves.map(m => <div key={m.action}><dt>{rootActionLabel(m.action)}</dt><dd>EV [{m.lowerEvBb}, {m.upperEvBb}] bb; loss [{m.lowerLossBb}, {m.upperLossBb}] bb</dd></div>)}</dl>
          <p>Study <code>{metadata?.studyHash}</code></p><p>EV scope <code>{question.evScope}</code></p>
        </details>
      </section>}
    </main>
  </div>;
}
