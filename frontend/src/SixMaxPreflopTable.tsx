import type { ReactNode } from "react";
import PokerTable, { type TableMove } from "./PokerTable";
import { actionLabel, type PreflopAction, type PreflopFeedback, type PreflopPlayer, type PreflopQuestion } from "./sixMaxPreflopApi";

export function actionTone(action: PreflopAction): TableMove {
  return action.startsWith("raise:") ? "raise" : action as TableMove;
}

function lastMove(player: PreflopPlayer) {
  const move = player.lastAction;
  if (!move) return "Yet to act";
  switch (move.kind) {
    case "POST_SMALL_BLIND": case "POST_BIG_BLIND": return `Posted ${move.amountBb} bb`;
    case "FOLD": return "Folded";
    case "CHECK": return "Checked";
    case "CALL": return `Called to ${move.amountBb} bb`;
    case "RAISE_TO": return `Raised to ${move.amountBb} bb`;
  }
}
export default function SixMaxPreflopTable({ question, selectedAction, children }: {
  question: PreflopQuestion; selectedAction?: PreflopAction; children?: ReactNode;
}) {
  return <PokerTable heroSeat={question.actingSeat} heroCombo={question.heroCombo}
    potBb={question.potBb} toCallBb={question.toCallBb}
    selectedMove={selectedAction ? { kind: actionTone(selectedAction), label: actionLabel(selectedAction, question.stackBb) } : undefined}
    players={question.players.map((player) => ({
      seat: player.seat, status: player.status === "FOLDED" ? "Out of hand" : player.status === "ALL_IN" ? "All-in" : "In hand",
      stack: `${player.remainingStackBb} bb behind`, contribution: `${player.committedBb} bb in pot`,
      folded: player.status === "FOLDED", allIn: player.status === "ALL_IN",
      move: player.lastAction?.kind === "CHECK" ? "check" : player.lastAction?.kind === "CALL" ? "call"
        : player.lastAction?.kind === "RAISE_TO" ? "raise" : player.lastAction?.kind === "FOLD" ? "fold"
        : player.lastAction ? "blind" : "waiting",
      moveLabel: lastMove(player),
    }))}>{children}</PokerTable>;
}
export function PreflopActionValues({ question, feedback }: { question: PreflopQuestion; feedback: PreflopFeedback }) {
  return <div className="preflop-values" aria-label="Action values">
    {question.legalActions.map((action) => <div key={action} className={`move-${actionTone(action)} ${action === feedback.selectedAction ? "chosen" : ""}`}>
      <span>{actionLabel(action, question.stackBb)}{action === feedback.selectedAction ? " · Your choice" : ""}</span>
      <strong>{feedback.actionEvBb[action] >= 0 ? "+" : ""}{feedback.actionEvBb[action].toFixed(2)} bb</strong>
      <small>Policy frequency {(100 * feedback.actionFrequency[action]).toFixed(1)}%</small>
      <div className="preflop-frequency" aria-hidden="true"><span style={{ width: `${100 * feedback.actionFrequency[action]}%` }} /></div>
      {feedback.actionPayoffStandardErrorBb[action] > 0 && <small>Payoff SE {feedback.actionPayoffStandardErrorBb[action].toFixed(3)} bb</small>}
    </div>)}
  </div>;
}
