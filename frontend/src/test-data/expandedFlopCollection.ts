import saved from "./expanded-flop-collection-review.json";
import { flopIntervalMetadata } from "./flopInterval";
import { expandedFlopCollectionSelection, expandedFlopCollectionStatus, expandedGrade,
  type ExpandedFlopCollectionMetadata, type ExpandedFlopCollectionReview, type ExpandedFlopCollectionAction } from "../expandedFlopCollectionApi";

// CI separately enforces byte equality with the complete physically replayed collection.
export const expandedFlopCollectionFixture = saved as ExpandedFlopCollectionReview;
export const expandedFlopCollectionMetadata: ExpandedFlopCollectionMetadata = {
  collectionHash: expandedFlopCollectionFixture.collectionHash, publicationStatus: expandedFlopCollectionStatus, trainerAdmission: false,
  selection: expandedFlopCollectionSelection, model: flopIntervalMetadata.model, chanceModel: flopIntervalMetadata.chanceModel,
  evScope: flopIntervalMetadata.evScope, reachUnits: flopIntervalMetadata.reachUnits, beliefs: flopIntervalMetadata.beliefs,
  sourceHistoryReachStatus: flopIntervalMetadata.sourceHistoryReachStatus, questionKinds: ["INITIAL_CHECK_BET", "BB_FACING_BET", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "PAIRED_BTN_FACING_BET", "PAIRED_BTN_FACING_BET", "PAIRED_BTN_FACING_BET", "PAIRED_BTN_FACING_BET"],
  availableQuestions: 10, activePlayers: 2, maximumAttempts: 100,
};
export function expandedFlopCollectionFixtureReview(choices: { questionIndex: number; action: ExpandedFlopCollectionAction }[]): ExpandedFlopCollectionReview {
  const attempts = choices.map((choice, index) => ({ ...expandedFlopCollectionFixture.attempts.find(a => a.questionIndex === choice.questionIndex && a.action === choice.action)!, index }));
  return { ...expandedFlopCollectionFixture, attempts, counts: {
    distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size,
    withinLimit: attempts.filter(a => expandedGrade(a.feedback).selected.classification === "WITHIN_LIMIT").length,
    outsideLimit: attempts.filter(a => expandedGrade(a.feedback).selected.classification === "OUTSIDE_LIMIT").length,
    strategyDependent: attempts.filter(a => expandedGrade(a.feedback).selected.classification === "STRATEGY_DEPENDENT").length,
  } };
}
