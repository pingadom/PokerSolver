import saved from "./root-preflop-drill.json";
import { rootReviewCounts, type RootChoice, type RootGrade, type RootQuestion, type RootReview } from "../rootPreflopApi";

export const rootMetadata = saved.metadata;
export const rootQuestion = saved.questions[0] as RootQuestion;
export const rootGrades = saved.grades as RootGrade[];
export function rootFixtureReview(choices: RootChoice[]): RootReview {
  const attempts = choices.map((attempt, ordinal) => ({ ordinal, attempt,
    grade: rootGrades.find(g => g.move.action === attempt.action)! }));
  return { studyHash: saved.metadata.studyHash, attempts, counts: rootReviewCounts(attempts) };
}
