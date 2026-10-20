import saved from "./qualified-flop-collection-review.json";
import { qualifiedFlopCollectionStatus, qualifiedFlopCollectionSelection, qualifiedFlopBeliefs, qualifiedGrade,
  type QualifiedFlopCollectionMetadata, type QualifiedFlopCollectionReview, type QualifiedFlopCollectionAction } from "../qualifiedFlopCollectionApi";
import { expandedFlopCollectionMetadata } from "./expandedFlopCollection";
export const qualifiedFlopCollectionFixture = saved as QualifiedFlopCollectionReview;
export const qualifiedFlopCollectionMetadata: QualifiedFlopCollectionMetadata = {
  ...expandedFlopCollectionMetadata, collectionHash: saved.collectionHash, legacyCollectionHash: saved.questions[0].decision.legacy!.collectionHash,
  publicationStatus: qualifiedFlopCollectionStatus, selection: qualifiedFlopCollectionSelection, beliefs: qualifiedFlopBeliefs,
  availableQuestions: 11, questionKinds: [...Array(10).fill("LEGACY_TEN_QUESTION_COLLECTION"), "QUALIFIED_WEAK_HAND_FLOP"],
};
export function qualifiedFlopCollectionFixtureReview(choices: {questionIndex: number; action: QualifiedFlopCollectionAction}[]): QualifiedFlopCollectionReview {
  const attempts = choices.map((c,index) => ({...qualifiedFlopCollectionFixture.attempts.find(a => a.questionIndex === c.questionIndex && a.action === c.action)!,index}));
  return {...qualifiedFlopCollectionFixture, attempts, counts: {
    distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size,
    withinLimit: attempts.filter(a => qualifiedGrade(a.feedback).selected.classification === "WITHIN_LIMIT").length,
    outsideLimit: attempts.filter(a => qualifiedGrade(a.feedback).selected.classification === "OUTSIDE_LIMIT").length,
    strategyDependent: attempts.filter(a => qualifiedGrade(a.feedback).selected.classification === "STRATEGY_DEPENDENT").length,
  }};
}
