import type { ReactNode } from "react";
import PokerTable from "./PokerTable";
import UnpairedFlopCollectionDecisionTable, { unpairedFlopCollectionActionLabel } from "./UnpairedFlopCollectionDecisionTable";
import { intervalRange } from "./intervalTrainerFormatting";
import type { SizingFlopCollectionAction, SizingFlopCollectionQuestion, SizingFlopCollectionFeedback } from "./sizingFlopCollectionApi";

export function sizingFlopCollectionActionLabel(action: SizingFlopCollectionAction, q: SizingFlopCollectionQuestion) {
  return q.decision.legacy ? unpairedFlopCollectionActionLabel(action, q.decision.legacy)
    : action === "fold" ? "Fold" : `Call ${q.decision.pot.table.toCallBb} bb`;
}
const verdict = (classification: string) => classification === "WITHIN_LIMIT" ? "Within limit" : classification === "OUTSIDE_LIMIT" ? "Outside limit" : "Strategy-dependent";

export default function SizingFlopCollectionDecisionTable({ question, feedback, knownFeedback, attemptCount, busy, submit, prompt, controls, children }: {
  question: SizingFlopCollectionQuestion; feedback: SizingFlopCollectionFeedback | null; knownFeedback?: SizingFlopCollectionFeedback;
  attemptCount: number; busy: boolean; submit: (action: SizingFlopCollectionAction) => void;
  prompt: string; controls: ReactNode; children?: ReactNode;
}) {
  if (question.decision.legacy) return <UnpairedFlopCollectionDecisionTable question={question.decision.legacy}
    feedback={feedback?.decision.legacy ?? null} knownFeedback={knownFeedback?.decision.legacy ?? undefined}
    attemptCount={attemptCount} busy={busy} submit={submit} prompt={prompt} controls={controls}>{children}</UnpairedFlopCollectionDecisionTable>;
  const pot = question.decision.pot, c = pot.table, graded = feedback?.decision.pot;
  const moves = graded?.alternatives ?? knownFeedback?.decision.pot?.alternatives;
  return <>
    <PokerTable heroSeat={c.actingSeat} heroCombo={pot.heroCards} board={c.board} potBb={c.potBb} toCallBb={c.toCallBb}
      selectedMove={graded ? {kind: graded.action === "bet" ? "raise" : graded.action, label: sizingFlopCollectionActionLabel(graded.action, question)} : undefined}
      players={c.players.map(player => ({ seat: player.seat, status: player.status === "FOLDED" ? "Out of hand" : "In hand",
        stack: `${player.remainingStackBb} bb behind`, contribution: `${player.committedBb} bb in pot`, folded: player.status === "FOLDED", allIn: player.status === "ALL_IN",
        move: player.status === "FOLDED" ? "fold" : player.seat === "BTN" ? "raise" : "check",
        moveLabel: player.status === "FOLDED" ? "Folded preflop" : player.seat === "BTN" ? `Bet ${c.betSizeBb} bb · Pot-sized` : "Checked",
      }))}>
      <p className="interval-prompt">{prompt}<span>Pot-sized bet · {c.toCallBb} bb to call · {attemptCount} / 100 attempts · Loss limit 0.01 bb</span></p>
      <div className="trainer-actions preflop-actions flop-interval-actions">
        {c.legalActions.map((action,index) => {
          const value = moves?.find(m => m.action === action);
          return <button key={action} type="button" aria-label={sizingFlopCollectionActionLabel(action,question)}
            aria-describedby={value ? `sizing-pot-value-${index}` : undefined}
            className={`trainer-button poker-action move-${action}${graded?.action === action ? " selected" : ""}${value ? " graded" : ""}`}
            disabled={busy || !!graded || attemptCount >= 100} onClick={() => submit(action)}>
            <span className="poker-action-label"><kbd aria-hidden="true">{index+1}</kbd>{sizingFlopCollectionActionLabel(action,question)}</span>
            {value && <span className="poker-action-value" id={`sizing-pot-value-${index}`}>
              <strong>Hand EV {intervalRange(value.lowerEvBb,value.upperEvBb)}</strong>
              <small>Loss {intervalRange(value.lowerLossBb,value.upperLossBb)}</small>
              <small>{verdict(value.classification)}{graded?.action === action ? " · Your choice" : ""}</small>
            </span>}
          </button>;
        })}
      </div>
      {!graded && !moves && <p className="flop-value-hint">Choose a move to reveal both EV and loss ranges.</p>}
      {busy && <p role="status">Checking the requested result…</p>}
      {controls}
      {graded && <div className="poker-decision-feedback interval-feedback" aria-live="polite">
        <h2>{verdict(graded.selected.classification)}</h2>
        <p>You chose {sizingFlopCollectionActionLabel(graded.action,question).toLowerCase()}. {graded.selected.classification === "WITHIN_LIMIT"
          ? "Its loss stays within 0.01 bb across the supported strategies."
          : graded.selected.classification === "OUTSIDE_LIMIT" ? "Its loss exceeds 0.01 bb across the supported strategies." : "The supported strategies do not give a single verdict."}</p>
        <p>Hand EV: <strong>{intervalRange(graded.selected.lowerEvBb,graded.selected.upperEvBb)}</strong> · Loss: <strong>{intervalRange(graded.selected.lowerLossBb,graded.selected.upperLossBb)}</strong></p>
        <details><summary>What these ranges mean</summary>
          <p>Hand EV includes chips already committed. A negative value does not mean you lose that amount again by choosing this move.</p>
          <p>Loss compares both moves against the same supported opponent strategy. The bounds include numerical slack; they are not action frequencies or sampling error.</p>
          <p>This pot-sized betting game has its own qualified strategy. The board and hand labels cannot transfer EVs from another bet size.</p>
          <dl>{graded.alternatives.map(m => <div key={m.action}><dt>{sizingFlopCollectionActionLabel(m.action,question)}</dt><dd>Exact hand EV [{String(m.lowerEvBb)}, {String(m.upperEvBb)}] bb; loss [{String(m.lowerLossBb)}, {String(m.upperLossBb)}] bb</dd></div>)}</dl>
        </details>
      </div>}
      {children}
    </PokerTable>
    <details className="interval-context"><summary>Hand history and verified report</summary>
      <ol>{c.preflopHistory.map((move,i) => <li key={i}>{move.seat}: {move.kind.replaceAll("_"," ").toLowerCase()}{move.amountBb > 0 ? ` ${move.amountBb} bb` : ""}</li>)}</ol>
      <ol aria-label="Flop actions">{c.flopHistory.map((move,i) => <li key={i}>{move.seat}: {move.action}{move.amountBb > 0 ? ` ${move.amountBb} bb` : ""}</li>)}</ol>
      <p>Report <code>{pot.reportHash}</code> · Source question {pot.sourceIndex}</p>
      <p>Two-active-player research game at a six-seat table. Supplied range beliefs, no rake, one flop bet followed by exact checkdown. Public trainer admission remains off.</p>
    </details>
  </>;
}
