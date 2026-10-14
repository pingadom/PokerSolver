import type { ReactNode } from "react";
import FlopIntervalDecisionTable, { flopActionLabel } from "./FlopIntervalDecisionTable";
import LaterFlopIntervalDecisionTable, { laterFlopActionLabel } from "./LaterFlopIntervalDecisionTable";
import type { FlopAction } from "./flopIntervalApi";
import type { LaterFlopAction } from "./laterFlopIntervalApi";
import type { FlopCollectionAction, FlopCollectionQuestion, FlopCollectionFeedback } from "./flopCollectionApi";

export function flopCollectionActionLabel(action: FlopCollectionAction, question: FlopCollectionQuestion) {
  const d = question.decision;
  return d.kind === "INITIAL_CHECK_BET" ? flopActionLabel(action as FlopAction, d.initial)
    : laterFlopActionLabel(action as LaterFlopAction, d.later);
}
export default function FlopCollectionDecisionTable({ question, feedback, knownFeedback, attemptCount, busy, submit, prompt, controls, children }: {
  question: FlopCollectionQuestion; feedback: FlopCollectionFeedback | null; knownFeedback?: FlopCollectionFeedback;
  attemptCount: number; busy: boolean; submit: (action: FlopCollectionAction) => void;
  prompt: string; controls: ReactNode; children?: ReactNode;
}) {
  const q = question.decision, f = feedback?.decision, known = knownFeedback?.decision;
  const shared = { attemptCount, busy, submit, prompt, controls, children };
  if (q.kind === "INITIAL_CHECK_BET") return <FlopIntervalDecisionTable {...shared} question={q.initial}
    feedback={f?.kind === "INITIAL_CHECK_BET" ? f.initial : null}
    knownMoves={known?.kind === "INITIAL_CHECK_BET" ? known.initial.alternatives : undefined} />;
  return <LaterFlopIntervalDecisionTable {...shared} question={q.later}
    feedback={f?.kind === "BB_FACING_BET" ? f.later : null}
    knownMoves={known?.kind === "BB_FACING_BET" ? known.later.alternatives : undefined} />;
}
