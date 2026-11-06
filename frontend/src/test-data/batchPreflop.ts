import saved from "./batch-preflop-drill.json";
import { batchPreflopClient } from "../batchPreflopApi";
import type { RootChoice, RootGrade, RootQuestion, RootReview } from "../rootPreflopProtocol";

export const batchMetadata = saved.metadata;
export const batchQuestion = saved.questions[0] as RootQuestion;
export const batchGrades = saved.grades as RootGrade[];
export function batchFixtureReview(choices: RootChoice[]): RootReview {
  const attempts = choices.map((attempt, ordinal) => ({ ordinal, attempt,
    grade: batchGrades.find(g => g.move.action === attempt.action)! }));
  return { studyHash: saved.metadata.studyHash, attempts, counts: batchPreflopClient.rootReviewCounts(attempts) };
}
