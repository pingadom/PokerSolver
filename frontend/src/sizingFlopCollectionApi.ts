import frozen from "./test-data/sizing-flop-collection-review.json";
import { IntervalProtocolError, intervalEqual } from "./intervalTrainerApi";
import { parseUnpairedFlopCollectionMetadata, parseUnpairedFlopCollectionQuestion, parseUnpairedFlopCollectionFeedback,
  checkUnpairedFlopCollectionInventory, unpairedDecision, unpairedGrade, unpairedFlopCollectionStatus, unpairedFlopCollectionSelection,
  type UnpairedFlopCollectionMetadata, type UnpairedFlopCollectionQuestion, type UnpairedFlopCollectionFeedback, type UnpairedFlopCollectionAction } from "./unpairedFlopCollectionApi";
import type { WeakQuestion, WeakFeedback } from "./qualifiedFlopCollectionApi";

export const sizingFlopCollectionStatus = "TWENTY_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
export const sizingFlopCollectionSelection = "ORDERED_LEGACY_EIGHTEEN_THEN_POT_SIZE_BB_QUESTIONS/v7";
export const sizingFlopCollectionSchema = "pokerlab-flop-interval-collection-review/v7";
const legacyKind = "LEGACY_EIGHTEEN_QUALIFIED_COLLECTION", potKind = "POT_SIZE_BIG_BLIND_QUESTIONS";
export type SizingFlopCollectionAction = UnpairedFlopCollectionAction;
export type SizingFlopCollectionMetadata = UnpairedFlopCollectionMetadata;
export type PotSizeQuestion = { reportHash: string; index: number; totalQuestions: number; sourceIndex: number; heroCards: string; table: WeakQuestion["context"] };
export type PotSizeFeedback = { question: PotSizeQuestion; action: SizingFlopCollectionAction; selected: WeakFeedback["selected"]; alternatives: WeakFeedback["alternatives"] };
type Decision = { kind: typeof legacyKind; legacy: UnpairedFlopCollectionQuestion; pot: null }
  | { kind: typeof potKind; legacy: null; pot: PotSizeQuestion };
type Grade = { kind: typeof legacyKind; legacy: UnpairedFlopCollectionFeedback; pot: null }
  | { kind: typeof potKind; legacy: null; pot: PotSizeFeedback };
export type SizingFlopCollectionQuestion = { collectionHash: string; index: number; sourceQuestionIndex: number; totalQuestions: number; selection: string; decision: Decision };
export type SizingFlopCollectionFeedback = { collectionHash: string; questionIndex: number; sourceQuestionIndex: number; decision: Grade };
export type SizingFlopCollectionAttempt = { index: number; questionIndex: number; action: SizingFlopCollectionAction; feedback: SizingFlopCollectionFeedback };
export type SizingFlopCollectionReview = { schemaVersion: string; collectionHash: string; selection: string;
  questions: SizingFlopCollectionQuestion[]; attempts: SizingFlopCollectionAttempt[];
  counts: { distinctQuestionsAttempted: number; withinLimit: number; outsideLimit: number; strategyDependent: number } };
const canonical = frozen as SizingFlopCollectionReview;
export class SizingFlopCollectionRequestError extends Error {
  constructor(readonly status: number) { super(status === 404 ? "The twenty-decision research collection is not enabled on this server."
    : status === 400 ? "The collection changed or this choice was rejected. Load the current collection."
      : `The collection request failed (${status}). Please try again.`); }
}
function ensure(condition: unknown): asserts condition { if (!condition) throw new IntervalProtocolError(); }
function object(value: unknown, keys: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  ensure(Object.keys(value).sort().join(" ") === keys.split(" ").sort().join(" "));
  return value as Record<string, unknown>;
}
export function sizingDecision(q: SizingFlopCollectionQuestion) { return q.decision.legacy ? unpairedDecision(q.decision.legacy) : { reportHash: q.decision.pot.reportHash, heroCards: q.decision.pot.heroCards, context: q.decision.pot.table }; }
export function sizingGrade(f: SizingFlopCollectionFeedback) { return f.decision.legacy ? unpairedGrade(f.decision.legacy) : { ...f.decision.pot, reportHash: f.decision.pot.question.reportHash }; }
export function sizingActions(q: SizingFlopCollectionQuestion): SizingFlopCollectionAction[] { return sizingDecision(q).context.legalActions; }
export function sizingQuestionIdentity(q: SizingFlopCollectionQuestion) { return `${q.collectionHash}:${q.index}:${sizingDecision(q).reportHash}`; }
function legacyMetadata(m: SizingFlopCollectionMetadata): UnpairedFlopCollectionMetadata {
  return parseUnpairedFlopCollectionMetadata({ ...m, collectionHash: canonical.questions[0].decision.legacy!.collectionHash,
    legacyCollectionHash: canonical.questions[0].decision.legacy!.decision.legacy!.collectionHash,
    publicationStatus: unpairedFlopCollectionStatus, selection: unpairedFlopCollectionSelection,
    availableQuestions: 18, questionKinds: [...Array(14).fill("LEGACY_FOURTEEN_QUALIFIED_COLLECTION"), ...Array(4).fill("SHARED_REFERENCE_UNPAIRED_QUESTIONS")],
    addedReferenceSharing: "ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1",
    startupBudget: {sourceReplays: 7, freshReferenceRuns: 14, freshIterations: 1212000} });
}
export function parseSizingFlopCollectionMetadata(value: unknown): SizingFlopCollectionMetadata {
  const m = object(value, "collectionHash legacyCollectionHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus questionKinds availableQuestions activePlayers maximumAttempts independentInputSets addedReferenceSharing startupBudget");
  ensure(m.collectionHash === canonical.collectionHash && m.legacyCollectionHash === canonical.questions[0].decision.legacy!.collectionHash &&
    m.publicationStatus === sizingFlopCollectionStatus && m.trainerAdmission === false && m.selection === sizingFlopCollectionSelection &&
    m.availableQuestions === 20 && m.independentInputSets === 2 && m.addedReferenceSharing === "SEPARATE_BET_SIZE_GAME_TWO_FRESH_POLICIES_BOTH_BB_QUESTIONS_CHECKED/v1" &&
    intervalEqual(m.questionKinds, [...Array(18).fill(legacyKind), ...Array(2).fill(potKind)]) &&
    intervalEqual(m.startupBudget, {sourceReplays: 8, freshReferenceRuns: 16, freshIterations: 1452000}));
  legacyMetadata(value as SizingFlopCollectionMetadata); return value as SizingFlopCollectionMetadata;
}
export function parseSizingFlopCollectionQuestion(value: unknown, metadata: SizingFlopCollectionMetadata, index: number): SizingFlopCollectionQuestion {
  const q = object(value, "collectionHash index totalQuestions selection sourceQuestionIndex decision");
  ensure(Number.isInteger(index) && index >= 0 && index < 20 && q.index === index && q.collectionHash === metadata.collectionHash &&
    q.totalQuestions === 20 && q.selection === sizingFlopCollectionSelection && q.sourceQuestionIndex === (index < 18 ? index : index - 18));
  const d = object(q.decision, "kind legacy pot"); ensure(d.kind === metadata.questionKinds[index]);
  if (index < 18) { ensure(d.pot === null); parseUnpairedFlopCollectionQuestion(d.legacy, legacyMetadata(metadata), index); }
  else ensure(d.legacy === null);
  // This version serves one pinned, physically qualified inventory. Equality binds every
  // nested index, hand, board, history and source to that inventory, including shared pins.
  ensure(intervalEqual(value, canonical.questions[index])); return value as SizingFlopCollectionQuestion;
}
export function checkSizingFlopCollectionInventory(questions: SizingFlopCollectionQuestion[], metadata: SizingFlopCollectionMetadata) {
  ensure(questions.length === 20); questions.forEach((q, i) => parseSizingFlopCollectionQuestion(q, metadata, i));
  checkUnpairedFlopCollectionInventory(questions.slice(0, 18).map(q => q.decision.legacy!), legacyMetadata(metadata));
}
export function parseSizingFlopCollectionFeedback(value: unknown, question: SizingFlopCollectionQuestion, action: SizingFlopCollectionAction): SizingFlopCollectionFeedback {
  const f = object(value, "collectionHash questionIndex sourceQuestionIndex decision");
  ensure(sizingActions(question).includes(action) && f.collectionHash === question.collectionHash && f.questionIndex === question.index && f.sourceQuestionIndex === question.sourceQuestionIndex);
  const d = object(f.decision, "kind legacy pot"); ensure(d.kind === question.decision.kind);
  if (question.decision.legacy) {
    ensure(d.pot === null); parseUnpairedFlopCollectionFeedback(d.legacy, question.decision.legacy, action);
  } else ensure(d.legacy === null);
  const expected = canonical.attempts.find(a => a.questionIndex === question.index && a.action === action);
  ensure(expected && intervalEqual(value, expected.feedback)); return value as SizingFlopCollectionFeedback;
}
export function sizingReviewCounts(attempts: SizingFlopCollectionAttempt[]) {
  const counts = { distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size, withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  for (const a of attempts) {
    const classification = sizingGrade(a.feedback).selected.classification;
    if (classification === "WITHIN_LIMIT") counts.withinLimit++; else if (classification === "OUTSIDE_LIMIT") counts.outsideLimit++; else counts.strategyDependent++;
  }
  return counts;
}
export function parseSizingFlopCollectionReview(value: unknown, metadata: SizingFlopCollectionMetadata,
  questions: SizingFlopCollectionQuestion[], attempts?: SizingFlopCollectionAttempt[]): SizingFlopCollectionReview {
  const r = object(value, "schemaVersion collectionHash selection questions attempts counts");
  ensure(r.schemaVersion === sizingFlopCollectionSchema && r.collectionHash === metadata.collectionHash && r.selection === sizingFlopCollectionSelection &&
    intervalEqual(r.questions, questions) && Array.isArray(r.attempts) && r.attempts.length <= 100);
  checkSizingFlopCollectionInventory(questions, metadata);
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index questionIndex action feedback");
    ensure(a.index === index && typeof a.questionIndex === "number" && Number.isInteger(a.questionIndex) && a.questionIndex >= 0 && a.questionIndex < 20 && typeof a.action === "string");
    parseSizingFlopCollectionFeedback(a.feedback, questions[a.questionIndex], a.action as SizingFlopCollectionAction);
  });
  ensure(intervalEqual(r.counts, sizingReviewCounts(r.attempts as SizingFlopCollectionAttempt[])) && (!attempts || intervalEqual(r.attempts, attempts)));
  return value as SizingFlopCollectionReview;
}
export async function sizingFlopCollectionRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/flop-interval-collection-v7${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new SizingFlopCollectionRequestError(response.status);
  try { return await response.json(); } catch { throw new IntervalProtocolError(); }
}
export function sizingReviewChoices(attempts: SizingFlopCollectionAttempt[]) {
  return attempts.map(({ questionIndex, action, feedback }) => ({ questionIndex, reportHash: sizingGrade(feedback).reportHash, action }));
}
export function readSizingFlopReview(text: string, metadata: SizingFlopCollectionMetadata, questions: SizingFlopCollectionQuestion[]) {
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
  return parseSizingFlopCollectionReview(value, metadata, questions);
}
export function sizingSessionKey(pin: string) { return `pokerlab:flop-collection:v7:${pin}`; }
export function sizingSessionReview(metadata: SizingFlopCollectionMetadata, questions: SizingFlopCollectionQuestion[], attempts: SizingFlopCollectionAttempt[]) {
  return parseSizingFlopCollectionReview({schemaVersion: sizingFlopCollectionSchema, collectionHash: metadata.collectionHash,
    selection: metadata.selection, questions, attempts, counts: sizingReviewCounts(attempts)}, metadata, questions, attempts);
}
export async function sizingFlopReviewDocument(signal: AbortSignal, metadata: SizingFlopCollectionMetadata,
  questions: SizingFlopCollectionQuestion[], attempts: SizingFlopCollectionAttempt[]): Promise<string> {
  const response = await fetch("/api/v1/trainer/research/flop-interval-collection-v7/review/export", {
    signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ collectionHash: metadata.collectionHash, choices: sizingReviewChoices(attempts) }),
  });
  if (!response.ok) throw new SizingFlopCollectionRequestError(response.status);
  const text = await response.text(), review = readSizingFlopReview(text, metadata, questions);
  ensure(intervalEqual(review.attempts, attempts)); return text;
}
