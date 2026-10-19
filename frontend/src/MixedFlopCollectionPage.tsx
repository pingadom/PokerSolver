import { useCallback, useEffect, useRef, useState } from "react";
import { flopClassificationLabel as classificationLabel } from "./FlopIntervalDecisionTable";
import MixedFlopCollectionDecisionTable, { mixedFlopCollectionActionLabel as intervalActionLabel } from "./MixedFlopCollectionDecisionTable";
import { IntervalProtocolError, intervalEqual } from "./intervalTrainerApi";
import { intervalRange } from "./intervalTrainerFormatting";
import {
  MixedFlopCollectionRequestError, mixedFlopCollectionRequest, parseMixedFlopCollectionMetadata, parseMixedFlopCollectionQuestion, parseMixedFlopCollectionFeedback,
  parseMixedFlopCollectionReview, mixedFlopReviewDocument, mixedReviewChoices, checkMixedFlopCollectionInventory, mixedDecision, mixedGrade, mixedActions, type MixedFlopCollectionAction,
  type MixedFlopCollectionMetadata, type MixedFlopCollectionQuestion, type MixedFlopCollectionAttempt, type MixedFlopCollectionReview,
} from "./mixedFlopCollectionApi";
import "./intervalTrainer.css";
import "./flopInterval.css";
import "./btnFlopInterval.css";
import "./mixedFlopCollection.css";

function download(text: string, pin: string) {
  const url = URL.createObjectURL(new Blob([text], { type: "application/json" }));
  const anchor = document.createElement("a");
  anchor.href = url; anchor.download = `pokerlab-mixed-flop-review-${pin.slice(0, 12)}.json`;
  anchor.click(); URL.revokeObjectURL(url);
}
export function mixedQuestionLabel(q: MixedFlopCollectionQuestion) {
  const d = q.decision;
  return d.kind === "INITIAL_CHECK_BET" ? "BB · First move" : d.kind === "BB_FACING_BET" ? "BB · Facing bet" : `BTN · ${d.btn.heroCards}`;
}
const locationFor = (pin: string, index: number) => `#interval-mixed-flop?collection=${pin}&question=${index}`;

export default function MixedFlopCollectionPage() {
  const [generation, setGeneration] = useState(0);
  const [metadata, setMetadata] = useState<MixedFlopCollectionMetadata | null>(null);
  const [questions, setQuestions] = useState<MixedFlopCollectionQuestion[]>([]);
  const [index, setIndex] = useState(0);
  const [attempts, setAttempts] = useState<MixedFlopCollectionAttempt[]>([]);
  const [review, setReview] = useState<MixedFlopCollectionReview | null>(null);
  const [exporting, setExporting] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [fatal, setFatal] = useState(false);
  const operation = useRef<AbortController | null>(null);
  const pending = useRef(true);
  const saved = useRef<MixedFlopCollectionAttempt[]>([]);
  const question = questions[index];
  const feedback = retrying ? null : [...attempts].reverse().find((a) => a.questionIndex === index)?.feedback ?? null;

  const clear = useCallback(() => {
    operation.current?.abort(); pending.current = true; saved.current = [];
    setBusy(true); setError(""); setFatal(false); setMetadata(null); setQuestions([]);
    setIndex(0); setExporting(false); setRetrying(false); setAttempts([]); setReview(null);
  }, []);
  useEffect(() => {
    const changed = () => { clear(); setGeneration((g) => g + 1); };
    window.addEventListener("hashchange", changed);
    return () => { window.removeEventListener("hashchange", changed); operation.current?.abort(); };
  }, [clear]);
  useEffect(() => {
    const controller = new AbortController(); operation.current = controller; pending.current = true;
    void (async () => {
      const loaded = parseMixedFlopCollectionMetadata(await mixedFlopCollectionRequest("", controller.signal));
      if (controller.signal.aborted) return;
      const params = new URLSearchParams(window.location.hash.split("?")[1] ?? "");
      const pin = params.get("collection"), requested = params.get("question") ?? "0";
      if ([...params.keys()].some((key) => !["collection", "question"].includes(key) || params.getAll(key).length !== 1) ||
        pin !== null && pin !== loaded.collectionHash || !/^(0|[1-9]\d*)$/.test(requested) ||
        Number(requested) >= loaded.availableQuestions) throw new IntervalProtocolError();
      const inventory = await Promise.all(Array.from({ length: loaded.availableQuestions }, async (_, i) =>
        parseMixedFlopCollectionQuestion(await mixedFlopCollectionRequest(`/questions/${i}?collectionHash=${loaded.collectionHash}`, controller.signal), loaded, i)));
      if (controller.signal.aborted) return;
      checkMixedFlopCollectionInventory(inventory, loaded);
      setMetadata(loaded); setQuestions(inventory); setIndex(Number(requested));
      window.history.replaceState(null, "", locationFor(loaded.collectionHash, Number(requested)));
    })().catch((cause: unknown) => {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "Could not load these questions.");
        setFatal(cause instanceof IntervalProtocolError || cause instanceof MixedFlopCollectionRequestError && [400, 404].includes(cause.status));
      }
    }).finally(() => { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } });
    return () => controller.abort();
  }, [generation]);

  const fail = (cause: unknown, controller: AbortController) => {
    if (!controller.signal.aborted) {
      setError(cause instanceof Error ? cause.message : "The request failed. Please try again.");
      setFatal(cause instanceof IntervalProtocolError || cause instanceof MixedFlopCollectionRequestError && [400, 404].includes(cause.status));
    }
  };
  async function submit(action: MixedFlopCollectionAction) {
    if (pending.current || !question || feedback || fatal || saved.current.length >= 100) return;
    pending.current = true; setBusy(true); setError(""); setReview(null);
    const controller = new AbortController(); operation.current = controller;
    try {
      const graded = parseMixedFlopCollectionFeedback(await mixedFlopCollectionRequest("/grade", controller.signal, {
        collectionHash: question.collectionHash, questionIndex: question.index, reportHash: mixedDecision(question).reportHash, action,
      }), question, action);
      if (controller.signal.aborted) return;
      const previous = saved.current.find((a) => a.questionIndex === question.index);
      if (previous && !intervalEqual(mixedGrade(previous.feedback).alternatives, mixedGrade(graded).alternatives)) throw new IntervalProtocolError();
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
      const result = parseMixedFlopCollectionReview(await mixedFlopCollectionRequest("/review", controller.signal, {
        collectionHash: metadata.collectionHash, choices: mixedReviewChoices(captured),
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
      const text = await mixedFlopReviewDocument(controller.signal, metadata, questions, captured);
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
  useEffect(() => {
    const key = (event: KeyboardEvent) => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey ||
        (event.target instanceof HTMLElement && (event.target.isContentEditable || event.target.closest("input, textarea, select, button, a, summary")))) return;
      const action = question ? mixedActions(question)[Number(event.key) - 1] : undefined;
      if (/^[1-9]$/.test(event.key) && action) { event.preventDefault(); void submit(action); }
    };
    window.addEventListener("keydown", key); return () => window.removeEventListener("keydown", key);
  });
  function reset() { clear(); window.history.replaceState(null, "", "#interval-mixed-flop"); setGeneration((g) => g + 1); }
  const covered = new Set(attempts.map((a) => a.questionIndex)).size;
  return <div className={`trainer-page preflop-page interval-page collection-page flop-interval-page mixed-flop-collection-page btn-flop-interval-page${question?.decision.kind === "BB_FACING_BET" ? " later-flop-interval-page" : ""}`}>
    <header className="trainer-topbar"><a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a><a className="trainer-back" href="#solver">← Solver project</a></header>
    <main className="trainer-container hand-play-container">
      <div className="trainer-title-row hand-play-title"><div><p className="eyebrow">FLOP PRACTICE · SIX DECISIONS</p>
        <h1>Play both sides</h1><p className="trainer-intro">Practise BB’s first move, BB facing a bet, and four BTN hands after a check.</p></div></div>
      <p className="trainer-disclosure hand-play-disclosure" role="note">Research collection · Supplied hand beliefs, no rake, one flop bet then exact checkdown. Two active players. Six independent decisions; each is graded separately.</p>
      {error && <div className="trainer-error" role="alert"><p>{error}</p>
        <button className="trainer-button" disabled={busy} onClick={fatal || !questions.length ? reset : () => setError("")}>
          {fatal ? "Load current collection" : questions.length ? "Dismiss error" : "Retry loading"}</button></div>}
      {!question && !error && <p role="status">Loading the qualified questions…</p>}
      {question && !fatal && <section className="trainer-card trainer-play preflop-play" aria-label="Mixed flop research decision">
        <MixedFlopCollectionDecisionTable question={question} feedback={feedback} knownFeedback={attempts.find(a => a.questionIndex === index)?.feedback} attemptCount={attempts.length} busy={busy}
          submit={(action) => void submit(action)} prompt={`Question ${index + 1} of ${questions.length} · ${covered} of ${questions.length} practised`}
          controls={<>
            <nav className="mixed-context-navigation" aria-label="Choose a flop decision">
              {questions.map((q, i) => <button type="button" key={q.index} className="trainer-button" aria-current={i === index ? "step" : undefined}
                disabled={busy} onClick={() => navigate(i)}>{i + 1} · {mixedQuestionLabel(q)}{attempts.some(a => a.questionIndex === i) ? " ✓" : ""}</button>)}
            </nav>
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
          {review && <section className="interval-review" aria-label="Mixed flop practice review">
            <h2>{review.counts.distinctQuestionsAttempted} of {questions.length} questions practised · {review.attempts.length} attempts</h2>
            <p>Within limit: {review.counts.withinLimit} · Outside limit: {review.counts.outsideLimit} · Strategy-dependent: {review.counts.strategyDependent}</p>
            <ol>{review.attempts.map((a) => <li key={a.index}>Question {a.questionIndex + 1} · {mixedQuestionLabel(questions[a.questionIndex])} · {intervalActionLabel(a.action, questions[a.questionIndex])} · {classificationLabel(mixedGrade(a.feedback).selected)} · Loss {intervalRange(mixedGrade(a.feedback).selected.lowerLossBb, mixedGrade(a.feedback).selected.upperLossBb)}</li>)}</ol>
            <button className="trainer-button" disabled={busy} onClick={() => void exportReview()}>{exporting ? "Preparing review…" : "Download replayable review"}</button>
            <p>Repeated attempts count once towards question coverage. This review can be replayed with the mixed flop collection CLI.</p>
          </section>}
        </MixedFlopCollectionDecisionTable>
      </section>}
    </main>
  </div>;
}
