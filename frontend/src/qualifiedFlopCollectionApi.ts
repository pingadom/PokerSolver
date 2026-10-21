import weakSaved from "./test-data/weak-hand-flop-drill.json";
import { IntervalProtocolError, intervalEqual } from "./intervalTrainerApi";
import { parseExpandedFlopCollectionMetadata, parseExpandedFlopCollectionQuestion, parseExpandedFlopCollectionFeedback,
  checkExpandedFlopCollectionInventory, expandedDecision, expandedGrade, expandedFlopCollectionStatus, expandedFlopCollectionSelection,
  type ExpandedFlopCollectionMetadata, type ExpandedFlopCollectionQuestion, type ExpandedFlopCollectionFeedback, type ExpandedFlopCollectionAction } from "./expandedFlopCollectionApi";
import type { PairedFlopIntervalQuestion, PairedFlopIntervalFeedback } from "./pairedFlopIntervalApi";

export const qualifiedFlopCollectionStatus = "ELEVEN_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
export const qualifiedFlopCollectionSelection = "ORDERED_LEGACY_TEN_THEN_QUALIFIED_WEAK_HAND/v4";
export const qualifiedFlopCollectionSchema = "pokerlab-flop-interval-collection-review/v4";
export const qualifiedFlopBeliefs = "INDEPENDENT_SOURCE_SPECIFIC_SUPPLIED_JOINT_PRIORS/v1";
const legacyKind = "LEGACY_TEN_QUESTION_COLLECTION", weakKind = "QUALIFIED_WEAK_HAND_FLOP";
export type QualifiedFlopCollectionAction = ExpandedFlopCollectionAction;
export type WeakQuestion = Omit<PairedFlopIntervalQuestion, "questionIndex">;
export type WeakFeedback = Omit<PairedFlopIntervalFeedback, "questionIndex">;
export type QualifiedFlopCollectionMetadata = Omit<ExpandedFlopCollectionMetadata, "questionKinds"> & {
  legacyCollectionHash: string; questionKinds: string[];
};
type Decision = { kind: typeof legacyKind; legacy: ExpandedFlopCollectionQuestion; weak: null }
  | { kind: typeof weakKind; legacy: null; weak: WeakQuestion };
type Grade = { kind: typeof legacyKind; legacy: ExpandedFlopCollectionFeedback; weak: null }
  | { kind: typeof weakKind; legacy: null; weak: WeakFeedback };
export type QualifiedFlopCollectionQuestion = { collectionHash: string; index: number; sourceQuestionIndex: number; totalQuestions: number; selection: string; decision: Decision };
export type QualifiedFlopCollectionFeedback = { collectionHash: string; questionIndex: number; sourceQuestionIndex: number; decision: Grade };
export type QualifiedFlopCollectionAttempt = { index: number; questionIndex: number; action: QualifiedFlopCollectionAction; feedback: QualifiedFlopCollectionFeedback };
export type QualifiedFlopCollectionReview = { schemaVersion: string; collectionHash: string; selection: string;
  questions: QualifiedFlopCollectionQuestion[]; attempts: QualifiedFlopCollectionAttempt[];
  counts: { distinctQuestionsAttempted: number; withinLimit: number; outsideLimit: number; strategyDependent: number } };
export class QualifiedFlopCollectionRequestError extends Error {
  constructor(readonly status: number) { super(status === 404 ? "This research collection is not enabled on this server."
    : status === 400 ? "The collection changed or this choice was rejected. Load the current collection."
      : `The collection request failed (${status}). Please try again.`); }
}
function ensure(condition: unknown): asserts condition { if (!condition) throw new IntervalProtocolError(); }
function object(value: unknown, keys: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  ensure(Object.keys(value).sort().join(" ") === keys.split(" ").sort().join(" "));
  return value as Record<string, unknown>;
}
export function qualifiedDecision(q: QualifiedFlopCollectionQuestion) { return q.decision.legacy ? expandedDecision(q.decision.legacy) : q.decision.weak; }
export function qualifiedGrade(f: QualifiedFlopCollectionFeedback) { return f.decision.legacy ? expandedGrade(f.decision.legacy) : f.decision.weak; }
export function qualifiedActions(q: QualifiedFlopCollectionQuestion): QualifiedFlopCollectionAction[] { return qualifiedDecision(q).context.legalActions; }
function legacyMetadata(m: QualifiedFlopCollectionMetadata): ExpandedFlopCollectionMetadata {
  return parseExpandedFlopCollectionMetadata({ collectionHash: m.legacyCollectionHash, publicationStatus: expandedFlopCollectionStatus,
    trainerAdmission: false, selection: expandedFlopCollectionSelection, model: m.model, chanceModel: m.chanceModel,
    evScope: m.evScope, reachUnits: m.reachUnits, beliefs: "EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1",
    sourceHistoryReachStatus: m.sourceHistoryReachStatus, availableQuestions: 10, activePlayers: m.activePlayers, maximumAttempts: m.maximumAttempts,
    questionKinds: ["INITIAL_CHECK_BET", "BB_FACING_BET", ...Array(4).fill("BTN_AFTER_CHECK"), ...Array(4).fill("PAIRED_BTN_FACING_BET")] });
}
export function parseQualifiedFlopCollectionMetadata(value: unknown): QualifiedFlopCollectionMetadata {
  const m = object(value, "collectionHash legacyCollectionHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus questionKinds availableQuestions activePlayers maximumAttempts");
  ensure(typeof m.collectionHash === "string" && /^[0-9a-f]{64}$/.test(m.collectionHash) && m.publicationStatus === qualifiedFlopCollectionStatus &&
    m.trainerAdmission === false && m.selection === qualifiedFlopCollectionSelection && m.beliefs === qualifiedFlopBeliefs &&
    m.availableQuestions === 11 && intervalEqual(m.questionKinds, [...Array(10).fill(legacyKind), weakKind]));
  legacyMetadata(value as QualifiedFlopCollectionMetadata);
  return value as QualifiedFlopCollectionMetadata;
}
export function parseQualifiedFlopCollectionQuestion(value: unknown, metadata: QualifiedFlopCollectionMetadata, index: number): QualifiedFlopCollectionQuestion {
  const q = object(value, "collectionHash index totalQuestions selection sourceQuestionIndex decision");
  ensure(Number.isInteger(index) && index >= 0 && index < 11 && q.index === index && q.collectionHash === metadata.collectionHash &&
    q.totalQuestions === 11 && q.selection === qualifiedFlopCollectionSelection && q.sourceQuestionIndex === (index < 10 ? index : 0));
  const d = object(q.decision, "kind legacy weak");
  ensure(d.kind === metadata.questionKinds[index]);
  if (index < 10) { ensure(d.weak === null); parseExpandedFlopCollectionQuestion(d.legacy, legacyMetadata(metadata), index); }
  else { ensure(d.legacy === null && intervalEqual(d.weak, weakSaved.question)); }
  return value as QualifiedFlopCollectionQuestion;
}
export function checkQualifiedFlopCollectionInventory(questions: QualifiedFlopCollectionQuestion[], metadata: QualifiedFlopCollectionMetadata) {
  ensure(questions.length === 11); questions.forEach((q, i) => parseQualifiedFlopCollectionQuestion(q, metadata, i));
  checkExpandedFlopCollectionInventory(questions.slice(0, 10).map(q => q.decision.legacy!), legacyMetadata(metadata));
}
export function parseQualifiedFlopCollectionFeedback(value: unknown, question: QualifiedFlopCollectionQuestion, action: QualifiedFlopCollectionAction): QualifiedFlopCollectionFeedback {
  const f = object(value, "collectionHash questionIndex sourceQuestionIndex decision");
  ensure(f.collectionHash === question.collectionHash && f.questionIndex === question.index && f.sourceQuestionIndex === question.sourceQuestionIndex);
  const d = object(f.decision, "kind legacy weak"); ensure(d.kind === question.decision.kind);
  if (question.decision.legacy) {
    ensure(d.weak === null); parseExpandedFlopCollectionFeedback(d.legacy, question.decision.legacy, action);
  } else {
    const canonical = weakSaved.attempts.find(a => a.action === action);
    ensure(d.legacy === null && canonical && intervalEqual(d.weak, canonical.feedback));
  }
  return value as QualifiedFlopCollectionFeedback;
}
export function parseQualifiedFlopCollectionReview(value: unknown, metadata: QualifiedFlopCollectionMetadata,
  questions: QualifiedFlopCollectionQuestion[], attempts: QualifiedFlopCollectionAttempt[]): QualifiedFlopCollectionReview {
  const r = object(value, "schemaVersion collectionHash selection questions attempts counts");
  ensure(r.schemaVersion === qualifiedFlopCollectionSchema && r.collectionHash === metadata.collectionHash && r.selection === qualifiedFlopCollectionSelection &&
    intervalEqual(r.questions, questions) && Array.isArray(r.attempts) && r.attempts.length === attempts.length && attempts.length <= 100);
  checkQualifiedFlopCollectionInventory(questions, metadata);
  const counts = { distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size, withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index questionIndex action feedback"), expected = attempts[index];
    ensure(a.index === index && a.questionIndex === expected.questionIndex && a.action === expected.action);
    const f = parseQualifiedFlopCollectionFeedback(a.feedback, questions[expected.questionIndex], expected.action);
    ensure(intervalEqual(f, expected.feedback));
    const classification = qualifiedGrade(f).selected.classification;
    if (classification === "WITHIN_LIMIT") counts.withinLimit++; else if (classification === "OUTSIDE_LIMIT") counts.outsideLimit++; else counts.strategyDependent++;
  });
  ensure(intervalEqual(r.counts, counts)); return value as QualifiedFlopCollectionReview;
}
export async function qualifiedFlopCollectionRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/flop-interval-collection-v4${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new QualifiedFlopCollectionRequestError(response.status);
  try { return await response.json(); } catch { throw new IntervalProtocolError(); }
}
export function qualifiedReviewChoices(attempts: QualifiedFlopCollectionAttempt[]) {
  return attempts.map(({ questionIndex, action, feedback }) => ({ questionIndex, reportHash: qualifiedGrade(feedback).reportHash, action }));
}
function replayableJson(text: string): unknown {
  // JSON.parse collapses duplicate keys. Offline replay rejects them, including escaped aliases.
  let value: unknown;
  try { value = JSON.parse(text); } catch { throw new IntervalProtocolError(); }
  const frames: (Set<string> | null)[] = [];
  const tokens = /"(?:\\[\s\S]|[^"\\])*"|[{}[\]]/g;
  for (const token of text.matchAll(tokens)) {
    const raw = token[0];
    if (raw === "{") frames.push(new Set());
    else if (raw === "[") frames.push(null);
    else if (raw === "}" || raw === "]") frames.pop();
    else {
      let next = token.index + raw.length;
      while (/\s/.test(text[next] ?? "")) next++;
      if (text[next] === ":") {
        const keys = frames.at(-1), key = JSON.parse(raw) as string;
        ensure(keys && !keys.has(key)); keys.add(key);
      }
    }
  }
  return value;
}
export async function qualifiedFlopReviewDocument(signal: AbortSignal, metadata: QualifiedFlopCollectionMetadata,
  questions: QualifiedFlopCollectionQuestion[], attempts: QualifiedFlopCollectionAttempt[]): Promise<string> {
  const response = await fetch("/api/v1/trainer/research/flop-interval-collection-v4/review/export", {
    signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ collectionHash: metadata.collectionHash, choices: qualifiedReviewChoices(attempts) }),
  });
  if (!response.ok) throw new QualifiedFlopCollectionRequestError(response.status);
  const text = await response.text(); ensure(new TextEncoder().encode(text).length <= 1024 * 1024);
  const value = replayableJson(text);
  parseQualifiedFlopCollectionReview(value, metadata, questions, attempts); return text;
}
