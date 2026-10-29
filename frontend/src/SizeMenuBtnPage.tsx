import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import SizeMenuBtnTable, { btnActionLabel, btnClassificationLabel } from "./SizeMenuBtnTable";
import { intervalRange } from "./intervalTrainerFormatting";
import { BtnProtocolError, BtnRequestError, btnChoices, btnMaximumAttempts, btnRequest, btnReviewDocument,
  btnSessionKey, btnSessionReview, checkBtnInventory, parseBtnGrade, parseBtnMetadata, parseBtnQuestion,
  parseBtnReview, readBtnReview, type BtnAction, type BtnAttempt, type BtnMetadata, type BtnQuestion, type BtnReview } from "./sizeMenuBtnApi";
import "./intervalTrainer.css";
import "./flopInterval.css";
import "./sizeMenuBtn.css";

const locationFor = (pin: string, index: number) => `#btn-sizing-flop?study=${pin}&hand=${index}`;
export const btnHandLabel = (q: BtnQuestion) => q.heroCards.replaceAll("s", "♠").replaceAll("h", "♥").replaceAll("d", "♦").replaceAll("c", "♣");
function download(text: string, pin: string) {
  const url = URL.createObjectURL(new Blob([text], { type: "application/json" }));
  const a = document.createElement("a"); a.href = url; a.download = `pokerlab-btn-sizing-review-${pin.slice(0, 12)}.json`;
  a.click(); URL.revokeObjectURL(url);
}
export default function SizeMenuBtnPage() {
  const [generation, setGeneration] = useState(0);
  const [metadata, setMetadata] = useState<BtnMetadata | null>(null);
  const [questions, setQuestions] = useState<BtnQuestion[]>([]);
  const [index, setIndex] = useState(0);
  const [attempts, setAttempts] = useState<BtnAttempt[]>([]);
  const [review, setReview] = useState<BtnReview | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [busy, setBusy] = useState(true);
  const [fatal, setFatal] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const operation = useRef<AbortController | null>(null);
  const pending = useRef(true), saved = useRef<BtnAttempt[]>([]);
  const q = questions[index];
  const feedback = retrying ? null : [...attempts].reverse().find(a => a.attempt.sourceCaseIndex === index)?.grade ?? null;
  const clear = useCallback(() => {
    operation.current?.abort(); pending.current = true; saved.current = [];
    setMetadata(null); setQuestions([]); setAttempts([]); setIndex(0); setReview(null); setRetrying(false);
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
      const m = parseBtnMetadata(await btnRequest("", controller.signal));
      if (controller.signal.aborted) return;
      const params = new URLSearchParams(window.location.hash.split("?")[1] ?? "");
      const pin = params.get("study"), hand = params.get("hand") ?? "0";
      if (window.location.hash.split("?")[0] !== "#btn-sizing-flop" || [...params.keys()].some(k => !["study", "hand"].includes(k) || params.getAll(k).length !== 1) ||
        pin !== null && pin !== m.studyHash || !/^[0-3]$/.test(hand)) throw new BtnProtocolError();
      const inventory = await Promise.all(Array.from({ length: 4 }, async (_, i) =>
        parseBtnQuestion(await btnRequest(`/questions/${i}?studyHash=${m.studyHash}`, controller.signal), m, i)));
      if (controller.signal.aborted) return;
      checkBtnInventory(inventory, m);
      let restored: BtnAttempt[] = [];
      try {
        const text = window.localStorage.getItem(btnSessionKey(m.studyHash));
        if (text) { restored = readBtnReview(text, m, inventory).attempts; setNotice(`Restored ${restored.length} saved attempts.`); }
      } catch { setNotice("Saved practice could not be restored. Import a valid review or start a new session."); }
      saved.current = restored; setAttempts(restored); setMetadata(m); setQuestions(inventory); setIndex(Number(hand));
      window.history.replaceState(null, "", locationFor(m.studyHash, Number(hand)));
    })().catch((cause: unknown) => {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "Could not load this drill.");
        setFatal(cause instanceof BtnProtocolError || cause instanceof BtnRequestError && [400, 404].includes(cause.status));
      }
    }).finally(() => { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } });
    return () => controller.abort();
  }, [generation]);

  function fail(cause: unknown, controller: AbortController) {
    if (controller.signal.aborted) return;
    setError(cause instanceof Error ? cause.message : "The request failed. Please try again.");
    setFatal(cause instanceof BtnProtocolError || cause instanceof BtnRequestError && [400, 404].includes(cause.status));
  }
  function persist(m: BtnMetadata, next: BtnAttempt[]) {
    try { window.localStorage.setItem(btnSessionKey(m.studyHash), JSON.stringify(btnSessionReview(m, questions, next))); setNotice("Practice saved on this device."); }
    catch { setNotice("Device storage is unavailable. Download a review to keep your practice."); }
  }
  async function submit(action: BtnAction) {
    if (pending.current || !metadata || !q || feedback || fatal || saved.current.length >= btnMaximumAttempts) return;
    pending.current = true; setBusy(true); setError(""); setReview(null);
    const controller = new AbortController(); operation.current = controller;
    try {
      const grade = parseBtnGrade(await btnRequest("/grade", controller.signal,
        { studyHash: metadata.studyHash, sourceCaseIndex: q.sourceCaseIndex, action }), q, action);
      if (controller.signal.aborted) return;
      const next = [...saved.current, { ordinal: saved.current.length, attempt: { sourceCaseIndex: q.sourceCaseIndex, action }, grade }];
      saved.current = next; setAttempts(next); setRetrying(false); persist(metadata, next);
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  async function loadReview() {
    if (pending.current || !metadata || fatal) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    const choices = btnChoices(saved.current);
    try {
      const result = parseBtnReview(await btnRequest("/review", controller.signal,
        { studyHash: metadata.studyHash, choices }), metadata, questions, choices);
      if (!controller.signal.aborted) setReview(result);
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  async function exportReview() {
    if (pending.current || !metadata || !review || fatal) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    try {
      const text = await btnReviewDocument(controller.signal, metadata, questions, saved.current.slice());
      if (!controller.signal.aborted) download(text, metadata.studyHash);
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  async function importReview(file: File) {
    if (pending.current || !metadata || fatal) return;
    pending.current = true; setBusy(true); setError("");
    const controller = new AbortController(); operation.current = controller;
    try {
      let local: BtnReview;
      try {
        if (file.size > 1024 * 1024) throw new BtnProtocolError();
        local = readBtnReview(await file.text(), metadata, questions);
      } catch { throw new Error("This saved review is invalid or belongs to another study. Your current attempts are unchanged."); }
      if (controller.signal.aborted) return;
      const canonical = parseBtnReview(await btnRequest("/review", controller.signal,
        { studyHash: metadata.studyHash, choices: btnChoices(local.attempts) }), metadata, questions, btnChoices(local.attempts));
      if (controller.signal.aborted) return;
      saved.current = canonical.attempts; setAttempts(canonical.attempts); setReview(canonical); setRetrying(false); persist(metadata, canonical.attempts);
    } catch (cause) { fail(cause, controller); }
    finally { if (!controller.signal.aborted) { pending.current = false; setBusy(false); } }
  }
  function navigate(next: number) {
    if (pending.current || !metadata || fatal || next < 0 || next >= 4) return;
    setIndex(next); setRetrying(false); setReview(null); setError("");
    window.history.replaceState(null, "", locationFor(metadata.studyHash, next));
  }
  useLayoutEffect(() => {
    const key = (event: KeyboardEvent) => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey ||
        event.target instanceof HTMLElement && (event.target.isContentEditable || event.target.closest("input,textarea,select,button,a,summary"))) return;
      if (event.key === "1" || event.key === "2") { event.preventDefault(); void submit(event.key === "1" ? "fold" : "call"); }
    };
    window.addEventListener("keydown", key); return () => window.removeEventListener("keydown", key);
  });
  function reload(reset = false) {
    if (reset && metadata) { try { window.localStorage.removeItem(btnSessionKey(metadata.studyHash)); } catch { /* In-memory reset still works. */ } }
    clear(); window.history.replaceState(null, "", "#btn-sizing-flop"); setGeneration(g => g + 1);
  }
  const covered = new Set(attempts.map(a => a.attempt.sourceCaseIndex)).size;
  return <div className="trainer-page preflop-page interval-page flop-interval-page btn-sizing-page">
    <header className="trainer-topbar"><a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a><a className="trainer-back" href="#solver">← Solver project</a></header>
    <main className="trainer-container hand-play-container">
      <div className="trainer-title-row hand-play-title"><div><p className="eyebrow">BTN · FOUR FLOP DECISIONS</p><h1>Facing the bet</h1>
        <p className="trainer-intro">BB bets a quarter of the pot. Choose whether to fold or call.</p></div></div>
      <p className="trainer-disclosure hand-play-disclosure" role="note">Research drill · Two active players at a six-seat table · Jointly solved sizing menu · No rake · Exact checkdown after this flop.</p>
      {error && <div className="trainer-error" role="alert"><p>{error}</p><button className="trainer-button" disabled={busy}
        onClick={fatal || !questions.length ? () => reload() : () => setError("")}>{fatal ? "Load current study" : questions.length ? "Dismiss error" : "Retry loading"}</button></div>}
      {!q && !error && <p role="status">Loading the four hands…</p>}
      {q && !fatal && <section className="trainer-card trainer-play preflop-play" aria-label="BTN sizing decision">
        <nav className="btn-sizing-hands" aria-label="Choose a BTN hand">{questions.map(hand => <button type="button" key={hand.sourceCaseIndex}
          className="trainer-button" aria-current={hand.sourceCaseIndex === index ? "step" : undefined} disabled={busy} onClick={() => navigate(hand.sourceCaseIndex)}>
          {btnHandLabel(hand)}{attempts.some(a => a.attempt.sourceCaseIndex === hand.sourceCaseIndex) ? " ✓" : ""}</button>)}</nav>
        <SizeMenuBtnTable question={q} feedback={feedback} busy={busy} attemptCount={attempts.length} covered={covered} submit={action => void submit(action)}
          controls={<nav className="interval-controls btn-sizing-controls" aria-label="Hand controls">
            <button className="trainer-button poker-next" disabled={busy} onClick={() => navigate((index + 1) % 4)}>Next hand</button>
            {feedback && attempts.length < btnMaximumAttempts && <button className="trainer-button" disabled={busy} onClick={() => { setRetrying(true); setReview(null); setError(""); }}>Try another move</button>}
            <button className="trainer-button" disabled={busy} onClick={() => void loadReview()}>Review {attempts.length} {attempts.length === 1 ? "attempt" : "attempts"}</button>
            <button className="trainer-button" disabled={busy} onClick={() => reload(true)}>Start over</button>
          </nav>}>
          {attempts.length >= btnMaximumAttempts && <p role="status">Attempt limit reached. Navigation, review and export remain available.</p>}
          {review && <section className="interval-review" aria-label="BTN practice review">
            <h2>{review.counts.distinctQuestions} of 4 hands practised · {review.attempts.length} attempts</h2>
            <p>Within limit: {review.counts.withinLimit} · Outside limit: {review.counts.outsideLimit} · Strategy-dependent: {review.counts.strategyDependent}</p>
            <ol>{review.attempts.map(a => <li key={a.ordinal}>{btnHandLabel(questions[a.attempt.sourceCaseIndex])} · {btnActionLabel(a.attempt.action, questions[a.attempt.sourceCaseIndex])} · {btnClassificationLabel(a.grade.move)} · Loss {intervalRange(a.grade.move.lowerLossBb, a.grade.move.upperLossBb)}</li>)}</ol>
            <button className="trainer-button" disabled={busy} onClick={() => void exportReview()}>Download review</button>
            <p>Repeated attempts count once towards hand coverage.</p>
          </section>}
        </SizeMenuBtnTable>
        <div className="btn-sizing-session"><label className="trainer-button">Import review<input type="file" aria-label="Import review" accept=".json,application/json" disabled={busy}
          onChange={event => { const file = event.currentTarget.files?.[0]; event.currentTarget.value = ""; if (file) void importReview(file); }} /></label>
          {notice && <p role="status">{notice}</p>}</div>
        <details className="interval-context"><summary>Hand history and EV details</summary>
          <ol>{q.context.preflopHistory.map((move, i) => <li key={i}>{move.seat}: {move.kind.replaceAll("_", " ").toLowerCase()}{move.amountBb > 0 ? ` ${move.amountBb} bb` : ""}</li>)}</ol>
          <p>Flop: BB bet {q.context.flopHistory[0].amountBb} bb. BTN has {q.context.toCallBb} bb to call.</p>
          <p>Hand EV is total chip profit from the start of the hand, including chips already committed. It is not an extra amount lost by clicking a move. EV and loss intervals are rounded outwards.</p>
          <p>Loss compares against the best legal move across the supported strategies, with a 0.01 bb limit. These are four supplied hands in a restricted two-player game, rather than a general range chart.</p>
          <p>Study <code>{metadata?.studyHash}</code> · Public trainer admission remains off.</p>
        </details>
      </section>}
    </main>
  </div>;
}
