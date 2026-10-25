import { callQuestionLabel } from "./CallFlopCollectionPage";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { flopClassificationLabel as classificationLabel } from "./FlopIntervalDecisionTable";
import UnpairedFlopCollectionDecisionTable, { unpairedFlopCollectionActionLabel as intervalActionLabel } from "./UnpairedFlopCollectionDecisionTable";
import { IntervalProtocolError, intervalEqual } from "./intervalTrainerApi";
import { intervalRange } from "./intervalTrainerFormatting";
import {
  readUnpairedFlopReview, unpairedSessionKey, unpairedSessionReview, unpairedQuestionIdentity,
  UnpairedFlopCollectionRequestError, unpairedFlopCollectionRequest, parseUnpairedFlopCollectionMetadata, parseUnpairedFlopCollectionQuestion, parseUnpairedFlopCollectionFeedback,
  parseUnpairedFlopCollectionReview, unpairedFlopReviewDocument, unpairedReviewChoices, checkUnpairedFlopCollectionInventory, unpairedDecision, unpairedGrade, unpairedActions, type UnpairedFlopCollectionAction,
  type UnpairedFlopCollectionMetadata, type UnpairedFlopCollectionQuestion, type UnpairedFlopCollectionAttempt, type UnpairedFlopCollectionReview,
} from "./unpairedFlopCollectionApi";
import "./intervalTrainer.css";
import "./flopInterval.css";
import "./btnFlopInterval.css";
import "./mixedFlopCollection.css";
import "./callFlopCollection.css";

function download(text: string, pin: string) {
  const url = URL.createObjectURL(new Blob([text], { type: "application/json" }));
  const anchor = document.createElement("a");
  anchor.href = url; anchor.download = `pokerlab-unpaired-flop-review-${pin.slice(0, 12)}.json`;
  anchor.click(); URL.revokeObjectURL(url);
}
export function unpairedQuestionLabel(q: UnpairedFlopCollectionQuestion) {
  if (q.decision.legacy) return callQuestionLabel(q.decision.legacy);
  const cards = q.decision.shared.decision.heroCards.replaceAll("h", "♥").replaceAll("s", "♠").replaceAll("c", "♣").replaceAll("d", "♦");
  return `BTN · ${cards} · 6♦ T♦ Q♣ · Facing bet`;
}
const locationFor = (pin: string, index: number) => `#interval-unpaired-flop?collection=${pin}&question=${index}`;

export default function UnpairedFlopCollectionPage() {
  const [generation, setGeneration] = useState(0);
  const [metadata, setMetadata] = useState<UnpairedFlopCollectionMetadata | null>(null);
  const [questions, setQuestions] = useState<UnpairedFlopCollectionQuestion[]>([]);
  const [index, setIndex] = useState(0);
  const [attempts, setAttempts] = useState<UnpairedFlopCollectionAttempt[]>([]);
  const [review, setReview] = useState<UnpairedFlopCollectionReview | null>(null);
  const [inspections, setInspections] = useState<Record<string, UnpairedFlopCollectionAttempt["feedback"]>>({});
  const [exporting, setExporting] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [fatal, setFatal] = useState(false);
  const [sessionNotice, setSessionNotice] = useState("");
  const operation = useRef<AbortController | null>(null);
  const pending = useRef(true);
  const saved = useRef<UnpairedFlopCollectionAttempt[]>([]);
  const question = questions[index];
  const feedback = retrying ? null : [...attempts].reverse().find((a) => a.questionIndex === index)?.feedback ?? null;

  const clear = useCallback(() => {
    operation.current?.abort(); pending.current = true; saved.current = [];
    setBusy(true); setError(""); setFatal(false); setMetadata(null); setQuestions([]);
    setIndex(0); setExporting(false); setRetrying(false); setAttempts([]); setReview(null);
    setInspections({});
  }, []);
  useEffect(() => {
    const changed = () => { clear(); setGeneration((g) => g + 1); };
    window.addEventListener("hashchange", changed);
    return () => { window.removeEventListener("hashchange", changed); operation.current?.abort(); };
  }, [clear]);
  useEffect(() => {
    const controller = new AbortController(); operation.current = controller; pending.current = true;
    void (async () => {
      const loaded = parseUnpairedFlopCollectionMetadata(await unpairedFlopCollectionRequest("", controller.signal));
      if (controller.signal.aborted) return;
      const params = new URLSearchParams(window.location.hash.split("?")[1] ?? "");
      const pin = params.get("collection"), requested = params.get("question") ?? "0";
      if ([...params.keys()].some((key) => !["collection", "question"].includes(key) || params.getAll(key).length !== 1) ||
        pin !== null && pin !== loaded.collectionHash || !/^(0|[1-9]\d*)$/.test(requested) ||
        Number(requested) >= loaded.availableQuestions) throw new IntervalProtocolError();
      const inventory = await Promise.all(Array.from({ length: loaded.availableQuestions }, async (_, i) =>
        parseUnpairedFlopCollectionQuestion(await unpairedFlopCollectionRequest(`/questions/${i}?collectionHash=${loaded.collectionHash}`, controller.signal), loaded, i)));
      if (controller.signal.aborted) return;
      checkUnpairedFlopCollectionInventory(inventory, loaded);
      let restored: UnpairedFlopCollectionAttempt[] = [];
      try {
        const text = window.localStorage.getItem(unpairedSessionKey(loaded.collectionHash));
        if (text) { restored = readUnpairedFlopReview(text, loaded, inventory).attempts; setSessionNotice(`Restored ${restored.length} saved attempts.`); }
      } catch { setSessionNotice("Saved practice could not be restored. Import a valid review or start a new session."); }
      saved.current = restored; setAttempts(restored);
      setMetadata(loaded); setQuestions(inventory); setIndex(Number(requested));
      window.history.replaceState(null, "", locationFor(loaded.collectionHash, Number(requested)));
    })().catch((cause: unknown) => {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "Could not load these questions.");
        setFatal(cause instanceof IntervalProtocolError || cause instanceof UnpairedFlopCollectionRequestError && [400, 404].includes(cause.status));
      }
    }).finally(() => { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } });
    return () => controller.abort();
  }, [generation]);

  const fail = (cause: unknown, controller: AbortController) => {
    if (!controller.signal.aborted) {
      setError(cause instanceof Error ? cause.message : "The request failed. Please try again.");
      setFatal(cause instanceof IntervalProtocolError || cause instanceof UnpairedFlopCollectionRequestError && [400, 404].includes(cause.status));
    }
  };
  function persist(loaded: UnpairedFlopCollectionMetadata, inventory: UnpairedFlopCollectionQuestion[], next: UnpairedFlopCollectionAttempt[]) {
    try { window.localStorage.setItem(unpairedSessionKey(loaded.collectionHash), JSON.stringify(unpairedSessionReview(loaded, inventory, next))); setSessionNotice("Practice saved on this device."); }
    catch { setSessionNotice("Device storage is unavailable. Download a review to keep your practice."); }
  }
  async function importReview(file: File) {
    if (pending.current || !metadata || fatal) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    try {
      if (file.size > 1024 * 1024) throw new IntervalProtocolError();
      const imported = readUnpairedFlopReview(await file.text(), metadata, questions);
      const verified = parseUnpairedFlopCollectionReview(await unpairedFlopCollectionRequest("/review", controller.signal, {
        collectionHash: metadata.collectionHash, choices: unpairedReviewChoices(imported.attempts),
      }), metadata, questions, imported.attempts);
      if (controller.signal.aborted) return;
      saved.current = verified.attempts; setAttempts(verified.attempts); setReview(verified); setRetrying(false);
      persist(metadata, questions, verified.attempts);
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause instanceof UnpairedFlopCollectionRequestError ? cause.message : "This file is not a valid review for the eighteen-decision collection. Your current session is unchanged.");
    } finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  async function inspectValues() {
    if (pending.current || !question || fatal || inspections[unpairedQuestionIdentity(question)]) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    try {
      const action = unpairedActions(question)[0];
      const checked = parseUnpairedFlopCollectionFeedback(await unpairedFlopCollectionRequest("/grade", controller.signal, {
        collectionHash: question.collectionHash, questionIndex: question.index, reportHash: unpairedDecision(question).reportHash, action,
      }), question, action);
      if (!controller.signal.aborted) setInspections(previous => ({...previous, [unpairedQuestionIdentity(question)]: checked}));
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  async function submit(action: UnpairedFlopCollectionAction) {
    if (pending.current || !question || feedback || fatal || saved.current.length >= 100) return;
    pending.current = true; setBusy(true); setError(""); setReview(null);
    const controller = new AbortController(); operation.current = controller;
    try {
      const graded = parseUnpairedFlopCollectionFeedback(await unpairedFlopCollectionRequest("/grade", controller.signal, {
        collectionHash: question.collectionHash, questionIndex: question.index, reportHash: unpairedDecision(question).reportHash, action,
      }), question, action);
      if (controller.signal.aborted) return;
      const previous = saved.current.find((a) => a.questionIndex === question.index)?.feedback ?? inspections[unpairedQuestionIdentity(question)];
      if (previous && !intervalEqual(unpairedGrade(previous).alternatives, unpairedGrade(graded).alternatives)) throw new IntervalProtocolError();
      const next = [...saved.current, { index: saved.current.length, questionIndex: question.index, action, feedback: graded }];
      saved.current = next; setAttempts(next); setRetrying(false);
      if (metadata) persist(metadata, questions, next);
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  async function loadReview() {
    if (pending.current || !metadata || fatal) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    const captured = saved.current.slice();
    try {
      const result = parseUnpairedFlopCollectionReview(await unpairedFlopCollectionRequest("/review", controller.signal, {
        collectionHash: metadata.collectionHash, choices: unpairedReviewChoices(captured),
      }), metadata, questions, captured);
      if (!controller.signal.aborted) setReview(result);
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  async function exportReview() {
    if (pending.current || !metadata || !review || fatal) return;
    pending.current = true; setBusy(true); setExporting(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    const captured = saved.current.slice();
    try {
      const text = await unpairedFlopReviewDocument(controller.signal, metadata, questions, captured);
      if (!controller.signal.aborted) download(text, metadata.collectionHash);
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); setExporting(false); } }
  }
  function navigate(next: number) {
    if (pending.current || fatal || !metadata || next < 0 || next >= questions.length) return;
    setIndex(next); setRetrying(false); setReview(null); setError("");
    // replaceState does not emit hashchange: moving between questions preserves the session.
    window.history.replaceState(null, "", locationFor(metadata.collectionHash, next));
  }
  useLayoutEffect(() => {
    const key = (event: KeyboardEvent) => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey ||
        (event.target instanceof HTMLElement && (event.target.isContentEditable || event.target.closest("input, textarea, select, button, a, summary")))) return;
      const action = question ? unpairedActions(question)[Number(event.key) - 1] : undefined;
      if (/^[1-9]$/.test(event.key) && action) { event.preventDefault(); void submit(action); }
    };
    window.addEventListener("keydown", key); return () => window.removeEventListener("keydown", key);
  });
  function reset() {
    if (metadata) { try { window.localStorage.removeItem(unpairedSessionKey(metadata.collectionHash)); } catch { /* The session can still reset in memory. */ } }
    setSessionNotice(""); clear(); window.history.replaceState(null, "", "#interval-unpaired-flop"); setGeneration((g) => g + 1); }
  const covered = new Set(attempts.map((a) => a.questionIndex)).size;
  return <div className={`trainer-page preflop-page interval-page collection-page flop-interval-page expanded-flop-collection-page btn-flop-interval-page call-flop-collection-page unpaired-flop-collection-page${question?.decision.legacy?.decision.legacy?.decision.legacy?.decision.kind === "BB_FACING_BET" ? " later-flop-interval-page" : ""}`}>
    <header className="trainer-topbar"><a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a><a className="trainer-back" href="#solver">← Solver project</a></header>
    <main className="trainer-container hand-play-container">
      <div className="trainer-title-row hand-play-title"><div><p className="eyebrow">FLOP PRACTICE · EIGHTEEN DECISIONS</p>
        <h1>Play the flop</h1><p className="trainer-intro">Check, bet, call or fold. Compare the EV of each move and revisit your decisions.</p></div></div>
      <p className="trainer-disclosure hand-play-disclosure" role="note">{index >= 10 ? "Second supplied range set · " : "Original supplied range set · "}Research collection · Two active players at a six-seat table · No rake · One flop bet, then exact checkdown.</p>

      {error && <div className="trainer-error" role="alert"><p>{error}</p>
        <button className="trainer-button" disabled={busy} onClick={fatal || !questions.length ? reset : () => setError("")}>
          {fatal ? "Load current collection" : questions.length ? "Dismiss error" : "Retry loading"}</button></div>}
      {!question && !error && <p role="status">Loading the eighteen decisions…</p>}
      {question && !fatal && <section className="trainer-card trainer-play preflop-play" aria-label="Independent flop research decision">
        <UnpairedFlopCollectionDecisionTable key={unpairedQuestionIdentity(question)} question={question} feedback={feedback} knownFeedback={attempts.find(a => a.questionIndex === index)?.feedback ?? inspections[unpairedQuestionIdentity(question)]} attemptCount={attempts.length} busy={busy}
          submit={(action) => void submit(action)} prompt={`Question ${index + 1} of ${questions.length} · ${covered} of ${questions.length} practised`}
          controls={<>
            <nav className="interval-controls collection-navigation" aria-label="Question navigation">
              {!feedback && !attempts.some(a => a.questionIndex === index) && !inspections[unpairedQuestionIdentity(question)] && <button className="trainer-button" disabled={busy} onClick={() => void inspectValues()}>Inspect action EVs</button>}
              <button className="trainer-button" disabled={busy || index === 0} onClick={() => navigate(index - 1)}>Previous question</button>
              <button className="trainer-button poker-next" disabled={busy || index === questions.length - 1} onClick={() => navigate(index + 1)}>Next question</button>
              {feedback && attempts.length < 100 && <button className="trainer-button" disabled={busy} onClick={() => { setRetrying(true); setReview(null); setError(""); }}>Try another move</button>}
              <button className="trainer-button" disabled={busy} onClick={() => void loadReview()}>Review {attempts.length} {attempts.length === 1 ? "attempt" : "attempts"}</button>
              <button className="trainer-button" disabled={busy} onClick={reset}>Start over</button>
            </nav>
            {!feedback && !attempts.some(a => a.questionIndex === index) && inspections[unpairedQuestionIdentity(question)] && <p role="status">EVs shown for study. No attempt recorded.</p>}
            <details className="call-question-picker"><summary>Choose a decision · {index + 1} of {questions.length}</summary>
            <nav className="mixed-context-navigation" aria-label="Choose a flop decision">
              {questions.map((q, i) => <button type="button" key={q.index} className="trainer-button" aria-current={i === index ? "step" : undefined}
                disabled={busy} onClick={() => navigate(i)}>{i + 1} · {unpairedQuestionLabel(q)}{attempts.some(a => a.questionIndex === i) ? " ✓" : ""}</button>)}
            </nav>
            </details>
            {attempts.length >= 100 && <p role="status">Attempt limit reached. Navigation, review and export remain available.</p>}
          </>}>
          {review && <section className="interval-review" aria-label="Independent flop practice review">
            <h2>{review.counts.distinctQuestionsAttempted} of {questions.length} questions practised · {review.attempts.length} attempts</h2>
            <p>Within limit: {review.counts.withinLimit} · Outside limit: {review.counts.outsideLimit} · Strategy-dependent: {review.counts.strategyDependent}</p>
            <ol>{review.attempts.map((a) => <li key={a.index}>Question {a.questionIndex + 1} · {unpairedQuestionLabel(questions[a.questionIndex])} · {intervalActionLabel(a.action, questions[a.questionIndex])} · {classificationLabel(unpairedGrade(a.feedback).selected)} · Loss {intervalRange(unpairedGrade(a.feedback).selected.lowerLossBb, unpairedGrade(a.feedback).selected.upperLossBb)}</li>)}</ol>
            <button className="trainer-button" disabled={busy} onClick={() => void exportReview()}>{exporting ? "Preparing review…" : "Download replayable review"}</button>
            <p>Repeated attempts count once towards question coverage. You can import this review here to resume it on another device.</p>
          </section>}
        </UnpairedFlopCollectionDecisionTable>
        <div className="call-session-tools">
          <label className="trainer-button">Import saved review<input aria-label="Import saved review" type="file" accept=".json,application/json" disabled={busy} onChange={event => {
            const file = event.currentTarget.files?.[0]; event.currentTarget.value = ""; if (file) void importReview(file);
          }} /></label>
          {sessionNotice && <p role="status">{sessionNotice}</p>}
        </div>
      </section>}
    </main>
  </div>;
}
