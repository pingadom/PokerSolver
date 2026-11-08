import saved from "./sevens-preflop-drill.json";
import { sevensPreflopClient } from "../sevensPreflopApi";
import type { RootChoice, RootGrade, RootQuestion, RootReview } from "../rootPreflopProtocol";

export const sevensMetadata = saved.metadata;
export const sevensQuestion = saved.questions[0] as RootQuestion;
export const sevensGrades = saved.grades as RootGrade[];
export function sevensFixtureReview(choices: RootChoice[]): RootReview {
  const attempts = choices.map((attempt, ordinal) => ({ ordinal, attempt,
    grade: sevensGrades.find(g => g.move.action === attempt.action)! }));
  return { studyHash: saved.metadata.studyHash, attempts, counts: sevensPreflopClient.rootReviewCounts(attempts) };
}
