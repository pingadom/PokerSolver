import { useCallback, useEffect, useRef, useState } from "react";
import { flopClassificationLabel as classificationLabel } from "./FlopIntervalDecisionTable";
import FlopCollectionDecisionTable, { flopCollectionActionLabel as intervalActionLabel } from "./FlopCollectionDecisionTable";
import { IntervalProtocolError, intervalEqual } from "./intervalTrainerApi";
import { intervalRange } from "./intervalTrainerFormatting";
import {
  FlopCollectionRequestError, flopCollectionRequest, parseFlopCollectionMetadata, parseFlopCollectionQuestion, parseFlopCollectionFeedback,
  parseFlopCollectionReview, checkFlopCollectionInventory, collectionDecision, collectionGrade, collectionActions, type FlopCollectionAction,
  type FlopCollectionMetadata, type FlopCollectionQuestion, type FlopCollectionAttempt, type FlopCollectionReview,
} from "./flopCollectionApi";
import "./intervalTrainer.css";
import "./flopInterval.css";

function download(review: FlopCollectionReview) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(review, null, 2) + "\n"], { type: "application/json" }));
  const anchor = document.createElement("a");
  anchor.href = url; anchor.download = `pokerlab-flop-collection-${review.collectionHash.slice(0, 12)}.json`;
  anchor.click(); URL.revokeObjectURL(url);
}
const locationFor = (pin: string, index: number) => `#interval-flop-collection?collection=${pin}&question=${index}`;

export default function FlopCollectionPage() {
  const [generation, setGeneration] = useState(0);
  const [metadata, setMetadata] = useState<FlopCollectionMetadata | null>(null);
  const [questions, setQuestions] = useState<FlopCollectionQuestion[]>([]);
  const [index, setIndex] = useState(0);
  const [attempts, setAttempts] = useState<FlopCollectionAttempt[]>([]);
  const [review, setReview] = useState<FlopCollectionReview | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [fatal, setFatal] = useState(false);
  const operation = useRef<AbortController | null>(null);
  const pending = useRef(true);
  const saved = useRef<FlopCollectionAttempt[]>([]);
  const question = questions[index];
  const feedback = retrying ? null : [...attempts].reverse().find((a) => a.questionIndex === index)?.feedback ?? null;

  const clear = useCallback(() => {
    operation.current?.abort(); pending.current = true; saved.current = [];
    setBusy(true); setError(""); setFatal(false); setMetadata(null); setQuestions([]);
    setIndex(0); setRetrying(false); setAttempts([]); setReview(null);
  }, []);
  useEffect(() => {
    const changed = () => { clear(); setGeneration((g) => g + 1); };
    window.addEventListener("hashchange", changed);
    return () => { window.removeEventListener("hashchange", changed); operation.current?.abort(); };
  }, [clear]);
  useEffect(() => {
    const controller = new AbortController(); operation.current = controller; pending.current = true;
    void (async () => {
      const loaded = parseFlopCollectionMetadata(await flopCollectionRequest("", controller.signal));
      if (controller.signal.aborted) return;
      const params = new URLSearchParams(window.location.hash.split("?")[1] ?? "");
      const pin = params.get("collection"), requested = params.get("question") ?? "0";
      if ([...params.keys()].some((key) => !["collection", "question"].includes(key) || params.getAll(key).length !== 1) ||
        pin !== null && pin !== loaded.collectionHash || !/^(0|[1-9]\d*)$/.test(requested) ||
        Number(requested) >= loaded.availableQuestions) throw new IntervalProtocolError();
      const inventory = await Promise.all(Array.from({ length: loaded.availableQuestions }, async (_, i) =>
        parseFlopCollectionQuestion(await flopCollectionRequest(`/questions/${i}?collectionHash=${loaded.collectionHash}`, controller.signal), loaded, i)));
      if (controller.signal.aborted) return;
      checkFlopCollectionInventory(inventory, loaded);
      setMetadata(loaded); setQuestions(inventory); setIndex(Number(requested));
      window.history.replaceState(null, "", locationFor(loaded.collectionHash, Number(requested)));
    })().catch((cause: unknown) => {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "Could not load these questions.");
        setFatal(cause instanceof IntervalProtocolError || cause instanceof FlopCollectionRequestError && cause.status === 400);
      }
    }).finally(() => { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } });
    return () => controller.abort();
  }, [generation]);

  const fail = (cause: unknown, controller: AbortController) => {
    if (!controller.signal.aborted) {
      setError(cause instanceof Error ? cause.message : "The request failed. Please try again.");
      setFatal(cause instanceof IntervalProtocolError || cause instanceof FlopCollectionRequestError && cause.status === 400);
    }
  };
  async function submit(action: FlopCollectionAction) {
    if (pending.current || !question || feedback || fatal || saved.current.length >= 100) return;
    pending.current = true; setBusy(true); setError(""); setReview(null);
    const controller = new AbortController(); operation.current = controller;
    try {
      const graded = parseFlopCollectionFeedback(await flopCollectionRequest("/grade", controller.signal, {
        collectionHash: question.collectionHash, questionIndex: question.index, reportHash: collectionDecision(question).reportHash, action,
      }), question, action);
      if (controller.signal.aborted) return;
      const previous = saved.current.find((a) => a.questionIndex === question.index);
      if (previous && !intervalEqual(collectionGrade(previous.feedback).alternatives, collectionGrade(graded).alternatives)) throw new IntervalProtocolError();
      const next = [...saved.current, { index: saved.current.length, questionIndex: question.index, action, feedback: graded }];
      saved.current = next; setAttempts(next); setRetrying(false);
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  async function loadReview() {
    if (pending.current || !metadata || fatal) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    const captured = saved.current.slice();
    try {
      const result = parseFlopCollectionReview(await flopCollectionRequest("/review", controller.signal, {
        collectionHash: metadata.collectionHash, choices: captured.map((a) => ({
          questionIndex: a.questionIndex, reportHash: collectionGrade(a.feedback).reportHash, action: a.action,
        })),
      }), metadata, questions, captured);
      if (!controller.signal.aborted) setReview(result);
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  function navigate(next: number) {
    if (pending.current || fatal || !metadata || next < 0 || next >= questions.length) return;
    setIndex(next); setRetrying(false); setReview(null); setError("");
    // replaceState does not emit hashchange: moving between questions preserves the session.
    window.history.replaceState(null, "", locationFor(metadata.collectionHash, next));
  }
  useEffect(() => {
    const key = (event: KeyboardEvent) => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey ||
        (event.target instanceof HTMLElement && (event.target.isContentEditable || event.target.closest("input, textarea, select, button, a, summary")))) return;
      const action = question ? collectionActions(question)[Number(event.key) - 1] : undefined;
      if (/^[1-9]$/.test(event.key) && action) { event.preventDefault(); void submit(action); }
    };
    window.addEventListener("keydown", key); return () => window.removeEventListener("keydown", key);
  });
  function reset() { clear(); window.history.replaceState(null, "", "#interval-flop-collection"); setGeneration((g) => g + 1); }
  const covered = new Set(attempts.map((a) => a.questionIndex)).size;
  return <div className={`trainer-page preflop-page interval-page collection-page flop-interval-page${question?.decision.kind === "BB_FACING_BET" ? " later-flop-interval-page" : ""}`}>
    <header className="trainer-topbar"><a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a><a className="trainer-back" href="#solver">← Solver project</a></header>
    <main className="trainer-container hand-play-container">
      <div className="trainer-title-row hand-play-title"><div><p className="eyebrow">TWO QUALIFIED FLOP CONTEXTS</p>
        <h1>Flop practice</h1><p className="trainer-intro">Play distinct decisions, then compare the supported EV and loss ranges.</p></div></div>
      <p className="trainer-disclosure hand-play-disclosure" role="note">Research collection · Supplied hand beliefs, no rake, one flop bet then exact checkdown. These are two independent decision drills, not a full-hand strategy.</p>
      {error && <div className="trainer-error" role="alert"><p>{error}</p>
        <button className="trainer-button" disabled={busy} onClick={fatal || !questions.length ? reset : () => setError("")}>
          {fatal ? "Load current collection" : questions.length ? "Dismiss error" : "Retry loading"}</button></div>}
      {!question && !error && <p role="status">Loading the qualified questions…</p>}
      {question && !fatal && <section className="trainer-card trainer-play preflop-play" aria-label="Collection research decision">
        <FlopCollectionDecisionTable question={question} feedback={feedback} knownFeedback={attempts.find(a => a.questionIndex === index)?.feedback} attemptCount={attempts.length} busy={busy}
          submit={(action) => void submit(action)} prompt={`Question ${index + 1} of ${questions.length} · ${covered} of ${questions.length} practised`}
          controls={<>
            <nav className="interval-controls collection-navigation" aria-label="Question navigation">
              <button className="trainer-button" disabled={busy || index === 0} onClick={() => navigate(index - 1)}>Previous question</button>
              <button className="trainer-button poker-next" disabled={busy || index === questions.length - 1} onClick={() => navigate(index + 1)}>Next question</button>
              {feedback && attempts.length < 100 && <button className="trainer-button" disabled={busy} onClick={() => { setRetrying(true); setReview(null); setError(""); }}>Try another move</button>}
              <button className="trainer-button" disabled={busy} onClick={() => void loadReview()}>Review {attempts.length} {attempts.length === 1 ? "attempt" : "attempts"}</button>
              <button className="trainer-button" disabled={busy} onClick={reset}>Start over</button>
            </nav>
            {index === questions.length - 1 && <p className="collection-end">Last question · {covered} of {questions.length} practised. Review your session or revisit a decision.</p>}
            {attempts.length >= 100 && <p role="status">Attempt limit reached. Navigation, review and export remain available.</p>}
          </>}>
          {review && <section className="interval-review" aria-label="Collection review">
            <h2>{review.counts.distinctQuestionsAttempted} of {questions.length} questions practised · {review.attempts.length} attempts</h2>
            <p>Within limit: {review.counts.withinLimit} · Outside limit: {review.counts.outsideLimit} · Strategy-dependent: {review.counts.strategyDependent}</p>
            <ol>{review.attempts.map((a) => <li key={a.index}>Question {a.questionIndex + 1} · {intervalActionLabel(a.action, questions[a.questionIndex])} · {classificationLabel(collectionGrade(a.feedback).selected)} · Loss {intervalRange(collectionGrade(a.feedback).selected.lowerLossBb, collectionGrade(a.feedback).selected.upperLossBb)}</li>)}</ol>
            <button className="trainer-button" onClick={() => download(review)}>Download replayable review</button>
            <p>Repeated attempts count once towards question coverage. This review can be replayed with the collection CLI.</p>
          </section>}
        </FlopCollectionDecisionTable>
      </section>}
    </main>
  </div>;
}
