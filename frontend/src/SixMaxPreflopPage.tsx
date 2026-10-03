import { useEffect, useRef, useState } from "react";
import SixMaxPreflopTable, { actionTone, PreflopActionValues } from "./SixMaxPreflopTable";
import PokerActionButton, { PokerDecisionFeedback } from "./PokerActionButton";
import {
  actionLabel, postPreflop, preflopRequest, PreflopRequestError, sameQuestion,
  type PreflopAction, type PreflopFeedback, type PreflopGradedQuestion,
  type PreflopMetadata, type PreflopQuestion, type PreflopReview,
} from "./sixMaxPreflopApi";

const params = () => new URLSearchParams(window.location.hash.split("?")[1] ?? "");
const changed = "The solution changed. Start with the current solution.";
const freshSeed = () => {
  const words = crypto.getRandomValues(new Uint32Array(2));
  return BigInt.asIntN(64, BigInt(words[0]) << 32n | BigInt(words[1])).toString();
};
const initialSeed = () => {
  const value = params().get("seed");
  if (value && /^-?\d{1,20}$/.test(value)) {
    const seed = BigInt(value);
    if (seed >= -(2n ** 63n) && seed < 2n ** 63n) return seed.toString();
  }
  return freshSeed();
};
function setLocation(seed: string, pack: string) {
  window.history.replaceState(null, "", `#sixmax-preflop?${new URLSearchParams({ seed, pack })}`);
}
function checkMetadata(metadata: PreflopMetadata) {
  if (metadata.packSchema !== "six-max-preflop-checkdown-pack/v1" ||
      metadata.publicationStatus !== "VALIDATION_ONLY" ||
      metadata.continuationModel !== "MANDATORY_CHECKDOWN" ||
      metadata.payoffMethod !== "EXACT_ENUMERATION" ||
      metadata.chanceModel !== "EXACT_RANGE_PRODUCT" ||
      metadata.maximumPayoffStandardErrorBb !== 0 || !Number.isFinite(metadata.nashConvBb) ||
      metadata.nashConvBb < 0 || metadata.nashConvBb > 0.05 || metadata.sessionLength !== 10)
    throw new Error("This solution is not supported by the full-round research drill.");
}

export default function SixMaxPreflopPage() {
  const [seed, setSeed] = useState(initialSeed);
  const [metadata, setMetadata] = useState<PreflopMetadata | null>(null);
  const [index, setIndex] = useState(0);
  const [question, setQuestion] = useState<PreflopQuestion | null>(null);
  const [feedback, setFeedback] = useState<PreflopFeedback | null>(null);
  const [answers, setAnswers] = useState<PreflopAction[]>([]);
  const [review, setReview] = useState<PreflopReview | null>(null);
  const [error, setError] = useState("");
  const [stale, setStale] = useState(false);
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  const [copied, setCopied] = useState(false);
  const operation = useRef<AbortController | null>(null);
  const shown = useRef<PreflopQuestion[]>([]);

  useEffect(() => () => operation.current?.abort(), []);
  useEffect(() => {
    if (metadata) return;
    const controller = new AbortController();
    preflopRequest<PreflopMetadata>("", { signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        checkMetadata(loaded); setMetadata(loaded);
        if (params().get("pack") && params().get("pack") !== loaded.packHash) {
          setStale(true); setError("This session link belongs to an older solution.");
        } else { setLocation(seed, loaded.packHash); setError(""); }
      })
      .catch((cause) => {
        if (!controller.signal.aborted)
          setError(cause instanceof PreflopRequestError && cause.status === 404
            ? "The full-round research drill is not enabled on this server."
            : cause instanceof Error ? cause.message : "Could not load the preflop solution.");
      });
    return () => controller.abort();
  }, [metadata, retry, seed]);

  useEffect(() => {
    if (!metadata || stale || review) return;
    const controller = new AbortController();
    preflopRequest<PreflopQuestion>(`/sessions/${seed}/questions/${index}`, { signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        if (loaded.packHash !== metadata.packHash || loaded.sessionSeed !== seed || loaded.index !== index)
          { setStale(true); setQuestion(null); setError(changed); }
        else { shown.current[index] = loaded; setQuestion(loaded); setError(""); }
      })
      .catch((cause) => {
        if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : "Could not load this decision.");
      });
    return () => controller.abort();
  }, [metadata, seed, index, stale, review, retry]);

  function failure(cause: unknown, fallback: string) {
    const message = cause instanceof Error ? cause.message : fallback;
    if (message === changed || /pack hash does not match/i.test(message)) setStale(true);
    setError(message);
  }
  async function answer(action: PreflopAction) {
    if (!metadata || !question || feedback || operation.current || stale) return;
    const controller = new AbortController(); operation.current = controller;
    setBusy(true); setError("");
    try {
      const result = await postPreflop<PreflopGradedQuestion>("/grade", {
        sessionSeed: seed, index, packHash: metadata.packHash, action,
      }, controller.signal);
      if (controller.signal.aborted) return;
      if (!sameQuestion(result.question, question) || result.feedback.selectedAction !== action) throw new Error(changed);
      setFeedback(result.feedback); setAnswers((current) => [...current.slice(0, index), action]);
    } catch (cause) { if (!controller.signal.aborted) failure(cause, "Could not grade this decision."); }
    finally { if (!controller.signal.aborted) { operation.current = null; setBusy(false); } }
  }
  async function advance() {
    if (!feedback || !metadata || operation.current || stale) return;
    if (index < metadata.sessionLength - 1) {
      setQuestion(null); setFeedback(null); setError(""); setIndex(index + 1); return;
    }
    const controller = new AbortController(); operation.current = controller;
    setBusy(true); setError("");
    try {
      const result = await postPreflop<PreflopReview>("/review", {
        sessionSeed: seed, packHash: metadata.packHash, actions: answers,
      }, controller.signal);
      if (controller.signal.aborted) return;
      if (result.packHash !== metadata.packHash || result.attempts.length !== metadata.sessionLength ||
          result.attempts.some((attempt, i) => !shown.current[i] || !sameQuestion(attempt.question, shown.current[i]) ||
            attempt.feedback.selectedAction !== answers[i])) throw new Error(changed);
      setReview(result);
    } catch (cause) { if (!controller.signal.aborted) failure(cause, "Could not review this session."); }
    finally { if (!controller.signal.aborted) { operation.current = null; setBusy(false); } }
  }
  async function newSession(replay = false) {
    if (!metadata || operation.current) return;
    const controller = new AbortController(); operation.current = controller;
    setBusy(true); setError("");
    try {
      const latest = await preflopRequest<PreflopMetadata>("", { signal: controller.signal });
      if (controller.signal.aborted) return;
      checkMetadata(latest);
      if (replay && latest.packHash !== metadata.packHash) throw new Error(changed);
      const nextSeed = replay ? seed : freshSeed();
      setLocation(nextSeed, latest.packHash); shown.current = [];
      setSeed(nextSeed); setMetadata(latest); setIndex(0); setAnswers([]);
      setQuestion(null); setFeedback(null); setReview(null); setStale(false); setCopied(false);
    } catch (cause) { if (!controller.signal.aborted) failure(cause, "Could not start a session."); }
    finally { if (!controller.signal.aborted) { operation.current = null; setBusy(false); } }
  }
  async function copySession() {
    try { await navigator.clipboard.writeText(window.location.href); setCopied(true); }
    catch { setError("Could not copy the link. You can copy the address from your browser."); }
  }
  useEffect(() => {
    const keys = (event: KeyboardEvent) => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey ||
          (event.target instanceof HTMLElement && (event.target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName))) ||
          busy || stale || review || !question) return;
      if (event.key === "Enter" && feedback &&
          !(event.target instanceof HTMLElement && event.target.closest("button,a,summary"))) {
        event.preventDefault(); void advance();
      }
      const action = question.legalActions[Number(event.key) - 1];
      if (/^[1-9]$/.test(event.key) && action && !feedback) { event.preventDefault(); void answer(action); }
    };
    window.addEventListener("keydown", keys); return () => window.removeEventListener("keydown", keys);
  });

  return <main className="trainer-page preflop-page">
    <header className="trainer-topbar"><a className="trainer-brand" href="#solver"><span>♠</span> PokerLab</a><a className="trainer-back" href="#solver">← Solver project</a></header>
    <div className="trainer-container hand-play-container">
      <div className="trainer-title-row hand-play-title"><div><p className="eyebrow">SIX-SEAT PREFLOP / SOLVER RESEARCH</p><h1>Play the preflop decision</h1><p className="trainer-intro">Ten decisions drawn from our saved six-seat policy. Opens, calls and re-raises are in play.</p></div><span className="trainer-status">VALIDATION ONLY</span></div>
      <div className="trainer-disclosure hand-play-disclosure" role="note">Synthetic ranges and a limited raise menu. If betting ends before an all-in, every later street is checked down. This is a solver test, not a general cash-game strategy.</div>
      {error && <div className="trainer-error" role="alert">{error} {!stale && <button className="trainer-button secondary" disabled={busy} onClick={() => { setError(""); setRetry((value) => value + 1); }}>Retry connection</button>}</div>}
      {stale && metadata && <button className="trainer-button" disabled={busy} onClick={() => void newSession()}>Start with current solution</button>}
      {!metadata && !error && <p className="trainer-loading" role="status">Loading preflop solution…</p>}
      {metadata && !stale && <div className="trainer-layout preflop-layout">
        <section className="trainer-card trainer-play preflop-play" aria-label="Full-round preflop drill">
          {review ? <>
            <div className="trainer-card-heading"><span>SESSION COMPLETE</span><span>10 / 10</span></div><h2>Your review</h2>
            <p className="trainer-summary">Total EV loss: <strong>{review.totalEvLossBb.toFixed(2)} bb</strong> · Average: {review.averageEvLossBb.toFixed(2)} bb per decision</p>
            <ol className="trainer-review-list preflop-review">{review.attempts.map((attempt) => <li key={attempt.question.index}><details>
              <summary><span>{attempt.question.index + 1}. {attempt.question.actingSeat} · {attempt.question.heroCombo}</span><span>{actionLabel(attempt.feedback.selectedAction, metadata.stackBb)} · {attempt.feedback.evLossBb.toFixed(2)} bb lost</span></summary>
              <SixMaxPreflopTable question={attempt.question} selectedAction={attempt.feedback.selectedAction} /><PreflopActionValues question={attempt.question} feedback={attempt.feedback} />
            </details></li>)}</ol>
            <p className="trainer-explanation">The total adds one-step decision losses against the saved policy. It is not full-hand exploitability.</p>
            <div className="trainer-actions"><button className="trainer-button" disabled={busy} onClick={() => void newSession()}>New session</button><button className="trainer-button secondary" disabled={busy} onClick={() => void newSession(true)}>Replay this session</button></div>
          </> : !question ? <p className="trainer-loading" role="status">{error ? "Decision unavailable." : "Loading decision…"}</p> : <>
            <div className="trainer-card-heading"><span>DECISION {index + 1} OF {metadata.sessionLength}</span><span>{question.actingSeat} {feedback ? "decision graded" : "to act"}</span></div>
            <div className="trainer-progress" aria-hidden="true"><span style={{ width: `${(index + 1) / metadata.sessionLength * 100}%` }} /></div>
            <SixMaxPreflopTable question={question} selectedAction={feedback?.selectedAction}>
              <h2>{question.actingSeat}: {question.toCallBb > 0 ? `facing ${question.toCallBb} bb` : "no bet to call"}</h2>
              <div className="trainer-actions preflop-actions">{question.legalActions.map((action, i) => <PokerActionButton key={action}
                label={actionLabel(action, question.stackBb)} tone={actionTone(action)} shortcut={i + 1}
                selected={feedback?.selectedAction === action} disabled={busy || Boolean(feedback)} onClick={() => void answer(action)}
                value={feedback ? { evBb: feedback.actionEvBb[action], frequency: feedback.actionFrequency[action], standardErrorBb: feedback.actionPayoffStandardErrorBb[action] } : undefined} />)}
                {feedback && <button className="trainer-button poker-next" disabled={busy} onClick={() => void advance()}>{index === metadata.sessionLength - 1 ? "Review session" : "Next decision"}<span aria-hidden="true">→</span></button>}
              </div>
              {feedback && <PokerDecisionFeedback choice={actionLabel(feedback.selectedAction, question.stackBb)} evLossBb={feedback.evLossBb}>
                EVs average over hidden hands compatible with your cards and the public actions. Later play follows the saved policy; a mixed frequency is not a command to always choose one action.
              </PokerDecisionFeedback>}
              <p className="preflop-keyboard" role="status">{busy ? "Grading your decision…" : feedback ? "Decision graded · Enter to continue" : `Your turn · 1–${question.legalActions.length} to choose`}</p>
            </SixMaxPreflopTable>
            {question.priorActions.length > 0 && <details className="preflop-history"><summary>Betting history · {question.priorActions.length} actions</summary><ol>{question.priorActions.map((event, i) => <li key={i}><strong>{event.seat}</strong> · {actionLabel(event.action, question.stackBb)}</li>)}</ol></details>}
          </>}
        </section>
        <aside className="trainer-card trainer-reference preflop-reference" aria-label="Preflop game details"><h2>Game details</h2><dl className="trainer-facts">
          <div><dt>Starting stacks</dt><dd>{metadata.stackBb} bb each</dd></div><div><dt>Blinds</dt><dd>{metadata.smallBlindBb} / 1 bb</dd></div><div><dt>Raise targets</dt><dd>{metadata.raiseToBb.map((size) => `${size} bb`).join(" / ")}</dd></div>
          <div><dt>Ranges by seat</dt><dd>{metadata.rangeComboCounts.join(" / ")} exact combos</dd></div><div><dt>Rake</dt><dd>{metadata.rake.fraction === 0 || metadata.rake.capBb === 0 ? "None" : `${100 * metadata.rake.fraction}% · ${metadata.rake.capBb} bb cap${metadata.rake.noFlopNoDrop ? " · no flop, no drop" : ""}`}</dd></div>
          <div><dt>Board payoffs</dt><dd>Exact enumeration</dd></div><div><dt>Measured NashConv</dt><dd>{metadata.nashConvBb.toFixed(6)} bb</dd></div><div><dt>Payoff sampling SE</dt><dd>{metadata.maximumPayoffStandardErrorBb} bb</dd></div>
        </dl><p className="trainer-boundary">All six hands are dealt with blockers, including folded seats. Only your hand is shown. This finite game ends in an all-in, a fold win or mandatory checkdown.</p>
          <div className="preflop-session-tools"><button className="trainer-button secondary" disabled={busy} onClick={() => void newSession()}>New session</button><button className="trainer-button secondary" disabled={busy} onClick={() => void copySession()}>Copy session link</button></div>
          {copied && <p role="status">Session link copied. It replays from decision one.</p>}
          <a className="trainer-lab-link" href="#multiway">Try the six-seat call/fold drill ↗</a>
        </aside>
      </div>}
    </div>
  </main>;
}
