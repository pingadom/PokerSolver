import type { ReactNode } from "react";
import FlopIntervalDecisionTable, { flopActionLabel } from "./FlopIntervalDecisionTable";
import LaterFlopIntervalDecisionTable, { laterFlopActionLabel } from "./LaterFlopIntervalDecisionTable";
import BtnFlopIntervalDecisionTable, { btnFlopActionLabel } from "./BtnFlopIntervalDecisionTable";
import type { FlopAction } from "./flopIntervalApi";
import type { LaterFlopAction } from "./laterFlopIntervalApi";
import type { MixedFlopCollectionAction, MixedFlopCollectionQuestion, MixedFlopCollectionFeedback } from "./mixedFlopCollectionApi";

export function mixedFlopCollectionActionLabel(action: MixedFlopCollectionAction, question: MixedFlopCollectionQuestion) {
  const d = question.decision;
  return d.kind === "INITIAL_CHECK_BET" ? flopActionLabel(action as FlopAction, d.initial)
    : d.kind === "BB_FACING_BET" ? laterFlopActionLabel(action as LaterFlopAction, d.later)
      : btnFlopActionLabel(action as FlopAction, d.btn);
}
export default function MixedFlopCollectionDecisionTable({ question, feedback, knownFeedback, attemptCount, busy, submit, prompt, controls, children }: {
  question: MixedFlopCollectionQuestion; feedback: MixedFlopCollectionFeedback | null; knownFeedback?: MixedFlopCollectionFeedback;
  attemptCount: number; busy: boolean; submit: (action: MixedFlopCollectionAction) => void;
  prompt: string; controls: ReactNode; children?: ReactNode;
}) {
  const q = question.decision, f = feedback?.decision, known = knownFeedback?.decision;
  const shared = { attemptCount, busy, submit, prompt, controls, children };
  if (q.kind === "INITIAL_CHECK_BET") return <FlopIntervalDecisionTable {...shared} question={q.initial}
    feedback={f?.kind === "INITIAL_CHECK_BET" ? f.initial : null}
    knownMoves={known?.kind === "INITIAL_CHECK_BET" ? known.initial.alternatives : undefined} />;
  if (q.kind === "BB_FACING_BET") return <LaterFlopIntervalDecisionTable {...shared} question={q.later}
    feedback={f?.kind === "BB_FACING_BET" ? f.later : null}
    knownMoves={known?.kind === "BB_FACING_BET" ? known.later.alternatives : undefined} />;
  return <BtnFlopIntervalDecisionTable {...shared} question={q.btn}
    feedback={f?.kind === "BTN_AFTER_CHECK" ? f.btn : null}
    knownMoves={known?.kind === "BTN_AFTER_CHECK" ? known.btn.alternatives : undefined} />;
}
