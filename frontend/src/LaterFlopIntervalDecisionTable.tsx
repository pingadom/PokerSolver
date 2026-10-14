import type { ReactNode } from "react";
import PokerTable from "./PokerTable";
import type { LaterFlopAction, LaterFlopIntervalQuestion, LaterFlopIntervalFeedback, LaterFlopIntervalMove } from "./laterFlopIntervalApi";
import { intervalRange } from "./intervalTrainerFormatting";

export const laterFlopActionLabel = (action: LaterFlopAction, question: LaterFlopIntervalQuestion) => action === "fold" ? "Fold" : `Call ${question.context.toCallBb} bb`;
export const laterFlopClassificationLabel = (move: LaterFlopIntervalMove) => move.classification === "WITHIN_LIMIT" ? "Within limit"
  : move.classification === "OUTSIDE_LIMIT" ? "Outside limit" : "Strategy-dependent";

export default function LaterFlopIntervalDecisionTable({ question, feedback, knownMoves, attemptCount, busy, submit, controls, children, prompt }: {
  question: LaterFlopIntervalQuestion; feedback: LaterFlopIntervalFeedback | null; knownMoves?: LaterFlopIntervalMove[];
  attemptCount: number; busy: boolean; submit: (action: LaterFlopAction) => void; controls: ReactNode; children?: ReactNode; prompt?: string;
}) {
  const c = question.context;
  return <>
    <PokerTable heroSeat={question.hero} heroCombo={question.heroCards} board={c.board} potBb={c.potBb} toCallBb={c.toCallBb}
      selectedMove={feedback ? { kind: feedback.selected.action, label: laterFlopActionLabel(feedback.selected.action, question) } : undefined}
      players={c.players.map((player) => ({
        seat: player.seat, status: player.status === "FOLDED" ? "Out of hand" : "In hand",
        stack: `${player.remainingStackBb} bb behind`, contribution: `${player.committedBb} bb in pot`,
        folded: player.status === "FOLDED", allIn: false, move: player.status === "FOLDED" ? "fold" : player.seat === "BTN" ? "raise" : "check",
        moveLabel: player.status === "FOLDED" ? "Folded preflop" : player.seat === "BTN" ? `Bet ${c.betSizeBb} bb` : "Checked",
      }))}>
      <p className="interval-prompt">{prompt ?? (feedback ? "Your move and its supported ranges" : `${question.hero} · Your flop decision`)}
        <span>{c.toCallBb} bb to call · {attemptCount} / 100 attempts · Loss limit {question.maximumLossBb} bb</span></p>
      <div className="trainer-actions preflop-actions flop-interval-actions">
        {c.legalActions.map((action, index) => {
          const value = (feedback?.alternatives ?? knownMoves)?.find((m) => m.action === action);
          return <button key={action} type="button" aria-label={laterFlopActionLabel(action, question)}
            aria-describedby={value ? `later-flop-value-${index}` : undefined}
            className={`trainer-button poker-action move-${action}${feedback?.selected.action === action ? " selected" : ""}${value ? " graded" : ""}`}
            disabled={busy || !!feedback || attemptCount >= 100} onClick={() => submit(action)}>
            <span className="poker-action-label"><kbd aria-hidden="true">{index + 1}</kbd>{laterFlopActionLabel(action, question)}</span>
            {value && <span className="poker-action-value" id={`later-flop-value-${index}`}>
              <strong>Hand EV {intervalRange(value.lowerEvBb, value.upperEvBb)}</strong>
              <small>Loss {intervalRange(value.lowerLossBb, value.upperLossBb)}</small>
              <small>{laterFlopClassificationLabel(value)}{action === feedback?.selected.action ? " · Your choice" : ""}</small>
            </span>}
          </button>;
        })}
      </div>
      {!feedback && !knownMoves && <p className="flop-value-hint">Choose a move to reveal both EV and loss ranges.</p>}
      {busy && <p role="status">Checking the requested result…</p>}
      {controls}
      {feedback && <div className="poker-decision-feedback interval-feedback" aria-live="polite">
        <h2>{laterFlopClassificationLabel(feedback.selected)}</h2>
        <p>You chose {laterFlopActionLabel(feedback.selected.action, question).toLowerCase()}. {feedback.selected.classification === "WITHIN_LIMIT"
          ? `Its loss stays within ${question.maximumLossBb} bb across the supported strategies.`
          : feedback.selected.classification === "OUTSIDE_LIMIT" ? `Its loss exceeds ${question.maximumLossBb} bb across the supported strategies.`
            : `Its loss can fall on either side of ${question.maximumLossBb} bb. The supported strategies do not give a single verdict.`}</p>
        <p>Hand EV: <strong>{intervalRange(feedback.selected.lowerEvBb, feedback.selected.upperEvBb)}</strong> · Loss: <strong>{intervalRange(feedback.selected.lowerLossBb, feedback.selected.upperLossBb)}</strong></p>
        <details><summary>What these ranges mean</summary>
          <p>Hand EV is final chip profit, including chips already committed preflop. A negative value does not mean you lose that amount again by choosing this move. Hero uses optimal future continuation against each supported opponent strategy.</p>
          <p>Loss uses conservative bounds from separate action endpoints. It is not an exact joint-regret range, a strategy frequency or sampling error. Displayed endpoints are rounded outwards.</p>
          <p>This game allows one flop bet, followed by exact turn/river checkdown. Supplied hand beliefs do not establish how often the preceding history occurs.</p>
          <dl>{feedback.alternatives.map((m) => <div key={m.action}><dt>{laterFlopActionLabel(m.action, question)}</dt>
            <dd>Exact hand EV [{String(m.lowerEvBb)}, {String(m.upperEvBb)}] bb; loss [{String(m.lowerLossBb)}, {String(m.upperLossBb)}] bb</dd></div>)}</dl>
        </details>
      </div>}
      {children}
    </PokerTable>
    <details className="interval-context"><summary>Hand history and verified report</summary>
      <ol>{c.preflopHistory.map((move, i) => <li key={i}>{move.seat}: {move.kind.replaceAll("_", " ").toLowerCase()}{move.amountBb > 0 ? ` ${move.amountBb} bb` : ""}</li>)}</ol>
      <ol aria-label="Flop actions">{c.flopHistory.map((move, i) => <li key={i}>{move.seat}: {move.action}{move.amountBb > 0 ? ` ${move.amountBb} bb` : ""}</li>)}</ol>
      <p>Report <code>{question.reportHash}</code></p>
      <p>This single-question, two-active-player research example is not a general GTO trainer. Source-history reach is unavailable; public trainer admission remains off.</p>
      <p>Security slack {question.securitySlackBb} bb · Minimum question reach {question.minimumReach}, conditioned on the physical flop with hero’s past cancelled.</p>
    </details>
  </>;
}
