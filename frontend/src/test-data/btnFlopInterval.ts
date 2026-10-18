import fixture from "./btn-flop-interval-drill.json";
import type { BtnFlopIntervalMetadata, BtnFlopIntervalReview, BtnFlopIntervalChoice } from "../btnFlopIntervalApi";
// CI compares these bytes with the complete physically replayed owned artifact.
export const btnFlopFixture = fixture as BtnFlopIntervalReview;
const q = btnFlopFixture.questions[0];
export const btnFlopMetadata: BtnFlopIntervalMetadata = {
  reportHash: q.reportHash, publicationStatus: q.publicationStatus, trainerAdmission: false,
  selection: q.selection, model: q.model, chanceModel: q.chanceModel, evScope: q.evScope,
  reachUnits: q.reachUnits, beliefs: q.beliefs, sourceHistoryReachStatus: q.sourceHistoryReachStatus,
  availableQuestions: 4, activePlayers: 2, maximumAttempts: 100,
};
export function btnFlopFixtureReview(choices: BtnFlopIntervalChoice[]): BtnFlopIntervalReview {
  const attempts = choices.map(({questionIndex, action}, index) => ({
    ...btnFlopFixture.attempts.find(a => a.questionIndex === questionIndex && a.action === action)!, index,
  }));
  return { ...btnFlopFixture, attempts, counts: {
    distinctQuestionsAttempted: new Set(choices.map(c => c.questionIndex)).size,
    withinLimit: attempts.filter(a => a.feedback.selected.classification === "WITHIN_LIMIT").length,
    outsideLimit: attempts.filter(a => a.feedback.selected.classification === "OUTSIDE_LIMIT").length,
    strategyDependent: attempts.filter(a => a.feedback.selected.classification === "STRATEGY_DEPENDENT").length,
  } };
}
