import type { ReactNode } from "react";
import PokerTable from "./PokerTable";
import type { PairedFlopAction, PairedFlopIntervalQuestion, PairedFlopIntervalFeedback, PairedFlopIntervalMove } from "./pairedFlopIntervalApi";
import { intervalRange } from "./intervalTrainerFormatting";

export const pairedFlopActionLabel = (action: PairedFlopAction, question: Pick<PairedFlopIntervalQuestion, "context">) => action === "fold" ? "Fold" : `Call ${question.context.toCallBb} bb`;
export const pairedFlopClassificationLabel = (move: Pick<PairedFlopIntervalMove, "classification">) => move.classification === "WITHIN_LIMIT" ? "Within limit"
  : move.classification === "OUTSIDE_LIMIT" ? "Outside limit" : "Strategy-dependent";

export default function PairedFlopIntervalDecisionTable({ question, feedback, knownMoves, attemptCount, busy, submit, controls, children, prompt, researchDescription }: {
  question: Omit<PairedFlopIntervalQuestion, "questionIndex">; feedback: Pick<PairedFlopIntervalFeedback, "selected" | "alternatives"> | null; knownMoves?: PairedFlopIntervalMove[];
  attemptCount: number; busy: boolean; submit: (action: PairedFlopAction) => void; controls: ReactNode; children?: ReactNode; prompt?: string; researchDescription?: string;
}) {
  const c = question.context;
  return <>
    <PokerTable heroSeat={question.hero} heroCombo={question.heroCards} board={c.board} potBb={c.potBb} toCallBb={c.toCallBb}
      selectedMove={feedback ? { kind: feedback.selected.action === "fold" ? "fold" : "call", label: pairedFlopActionLabel(feedback.selected.action, question) } : undefined}
      players={c.players.map((player) => ({
        seat: player.seat, status: player.status === "FOLDED" ? "Out of hand" : "In hand",
        stack: `${player.remainingStackBb} bb behind`, contribution: `${player.committedBb} bb in pot`,
        folded: player.status === "FOLDED", allIn: false, move: player.status === "FOLDED" ? "fold" : player.seat === "BB" ? "raise" : "waiting",
        moveLabel: player.status === "FOLDED" ? "Folded preflop" : player.seat === "BB" ? `Bet ${c.betSizeBb} bb` : "Decision pending",
      }))}>
      <p className="interval-prompt">{prompt ?? (feedback ? "Your move and its supported ranges" : `${question.hero} · Your flop decision`)}
        <span>Call {c.toCallBb} bb · {attemptCount} / 100 attempts · Loss limit {question.maximumLossBb} bb</span></p>
      <div className="trainer-actions preflop-actions flop-interval-actions">
        {c.legalActions.map((action, index) => {
          const value = (feedback?.alternatives ?? knownMoves)?.find((m) => m.action === action);
          return <button key={action} type="button" aria-label={pairedFlopActionLabel(action, question)}
            aria-describedby={value ? `paired-flop-value-${index}` : undefined}
            className={`trainer-button poker-action move-${action}${feedback?.selected.action === action ? " selected" : ""}${value ? " graded" : ""}`}
            disabled={busy || !!feedback || attemptCount >= 100} onClick={() => submit(action)}>
            <span className="poker-action-label"><kbd aria-hidden="true">{index + 1}</kbd>{pairedFlopActionLabel(action, question)}</span>
            {value && <span className="poker-action-value" id={`paired-flop-value-${index}`}>
              <strong>Hand EV {intervalRange(value.lowerEvBb, value.upperEvBb)}</strong>
              <small>Loss {intervalRange(value.lowerLossBb, value.upperLossBb)}</small>
              <small>{pairedFlopClassificationLabel(value)}{action === feedback?.selected.action ? " · Your choice" : ""}</small>
            </span>}
          </button>;
        })}
      </div>
      {!feedback && !knownMoves && <p className="flop-value-hint">Choose a move to reveal both EV and loss ranges.</p>}
      {busy && <p role="status">Checking the requested result…</p>}
      {controls}
      {feedback && <div className="poker-decision-feedback interval-feedback" aria-live="polite">
        <h2>{pairedFlopClassificationLabel(feedback.selected)}</h2>
        <p>You chose {pairedFlopActionLabel(feedback.selected.action, question).toLowerCase()}. {feedback.selected.classification === "WITHIN_LIMIT"
          ? `Its loss stays within ${question.maximumLossBb} bb across the supported strategies.`
          : feedback.selected.classification === "OUTSIDE_LIMIT" ? `Its loss exceeds ${question.maximumLossBb} bb across the supported strategies.`
            : `Its loss can fall on either side of ${question.maximumLossBb} bb. The supported strategies do not give a single verdict.`}</p>
        <p>Hand EV: <strong>{intervalRange(feedback.selected.lowerEvBb, feedback.selected.upperEvBb)}</strong> · Loss: <strong>{intervalRange(feedback.selected.lowerLossBb, feedback.selected.upperLossBb)}</strong></p>
        <details><summary>What these ranges mean</summary>
          <p>Hand EV is final chip profit, including chips already committed preflop. A negative value does not mean you lose that amount again by choosing this move. Hero uses optimal future continuation against each supported opponent strategy.</p>
          <p>Loss uses conservative bounds from separate action endpoints. It is not an exact joint-regret range, a strategy frequency or sampling error. Displayed endpoints are rounded outwards.</p>
          <p>This game allows one flop bet, followed by exact turn/river checkdown. Supplied hand beliefs do not establish how often the preceding history occurs.</p>
          <dl>{feedback.alternatives.map((m) => <div key={m.action}><dt>{pairedFlopActionLabel(m.action, question)}</dt>
            <dd>Exact hand EV [{String(m.lowerEvBb)}, {String(m.upperEvBb)}] bb; loss [{String(m.lowerLossBb)}, {String(m.upperLossBb)}] bb</dd></div>)}</dl>
        </details>
      </div>}
      {children}
    </PokerTable>
    <details className="interval-context"><summary>Hand history and research details</summary>
      <ol>{c.preflopHistory.map((move, i) => <li key={i}>{move.seat}: {move.kind.replaceAll("_", " ").toLowerCase()}{move.amountBb > 0 ? ` ${move.amountBb} bb` : ""}</li>)}</ol>
      <p>Flop: BB bet {c.betSizeBb} bb. BTN faces a call of {c.toCallBb} bb.</p>
      <p>Report <code>{question.reportHash}</code></p>
      <p>{researchDescription ?? "These four supplied BTN hands form a small research drill with two active players. Source-history reach is unavailable; public trainer admission remains off."}</p>
      <p>Security slack {question.securitySlackBb} bb · Minimum question reach {question.minimumReach}, conditioned on the physical flop with hero’s past cancelled.</p>
    </details>
  </>;
}
