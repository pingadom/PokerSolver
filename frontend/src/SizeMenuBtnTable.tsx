import type { ReactNode } from "react";
import PokerTable from "./PokerTable";
import { intervalRange } from "./intervalTrainerFormatting";
import { btnMaximumAttempts, type BtnAction, type BtnGrade, type BtnMove, type BtnQuestion } from "./sizeMenuBtnApi";

export const btnActionLabel = (action: BtnAction, q: BtnQuestion) => action === "fold" ? "Fold" : `Call ${q.context.toCallBb} bb`;
export const btnClassificationLabel = (move: BtnMove) => move.classification === "WITHIN_LIMIT" ? "Within limit"
  : move.classification === "OUTSIDE_LIMIT" ? "Outside limit" : "Strategy-dependent";

export default function SizeMenuBtnTable({ question, feedback, busy, attemptCount, covered, submit, controls, children }: {
  question: BtnQuestion; feedback: BtnGrade | null; busy: boolean; attemptCount: number; covered: number;
  submit: (action: BtnAction) => void; controls: ReactNode; children?: ReactNode;
}) {
  const c = question.context;
  return <PokerTable heroSeat={c.actingSeat} heroCombo={question.heroCards} board={c.board} potBb={c.potBb} toCallBb={c.toCallBb}
    selectedMove={feedback ? { kind: feedback.move.action, label: btnActionLabel(feedback.move.action, question) } : undefined}
    players={c.players.map(player => {
      const last = c.flopHistory.filter(move => move.seat === player.seat).at(-1);
      const folded = player.status === "FOLDED";
      return { seat: player.seat, status: folded ? "Out of hand" : player.seat === c.actingSeat ? "Your decision" : "In hand",
        stack: `${player.remainingStackBb} bb behind`, contribution: `${player.committedBb} bb in pot`, folded, allIn: false,
        move: folded ? "fold" : last ? "raise" : "waiting",
        moveLabel: folded ? "Folded preflop" : last ? `Bet ${last.amountBb} bb · ¼ pot` : "Decision pending" };
    })}>
    <p className="interval-prompt">Hand {question.sourceCaseIndex + 1} of 4 · BTN facing a quarter-pot bet
      <span>{covered} of 4 practised · {attemptCount} / {btnMaximumAttempts} attempts</span></p>
    <div className="trainer-actions preflop-actions flop-interval-actions">
      {question.moves.map((move, i) => <button key={move.action} type="button" aria-label={btnActionLabel(move.action, question)}
        aria-describedby={`btn-sizing-value-${i}`} className={`trainer-button poker-action move-${move.action} graded${feedback?.move.action === move.action ? " selected" : ""}`}
        disabled={busy || !!feedback || attemptCount >= btnMaximumAttempts} onClick={() => submit(move.action)}>
        <span className="poker-action-label"><kbd aria-hidden="true">{i + 1}</kbd>{btnActionLabel(move.action, question)}</span>
        <span className="poker-action-value" id={`btn-sizing-value-${i}`}>
          <strong>Hand EV {intervalRange(move.lowerEvBb, move.upperEvBb)}</strong>
          <small>Loss {intervalRange(move.lowerLossBb, move.upperLossBb)}</small>
          {feedback && <small>{btnClassificationLabel(move)}{feedback.move.action === move.action ? " · Your choice" : ""}</small>}
        </span>
      </button>)}
    </div>
    {busy && <p role="status">Checking the requested result…</p>}
    {feedback ? <div className="poker-decision-feedback interval-feedback" aria-live="polite">
      <h2>{btnClassificationLabel(feedback.move)}</h2>
      <p>You chose {btnActionLabel(feedback.move.action, question).toLowerCase()}.
        {feedback.move.classification === "WITHIN_LIMIT" ? " Its loss stays within 0.01 bb across the supported strategies."
          : feedback.move.classification === "OUTSIDE_LIMIT" ? " Its loss exceeds 0.01 bb across the supported strategies."
            : " The supported strategies do not give a single verdict."}</p>
    </div> : <p className="btn-sizing-study-note">EVs are shown for study. A choice records an attempt.</p>}
    {controls}
    {children}
  </PokerTable>;
}
