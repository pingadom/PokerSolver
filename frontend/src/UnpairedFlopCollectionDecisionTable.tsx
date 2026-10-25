import type { ReactNode } from "react";
import CallFlopCollectionDecisionTable, { callFlopCollectionActionLabel } from "./CallFlopCollectionDecisionTable";
import PairedFlopIntervalDecisionTable, { pairedFlopActionLabel } from "./PairedFlopIntervalDecisionTable";
import type { PairedFlopAction } from "./pairedFlopIntervalApi";
import type { UnpairedFlopCollectionAction, UnpairedFlopCollectionQuestion, UnpairedFlopCollectionFeedback } from "./unpairedFlopCollectionApi";

export function unpairedFlopCollectionActionLabel(action: UnpairedFlopCollectionAction, q: UnpairedFlopCollectionQuestion) {
  return q.decision.legacy ? callFlopCollectionActionLabel(action, q.decision.legacy)
    : pairedFlopActionLabel(action as PairedFlopAction, q.decision.shared.decision);
}
export default function UnpairedFlopCollectionDecisionTable({ question, feedback, knownFeedback, attemptCount, busy, submit, prompt, controls, children }: {
  question: UnpairedFlopCollectionQuestion; feedback: UnpairedFlopCollectionFeedback | null; knownFeedback?: UnpairedFlopCollectionFeedback;
  attemptCount: number; busy: boolean; submit: (action: UnpairedFlopCollectionAction) => void;
  prompt: string; controls: ReactNode; children?: ReactNode;
}) {
  const shared = { attemptCount, busy, submit, prompt };
  if (question.decision.legacy) return <CallFlopCollectionDecisionTable {...shared} question={question.decision.legacy}
    feedback={feedback?.decision.legacy ?? null} knownFeedback={knownFeedback?.decision.legacy ?? undefined} controls={controls}>{children}</CallFlopCollectionDecisionTable>;
  return <PairedFlopIntervalDecisionTable {...shared} question={question.decision.shared.decision}
    feedback={feedback?.decision.shared ?? null} knownMoves={knownFeedback?.decision.shared?.alternatives}
    researchDescription="These four supplied BTN hands share one qualified game on 6♦ T♦ Q♣ and two fresh reference policies. Source-history reach is unavailable; public trainer admission remains off."
    controls={controls}>{children}</PairedFlopIntervalDecisionTable>;
}
