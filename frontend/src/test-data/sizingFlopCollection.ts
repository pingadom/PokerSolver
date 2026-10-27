import saved from "./sizing-flop-collection-review.json";
import { sizingFlopCollectionStatus, sizingFlopCollectionSelection, sizingReviewCounts,
  type SizingFlopCollectionMetadata, type SizingFlopCollectionReview, type SizingFlopCollectionAction } from "../sizingFlopCollectionApi";
import { unpairedFlopCollectionMetadata } from "./unpairedFlopCollection";
export const sizingFlopCollectionFixture = saved as SizingFlopCollectionReview;
export const sizingFlopCollectionMetadata: SizingFlopCollectionMetadata = {
  ...unpairedFlopCollectionMetadata, collectionHash: saved.collectionHash, legacyCollectionHash: saved.questions[0].decision.legacy!.collectionHash,
  publicationStatus: sizingFlopCollectionStatus, selection: sizingFlopCollectionSelection, availableQuestions: 20,
  questionKinds: [...Array(18).fill("LEGACY_EIGHTEEN_QUALIFIED_COLLECTION"), ...Array(2).fill("POT_SIZE_BIG_BLIND_QUESTIONS")],
  independentInputSets: 2, addedReferenceSharing: "SEPARATE_BET_SIZE_GAME_TWO_FRESH_POLICIES_BOTH_BB_QUESTIONS_CHECKED/v1",
  startupBudget: {sourceReplays: 8, freshReferenceRuns: 16, freshIterations: 1452000},
};
export function sizingFlopCollectionFixtureReview(choices: {questionIndex: number; action: SizingFlopCollectionAction}[]): SizingFlopCollectionReview {
  const attempts = choices.map((c,index) => ({...sizingFlopCollectionFixture.attempts.find(a => a.questionIndex === c.questionIndex && a.action === c.action)!,index}));
  return {...sizingFlopCollectionFixture, attempts, counts: sizingReviewCounts(attempts)};
}
