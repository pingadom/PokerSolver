import type { ReactNode } from "react";
import PokerTable, { type TableMove } from "./PokerTable";
import { actionLabel, type PreflopAction } from "./sixMaxPreflopApi";
import type { IntervalQuestion, IntervalFeedback, IntervalMove } from "./intervalTrainerApi";
import { intervalRange } from "./intervalTrainerFormatting";

const tone = (action: PreflopAction): TableMove => action.startsWith("raise:") ? "raise" : action as TableMove;
export const intervalActionLabel = (action: PreflopAction, question: IntervalQuestion) => {
  const hero = question.table.players.find((p) => p.seat === question.hero)!;
  return actionLabel(action, hero.committedBb + hero.remainingStackBb);
};
export const classificationLabel = (move: IntervalMove) => move.classification === "WITHIN_LIMIT" ? "Within limit"
  : move.classification === "OUTSIDE_LIMIT" ? "Outside limit" : "Strategy-dependent";

export default function IntervalDecisionTable({ question, feedback, attemptCount, busy, submit, controls, children, prompt }: {
  question: IntervalQuestion; feedback: IntervalFeedback | null; attemptCount: number; busy: boolean;
  submit: (action: PreflopAction) => void; controls: ReactNode; children?: ReactNode; prompt?: string;
}) {
  return <>
        <PokerTable heroSeat={question.hero} heroCombo={question.heroCards} potBb={question.table.potBb} toCallBb={question.table.toCallBb}
          selectedMove={feedback ? { kind: tone(feedback.selected.action), label: intervalActionLabel(feedback.selected.action, question) } : undefined}
          players={question.table.players.map((player) => {
            const move = player.lastAction;
            return { seat: player.seat, status: player.status === "FOLDED" ? "Out of hand" : player.status === "ALL_IN" ? "All-in" : "In hand",
              stack: `${player.remainingStackBb} bb behind`, contribution: `${player.committedBb} bb in pot`,
              folded: player.status === "FOLDED", allIn: player.status === "ALL_IN",
              move: move?.kind === "FOLD" ? "fold" : move?.kind === "RAISE_TO" ? "raise" : move?.kind === "CALL" ? "call" : move?.kind === "CHECK" ? "check" : move ? "blind" : "waiting",
              moveLabel: !move ? "Yet to act" : move.kind === "FOLD" ? "Folded" : move.kind === "CHECK" ? "Checked"
                : move.kind === "RAISE_TO" ? `Raised to ${move.amountBb} bb` : move.kind === "CALL" ? `Called to ${move.amountBb} bb` : `Posted ${move.amountBb} bb`,
            };
          })}>
          <p className="interval-prompt">{prompt ?? (feedback ? "Your move and its supported ranges" : `${question.hero} · Your decision`)}
            <span>{attemptCount} / 100 attempts · Loss limit {question.maximumLossBb} bb</span></p>
          <div className="trainer-actions preflop-actions">
            {question.legalActions.map((action, index) => {
              const value = feedback?.alternatives.find((move) => move.action === action);
              return <button key={action} type="button" aria-label={intervalActionLabel(action, question)}
                aria-describedby={value ? `interval-value-${index}` : undefined}
                className={`trainer-button poker-action move-${tone(action)}${feedback?.selected.action === action ? " selected" : ""}${value ? " graded" : ""}`}
                disabled={busy || !!feedback || attemptCount >= 100} onClick={() => void submit(action)}>
                <span className="poker-action-label"><kbd aria-hidden="true">{index + 1}</kbd>{intervalActionLabel(action, question)}</span>
                {value && <span className="poker-action-value" id={`interval-value-${index}`}>
                  <strong>EV {intervalRange(value.lowerEvBb, value.upperEvBb)}</strong>
                  <small>Loss {intervalRange(value.lowerLossBb, value.upperLossBb)}</small>
                  <small>{classificationLabel(value)}{action === feedback?.selected.action ? " · Your choice" : ""}</small>
                </span>}
              </button>;
            })}
          </div>
          {busy && <p role="status">Checking the requested result…</p>}
          {controls}
          {feedback && <div className="poker-decision-feedback interval-feedback" aria-live="polite">
            <h2>{classificationLabel(feedback.selected)}</h2>
            <p>You chose {intervalActionLabel(feedback.selected.action, question).toLowerCase()}. {feedback.selected.classification === "WITHIN_LIMIT"
              ? `Its loss stays within ${question.maximumLossBb} bb across the supported strategies.`
              : feedback.selected.classification === "OUTSIDE_LIMIT" ? `Its loss exceeds ${question.maximumLossBb} bb across the supported strategies.`
                : `Its loss can fall on either side of ${question.maximumLossBb} bb. The supported strategies do not give a single verdict.`}</p>
            <p>Decision EV: <strong>{intervalRange(feedback.selected.lowerEvBb, feedback.selected.upperEvBb)}</strong> · Loss: <strong>{intervalRange(feedback.selected.lowerLossBb, feedback.selected.upperLossBb)}</strong></p>
            <details><summary>Read the ranges and exact endpoints</summary>
              <p>EV is incremental to this decision, with optimal hero continuation against each fixed opponent strategy. Loss bounds compare moves jointly; subtracting separate EV endpoints does not reproduce them. Displayed endpoints are rounded outwards. These are supported strategy ranges, not sampling error or policy frequencies.</p>
              <p>Supplied conditional hand beliefs do not establish how often the preceding action history occurs. Checkdown postflop is required by this model.</p>
              <dl>{feedback.alternatives.map((move) => <div key={move.action}><dt>{intervalActionLabel(move.action, question)}</dt>
                <dd>EV [{String(move.lowerEvBb)}, {String(move.upperEvBb)}] bb; loss [{String(move.lowerLossBb)}, {String(move.upperLossBb)}] bb</dd></div>)}</dl>
            </details>
          </div>}
          {children}
        </PokerTable>
        <details className="interval-context"><summary>Public action history and report</summary>
          <ol>{question.history.map((move, index) => <li key={index}>{move.seat}: {intervalActionLabel(move.action, question)}</li>)}</ol>
          <p>Report <code>{question.reportHash}</code></p><p>Security slack {question.securitySlackBb} bb · Minimum conditional reach {question.minimumReach}</p>
        </details>
  </>;
}
