import fixture from "./flop-interval-drill.json";
import { flopIntervalModel, flopIntervalChance, type FlopAction, type FlopIntervalMetadata, type FlopIntervalReview } from "../flopIntervalApi";

// CI requires byte equality with the physically replayed saved drill.
export const flopIntervalFixture = fixture as FlopIntervalReview;
const q = flopIntervalFixture.question;
export const flopIntervalMetadata: FlopIntervalMetadata = {
  reportHash: q.reportHash, publicationStatus: q.publicationStatus, trainerAdmission: false,
  selection: q.selection, model: flopIntervalModel, chanceModel: flopIntervalChance,
  evScope: q.evScope, reachUnits: q.reachUnits, beliefs: "EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1",
  sourceHistoryReachStatus: "UNAVAILABLE_NO_SOURCE_POLICY", availableQuestions: 1, activePlayers: 2,
  maximumAttempts: 100, maximumLossBb: q.maximumLossBb,
};
export function flopFixtureReview(actions: FlopAction[]): FlopIntervalReview {
  const attempts = actions.map((action, index) => ({ ...flopIntervalFixture.attempts.find((a) => a.action === action)!, index }));
  return { ...flopIntervalFixture, attempts, counts: {
    withinLimit: attempts.filter((a) => a.feedback.selected.classification === "WITHIN_LIMIT").length,
    outsideLimit: attempts.filter((a) => a.feedback.selected.classification === "OUTSIDE_LIMIT").length,
    strategyDependent: attempts.filter((a) => a.feedback.selected.classification === "STRATEGY_DEPENDENT").length,
  } };
}
