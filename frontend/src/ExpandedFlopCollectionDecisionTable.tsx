import type { ReactNode } from "react";
import FlopIntervalDecisionTable, { flopActionLabel } from "./FlopIntervalDecisionTable";
import LaterFlopIntervalDecisionTable, { laterFlopActionLabel } from "./LaterFlopIntervalDecisionTable";
import BtnFlopIntervalDecisionTable, { btnFlopActionLabel } from "./BtnFlopIntervalDecisionTable";
import type { FlopAction } from "./flopIntervalApi";
import type { LaterFlopAction } from "./laterFlopIntervalApi";
import type { ExpandedFlopCollectionAction, ExpandedFlopCollectionQuestion, ExpandedFlopCollectionFeedback } from "./expandedFlopCollectionApi";

import PairedFlopIntervalDecisionTable, { pairedFlopActionLabel } from "./PairedFlopIntervalDecisionTable";

export function expandedFlopCollectionActionLabel(action: ExpandedFlopCollectionAction, question: ExpandedFlopCollectionQuestion) {
  const d = question.decision;
  return d.kind === "INITIAL_CHECK_BET" ? flopActionLabel(action as FlopAction, d.initial)
    : d.kind === "BB_FACING_BET" ? laterFlopActionLabel(action as LaterFlopAction, d.later)
      : d.kind === "BTN_AFTER_CHECK" ? btnFlopActionLabel(action as FlopAction, d.btn)
        : pairedFlopActionLabel(action as LaterFlopAction, d.paired);
}
export default function ExpandedFlopCollectionDecisionTable({ question, feedback, knownFeedback, attemptCount, busy, submit, prompt, controls, children }: {
  question: ExpandedFlopCollectionQuestion; feedback: ExpandedFlopCollectionFeedback | null; knownFeedback?: ExpandedFlopCollectionFeedback;
  attemptCount: number; busy: boolean; submit: (action: ExpandedFlopCollectionAction) => void;
  prompt: string; controls: ReactNode; children?: ReactNode;
}) {
  const q = question.decision, f = feedback?.decision, known = knownFeedback?.decision;
  // Keep the verdict directly below the actions. Navigation and review follow it
  // without changing the older standalone drills' layout.
  const shared = { attemptCount, busy, submit, prompt, controls: null, children: <>{controls}{children}</> };
  if (q.kind === "INITIAL_CHECK_BET") return <FlopIntervalDecisionTable {...shared} question={q.initial}
    feedback={f?.kind === "INITIAL_CHECK_BET" ? f.initial : null}
    knownMoves={known?.kind === "INITIAL_CHECK_BET" ? known.initial.alternatives : undefined} />;
  if (q.kind === "BB_FACING_BET") return <LaterFlopIntervalDecisionTable {...shared} question={q.later}
    feedback={f?.kind === "BB_FACING_BET" ? f.later : null}
    knownMoves={known?.kind === "BB_FACING_BET" ? known.later.alternatives : undefined} />;
  if (q.kind === "PAIRED_BTN_FACING_BET") return <PairedFlopIntervalDecisionTable {...shared} question={q.paired}
    feedback={f?.kind === "PAIRED_BTN_FACING_BET" ? f.paired : null}
    knownMoves={known?.kind === "PAIRED_BTN_FACING_BET" ? known.paired.alternatives : undefined} />;
  return <BtnFlopIntervalDecisionTable {...shared} question={q.btn}
    feedback={f?.kind === "BTN_AFTER_CHECK" ? f.btn : null}
    knownMoves={known?.kind === "BTN_AFTER_CHECK" ? known.btn.alternatives : undefined} />;
}
