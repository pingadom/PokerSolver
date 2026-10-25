import saved from "./unpaired-flop-collection-review.json";
import { unpairedFlopCollectionStatus, unpairedFlopCollectionSelection, unpairedReviewCounts,
  type UnpairedFlopCollectionMetadata, type UnpairedFlopCollectionReview, type UnpairedFlopCollectionAction } from "../unpairedFlopCollectionApi";
import { callFlopCollectionMetadata } from "./callFlopCollection";
export const unpairedFlopCollectionFixture = saved as UnpairedFlopCollectionReview;
export const unpairedFlopCollectionMetadata: UnpairedFlopCollectionMetadata = {
  ...callFlopCollectionMetadata, collectionHash: saved.collectionHash, legacyCollectionHash: saved.questions[0].decision.legacy!.collectionHash,
  publicationStatus: unpairedFlopCollectionStatus, selection: unpairedFlopCollectionSelection, availableQuestions: 18,
  questionKinds: [...Array(14).fill("LEGACY_FOURTEEN_QUALIFIED_COLLECTION"), ...Array(4).fill("SHARED_REFERENCE_UNPAIRED_QUESTIONS")],
  independentInputSets: 2, addedReferenceSharing: "ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1",
  startupBudget: {sourceReplays: 7, freshReferenceRuns: 14, freshIterations: 1212000},
};
export function unpairedFlopCollectionFixtureReview(choices: {questionIndex: number; action: UnpairedFlopCollectionAction}[]): UnpairedFlopCollectionReview {
  const attempts = choices.map((c,index) => ({...unpairedFlopCollectionFixture.attempts.find(a => a.questionIndex === c.questionIndex && a.action === c.action)!,index}));
  return {...unpairedFlopCollectionFixture, attempts, counts: unpairedReviewCounts(attempts)};
}
