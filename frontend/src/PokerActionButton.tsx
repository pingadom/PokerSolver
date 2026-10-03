import { useId, type ReactNode } from "react";
import type { TableMove } from "./PokerTable";

export default function PokerActionButton({ label, tone, shortcut, selected, disabled, onClick, value }: {
  label: string; tone: TableMove; shortcut?: number; selected: boolean; disabled: boolean;
  onClick: () => void;
  value?: { evBb: number; frequency: number; standardErrorBb?: number };
}) {
  const descriptionId = useId();
  return <button type="button" aria-label={label} aria-describedby={value ? descriptionId : undefined}
    className={`trainer-button poker-action move-${tone}${selected ? " selected" : ""}${value ? " graded" : ""}`}
    disabled={disabled} onClick={onClick}>
    <span className="poker-action-label">{shortcut && <kbd aria-hidden="true">{shortcut}</kbd>}{label}</span>
    {value && <span className="poker-action-value" id={descriptionId}>
      <strong>EV {value.evBb >= 0 ? "+" : ""}{value.evBb.toFixed(2)} bb</strong>
      <small>Policy frequency {(100 * value.frequency).toFixed(1)}%</small>
      <span className="preflop-frequency" aria-hidden="true"><span style={{ width: `${100 * value.frequency}%` }} /></span>
      {(value.standardErrorBb ?? 0) > 0 && <small>Payoff SE {value.standardErrorBb?.toFixed(3)} bb</small>}
      {selected && <small className="poker-your-choice">Your choice</small>}
    </span>}
  </button>;
}

export function PokerDecisionFeedback({ choice, evLossBb, children }: {
  choice: string; evLossBb: number; children: ReactNode;
}) {
  return <div className="poker-decision-feedback" aria-live="polite">
    <h3>Decision feedback</h3>
    <p>You chose {choice.toLowerCase()}. EV loss: <strong>{evLossBb.toFixed(2)} bb</strong>.</p>
    <details><summary>How to read these values</summary><p>{children}</p></details>
  </div>;
}
