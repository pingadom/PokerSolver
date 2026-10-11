import fixture from "./interval-drill.json";
// CI requires byte equality with docs/data's physically replayed drill. Keeping
// this copy inside frontend also preserves its isolated Docker build context.
import type { PreflopAction } from "../sixMaxPreflopApi";
import type { IntervalMetadata, IntervalReview } from "../intervalTrainerApi";

export const intervalFixture = fixture as IntervalReview;
const q = intervalFixture.question;
export const intervalMetadata: IntervalMetadata = {
  reportHash: q.reportHash, publicationStatus: q.publicationStatus, trainerAdmission: false,
  selection: q.selection, model: q.model, beliefs: q.beliefs, sourceHistoryReachStatus: q.sourceHistoryReachStatus,
  availableQuestions: 1, activePlayers: 2, maximumAttempts: 100, maximumLossBb: q.maximumLossBb,
};
export function fixtureReview(actions: PreflopAction[]): IntervalReview {
  const attempts = actions.map((action, index) => ({ ...intervalFixture.attempts.find((a) => a.action === action)!, index }));
  return { ...intervalFixture, attempts, counts: {
    withinLimit: attempts.filter((a) => a.feedback.selected.classification === "WITHIN_LIMIT").length,
    outsideLimit: attempts.filter((a) => a.feedback.selected.classification === "OUTSIDE_LIMIT").length,
    strategyDependent: attempts.filter((a) => a.feedback.selected.classification === "STRATEGY_DEPENDENT").length,
  } };
}
