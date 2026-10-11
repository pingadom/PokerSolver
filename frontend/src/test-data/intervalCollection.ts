import fixture from "./interval-collection-review.json";
import type { PreflopAction } from "../sixMaxPreflopApi";
import { collectionStatus, collectionSelection, type CollectionMetadata, type CollectionReview, type CollectionFeedback } from "../intervalCollectionApi";

// CI binds these bytes to the offline review whose sources physically replay.
export const collectionFixture = fixture as CollectionReview;
export const collectionMetadata: CollectionMetadata = {
  collectionHash: collectionFixture.collectionHash, publicationStatus: collectionStatus, trainerAdmission: false,
  selection: collectionSelection, availableQuestions: collectionFixture.questions.length, activePlayers: 2, maximumAttempts: 100,
};
export function fixtureGrade(questionIndex: number, action: PreflopAction, source = collectionFixture): CollectionFeedback {
  const feedback = structuredClone(source.attempts.find((a) => a.questionIndex === questionIndex)!.feedback);
  feedback.decision.selected = feedback.decision.alternatives.find((m) => m.action === action)!;
  return feedback;
}
export function collectionReview(choices: { questionIndex: number; action: PreflopAction }[], source = collectionFixture): CollectionReview {
  const attempts = choices.map(({ questionIndex, action }, index) => ({ questionIndex, action, index, feedback: fixtureGrade(questionIndex, action, source) }));
  return { ...structuredClone(source), attempts, counts: {
    distinctQuestionsAttempted: new Set(choices.map((c) => c.questionIndex)).size,
    withinLimit: attempts.filter((a) => a.feedback.decision.selected.classification === "WITHIN_LIMIT").length,
    outsideLimit: attempts.filter((a) => a.feedback.decision.selected.classification === "OUTSIDE_LIMIT").length,
    strategyDependent: attempts.filter((a) => a.feedback.decision.selected.classification === "STRATEGY_DEPENDENT").length,
  } };
}
