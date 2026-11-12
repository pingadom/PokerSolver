import saved from "./bank-preflop-drill.json";
import { bankPreflopClient } from "../bankPreflopApi";
import type { RootChoice, RootGrade, RootQuestion, RootReview } from "../bankPreflopProtocol";
export const bankMetadata = saved.metadata;
export const bankQuestions = saved.questions as RootQuestion[];
export const bankGrades = saved.grades as RootGrade[];
export function bankFixtureReview(choices: RootChoice[]): RootReview {
  const attempts = choices.map((attempt, ordinal) => ({ ordinal, attempt,
    grade: bankGrades.find(g => g.sourceCaseIndex === attempt.sourceCaseIndex && g.move.action === attempt.action)! }));
  return { studyHash: saved.metadata.studyHash, attempts, counts: bankPreflopClient.rootReviewCounts(attempts) };
}
