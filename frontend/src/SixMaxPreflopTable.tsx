import { actionLabel, type PreflopFeedback, type PreflopPlayer, type PreflopQuestion } from "./sixMaxPreflopApi";

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
export default function SixMaxPreflopTable({ question }: { question: PreflopQuestion }) {
  return <div className="preflop-table" aria-label="Six-seat action table">
    {question.players.map((player) => <div key={player.seat}
      className={`preflop-seat ${player.status.toLowerCase()}`} aria-label={`${player.seat} player`}>
      <div className="preflop-seat-top"><strong>{player.seat}</strong>
        <span>{player.status === "ACTING" ? "Your turn" : player.status === "ALL_IN" ? "All-in" : player.status === "FOLDED" ? "Out" : "In hand"}</span>
      </div><p className="preflop-last-action">{lastMove(player)}</p>
      <div className="preflop-seat-chips"><span>{player.remainingStackBb} bb behind</span><span>{player.committedBb} bb in pot</span></div>
      {player.seat === question.actingSeat && <div className="preflop-hole-cards" aria-label="Your cards">
        {question.heroCombo.split(" ").map((card) => <span key={card}
          className={card.endsWith("h") || card.endsWith("d") ? "red" : ""}>{card.slice(0, -1)}{({ s: "♠", h: "♥", d: "♦", c: "♣" } as Record<string, string>)[card.slice(-1)]}</span>)}
      </div>}
    </div>)}
  </div>;
}
export function PreflopActionValues({ question, feedback }: { question: PreflopQuestion; feedback: PreflopFeedback }) {
  return <div className="preflop-values" aria-label="Action values">
    {question.legalActions.map((action) => <div key={action} className={action === feedback.selectedAction ? "chosen" : ""}>
      <span>{actionLabel(action, question.stackBb)}{action === feedback.selectedAction ? " · Your choice" : ""}</span>
      <strong>{feedback.actionEvBb[action] >= 0 ? "+" : ""}{feedback.actionEvBb[action].toFixed(2)} bb</strong>
      <small>Policy frequency {(100 * feedback.actionFrequency[action]).toFixed(1)}%</small>
      <div className="preflop-frequency" aria-hidden="true"><span style={{ width: `${100 * feedback.actionFrequency[action]}%` }} /></div>
      {feedback.actionPayoffStandardErrorBb[action] > 0 && <small>Payoff SE {feedback.actionPayoffStandardErrorBb[action].toFixed(3)} bb</small>}
    </div>)}
  </div>;
}
