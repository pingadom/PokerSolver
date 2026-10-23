import type { ReactNode } from "react";
import QualifiedFlopCollectionDecisionTable, { qualifiedFlopCollectionActionLabel } from "./QualifiedFlopCollectionDecisionTable";
import PairedFlopIntervalDecisionTable, { pairedFlopActionLabel } from "./PairedFlopIntervalDecisionTable";
import type { PairedFlopAction } from "./pairedFlopIntervalApi";
import type { CallFlopCollectionAction, CallFlopCollectionQuestion, CallFlopCollectionFeedback } from "./callFlopCollectionApi";

export function callFlopCollectionActionLabel(action: CallFlopCollectionAction, q: CallFlopCollectionQuestion) {
  return q.decision.legacy ? qualifiedFlopCollectionActionLabel(action, q.decision.legacy)
    : pairedFlopActionLabel(action as PairedFlopAction, q.decision.shared.decision);
}
export default function CallFlopCollectionDecisionTable({ question, feedback, knownFeedback, attemptCount, busy, submit, prompt, controls, children }: {
  question: CallFlopCollectionQuestion; feedback: CallFlopCollectionFeedback | null; knownFeedback?: CallFlopCollectionFeedback;
  attemptCount: number; busy: boolean; submit: (action: CallFlopCollectionAction) => void;
  prompt: string; controls: ReactNode; children?: ReactNode;
}) {
  const shared = { attemptCount, busy, submit, prompt };
  if (question.decision.legacy) return <QualifiedFlopCollectionDecisionTable {...shared} question={question.decision.legacy}
    feedback={feedback?.decision.legacy ?? null} knownFeedback={knownFeedback?.decision.legacy ?? undefined} controls={controls}>{children}</QualifiedFlopCollectionDecisionTable>;
  return <PairedFlopIntervalDecisionTable {...shared} question={question.decision.shared.decision}
    feedback={feedback?.decision.shared ?? null} knownMoves={knownFeedback?.decision.shared?.alternatives}
    researchDescription="These three supplied BTN hands share one qualified game and two fresh reference policies. Source-history reach is unavailable; public trainer admission remains off."
    controls={controls}>{children}</PairedFlopIntervalDecisionTable>;
}
