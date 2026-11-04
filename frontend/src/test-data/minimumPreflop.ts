import saved from "./minimum-preflop-drill.json";
import { minimumPreflopClient } from "../minimumPreflopApi";
import type { RootChoice, RootGrade, RootQuestion, RootReview } from "../rootPreflopProtocol";

export const minimumMetadata = saved.metadata;
export const minimumQuestion = saved.questions[0] as RootQuestion;
export const minimumGrades = saved.grades as RootGrade[];
export function minimumFixtureReview(choices: RootChoice[]): RootReview {
  const attempts = choices.map((attempt, ordinal) => ({ ordinal, attempt,
    grade: minimumGrades.find(g => g.move.action === attempt.action)! }));
  return { studyHash: saved.metadata.studyHash, attempts, counts: minimumPreflopClient.rootReviewCounts(attempts) };
}
