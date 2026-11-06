import saved from "./sequence-preflop-drill.json";
import { sequencePreflopClient } from "../sequencePreflopApi";
import type { RootChoice, RootGrade, RootQuestion, RootReview } from "../rootPreflopProtocol";

export const sequenceMetadata = saved.metadata;
export const sequenceQuestion = saved.questions[0] as RootQuestion;
export const sequenceGrades = saved.grades as RootGrade[];
export function sequenceFixtureReview(choices: RootChoice[]): RootReview {
  const attempts = choices.map((attempt, ordinal) => ({ ordinal, attempt,
    grade: sequenceGrades.find(g => g.move.action === attempt.action)! }));
  return { studyHash: saved.metadata.studyHash, attempts, counts: sequencePreflopClient.rootReviewCounts(attempts) };
}
