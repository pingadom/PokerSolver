import type { PreflopAction } from "./sixMaxPreflopApi";
import {
  IntervalProtocolError, IntervalRequestError, intervalEqual, parseIntervalQuestion, parseIntervalFeedback,
  type IntervalQuestion, type IntervalFeedback, type IntervalMetadata,
} from "./intervalTrainerApi";

export const collectionStatus = "ORDERED_INTERVAL_COLLECTION_RESEARCH_ONLY";
export const collectionSelection = "ORDERED_DISTINCT_QUALIFIED_INTERVAL_QUESTIONS/v1";
export const collectionSchema = "pokerlab-supplied-interval-collection-review/v1";
export type CollectionMetadata = {
  collectionHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  availableQuestions: number; activePlayers: number; maximumAttempts: number;
};
export type CollectionQuestion = {
  collectionHash: string; index: number; totalQuestions: number; selection: string; decision: IntervalQuestion;
};
export type CollectionFeedback = { collectionHash: string; questionIndex: number; decision: IntervalFeedback };
export type CollectionAttempt = { index: number; questionIndex: number; action: PreflopAction; feedback: CollectionFeedback };
export type CollectionReview = {
  schemaVersion: string; collectionHash: string; selection: string; questions: CollectionQuestion[];
  attempts: CollectionAttempt[];
  counts: { distinctQuestionsAttempted: number; withinLimit: number; outsideLimit: number; strategyDependent: number };
};
function ensure(condition: unknown): asserts condition {
  if (!condition) throw new IntervalProtocolError();
}
function object(value: unknown, keys: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  const result = value as Record<string, unknown>;
  ensure(Object.keys(result).sort().join(" ") === keys.split(" ").sort().join(" "));
  return result;
}
export function parseCollectionMetadata(value: unknown): CollectionMetadata {
  const m = object(value, "collectionHash publicationStatus trainerAdmission selection availableQuestions activePlayers maximumAttempts");
  ensure(typeof m.collectionHash === "string" && /^[a-f0-9]{64}$/.test(m.collectionHash) &&
    m.publicationStatus === collectionStatus && m.trainerAdmission === false && m.selection === collectionSelection &&
    Number.isInteger(m.availableQuestions) && Number(m.availableQuestions) >= 2 && Number(m.availableQuestions) <= 16 &&
    m.activePlayers === 2 && m.maximumAttempts === 100);
  return value as CollectionMetadata;
}
export function parseCollectionQuestion(value: unknown, metadata: CollectionMetadata, index: number): CollectionQuestion {
  const q = object(value, "collectionHash index totalQuestions selection decision");
  ensure(Number.isInteger(index) && index >= 0 && index < metadata.availableQuestions &&
    q.collectionHash === metadata.collectionHash && q.index === index &&
    q.totalQuestions === metadata.availableQuestions && q.selection === collectionSelection);
  // The inner object retains the original single-question protocol. Collection
  // selection lives only in the outer envelope; the old parser stays strict.
  const d = object(q.decision, "reportHash publicationStatus trainerAdmission selection model beliefs sourceHistoryReachStatus informationSet hero heroCards history table legalActions maximumLossBb securitySlackBb minimumReach");
  const inner = { ...d, availableQuestions: 1, activePlayers: 2, maximumAttempts: 100 } as unknown as IntervalMetadata;
  parseIntervalQuestion(q.decision, inner);
  return value as CollectionQuestion;
}
export function parseCollectionFeedback(value: unknown, question: CollectionQuestion, action: PreflopAction): CollectionFeedback {
  const f = object(value, "collectionHash questionIndex decision");
  ensure(f.collectionHash === question.collectionHash && f.questionIndex === question.index);
  parseIntervalFeedback(f.decision, question.decision, action);
  return value as CollectionFeedback;
}
export function checkCollectionInventory(questions: CollectionQuestion[], metadata: CollectionMetadata) {
  ensure(questions.length === metadata.availableQuestions);
  questions.forEach((q, index) => parseCollectionQuestion(q, metadata, index));
  ensure(new Set(questions.map((q) => q.decision.reportHash)).size === questions.length &&
    new Set(questions.map((q) => `${q.decision.hero}:${q.decision.informationSet}`)).size === questions.length);
}
export function parseCollectionReview(value: unknown, metadata: CollectionMetadata,
  questions: CollectionQuestion[], attempts: CollectionAttempt[]): CollectionReview {
  const r = object(value, "schemaVersion collectionHash selection questions attempts counts");
  checkCollectionInventory(questions, metadata);
  ensure(r.schemaVersion === collectionSchema && r.collectionHash === metadata.collectionHash &&
    r.selection === collectionSelection && intervalEqual(r.questions, questions) &&
    Array.isArray(r.attempts) && r.attempts.length === attempts.length && attempts.length <= metadata.maximumAttempts);
  const counts = { distinctQuestionsAttempted: new Set(attempts.map((a) => a.questionIndex)).size,
    withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index questionIndex action feedback");
    const captured = attempts[index];
    ensure(a.index === index && a.questionIndex === captured.questionIndex && a.action === captured.action);
    const feedback = parseCollectionFeedback(a.feedback, questions[captured.questionIndex], captured.action);
    ensure(intervalEqual(feedback, captured.feedback));
    counts[feedback.decision.selected.classification === "WITHIN_LIMIT" ? "withinLimit"
      : feedback.decision.selected.classification === "OUTSIDE_LIMIT" ? "outsideLimit" : "strategyDependent"]++;
  });
  ensure(intervalEqual(r.counts, counts));
  return value as CollectionReview;
}
export async function collectionRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/interval-collection${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new IntervalRequestError(response.status);
  try { return await response.json(); }
  catch { throw new IntervalProtocolError(); }
}
