import saved from "./flop-collection-review.json";
import { flopIntervalMetadata } from "./flopInterval";
import { flopCollectionSelection, flopCollectionStatus, collectionGrade,
  type FlopCollectionMetadata, type FlopCollectionReview, type FlopCollectionAction } from "../flopCollectionApi";

// CI separately enforces byte equality with the complete physically replayed collection.
export const flopCollectionFixture = saved as FlopCollectionReview;
export const flopCollectionMetadata: FlopCollectionMetadata = {
  collectionHash: flopCollectionFixture.collectionHash, publicationStatus: flopCollectionStatus, trainerAdmission: false,
  selection: flopCollectionSelection, model: flopIntervalMetadata.model, chanceModel: flopIntervalMetadata.chanceModel,
  evScope: flopIntervalMetadata.evScope, reachUnits: flopIntervalMetadata.reachUnits, beliefs: flopIntervalMetadata.beliefs,
  sourceHistoryReachStatus: flopIntervalMetadata.sourceHistoryReachStatus, questionKinds: ["INITIAL_CHECK_BET", "BB_FACING_BET"],
  availableQuestions: 2, activePlayers: 2, maximumAttempts: 100,
};
export function flopCollectionFixtureReview(choices: { questionIndex: number; action: FlopCollectionAction }[]): FlopCollectionReview {
  const attempts = choices.map((choice, index) => ({ ...flopCollectionFixture.attempts.find(a => a.questionIndex === choice.questionIndex && a.action === choice.action)!, index }));
  return { ...flopCollectionFixture, attempts, counts: {
    distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size,
    withinLimit: attempts.filter(a => collectionGrade(a.feedback).selected.classification === "WITHIN_LIMIT").length,
    outsideLimit: attempts.filter(a => collectionGrade(a.feedback).selected.classification === "OUTSIDE_LIMIT").length,
    strategyDependent: attempts.filter(a => collectionGrade(a.feedback).selected.classification === "STRATEGY_DEPENDENT").length,
  } };
}
