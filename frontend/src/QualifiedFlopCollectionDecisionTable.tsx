import type { ReactNode } from "react";
import ExpandedFlopCollectionDecisionTable, { expandedFlopCollectionActionLabel } from "./ExpandedFlopCollectionDecisionTable";
import PairedFlopIntervalDecisionTable, { pairedFlopActionLabel } from "./PairedFlopIntervalDecisionTable";
import type { PairedFlopAction } from "./pairedFlopIntervalApi";
import type { QualifiedFlopCollectionAction, QualifiedFlopCollectionQuestion, QualifiedFlopCollectionFeedback } from "./qualifiedFlopCollectionApi";

export function qualifiedFlopCollectionActionLabel(action: QualifiedFlopCollectionAction, q: QualifiedFlopCollectionQuestion) {
  return q.decision.legacy ? expandedFlopCollectionActionLabel(action, q.decision.legacy)
    : pairedFlopActionLabel(action as PairedFlopAction, q.decision.weak);
}
export default function QualifiedFlopCollectionDecisionTable({ question, feedback, knownFeedback, attemptCount, busy, submit, prompt, controls, children }: {
  question: QualifiedFlopCollectionQuestion; feedback: QualifiedFlopCollectionFeedback | null; knownFeedback?: QualifiedFlopCollectionFeedback;
  attemptCount: number; busy: boolean; submit: (action: QualifiedFlopCollectionAction) => void;
  prompt: string; controls: ReactNode; children?: ReactNode;
}) {
  const shared = { attemptCount, busy, submit, prompt };
  if (question.decision.legacy) return <ExpandedFlopCollectionDecisionTable {...shared} question={question.decision.legacy}
    feedback={feedback?.decision.legacy ?? null} knownFeedback={knownFeedback?.decision.legacy ?? undefined} controls={controls}>{children}</ExpandedFlopCollectionDecisionTable>;
  return <PairedFlopIntervalDecisionTable {...shared} question={question.decision.weak}
    feedback={feedback?.decision.weak ?? null} knownMoves={knownFeedback?.decision.weak?.alternatives}
    controls={null}>{controls}{children}</PairedFlopIntervalDecisionTable>;
}
