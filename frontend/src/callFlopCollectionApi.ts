import frozen from "./test-data/call-flop-collection-review.json";
import { IntervalProtocolError, intervalEqual } from "./intervalTrainerApi";
import { parseQualifiedFlopCollectionMetadata, parseQualifiedFlopCollectionQuestion, parseQualifiedFlopCollectionFeedback,
  checkQualifiedFlopCollectionInventory, qualifiedDecision, qualifiedGrade, qualifiedFlopCollectionStatus, qualifiedFlopCollectionSelection,
  type QualifiedFlopCollectionMetadata, type QualifiedFlopCollectionQuestion, type QualifiedFlopCollectionFeedback, type QualifiedFlopCollectionAction,
  type WeakQuestion, type WeakFeedback } from "./qualifiedFlopCollectionApi";

export const callFlopCollectionStatus = "FOURTEEN_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
export const callFlopCollectionSelection = "ORDERED_LEGACY_ELEVEN_THEN_SHARED_CALL_QUESTIONS/v5";
export const callFlopCollectionSchema = "pokerlab-flop-interval-collection-review/v5";
const legacyKind = "LEGACY_ELEVEN_QUALIFIED_COLLECTION", sharedKind = "SHARED_REFERENCE_CALL_QUESTIONS";
export type CallFlopCollectionAction = QualifiedFlopCollectionAction;
export type CallFlopCollectionMetadata = Omit<QualifiedFlopCollectionMetadata, "questionKinds"> & {
  questionKinds: string[]; independentInputSets: number; addedReferenceSharing: string;
  startupBudget: { sourceReplays: number; freshReferenceRuns: number; freshIterations: number };
};
type Decision = { kind: typeof legacyKind; legacy: QualifiedFlopCollectionQuestion; shared: null }
  | { kind: typeof sharedKind; legacy: null; shared: { index: number; totalQuestions: number; decision: WeakQuestion } };
type Grade = { kind: typeof legacyKind; legacy: QualifiedFlopCollectionFeedback; shared: null }
  | { kind: typeof sharedKind; legacy: null; shared: WeakFeedback };
export type CallFlopCollectionQuestion = { collectionHash: string; index: number; sourceQuestionIndex: number; totalQuestions: number; selection: string; decision: Decision };
export type CallFlopCollectionFeedback = { collectionHash: string; questionIndex: number; sourceQuestionIndex: number; decision: Grade };
export type CallFlopCollectionAttempt = { index: number; questionIndex: number; action: CallFlopCollectionAction; feedback: CallFlopCollectionFeedback };
export type CallFlopCollectionReview = { schemaVersion: string; collectionHash: string; selection: string;
  questions: CallFlopCollectionQuestion[]; attempts: CallFlopCollectionAttempt[];
  counts: { distinctQuestionsAttempted: number; withinLimit: number; outsideLimit: number; strategyDependent: number } };
const canonical = frozen as CallFlopCollectionReview;
export class CallFlopCollectionRequestError extends Error {
  constructor(readonly status: number) { super(status === 404 ? "The fourteen-decision research collection is not enabled on this server."
    : status === 400 ? "The collection changed or this choice was rejected. Load the current collection."
      : `The collection request failed (${status}). Please try again.`); }
}
function ensure(condition: unknown): asserts condition { if (!condition) throw new IntervalProtocolError(); }
function object(value: unknown, keys: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  ensure(Object.keys(value).sort().join(" ") === keys.split(" ").sort().join(" "));
  return value as Record<string, unknown>;
}
export function callDecision(q: CallFlopCollectionQuestion) { return q.decision.legacy ? qualifiedDecision(q.decision.legacy) : q.decision.shared.decision; }
export function callGrade(f: CallFlopCollectionFeedback) { return f.decision.legacy ? qualifiedGrade(f.decision.legacy) : f.decision.shared; }
export function callActions(q: CallFlopCollectionQuestion): CallFlopCollectionAction[] { return callDecision(q).context.legalActions; }
export function callQuestionIdentity(q: CallFlopCollectionQuestion) { return `${q.collectionHash}:${q.index}:${callDecision(q).reportHash}`; }
function legacyMetadata(m: CallFlopCollectionMetadata): QualifiedFlopCollectionMetadata {
  return parseQualifiedFlopCollectionMetadata({ collectionHash: m.legacyCollectionHash,
    legacyCollectionHash: canonical.questions[0].decision.legacy!.decision.legacy!.collectionHash,
    publicationStatus: qualifiedFlopCollectionStatus, trainerAdmission: false, selection: qualifiedFlopCollectionSelection,
    model: m.model, chanceModel: m.chanceModel, evScope: m.evScope, reachUnits: m.reachUnits, beliefs: m.beliefs,
    sourceHistoryReachStatus: m.sourceHistoryReachStatus, availableQuestions: 11, activePlayers: m.activePlayers,
    maximumAttempts: m.maximumAttempts, questionKinds: [...Array(10).fill("LEGACY_TEN_QUESTION_COLLECTION"), "QUALIFIED_WEAK_HAND_FLOP"] });
}
export function parseCallFlopCollectionMetadata(value: unknown): CallFlopCollectionMetadata {
  const m = object(value, "collectionHash legacyCollectionHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus questionKinds availableQuestions activePlayers maximumAttempts independentInputSets addedReferenceSharing startupBudget");
  ensure(m.collectionHash === canonical.collectionHash && m.legacyCollectionHash === canonical.questions[0].decision.legacy!.collectionHash &&
    m.publicationStatus === callFlopCollectionStatus && m.trainerAdmission === false && m.selection === callFlopCollectionSelection &&
    m.availableQuestions === 14 && m.independentInputSets === 2 && m.addedReferenceSharing === "ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1" &&
    intervalEqual(m.questionKinds, [...Array(11).fill(legacyKind), ...Array(3).fill(sharedKind)]) &&
    intervalEqual(m.startupBudget, {sourceReplays: 6, freshReferenceRuns: 12, freshIterations: 972000}));
  legacyMetadata(value as CallFlopCollectionMetadata); return value as CallFlopCollectionMetadata;
}
export function parseCallFlopCollectionQuestion(value: unknown, metadata: CallFlopCollectionMetadata, index: number): CallFlopCollectionQuestion {
  const q = object(value, "collectionHash index totalQuestions selection sourceQuestionIndex decision");
  ensure(Number.isInteger(index) && index >= 0 && index < 14 && q.index === index && q.collectionHash === metadata.collectionHash &&
    q.totalQuestions === 14 && q.selection === callFlopCollectionSelection && q.sourceQuestionIndex === (index < 11 ? index : index - 11));
  const d = object(q.decision, "kind legacy shared"); ensure(d.kind === metadata.questionKinds[index]);
  if (index < 11) { ensure(d.shared === null); parseQualifiedFlopCollectionQuestion(d.legacy, legacyMetadata(metadata), index); }
  else ensure(d.legacy === null);
  // This version serves one pinned, physically qualified inventory. Equality binds every
  // nested index, hand, board, history and source to that inventory, including shared pins.
  ensure(intervalEqual(value, canonical.questions[index])); return value as CallFlopCollectionQuestion;
}
export function checkCallFlopCollectionInventory(questions: CallFlopCollectionQuestion[], metadata: CallFlopCollectionMetadata) {
  ensure(questions.length === 14); questions.forEach((q, i) => parseCallFlopCollectionQuestion(q, metadata, i));
  checkQualifiedFlopCollectionInventory(questions.slice(0, 11).map(q => q.decision.legacy!), legacyMetadata(metadata));
}
export function parseCallFlopCollectionFeedback(value: unknown, question: CallFlopCollectionQuestion, action: CallFlopCollectionAction): CallFlopCollectionFeedback {
  const f = object(value, "collectionHash questionIndex sourceQuestionIndex decision");
  ensure(callActions(question).includes(action) && f.collectionHash === question.collectionHash && f.questionIndex === question.index && f.sourceQuestionIndex === question.sourceQuestionIndex);
  const d = object(f.decision, "kind legacy shared"); ensure(d.kind === question.decision.kind);
  if (question.decision.legacy) {
    ensure(d.shared === null); parseQualifiedFlopCollectionFeedback(d.legacy, question.decision.legacy, action);
  } else ensure(d.legacy === null);
  const expected = canonical.attempts.find(a => a.questionIndex === question.index && a.action === action);
  ensure(expected && intervalEqual(value, expected.feedback)); return value as CallFlopCollectionFeedback;
}
export function callReviewCounts(attempts: CallFlopCollectionAttempt[]) {
  const counts = { distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size, withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  for (const a of attempts) {
    const classification = callGrade(a.feedback).selected.classification;
    if (classification === "WITHIN_LIMIT") counts.withinLimit++; else if (classification === "OUTSIDE_LIMIT") counts.outsideLimit++; else counts.strategyDependent++;
  }
  return counts;
}
export function parseCallFlopCollectionReview(value: unknown, metadata: CallFlopCollectionMetadata,
  questions: CallFlopCollectionQuestion[], attempts?: CallFlopCollectionAttempt[]): CallFlopCollectionReview {
  const r = object(value, "schemaVersion collectionHash selection questions attempts counts");
  ensure(r.schemaVersion === callFlopCollectionSchema && r.collectionHash === metadata.collectionHash && r.selection === callFlopCollectionSelection &&
    intervalEqual(r.questions, questions) && Array.isArray(r.attempts) && r.attempts.length <= 100);
  checkCallFlopCollectionInventory(questions, metadata);
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index questionIndex action feedback");
    ensure(a.index === index && typeof a.questionIndex === "number" && Number.isInteger(a.questionIndex) && a.questionIndex >= 0 && a.questionIndex < 14 && typeof a.action === "string");
    parseCallFlopCollectionFeedback(a.feedback, questions[a.questionIndex], a.action as CallFlopCollectionAction);
  });
  ensure(intervalEqual(r.counts, callReviewCounts(r.attempts as CallFlopCollectionAttempt[])) && (!attempts || intervalEqual(r.attempts, attempts)));
  return value as CallFlopCollectionReview;
}
export async function callFlopCollectionRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/flop-interval-collection-v5${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new CallFlopCollectionRequestError(response.status);
  try { return await response.json(); } catch { throw new IntervalProtocolError(); }
}
export function callReviewChoices(attempts: CallFlopCollectionAttempt[]) {
  return attempts.map(({ questionIndex, action, feedback }) => ({ questionIndex, reportHash: callGrade(feedback).reportHash, action }));
}
export function readCallFlopReview(text: string, metadata: CallFlopCollectionMetadata, questions: CallFlopCollectionQuestion[]) {
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
  return parseCallFlopCollectionReview(value, metadata, questions);
}
export function callSessionKey(pin: string) { return `pokerlab:flop-collection:v5:${pin}`; }
export function callSessionReview(metadata: CallFlopCollectionMetadata, questions: CallFlopCollectionQuestion[], attempts: CallFlopCollectionAttempt[]) {
  return parseCallFlopCollectionReview({schemaVersion: callFlopCollectionSchema, collectionHash: metadata.collectionHash,
    selection: metadata.selection, questions, attempts, counts: callReviewCounts(attempts)}, metadata, questions, attempts);
}
export async function callFlopReviewDocument(signal: AbortSignal, metadata: CallFlopCollectionMetadata,
  questions: CallFlopCollectionQuestion[], attempts: CallFlopCollectionAttempt[]): Promise<string> {
  const response = await fetch("/api/v1/trainer/research/flop-interval-collection-v5/review/export", {
    signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ collectionHash: metadata.collectionHash, choices: callReviewChoices(attempts) }),
  });
  if (!response.ok) throw new CallFlopCollectionRequestError(response.status);
  const text = await response.text(), review = readCallFlopReview(text, metadata, questions);
  ensure(intervalEqual(review.attempts, attempts)); return text;
}
