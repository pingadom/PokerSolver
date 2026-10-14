import { IntervalProtocolError, intervalEqual } from "./intervalTrainerApi";
import {
  parseFlopIntervalMetadata, parseFlopIntervalQuestion, parseFlopIntervalFeedback,
  type FlopAction, type FlopIntervalQuestion, type FlopIntervalFeedback,
} from "./flopIntervalApi";
import {
  parseLaterFlopIntervalMetadata, parseLaterFlopIntervalQuestion, parseLaterFlopIntervalFeedback,
  type LaterFlopAction, type LaterFlopIntervalQuestion, type LaterFlopIntervalFeedback,
} from "./laterFlopIntervalApi";

export const flopCollectionStatus = "TWO_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
export const flopCollectionSelection = "ORDERED_INITIAL_AND_LATER_BB_FLOP_QUESTIONS/v1";
export const flopCollectionSchema = "pokerlab-flop-interval-collection-review/v1";
export type FlopCollectionAction = FlopAction | LaterFlopAction;
export type FlopCollectionMetadata = {
  collectionHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  model: string; chanceModel: string; evScope: string; reachUnits: string; beliefs: string; sourceHistoryReachStatus: string;
  questionKinds: ["INITIAL_CHECK_BET", "BB_FACING_BET"]; availableQuestions: number; activePlayers: number; maximumAttempts: number;
};
type Decision = { kind: "INITIAL_CHECK_BET"; initial: FlopIntervalQuestion; later: null }
  | { kind: "BB_FACING_BET"; initial: null; later: LaterFlopIntervalQuestion };
type Grade = { kind: "INITIAL_CHECK_BET"; initial: FlopIntervalFeedback; later: null }
  | { kind: "BB_FACING_BET"; initial: null; later: LaterFlopIntervalFeedback };
export type FlopCollectionQuestion = { collectionHash: string; index: number; totalQuestions: number; selection: string; decision: Decision };
export type FlopCollectionFeedback = { collectionHash: string; questionIndex: number; decision: Grade };
export type FlopCollectionAttempt = { index: number; questionIndex: number; action: FlopCollectionAction; feedback: FlopCollectionFeedback };
export type FlopCollectionReview = {
  schemaVersion: string; collectionHash: string; selection: string; questions: FlopCollectionQuestion[]; attempts: FlopCollectionAttempt[];
  counts: { distinctQuestionsAttempted: number; withinLimit: number; outsideLimit: number; strategyDependent: number };
};
export class FlopCollectionRequestError extends Error {
  constructor(readonly status: number) { super(status === 404 ? "The flop research collection is not enabled on this server."
    : status === 400 ? "The server rejected this collection or action. Load the current collection to continue."
      : `The flop collection request failed (${status}). Please try again.`); }
}
function ensure(condition: unknown): asserts condition { if (!condition) throw new IntervalProtocolError(); }
function object(value: unknown, keys?: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  const record = value as Record<string, unknown>;
  if (keys) ensure(Object.keys(record).sort().join(" ") === keys.split(" ").sort().join(" "));
  return record;
}
function checked<T>(parse: () => T): T { try { return parse(); } catch { throw new IntervalProtocolError(); } }
export function collectionDecision(q: FlopCollectionQuestion) { return q.decision.initial ?? q.decision.later; }
export function collectionGrade(f: FlopCollectionFeedback) { return f.decision.initial ?? f.decision.later; }
export function collectionActions(q: FlopCollectionQuestion): FlopCollectionAction[] { return collectionDecision(q).context.legalActions; }
export function parseFlopCollectionMetadata(value: unknown): FlopCollectionMetadata {
  const m = object(value, "collectionHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus questionKinds availableQuestions activePlayers maximumAttempts");
  ensure(typeof m.collectionHash === "string" && /^[0-9a-f]{64}$/.test(m.collectionHash) && m.publicationStatus === flopCollectionStatus &&
    m.trainerAdmission === false && m.selection === flopCollectionSelection && m.availableQuestions === 2 && m.activePlayers === 2 &&
    m.maximumAttempts === 100 && intervalEqual(m.questionKinds, ["INITIAL_CHECK_BET", "BB_FACING_BET"]));
  // Reuse the unchanged source metadata validator for shared model/chance/provenance identities.
  checked(() => parseFlopIntervalMetadata({ reportHash: m.collectionHash, publicationStatus: "INITIAL_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY",
    selection: "SINGLE_QUALIFIED_INITIAL_FLOP_QUESTION/v1", trainerAdmission: false, model: m.model, chanceModel: m.chanceModel,
    evScope: m.evScope, reachUnits: m.reachUnits, beliefs: m.beliefs, sourceHistoryReachStatus: m.sourceHistoryReachStatus,
    availableQuestions: 1, activePlayers: 2, maximumAttempts: 100, maximumLossBb: .01 }));
  return value as FlopCollectionMetadata;
}
export function parseFlopCollectionQuestion(value: unknown, metadata: FlopCollectionMetadata, index: number): FlopCollectionQuestion {
  const q = object(value, "collectionHash index totalQuestions selection decision");
  ensure(Number.isInteger(index) && index >= 0 && index < 2 && q.index === index && q.collectionHash === metadata.collectionHash &&
    q.totalQuestions === 2 && q.selection === flopCollectionSelection);
  const d = object(q.decision, "kind initial later");
  ensure(d.kind === metadata.questionKinds[index]);
  const source = object(index === 0 ? d.initial : d.later);
  ensure(index === 0 ? d.later === null : d.initial === null);
  const inner = { reportHash: source.reportHash, publicationStatus: source.publicationStatus, trainerAdmission: source.trainerAdmission,
    selection: source.selection, model: metadata.model, chanceModel: metadata.chanceModel, evScope: metadata.evScope,
    reachUnits: metadata.reachUnits, beliefs: metadata.beliefs, sourceHistoryReachStatus: metadata.sourceHistoryReachStatus,
    maximumLossBb: source.maximumLossBb, availableQuestions: 1, activePlayers: 2, maximumAttempts: 100 };
  checked(() => index === 0 ? parseFlopIntervalQuestion(d.initial, parseFlopIntervalMetadata(inner))
    : parseLaterFlopIntervalQuestion(d.later, parseLaterFlopIntervalMetadata(inner)));
  return value as FlopCollectionQuestion;
}
export function checkFlopCollectionInventory(questions: FlopCollectionQuestion[], metadata: FlopCollectionMetadata) {
  ensure(questions.length === 2);
  questions.forEach((q, i) => parseFlopCollectionQuestion(q, metadata, i));
  const a = questions[0].decision, b = questions[1].decision;
  ensure(a.kind === "INITIAL_CHECK_BET" && b.kind === "BB_FACING_BET" && a.initial.reportHash !== b.later.reportHash &&
    a.initial.context.hero === b.later.hero && a.initial.context.heroCards === b.later.heroCards &&
    intervalEqual(a.initial.context.board, b.later.context.board) &&
    intervalEqual(a.initial.context.preflopHistory, b.later.context.preflopHistory) &&
    a.initial.context.publicHistory === b.later.context.publicPreflopHistory &&
    a.initial.informationSet !== b.later.informationSet && a.initial.context.betSizeBb === b.later.context.betSizeBb);
}
export function parseFlopCollectionFeedback(value: unknown, question: FlopCollectionQuestion, action: FlopCollectionAction): FlopCollectionFeedback {
  const f = object(value, "collectionHash questionIndex decision");
  ensure(f.collectionHash === question.collectionHash && f.questionIndex === question.index && collectionActions(question).includes(action));
  const d = object(f.decision, "kind initial later");
  ensure(d.kind === question.decision.kind);
  const q = question.decision;
  if (q.kind === "INITIAL_CHECK_BET") {
    ensure(d.later === null);
    checked(() => parseFlopIntervalFeedback(d.initial, q.initial, action as FlopAction));
  } else {
    ensure(d.initial === null);
    checked(() => parseLaterFlopIntervalFeedback(d.later, q.later, action as LaterFlopAction));
  }
  return value as FlopCollectionFeedback;
}
export function parseFlopCollectionReview(value: unknown, metadata: FlopCollectionMetadata,
  questions: FlopCollectionQuestion[], attempts: FlopCollectionAttempt[]): FlopCollectionReview {
  const r = object(value, "schemaVersion collectionHash selection questions attempts counts");
  ensure(r.schemaVersion === flopCollectionSchema && r.collectionHash === metadata.collectionHash && r.selection === flopCollectionSelection &&
    Array.isArray(r.questions) && intervalEqual(r.questions, questions) && Array.isArray(r.attempts) &&
    r.attempts.length === attempts.length && attempts.length <= 100);
  checkFlopCollectionInventory(r.questions as FlopCollectionQuestion[], metadata);
  const counts = { distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size, withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index questionIndex action feedback"), expected = attempts[index];
    ensure(a.index === index && a.questionIndex === expected.questionIndex && a.action === expected.action && Number.isInteger(a.questionIndex));
    const f = parseFlopCollectionFeedback(a.feedback, questions[expected.questionIndex], expected.action);
    ensure(intervalEqual(f, expected.feedback));
    const verdict = collectionGrade(f).selected.classification;
    counts[verdict === "WITHIN_LIMIT" ? "withinLimit" : verdict === "OUTSIDE_LIMIT" ? "outsideLimit" : "strategyDependent"]++;
  });
  ensure(intervalEqual(r.counts, counts));
  return value as FlopCollectionReview;
}
export async function flopCollectionRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/flop-interval-collection${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new FlopCollectionRequestError(response.status);
  try { return await response.json(); } catch { throw new IntervalProtocolError(); }
}
