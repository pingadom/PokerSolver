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

import { parsePairedFlopIntervalMetadata, parsePairedFlopIntervalQuestion, parsePairedFlopIntervalFeedback, checkPairedFlopInventory,
  type PairedFlopIntervalQuestion, type PairedFlopIntervalFeedback } from "./pairedFlopIntervalApi";

export const expandedFlopCollectionStatus = "TEN_QUALIFIED_FLOP_CONTEXTS_RESEARCH_ONLY";
export const expandedFlopCollectionSelection = "ORDERED_TWO_BB_FOUR_BTN_CHECK_FOUR_BTN_FACING_BET/v3";
export const expandedFlopCollectionSchema = "pokerlab-flop-interval-collection-review/v3";
export type ExpandedFlopCollectionAction = FlopAction | LaterFlopAction;
export type ExpandedFlopCollectionMetadata = {
  collectionHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  model: string; chanceModel: string; evScope: string; reachUnits: string; beliefs: string; sourceHistoryReachStatus: string;
  questionKinds: ["INITIAL_CHECK_BET", "BB_FACING_BET", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "PAIRED_BTN_FACING_BET", "PAIRED_BTN_FACING_BET", "PAIRED_BTN_FACING_BET", "PAIRED_BTN_FACING_BET"]; availableQuestions: number; activePlayers: number; maximumAttempts: number;
};
type Decision = { kind: "INITIAL_CHECK_BET"; initial: FlopIntervalQuestion; later: null; btn: null; paired: null }
  | { kind: "BB_FACING_BET"; initial: null; later: LaterFlopIntervalQuestion; btn: null; paired: null }
  | { kind: "BTN_AFTER_CHECK"; initial: null; later: null; btn: BtnFlopIntervalQuestion; paired: null }
  | { kind: "PAIRED_BTN_FACING_BET"; initial: null; later: null; btn: null; paired: PairedFlopIntervalQuestion };
type Grade = { kind: "INITIAL_CHECK_BET"; initial: FlopIntervalFeedback; later: null; btn: null; paired: null }
  | { kind: "BB_FACING_BET"; initial: null; later: LaterFlopIntervalFeedback; btn: null; paired: null }
  | { kind: "BTN_AFTER_CHECK"; initial: null; later: null; btn: BtnFlopIntervalFeedback; paired: null }
  | { kind: "PAIRED_BTN_FACING_BET"; initial: null; later: null; btn: null; paired: PairedFlopIntervalFeedback };
export type ExpandedFlopCollectionQuestion = { collectionHash: string; index: number; sourceQuestionIndex: number; totalQuestions: number; selection: string; decision: Decision };
export type ExpandedFlopCollectionFeedback = { collectionHash: string; questionIndex: number; sourceQuestionIndex: number; decision: Grade };
export type ExpandedFlopCollectionAttempt = { index: number; questionIndex: number; action: ExpandedFlopCollectionAction; feedback: ExpandedFlopCollectionFeedback };
export type ExpandedFlopCollectionReview = {
  schemaVersion: string; collectionHash: string; selection: string; questions: ExpandedFlopCollectionQuestion[]; attempts: ExpandedFlopCollectionAttempt[];
  counts: { distinctQuestionsAttempted: number; withinLimit: number; outsideLimit: number; strategyDependent: number };
};
export class ExpandedFlopCollectionRequestError extends Error {
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
export function expandedDecision(q: ExpandedFlopCollectionQuestion) { return q.decision.initial ?? q.decision.later ?? q.decision.btn ?? q.decision.paired; }
export function expandedGrade(f: ExpandedFlopCollectionFeedback) { return f.decision.initial ?? f.decision.later ?? f.decision.btn ?? f.decision.paired; }
export function expandedActions(q: ExpandedFlopCollectionQuestion): ExpandedFlopCollectionAction[] { return expandedDecision(q).context.legalActions; }
export function parseExpandedFlopCollectionMetadata(value: unknown): ExpandedFlopCollectionMetadata {
  const m = object(value, "collectionHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus questionKinds availableQuestions activePlayers maximumAttempts");
  ensure(typeof m.collectionHash === "string" && /^[0-9a-f]{64}$/.test(m.collectionHash) && m.publicationStatus === expandedFlopCollectionStatus &&
    m.trainerAdmission === false && m.selection === expandedFlopCollectionSelection && m.availableQuestions === 10 && m.activePlayers === 2 &&
    m.maximumAttempts === 100 && intervalEqual(m.questionKinds, ["INITIAL_CHECK_BET", "BB_FACING_BET", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "BTN_AFTER_CHECK", "PAIRED_BTN_FACING_BET", "PAIRED_BTN_FACING_BET", "PAIRED_BTN_FACING_BET", "PAIRED_BTN_FACING_BET"]));
  // Reuse the unchanged source metadata validator for shared model/chance/provenance identities.
  checked(() => parseFlopIntervalMetadata({ reportHash: m.collectionHash, publicationStatus: "INITIAL_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY",
    selection: "SINGLE_QUALIFIED_INITIAL_FLOP_QUESTION/v1", trainerAdmission: false, model: m.model, chanceModel: m.chanceModel,
    evScope: m.evScope, reachUnits: m.reachUnits, beliefs: m.beliefs, sourceHistoryReachStatus: m.sourceHistoryReachStatus,
    availableQuestions: 1, activePlayers: 2, maximumAttempts: 100, maximumLossBb: .01 }));
  return value as ExpandedFlopCollectionMetadata;
}
export function parseExpandedFlopCollectionQuestion(value: unknown, metadata: ExpandedFlopCollectionMetadata, index: number): ExpandedFlopCollectionQuestion {
  const q = object(value, "collectionHash index totalQuestions selection sourceQuestionIndex decision");
  ensure(Number.isInteger(index) && index >= 0 && index < 10 && q.index === index && q.collectionHash === metadata.collectionHash &&
    q.totalQuestions === 10 && q.sourceQuestionIndex === (index < 2 ? 0 : index < 6 ? index - 2 : index - 6) && q.selection === expandedFlopCollectionSelection);
  const d = object(q.decision, "kind initial later btn paired");
  ensure(d.kind === metadata.questionKinds[index]);
  const source = object(index === 0 ? d.initial : index === 1 ? d.later : index < 6 ? d.btn : d.paired);
  ensure(index < 6 ? d.paired === null : d.initial === null && d.later === null && d.btn === null);
  ensure(index === 0 ? d.later === null && d.btn === null : index === 1 ? d.initial === null && d.btn === null : d.initial === null && d.later === null);
  const inner = { reportHash: source.reportHash, publicationStatus: source.publicationStatus, trainerAdmission: source.trainerAdmission,
    selection: source.selection, model: metadata.model, chanceModel: metadata.chanceModel, evScope: metadata.evScope,
    reachUnits: metadata.reachUnits, beliefs: metadata.beliefs, sourceHistoryReachStatus: metadata.sourceHistoryReachStatus,
    maximumLossBb: source.maximumLossBb, availableQuestions: 1, activePlayers: 2, maximumAttempts: 100 };
  if (index >= 6) {
    const { maximumLossBb: _loss, ...pairedMetadata } = inner;
    void _loss;
    checked(() => parsePairedFlopIntervalQuestion(d.paired, parsePairedFlopIntervalMetadata({ ...pairedMetadata, availableQuestions: 4 }), index - 6));
  } else if (index >= 2) {
    const { maximumLossBb: _loss, ...btnMetadata } = inner;
    void _loss;
    checked(() => parseBtnFlopIntervalQuestion(d.btn, parseBtnFlopIntervalMetadata({ ...btnMetadata, availableQuestions: 4 }), index - 2));
  } else checked(() => index === 0 ? parseFlopIntervalQuestion(d.initial, parseFlopIntervalMetadata(inner))
    : parseLaterFlopIntervalQuestion(d.later, parseLaterFlopIntervalMetadata(inner)));
  return value as ExpandedFlopCollectionQuestion;
}
export function checkExpandedFlopCollectionInventory(questions: ExpandedFlopCollectionQuestion[], metadata: ExpandedFlopCollectionMetadata) {
  ensure(questions.length === 10);
  questions.forEach((q, i) => parseExpandedFlopCollectionQuestion(q, metadata, i));
  const a = questions[0].decision, b = questions[1].decision;
  ensure(a.kind === "INITIAL_CHECK_BET" && b.kind === "BB_FACING_BET" && a.initial.reportHash !== b.later.reportHash &&
    a.initial.context.hero === b.later.hero && a.initial.context.heroCards === b.later.heroCards &&
    intervalEqual(a.initial.context.board, b.later.context.board) &&
    intervalEqual(a.initial.context.preflopHistory, b.later.context.preflopHistory) &&
    a.initial.context.publicHistory === b.later.context.publicPreflopHistory &&
    a.initial.informationSet !== b.later.informationSet && a.initial.context.betSizeBb === b.later.context.betSizeBb);
  const btn = questions.slice(2, 6).map(q => { ensure(q.decision.kind === "BTN_AFTER_CHECK"); return q.decision.btn; });
  const first = btn[0];
  checked(() => checkBtnFlopInventory(btn, parseBtnFlopIntervalMetadata({
    reportHash: first.reportHash, publicationStatus: first.publicationStatus, trainerAdmission: false, selection: first.selection,
    model: first.model, chanceModel: first.chanceModel, evScope: first.evScope, reachUnits: first.reachUnits,
    beliefs: first.beliefs, sourceHistoryReachStatus: first.sourceHistoryReachStatus, availableQuestions: 4, activePlayers: 2, maximumAttempts: 100,
  })));
  ensure(new Set([a.initial.reportHash, b.later.reportHash, first.reportHash]).size === 3 &&
    new Set(questions.map(q => expandedDecision(q).informationSet)).size === 10 &&
    intervalEqual(a.initial.context.board, first.context.board) &&
    intervalEqual(a.initial.context.preflopHistory, first.context.preflopHistory) &&
    a.initial.context.publicHistory === first.context.publicPreflopHistory &&
    a.initial.context.betSizeBb === first.context.betSizeBb && a.initial.context.potBb === first.context.potBb);
  const paired = questions.slice(6).map(q => { ensure(q.decision.kind === "PAIRED_BTN_FACING_BET"); return q.decision.paired; });
  const last = paired[0];
  checked(() => checkPairedFlopInventory(paired, parsePairedFlopIntervalMetadata({
    reportHash: last.reportHash, publicationStatus: last.publicationStatus, trainerAdmission: false, selection: last.selection,
    model: last.model, chanceModel: last.chanceModel, evScope: last.evScope, reachUnits: last.reachUnits,
    beliefs: last.beliefs, sourceHistoryReachStatus: last.sourceHistoryReachStatus, availableQuestions: 4, activePlayers: 2, maximumAttempts: 100,
  })));
  ensure(new Set([a.initial.reportHash, b.later.reportHash, first.reportHash, last.reportHash]).size === 4 &&
    intervalEqual(first.context.board, ["2s", "8s", "Ts"]) && intervalEqual(last.context.board, ["8d", "Kc", "Kd"]) &&
    intervalEqual(first.context.preflopHistory, last.context.preflopHistory) &&
    first.context.publicPreflopHistory === last.context.publicPreflopHistory &&
    first.context.betSizeBb === last.context.betSizeBb && first.context.potBb + last.context.betSizeBb === last.context.potBb);

}
export function parseExpandedFlopCollectionFeedback(value: unknown, question: ExpandedFlopCollectionQuestion, action: ExpandedFlopCollectionAction): ExpandedFlopCollectionFeedback {
  const f = object(value, "collectionHash questionIndex sourceQuestionIndex decision");
  ensure(f.collectionHash === question.collectionHash && f.questionIndex === question.index && f.sourceQuestionIndex === question.sourceQuestionIndex && expandedActions(question).includes(action));
  const d = object(f.decision, "kind initial later btn paired");
  ensure(d.kind === question.decision.kind);
  const q = question.decision;
  ensure(q.kind === "PAIRED_BTN_FACING_BET" ? d.btn === null : d.paired === null);
  if (q.kind === "INITIAL_CHECK_BET") {
    ensure(d.later === null && d.btn === null);
    checked(() => parseFlopIntervalFeedback(d.initial, q.initial, action as FlopAction));
  } else if (q.kind === "BB_FACING_BET") {
    ensure(d.initial === null && d.btn === null);
    checked(() => parseLaterFlopIntervalFeedback(d.later, q.later, action as LaterFlopAction));
  } else if (q.kind === "BTN_AFTER_CHECK") {
    ensure(d.initial === null && d.later === null);
    checked(() => parseBtnFlopIntervalFeedback(d.btn, q.btn, action as FlopAction));
  } else {
    ensure(d.initial === null && d.later === null);
    checked(() => parsePairedFlopIntervalFeedback(d.paired, q.paired, action as LaterFlopAction));
  }
  return value as ExpandedFlopCollectionFeedback;
}
export function parseExpandedFlopCollectionReview(value: unknown, metadata: ExpandedFlopCollectionMetadata,
  questions: ExpandedFlopCollectionQuestion[], attempts: ExpandedFlopCollectionAttempt[]): ExpandedFlopCollectionReview {
  const r = object(value, "schemaVersion collectionHash selection questions attempts counts");
  ensure(r.schemaVersion === expandedFlopCollectionSchema && r.collectionHash === metadata.collectionHash && r.selection === expandedFlopCollectionSelection &&
    Array.isArray(r.questions) && intervalEqual(r.questions, questions) && Array.isArray(r.attempts) &&
    r.attempts.length === attempts.length && attempts.length <= 100);
  checkExpandedFlopCollectionInventory(r.questions as ExpandedFlopCollectionQuestion[], metadata);
  const counts = { distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size, withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index questionIndex action feedback"), expected = attempts[index];
    ensure(a.index === index && a.questionIndex === expected.questionIndex && a.action === expected.action && Number.isInteger(a.questionIndex));
    const f = parseExpandedFlopCollectionFeedback(a.feedback, questions[expected.questionIndex], expected.action);
    ensure(intervalEqual(f, expected.feedback));
    const verdict = expandedGrade(f).selected.classification;
    counts[verdict === "WITHIN_LIMIT" ? "withinLimit" : verdict === "OUTSIDE_LIMIT" ? "outsideLimit" : "strategyDependent"]++;
  });
  ensure(intervalEqual(r.counts, counts));
  return value as ExpandedFlopCollectionReview;
}
export async function expandedFlopCollectionRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/flop-interval-collection-v3${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new ExpandedFlopCollectionRequestError(response.status);
  try { return await response.json(); } catch { throw new IntervalProtocolError(); }
}

// Only choices cross the wire; the server recreates all grades from its owned reports.
export function expandedReviewChoices(attempts: ExpandedFlopCollectionAttempt[]) {
  return attempts.map(({ questionIndex, action, feedback }) => ({ questionIndex, reportHash: expandedGrade(feedback).reportHash, action }));
}
export async function expandedFlopReviewDocument(signal: AbortSignal, metadata: ExpandedFlopCollectionMetadata,
  questions: ExpandedFlopCollectionQuestion[], attempts: ExpandedFlopCollectionAttempt[]): Promise<string> {
  const response = await fetch("/api/v1/trainer/research/flop-interval-collection-v3/review/export", {
    signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ collectionHash: metadata.collectionHash, choices: expandedReviewChoices(attempts) }),
  });
  if (!response.ok) throw new ExpandedFlopCollectionRequestError(response.status);
  const text = await response.text();
  ensure(new TextEncoder().encode(text).length <= 1024 * 1024);
  let value: unknown;
  try { value = JSON.parse(text); } catch { throw new IntervalProtocolError(); }
  parseExpandedFlopCollectionReview(value, metadata, questions, attempts);
  return text;
}
