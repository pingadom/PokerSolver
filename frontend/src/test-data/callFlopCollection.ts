import saved from "./call-flop-collection-review.json";
import { callFlopCollectionStatus, callFlopCollectionSelection, callReviewCounts,
  type CallFlopCollectionMetadata, type CallFlopCollectionReview, type CallFlopCollectionAction } from "../callFlopCollectionApi";
import { qualifiedFlopCollectionMetadata } from "./qualifiedFlopCollection";
export const callFlopCollectionFixture = saved as CallFlopCollectionReview;
export const callFlopCollectionMetadata: CallFlopCollectionMetadata = {
  ...qualifiedFlopCollectionMetadata, collectionHash: saved.collectionHash, legacyCollectionHash: saved.questions[0].decision.legacy!.collectionHash,
  publicationStatus: callFlopCollectionStatus, selection: callFlopCollectionSelection, availableQuestions: 14,
  questionKinds: [...Array(11).fill("LEGACY_ELEVEN_QUALIFIED_COLLECTION"), ...Array(3).fill("SHARED_REFERENCE_CALL_QUESTIONS")],
  independentInputSets: 2, addedReferenceSharing: "ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1",
  startupBudget: {sourceReplays: 6, freshReferenceRuns: 12, freshIterations: 972000},
};
export function callFlopCollectionFixtureReview(choices: {questionIndex: number; action: CallFlopCollectionAction}[]): CallFlopCollectionReview {
  const attempts = choices.map((c,index) => ({...callFlopCollectionFixture.attempts.find(a => a.questionIndex === c.questionIndex && a.action === c.action)!,index}));
  return {...callFlopCollectionFixture, attempts, counts: callReviewCounts(attempts)};
}
