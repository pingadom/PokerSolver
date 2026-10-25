import frozen from "./test-data/unpaired-flop-collection-review.json";
import { IntervalProtocolError, intervalEqual } from "./intervalTrainerApi";
import { parseCallFlopCollectionMetadata, parseCallFlopCollectionQuestion, parseCallFlopCollectionFeedback,
  checkCallFlopCollectionInventory, callDecision, callGrade, callFlopCollectionStatus, callFlopCollectionSelection,
  type CallFlopCollectionMetadata, type CallFlopCollectionQuestion, type CallFlopCollectionFeedback, type CallFlopCollectionAction } from "./callFlopCollectionApi";
import type { WeakQuestion, WeakFeedback } from "./qualifiedFlopCollectionApi";

export const unpairedFlopCollectionStatus = "EIGHTEEN_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
export const unpairedFlopCollectionSelection = "ORDERED_LEGACY_FOURTEEN_THEN_UNPAIRED_FLOP_QUESTIONS/v6";
export const unpairedFlopCollectionSchema = "pokerlab-flop-interval-collection-review/v6";
const legacyKind = "LEGACY_FOURTEEN_QUALIFIED_COLLECTION", sharedKind = "SHARED_REFERENCE_UNPAIRED_QUESTIONS";
export type UnpairedFlopCollectionAction = CallFlopCollectionAction;
export type UnpairedFlopCollectionMetadata = Omit<CallFlopCollectionMetadata, "questionKinds"> & {
  questionKinds: string[]; independentInputSets: number; addedReferenceSharing: string;
  startupBudget: { sourceReplays: number; freshReferenceRuns: number; freshIterations: number };
};
type Decision = { kind: typeof legacyKind; legacy: CallFlopCollectionQuestion; shared: null }
  | { kind: typeof sharedKind; legacy: null; shared: { index: number; totalQuestions: number; decision: WeakQuestion } };
type Grade = { kind: typeof legacyKind; legacy: CallFlopCollectionFeedback; shared: null }
  | { kind: typeof sharedKind; legacy: null; shared: WeakFeedback };
export type UnpairedFlopCollectionQuestion = { collectionHash: string; index: number; sourceQuestionIndex: number; totalQuestions: number; selection: string; decision: Decision };
export type UnpairedFlopCollectionFeedback = { collectionHash: string; questionIndex: number; sourceQuestionIndex: number; decision: Grade };
export type UnpairedFlopCollectionAttempt = { index: number; questionIndex: number; action: UnpairedFlopCollectionAction; feedback: UnpairedFlopCollectionFeedback };
export type UnpairedFlopCollectionReview = { schemaVersion: string; collectionHash: string; selection: string;
  questions: UnpairedFlopCollectionQuestion[]; attempts: UnpairedFlopCollectionAttempt[];
  counts: { distinctQuestionsAttempted: number; withinLimit: number; outsideLimit: number; strategyDependent: number } };
const canonical = frozen as UnpairedFlopCollectionReview;
export class UnpairedFlopCollectionRequestError extends Error {
  constructor(readonly status: number) { super(status === 404 ? "The eighteen-decision research collection is not enabled on this server."
    : status === 400 ? "The collection changed or this choice was rejected. Load the current collection."
      : `The collection request failed (${status}). Please try again.`); }
}
function ensure(condition: unknown): asserts condition { if (!condition) throw new IntervalProtocolError(); }
function object(value: unknown, keys: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  ensure(Object.keys(value).sort().join(" ") === keys.split(" ").sort().join(" "));
  return value as Record<string, unknown>;
}
export function unpairedDecision(q: UnpairedFlopCollectionQuestion) { return q.decision.legacy ? callDecision(q.decision.legacy) : q.decision.shared.decision; }
export function unpairedGrade(f: UnpairedFlopCollectionFeedback) { return f.decision.legacy ? callGrade(f.decision.legacy) : f.decision.shared; }
export function unpairedActions(q: UnpairedFlopCollectionQuestion): UnpairedFlopCollectionAction[] { return unpairedDecision(q).context.legalActions; }
export function unpairedQuestionIdentity(q: UnpairedFlopCollectionQuestion) { return `${q.collectionHash}:${q.index}:${unpairedDecision(q).reportHash}`; }
function legacyMetadata(m: UnpairedFlopCollectionMetadata): CallFlopCollectionMetadata {
  return parseCallFlopCollectionMetadata({ collectionHash: m.legacyCollectionHash,
    legacyCollectionHash: canonical.questions[0].decision.legacy!.decision.legacy!.collectionHash,
    publicationStatus: callFlopCollectionStatus, trainerAdmission: false, selection: callFlopCollectionSelection,
    model: m.model, chanceModel: m.chanceModel, evScope: m.evScope, reachUnits: m.reachUnits, beliefs: m.beliefs,
    sourceHistoryReachStatus: m.sourceHistoryReachStatus, availableQuestions: 14, activePlayers: m.activePlayers,
    maximumAttempts: m.maximumAttempts, questionKinds: [...Array(11).fill("LEGACY_ELEVEN_QUALIFIED_COLLECTION"), ...Array(3).fill("SHARED_REFERENCE_CALL_QUESTIONS")],
    independentInputSets: 2, addedReferenceSharing: "ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1",
    startupBudget: {sourceReplays: 6, freshReferenceRuns: 12, freshIterations: 972000} });
}
export function parseUnpairedFlopCollectionMetadata(value: unknown): UnpairedFlopCollectionMetadata {
  const m = object(value, "collectionHash legacyCollectionHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus questionKinds availableQuestions activePlayers maximumAttempts independentInputSets addedReferenceSharing startupBudget");
  ensure(m.collectionHash === canonical.collectionHash && m.legacyCollectionHash === canonical.questions[0].decision.legacy!.collectionHash &&
    m.publicationStatus === unpairedFlopCollectionStatus && m.trainerAdmission === false && m.selection === unpairedFlopCollectionSelection &&
    m.availableQuestions === 18 && m.independentInputSets === 2 && m.addedReferenceSharing === "ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1" &&
    intervalEqual(m.questionKinds, [...Array(14).fill(legacyKind), ...Array(4).fill(sharedKind)]) &&
    intervalEqual(m.startupBudget, {sourceReplays: 7, freshReferenceRuns: 14, freshIterations: 1212000}));
  legacyMetadata(value as UnpairedFlopCollectionMetadata); return value as UnpairedFlopCollectionMetadata;
}
export function parseUnpairedFlopCollectionQuestion(value: unknown, metadata: UnpairedFlopCollectionMetadata, index: number): UnpairedFlopCollectionQuestion {
  const q = object(value, "collectionHash index totalQuestions selection sourceQuestionIndex decision");
  ensure(Number.isInteger(index) && index >= 0 && index < 18 && q.index === index && q.collectionHash === metadata.collectionHash &&
    q.totalQuestions === 18 && q.selection === unpairedFlopCollectionSelection && q.sourceQuestionIndex === (index < 14 ? index : index - 14));
  const d = object(q.decision, "kind legacy shared"); ensure(d.kind === metadata.questionKinds[index]);
  if (index < 14) { ensure(d.shared === null); parseCallFlopCollectionQuestion(d.legacy, legacyMetadata(metadata), index); }
  else ensure(d.legacy === null);
  // This version serves one pinned, physically qualified inventory. Equality binds every
  // nested index, hand, board, history and source to that inventory, including shared pins.
  ensure(intervalEqual(value, canonical.questions[index])); return value as UnpairedFlopCollectionQuestion;
}
export function checkUnpairedFlopCollectionInventory(questions: UnpairedFlopCollectionQuestion[], metadata: UnpairedFlopCollectionMetadata) {
  ensure(questions.length === 18); questions.forEach((q, i) => parseUnpairedFlopCollectionQuestion(q, metadata, i));
  checkCallFlopCollectionInventory(questions.slice(0, 14).map(q => q.decision.legacy!), legacyMetadata(metadata));
}
export function parseUnpairedFlopCollectionFeedback(value: unknown, question: UnpairedFlopCollectionQuestion, action: UnpairedFlopCollectionAction): UnpairedFlopCollectionFeedback {
  const f = object(value, "collectionHash questionIndex sourceQuestionIndex decision");
  ensure(unpairedActions(question).includes(action) && f.collectionHash === question.collectionHash && f.questionIndex === question.index && f.sourceQuestionIndex === question.sourceQuestionIndex);
  const d = object(f.decision, "kind legacy shared"); ensure(d.kind === question.decision.kind);
  if (question.decision.legacy) {
    ensure(d.shared === null); parseCallFlopCollectionFeedback(d.legacy, question.decision.legacy, action);
  } else ensure(d.legacy === null);
  const expected = canonical.attempts.find(a => a.questionIndex === question.index && a.action === action);
  ensure(expected && intervalEqual(value, expected.feedback)); return value as UnpairedFlopCollectionFeedback;
}
export function unpairedReviewCounts(attempts: UnpairedFlopCollectionAttempt[]) {
  const counts = { distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size, withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  for (const a of attempts) {
    const classification = unpairedGrade(a.feedback).selected.classification;
    if (classification === "WITHIN_LIMIT") counts.withinLimit++; else if (classification === "OUTSIDE_LIMIT") counts.outsideLimit++; else counts.strategyDependent++;
  }
  return counts;
}
export function parseUnpairedFlopCollectionReview(value: unknown, metadata: UnpairedFlopCollectionMetadata,
  questions: UnpairedFlopCollectionQuestion[], attempts?: UnpairedFlopCollectionAttempt[]): UnpairedFlopCollectionReview {
  const r = object(value, "schemaVersion collectionHash selection questions attempts counts");
  ensure(r.schemaVersion === unpairedFlopCollectionSchema && r.collectionHash === metadata.collectionHash && r.selection === unpairedFlopCollectionSelection &&
    intervalEqual(r.questions, questions) && Array.isArray(r.attempts) && r.attempts.length <= 100);
  checkUnpairedFlopCollectionInventory(questions, metadata);
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index questionIndex action feedback");
    ensure(a.index === index && typeof a.questionIndex === "number" && Number.isInteger(a.questionIndex) && a.questionIndex >= 0 && a.questionIndex < 18 && typeof a.action === "string");
    parseUnpairedFlopCollectionFeedback(a.feedback, questions[a.questionIndex], a.action as UnpairedFlopCollectionAction);
  });
  ensure(intervalEqual(r.counts, unpairedReviewCounts(r.attempts as UnpairedFlopCollectionAttempt[])) && (!attempts || intervalEqual(r.attempts, attempts)));
  return value as UnpairedFlopCollectionReview;
}
export async function unpairedFlopCollectionRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/flop-interval-collection-v6${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new UnpairedFlopCollectionRequestError(response.status);
  try { return await response.json(); } catch { throw new IntervalProtocolError(); }
}
export function unpairedReviewChoices(attempts: UnpairedFlopCollectionAttempt[]) {
  return attempts.map(({ questionIndex, action, feedback }) => ({ questionIndex, reportHash: unpairedGrade(feedback).reportHash, action }));
}
export function readUnpairedFlopReview(text: string, metadata: UnpairedFlopCollectionMetadata, questions: UnpairedFlopCollectionQuestion[]) {
  ensure(new TextEncoder().encode(text).length <= 1024 * 1024);
  let value: unknown;
  try { value = JSON.parse(text); } catch { throw new IntervalProtocolError(); }
  // Reject duplicate keys (including escaped aliases) which JSON.parse would collapse.
  const frames: (Set<string> | null)[] = [];
  for (const token of text.matchAll(/"(?:\\[\s\S]|[^"\\])*"|[{}[\]]/g)) {
    const raw = token[0];
    if (raw === "{") frames.push(new Set()); else if (raw === "[") frames.push(null);
    else if (raw === "}" || raw === "]") frames.pop(); else {
      let next = token.index + raw.length; while (/\s/.test(text[next] ?? "")) next++;
      if (text[next] === ":") { const keys = frames.at(-1), key = JSON.parse(raw) as string; ensure(keys && !keys.has(key)); keys.add(key); }
    }
  }
  return parseUnpairedFlopCollectionReview(value, metadata, questions);
}
export function unpairedSessionKey(pin: string) { return `pokerlab:flop-collection:v6:${pin}`; }
export function unpairedSessionReview(metadata: UnpairedFlopCollectionMetadata, questions: UnpairedFlopCollectionQuestion[], attempts: UnpairedFlopCollectionAttempt[]) {
  return parseUnpairedFlopCollectionReview({schemaVersion: unpairedFlopCollectionSchema, collectionHash: metadata.collectionHash,
    selection: metadata.selection, questions, attempts, counts: unpairedReviewCounts(attempts)}, metadata, questions, attempts);
}
export async function unpairedFlopReviewDocument(signal: AbortSignal, metadata: UnpairedFlopCollectionMetadata,
  questions: UnpairedFlopCollectionQuestion[], attempts: UnpairedFlopCollectionAttempt[]): Promise<string> {
  const response = await fetch("/api/v1/trainer/research/flop-interval-collection-v6/review/export", {
    signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ collectionHash: metadata.collectionHash, choices: unpairedReviewChoices(attempts) }),
  });
  if (!response.ok) throw new UnpairedFlopCollectionRequestError(response.status);
  const text = await response.text(), review = readUnpairedFlopReview(text, metadata, questions);
  ensure(intervalEqual(review.attempts, attempts)); return text;
}
