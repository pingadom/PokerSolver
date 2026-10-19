import { IntervalProtocolError, intervalEqual } from "./intervalTrainerApi";
import {
  parseFlopIntervalMetadata, parseFlopIntervalQuestion, parseFlopIntervalFeedback,
  type FlopAction, type FlopIntervalQuestion, type FlopIntervalFeedback,
} from "./flopIntervalApi";
import {
  parseLaterFlopIntervalMetadata, parseLaterFlopIntervalQuestion, parseLaterFlopIntervalFeedback,
  type LaterFlopAction, type LaterFlopIntervalQuestion, type LaterFlopIntervalFeedback,
} from "./laterFlopIntervalApi";

import {
  parseBtnFlopIntervalMetadata, parseBtnFlopIntervalQuestion, parseBtnFlopIntervalFeedback, checkBtnFlopInventory,
  type BtnFlopIntervalQuestion, type BtnFlopIntervalFeedback,
} from "./btnFlopIntervalApi";

export const mixedFlopCollectionStatus = "SIX_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
export const mixedFlopCollectionSelection = "ORDERED_TWO_BB_AND_FOUR_BTN_FLOP_QUESTIONS/v2";
export const mixedFlopCollectionSchema = "pokerlab-flop-interval-collection-review/v2";
export type MixedFlopCollectionAction = FlopAction | LaterFlopAction;
export type MixedFlopCollectionMetadata = {
  collectionHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  model: string; chanceModel: string; evScope: string; reachUnits: string; beliefs: string; sourceHistoryReachStatus: string;
  questionKinds: ["INITIAL_CHECK_BET", "BB_FACING_BET", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK"]; availableQuestions: number; activePlayers: number; maximumAttempts: number;
};
type Decision = { kind: "INITIAL_CHECK_BET"; initial: FlopIntervalQuestion; later: null; btn: null }
  | { kind: "BB_FACING_BET"; initial: null; later: LaterFlopIntervalQuestion; btn: null }
  | { kind: "BTN_AFTER_CHECK"; initial: null; later: null; btn: BtnFlopIntervalQuestion };
type Grade = { kind: "INITIAL_CHECK_BET"; initial: FlopIntervalFeedback; later: null; btn: null }
  | { kind: "BB_FACING_BET"; initial: null; later: LaterFlopIntervalFeedback; btn: null }
  | { kind: "BTN_AFTER_CHECK"; initial: null; later: null; btn: BtnFlopIntervalFeedback };
export type MixedFlopCollectionQuestion = { collectionHash: string; index: number; sourceQuestionIndex: number; totalQuestions: number; selection: string; decision: Decision };
export type MixedFlopCollectionFeedback = { collectionHash: string; questionIndex: number; sourceQuestionIndex: number; decision: Grade };
export type MixedFlopCollectionAttempt = { index: number; questionIndex: number; action: MixedFlopCollectionAction; feedback: MixedFlopCollectionFeedback };
export type MixedFlopCollectionReview = {
  schemaVersion: string; collectionHash: string; selection: string; questions: MixedFlopCollectionQuestion[]; attempts: MixedFlopCollectionAttempt[];
  counts: { distinctQuestionsAttempted: number; withinLimit: number; outsideLimit: number; strategyDependent: number };
};
export class MixedFlopCollectionRequestError extends Error {
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
export function mixedDecision(q: MixedFlopCollectionQuestion) { return q.decision.initial ?? q.decision.later ?? q.decision.btn; }
export function mixedGrade(f: MixedFlopCollectionFeedback) { return f.decision.initial ?? f.decision.later ?? f.decision.btn; }
export function mixedActions(q: MixedFlopCollectionQuestion): MixedFlopCollectionAction[] { return mixedDecision(q).context.legalActions; }
export function parseMixedFlopCollectionMetadata(value: unknown): MixedFlopCollectionMetadata {
  const m = object(value, "collectionHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus questionKinds availableQuestions activePlayers maximumAttempts");
  ensure(typeof m.collectionHash === "string" && /^[0-9a-f]{64}$/.test(m.collectionHash) && m.publicationStatus === mixedFlopCollectionStatus &&
    m.trainerAdmission === false && m.selection === mixedFlopCollectionSelection && m.availableQuestions === 6 && m.activePlayers === 2 &&
    m.maximumAttempts === 100 && intervalEqual(m.questionKinds, ["INITIAL_CHECK_BET", "BB_FACING_BET", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK"]));
  // Reuse the unchanged source metadata validator for shared model/chance/provenance identities.
  checked(() => parseFlopIntervalMetadata({ reportHash: m.collectionHash, publicationStatus: "INITIAL_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY",
    selection: "SINGLE_QUALIFIED_INITIAL_FLOP_QUESTION/v1", trainerAdmission: false, model: m.model, chanceModel: m.chanceModel,
    evScope: m.evScope, reachUnits: m.reachUnits, beliefs: m.beliefs, sourceHistoryReachStatus: m.sourceHistoryReachStatus,
    availableQuestions: 1, activePlayers: 2, maximumAttempts: 100, maximumLossBb: .01 }));
  return value as MixedFlopCollectionMetadata;
}
export function parseMixedFlopCollectionQuestion(value: unknown, metadata: MixedFlopCollectionMetadata, index: number): MixedFlopCollectionQuestion {
  const q = object(value, "collectionHash index totalQuestions selection sourceQuestionIndex decision");
  ensure(Number.isInteger(index) && index >= 0 && index < 6 && q.index === index && q.collectionHash === metadata.collectionHash &&
    q.totalQuestions === 6 && q.sourceQuestionIndex === (index < 2 ? 0 : index - 2) && q.selection === mixedFlopCollectionSelection);
  const d = object(q.decision, "kind initial later btn");
  ensure(d.kind === metadata.questionKinds[index]);
  const source = object(index === 0 ? d.initial : index === 1 ? d.later : d.btn);
  ensure(index === 0 ? d.later === null && d.btn === null : index === 1 ? d.initial === null && d.btn === null : d.initial === null && d.later === null);
  const inner = { reportHash: source.reportHash, publicationStatus: source.publicationStatus, trainerAdmission: source.trainerAdmission,
    selection: source.selection, model: metadata.model, chanceModel: metadata.chanceModel, evScope: metadata.evScope,
    reachUnits: metadata.reachUnits, beliefs: metadata.beliefs, sourceHistoryReachStatus: metadata.sourceHistoryReachStatus,
    maximumLossBb: source.maximumLossBb, availableQuestions: 1, activePlayers: 2, maximumAttempts: 100 };
  if (index >= 2) {
    const { maximumLossBb: _loss, ...btnMetadata } = inner;
    void _loss;
    checked(() => parseBtnFlopIntervalQuestion(d.btn, parseBtnFlopIntervalMetadata({ ...btnMetadata, availableQuestions: 4 }), index - 2));
  } else checked(() => index === 0 ? parseFlopIntervalQuestion(d.initial, parseFlopIntervalMetadata(inner))
    : parseLaterFlopIntervalQuestion(d.later, parseLaterFlopIntervalMetadata(inner)));
  return value as MixedFlopCollectionQuestion;
}
export function checkMixedFlopCollectionInventory(questions: MixedFlopCollectionQuestion[], metadata: MixedFlopCollectionMetadata) {
  ensure(questions.length === 6);
  questions.forEach((q, i) => parseMixedFlopCollectionQuestion(q, metadata, i));
  const a = questions[0].decision, b = questions[1].decision;
  ensure(a.kind === "INITIAL_CHECK_BET" && b.kind === "BB_FACING_BET" && a.initial.reportHash !== b.later.reportHash &&
    a.initial.context.hero === b.later.hero && a.initial.context.heroCards === b.later.heroCards &&
    intervalEqual(a.initial.context.board, b.later.context.board) &&
    intervalEqual(a.initial.context.preflopHistory, b.later.context.preflopHistory) &&
    a.initial.context.publicHistory === b.later.context.publicPreflopHistory &&
    a.initial.informationSet !== b.later.informationSet && a.initial.context.betSizeBb === b.later.context.betSizeBb);
  const btn = questions.slice(2).map(q => { ensure(q.decision.kind === "BTN_AFTER_CHECK"); return q.decision.btn; });
  const first = btn[0];
  checked(() => checkBtnFlopInventory(btn, parseBtnFlopIntervalMetadata({
    reportHash: first.reportHash, publicationStatus: first.publicationStatus, trainerAdmission: false, selection: first.selection,
    model: first.model, chanceModel: first.chanceModel, evScope: first.evScope, reachUnits: first.reachUnits,
    beliefs: first.beliefs, sourceHistoryReachStatus: first.sourceHistoryReachStatus, availableQuestions: 4, activePlayers: 2, maximumAttempts: 100,
  })));
  ensure(new Set([a.initial.reportHash, b.later.reportHash, first.reportHash]).size === 3 &&
    new Set(questions.map(q => mixedDecision(q).informationSet)).size === 6 &&
    intervalEqual(a.initial.context.board, first.context.board) &&
    intervalEqual(a.initial.context.preflopHistory, first.context.preflopHistory) &&
    a.initial.context.publicHistory === first.context.publicPreflopHistory &&
    a.initial.context.betSizeBb === first.context.betSizeBb && a.initial.context.potBb === first.context.potBb);
}
export function parseMixedFlopCollectionFeedback(value: unknown, question: MixedFlopCollectionQuestion, action: MixedFlopCollectionAction): MixedFlopCollectionFeedback {
  const f = object(value, "collectionHash questionIndex sourceQuestionIndex decision");
  ensure(f.collectionHash === question.collectionHash && f.questionIndex === question.index && f.sourceQuestionIndex === question.sourceQuestionIndex && mixedActions(question).includes(action));
  const d = object(f.decision, "kind initial later btn");
  ensure(d.kind === question.decision.kind);
  const q = question.decision;
  if (q.kind === "INITIAL_CHECK_BET") {
    ensure(d.later === null && d.btn === null);
    checked(() => parseFlopIntervalFeedback(d.initial, q.initial, action as FlopAction));
  } else if (q.kind === "BB_FACING_BET") {
    ensure(d.initial === null && d.btn === null);
    checked(() => parseLaterFlopIntervalFeedback(d.later, q.later, action as LaterFlopAction));
  } else {
    ensure(d.initial === null && d.later === null);
    checked(() => parseBtnFlopIntervalFeedback(d.btn, q.btn, action as FlopAction));
  }
  return value as MixedFlopCollectionFeedback;
}
export function parseMixedFlopCollectionReview(value: unknown, metadata: MixedFlopCollectionMetadata,
  questions: MixedFlopCollectionQuestion[], attempts: MixedFlopCollectionAttempt[]): MixedFlopCollectionReview {
  const r = object(value, "schemaVersion collectionHash selection questions attempts counts");
  ensure(r.schemaVersion === mixedFlopCollectionSchema && r.collectionHash === metadata.collectionHash && r.selection === mixedFlopCollectionSelection &&
    Array.isArray(r.questions) && intervalEqual(r.questions, questions) && Array.isArray(r.attempts) &&
    r.attempts.length === attempts.length && attempts.length <= 100);
  checkMixedFlopCollectionInventory(r.questions as MixedFlopCollectionQuestion[], metadata);
  const counts = { distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size, withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index questionIndex action feedback"), expected = attempts[index];
    ensure(a.index === index && a.questionIndex === expected.questionIndex && a.action === expected.action && Number.isInteger(a.questionIndex));
    const f = parseMixedFlopCollectionFeedback(a.feedback, questions[expected.questionIndex], expected.action);
    ensure(intervalEqual(f, expected.feedback));
    const verdict = mixedGrade(f).selected.classification;
    counts[verdict === "WITHIN_LIMIT" ? "withinLimit" : verdict === "OUTSIDE_LIMIT" ? "outsideLimit" : "strategyDependent"]++;
  });
  ensure(intervalEqual(r.counts, counts));
  return value as MixedFlopCollectionReview;
}
export async function mixedFlopCollectionRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/flop-interval-collection-v2${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new MixedFlopCollectionRequestError(response.status);
  try { return await response.json(); } catch { throw new IntervalProtocolError(); }
}

// Only choices cross the wire; the server recreates all grades from its owned reports.
export function mixedReviewChoices(attempts: MixedFlopCollectionAttempt[]) {
  return attempts.map(({ questionIndex, action, feedback }) => ({ questionIndex, reportHash: mixedGrade(feedback).reportHash, action }));
}
export async function mixedFlopReviewDocument(signal: AbortSignal, metadata: MixedFlopCollectionMetadata,
  questions: MixedFlopCollectionQuestion[], attempts: MixedFlopCollectionAttempt[]): Promise<string> {
  const response = await fetch("/api/v1/trainer/research/flop-interval-collection-v2/review/export", {
    signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ collectionHash: metadata.collectionHash, choices: mixedReviewChoices(attempts) }),
  });
  if (!response.ok) throw new MixedFlopCollectionRequestError(response.status);
  const text = await response.text();
  ensure(new TextEncoder().encode(text).length <= 1024 * 1024);
  let value: unknown;
  try { value = JSON.parse(text); } catch { throw new IntervalProtocolError(); }
  parseMixedFlopCollectionReview(value, metadata, questions, attempts);
  return text;
}
