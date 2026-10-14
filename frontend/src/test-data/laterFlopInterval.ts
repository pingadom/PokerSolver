import fixture from "./later-flop-interval-drill.json";
import { laterFlopIntervalModel, laterFlopIntervalChance, type LaterFlopAction, type LaterFlopIntervalMetadata, type LaterFlopIntervalReview } from "../laterFlopIntervalApi";

// CI requires byte equality with the physically replayed saved drill.
export const laterFlopIntervalFixture = fixture as LaterFlopIntervalReview;
const q = laterFlopIntervalFixture.question;
export const laterFlopIntervalMetadata: LaterFlopIntervalMetadata = {
  reportHash: q.reportHash, publicationStatus: q.publicationStatus, trainerAdmission: false,
  selection: q.selection, model: laterFlopIntervalModel, chanceModel: laterFlopIntervalChance,
  evScope: q.evScope, reachUnits: q.reachUnits, beliefs: "EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1",
  sourceHistoryReachStatus: "UNAVAILABLE_NO_SOURCE_POLICY", availableQuestions: 1, activePlayers: 2,
  maximumAttempts: 100, maximumLossBb: q.maximumLossBb,
};
export function laterFlopFixtureReview(actions: LaterFlopAction[]): LaterFlopIntervalReview {
  const attempts = actions.map((action, index) => ({ ...laterFlopIntervalFixture.attempts.find((a) => a.action === action)!, index }));
  return { ...laterFlopIntervalFixture, attempts, counts: {
    withinLimit: attempts.filter((a) => a.feedback.selected.classification === "WITHIN_LIMIT").length,
    outsideLimit: attempts.filter((a) => a.feedback.selected.classification === "OUTSIDE_LIMIT").length,
    strategyDependent: attempts.filter((a) => a.feedback.selected.classification === "STRATEGY_DEPENDENT").length,
  } };
}
