import saved from "./mixed-flop-collection-review.json";
import { flopIntervalMetadata } from "./flopInterval";
import { mixedFlopCollectionSelection, mixedFlopCollectionStatus, mixedGrade,
  type MixedFlopCollectionMetadata, type MixedFlopCollectionReview, type MixedFlopCollectionAction } from "../mixedFlopCollectionApi";

// CI separately enforces byte equality with the complete physically replayed collection.
export const mixedFlopCollectionFixture = saved as MixedFlopCollectionReview;
export const mixedFlopCollectionMetadata: MixedFlopCollectionMetadata = {
  collectionHash: mixedFlopCollectionFixture.collectionHash, publicationStatus: mixedFlopCollectionStatus, trainerAdmission: false,
  selection: mixedFlopCollectionSelection, model: flopIntervalMetadata.model, chanceModel: flopIntervalMetadata.chanceModel,
  evScope: flopIntervalMetadata.evScope, reachUnits: flopIntervalMetadata.reachUnits, beliefs: flopIntervalMetadata.beliefs,
  sourceHistoryReachStatus: flopIntervalMetadata.sourceHistoryReachStatus, questionKinds: ["INITIAL_CHECK_BET", "BB_FACING_BET", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK"],
  availableQuestions: 6, activePlayers: 2, maximumAttempts: 100,
};
export function mixedFlopCollectionFixtureReview(choices: { questionIndex: number; action: MixedFlopCollectionAction }[]): MixedFlopCollectionReview {
  const attempts = choices.map((choice, index) => ({ ...mixedFlopCollectionFixture.attempts.find(a => a.questionIndex === choice.questionIndex && a.action === choice.action)!, index }));
  return { ...mixedFlopCollectionFixture, attempts, counts: {
    distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size,
    withinLimit: attempts.filter(a => mixedGrade(a.feedback).selected.classification === "WITHIN_LIMIT").length,
    outsideLimit: attempts.filter(a => mixedGrade(a.feedback).selected.classification === "OUTSIDE_LIMIT").length,
    strategyDependent: attempts.filter(a => mixedGrade(a.feedback).selected.classification === "STRATEGY_DEPENDENT").length,
  } };
}
