import type { MultiwaySeat } from "./multiwayApi";
import { intervalEqual } from "./intervalTrainerApi";

export const btnFlopIntervalStatus = "FOUR_BTN_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY";
export const btnFlopIntervalSelection = "ALL_FOUR_SUPPLIED_BTN_HANDS_AFTER_BB_CHECK/v1";
export const btnFlopIntervalModel = "SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1";
export const btnFlopIntervalChance = "ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1";
export const btnFlopIntervalEvScope = "ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1";
export const btnFlopIntervalReachUnits = "COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1";
export const btnFlopIntervalSchema = "pokerlab-btn-flop-drill/v1";
const seats: MultiwaySeat[] = ["UTG", "HJ", "CO", "BTN", "SB", "BB"];
export const btnFlopHands = ["9s Js", "Ah Kh", "As Qs", "Qh Th"] as const;
export type BtnFlopAction = "check" | "bet";
export type BtnFlopIntervalMetadata = {
  reportHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  model: string; chanceModel: string; evScope: string; reachUnits: string;
  beliefs: string; sourceHistoryReachStatus: string;
  availableQuestions: number; activePlayers: number; maximumAttempts: number;
};
export type FlopPlayer = { seat: MultiwaySeat; status: "ACTING" | "ACTIVE" | "FOLDED" | "ALL_IN"; committedBb: number; remainingStackBb: number };
export type FlopPublicMove = { seat: MultiwaySeat; kind: "POST_SMALL_BLIND" | "POST_BIG_BLIND" | "FOLD" | "CHECK" | "CALL" | "RAISE_TO"; amountBb: number };
export type BtnFlopIntervalQuestion = {
  questionIndex: number;
  reportHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  informationSet: string; maximumLossBb: number; securitySlackBb: number; minimumReach: number;
  reachUnits: string; evScope: string;
  hero: MultiwaySeat; heroCards: string; model: string; chanceModel: string; beliefs: string; sourceHistoryReachStatus: string;
  context: { actingSeat: MultiwaySeat; board: string[]; publicPreflopHistory: string;
    flopHistory: { seat: MultiwaySeat; action: "check" | "bet"; amountBb: number }[];
    preflopHistory: FlopPublicMove[]; potBb: number; toCallBb: number; betSizeBb: number;
    players: FlopPlayer[]; legalActions: BtnFlopAction[] };
};
export type BtnFlopIntervalMove = { action: BtnFlopAction; lowerEvBb: number; upperEvBb: number;
  lowerLossBb: number; upperLossBb: number; classification: "WITHIN_LIMIT" | "OUTSIDE_LIMIT" | "STRATEGY_DEPENDENT" };
export type BtnFlopIntervalFeedback = { questionIndex: number; reportHash: string; publicationStatus: string; trainerAdmission: false;
  evScope: string; maximumLossBb: number; selected: BtnFlopIntervalMove; alternatives: BtnFlopIntervalMove[] };
export type BtnFlopIntervalChoice = { questionIndex: number; action: BtnFlopAction };
export type BtnFlopIntervalAttempt = BtnFlopIntervalChoice & { index: number; feedback: BtnFlopIntervalFeedback };
export type BtnFlopIntervalReview = { schemaVersion: string; reportHash: string; selection: string;
  questions: BtnFlopIntervalQuestion[]; attempts: BtnFlopIntervalAttempt[];
  counts: { distinctQuestionsAttempted: number; withinLimit: number; outsideLimit: number; strategyDependent: number } };

export class BtnFlopIntervalProtocolError extends Error {
  constructor() { super("The flop report changed or returned inconsistent data. Load the current report to continue."); }
}
export class BtnFlopIntervalRequestError extends Error {
  constructor(readonly status: number) {
    super(status === 404 ? "The BTN flop research drill is not enabled on this server."
      : status === 400 ? "The server rejected this report or action. Load the current report to continue."
        : `The flop trainer request failed (${status}). Please try again.`);
  }
}
function ensure(condition: unknown): asserts condition { if (!condition) throw new BtnFlopIntervalProtocolError(); }
function object(value: unknown, keys: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  const result = value as Record<string, unknown>;
  ensure(Object.keys(result).sort().join(" ") === keys.split(" ").sort().join(" "));
  return result;
}
function finite(value: unknown, minimum = -Infinity): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= minimum;
}
function card(value: unknown): value is string { return typeof value === "string" && /^[2-9TJQKA][cdhs]$/.test(value); }
function identity(value: Record<string, unknown>) {
  ensure(typeof value.reportHash === "string" && /^[0-9a-f]{64}$/.test(value.reportHash) &&
    value.publicationStatus === btnFlopIntervalStatus && value.trainerAdmission === false);
}
export function parseBtnFlopIntervalMetadata(value: unknown): BtnFlopIntervalMetadata {
  const m = object(value, "reportHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus availableQuestions activePlayers maximumAttempts");
  identity(m);
  ensure(m.selection === btnFlopIntervalSelection && m.model === btnFlopIntervalModel && m.chanceModel === btnFlopIntervalChance &&
    m.evScope === btnFlopIntervalEvScope && m.reachUnits === btnFlopIntervalReachUnits &&
    m.beliefs === "EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1" && m.sourceHistoryReachStatus === "UNAVAILABLE_NO_SOURCE_POLICY" &&
    m.availableQuestions === 4 && m.activePlayers === 2 && m.maximumAttempts === 100);
  return value as BtnFlopIntervalMetadata;
}
export function parseBtnFlopIntervalQuestion(value: unknown, metadata: BtnFlopIntervalMetadata, index: number): BtnFlopIntervalQuestion {
  const q = object(value, "questionIndex reportHash publicationStatus trainerAdmission selection informationSet hero heroCards model chanceModel beliefs sourceHistoryReachStatus context maximumLossBb securitySlackBb minimumReach reachUnits evScope");
  identity(q);
  ensure(q.reportHash === metadata.reportHash && Number.isInteger(index) && index >= 0 && index < 4 && q.questionIndex === index &&
    q.heroCards === btnFlopHands[index] && finite(q.maximumLossBb, Number.MIN_VALUE) && q.maximumLossBb <= .01 &&
    q.selection === btnFlopIntervalSelection && q.reachUnits === btnFlopIntervalReachUnits && q.evScope === btnFlopIntervalEvScope &&
    q.securitySlackBb === 1e-8 && q.minimumReach === .0001 &&
    typeof q.informationSet === "string" && q.informationSet.length > 0 && q.informationSet.length <= 4096);
  const c = object(q.context, "actingSeat board publicPreflopHistory preflopHistory flopHistory potBb toCallBb betSizeBb players legalActions");
  ensure(q.hero === "BTN" && c.actingSeat === q.hero && typeof q.heroCards === "string" &&
    q.model === metadata.model && q.chanceModel === metadata.chanceModel && q.beliefs === metadata.beliefs &&
    q.sourceHistoryReachStatus === metadata.sourceHistoryReachStatus);
  const hole = q.heroCards.split(" ");
  ensure(hole.length === 2 && hole.every(card) && Array.isArray(c.board) && c.board.length === 3 && c.board.every(card) &&
    new Set([...hole, ...c.board]).size === 5);
  ensure(typeof c.publicPreflopHistory === "string" && c.publicPreflopHistory.length > 0 && c.publicPreflopHistory.length <= 4096 &&
    finite(c.potBb, Number.MIN_VALUE) && c.toCallBb === 0 && finite(c.betSizeBb, Number.MIN_VALUE) &&
    intervalEqual(c.legalActions, ["check", "bet"]));
  ensure(Array.isArray(c.players) && c.players.length === 6);
  const players = c.players.map((item, index) => {
    const p = object(item, "seat status committedBb remainingStackBb");
    ensure(p.seat === seats[index] && ["ACTING", "ACTIVE", "FOLDED", "ALL_IN"].includes(String(p.status)) &&
      finite(p.committedBb, 0) && finite(p.remainingStackBb, 0) &&
      (p.status === "ACTING") === (p.seat === q.hero));
    return p as unknown as FlopPlayer;
  });
  const active = players.filter((p) => p.status !== "FOLDED");
  const hero = players.find((p) => p.seat === q.hero)!;
  const stack = hero.committedBb + hero.remainingStackBb;
  ensure(active.length === 2 && active.every((p) => p.remainingStackBb > 0 && p.status !== "ALL_IN") &&
    active.every(p => p.remainingStackBb >= (c.betSizeBb as number)) &&
    active.every(p => p.committedBb === hero.committedBb) &&
    players.every((p) => Math.abs(p.committedBb + p.remainingStackBb - stack) < 1e-9) &&
    Math.abs(players.reduce((sum, p) => sum + p.committedBb, 0) - c.potBb) < 1e-9);
  ensure(Array.isArray(c.preflopHistory) && c.preflopHistory.length >= 2 && c.preflopHistory.length <= 128);
  const committed = [0, 0, 0, 0, 0, 0], folded = [false, false, false, false, false, false];
  c.preflopHistory.forEach((item, index) => {
    const m = object(item, "seat kind amountBb");
    ensure(seats.includes(m.seat as MultiwaySeat) && finite(m.amountBb, 0));
    const seat = seats.indexOf(m.seat as MultiwaySeat);
    ensure(!folded[seat]);
    if (index < 2) ensure(m.kind === (index === 0 ? "POST_SMALL_BLIND" : "POST_BIG_BLIND") && m.seat === (index === 0 ? "SB" : "BB") && m.amountBb > 0);
    else ensure(["FOLD", "CHECK", "CALL", "RAISE_TO"].includes(String(m.kind)));
    const maximum = Math.max(...committed.filter((_p, i) => !folded[i]));
    if (m.kind === "FOLD") { ensure(m.amountBb === 0); folded[seat] = true; }
    else if (m.kind === "CHECK") ensure(m.amountBb === 0 && committed[seat] === maximum);
    else {
      ensure(m.amountBb >= committed[seat] && m.amountBb <= stack);
      if (m.kind === "CALL") ensure(m.amountBb === maximum);
      if (m.kind === "RAISE_TO") ensure(m.amountBb > maximum);
      committed[seat] = m.amountBb;
    }
  });
  ensure(!folded[3] && !folded[5] && committed[3] === committed[5] &&
    (c.betSizeBb as number) <= stack - committed[3] && (c.betSizeBb as number) <= stack - committed[5]);
  ensure(c.publicPreflopHistory === "|BB:call" && Array.isArray(c.flopHistory) && c.flopHistory.length === 1);
  const checked = object(c.flopHistory[0], "seat action amountBb");
  ensure(checked.seat === "BB" && checked.action === "check" && checked.amountBb === 0);
  ensure(players.every((p, i) => (p.status === "FOLDED") === folded[i] && p.committedBb === committed[i]));
  return value as BtnFlopIntervalQuestion;
}
export function parseBtnFlopIntervalFeedback(value: unknown, question: BtnFlopIntervalQuestion, action: BtnFlopAction): BtnFlopIntervalFeedback {
  const f = object(value, "questionIndex reportHash publicationStatus trainerAdmission evScope maximumLossBb selected alternatives");
  identity(f);
  ensure(f.reportHash === question.reportHash && f.questionIndex === question.questionIndex && f.maximumLossBb === question.maximumLossBb && f.evScope === btnFlopIntervalEvScope &&
    Array.isArray(f.alternatives) && f.alternatives.length === question.context.legalActions.length);
  f.alternatives.forEach((entry, index) => {
    const m = object(entry, "action lowerEvBb upperEvBb lowerLossBb upperLossBb classification");
    ensure(m.action === question.context.legalActions[index] && finite(m.lowerEvBb) && finite(m.upperEvBb) && m.lowerEvBb <= m.upperEvBb &&
      finite(m.lowerLossBb, 0) && finite(m.upperLossBb, 0) && m.lowerLossBb <= m.upperLossBb);
    ensure(m.classification === (m.upperLossBb <= question.maximumLossBb ? "WITHIN_LIMIT" : m.lowerLossBb > question.maximumLossBb ? "OUTSIDE_LIMIT" : "STRATEGY_DEPENDENT"));
  });
  ensure(question.context.legalActions.includes(action) && intervalEqual(f.selected, f.alternatives.find((m) => (m as BtnFlopIntervalMove).action === action)));
  return value as BtnFlopIntervalFeedback;
}
export function checkBtnFlopInventory(questions: BtnFlopIntervalQuestion[], metadata: BtnFlopIntervalMetadata) {
  ensure(questions.length === metadata.availableQuestions);
  const first = questions[0];
  questions.forEach((q, i) => {
    parseBtnFlopIntervalQuestion(q, metadata, i);
    ensure(q.maximumLossBb === first.maximumLossBb && intervalEqual(q.context, first.context));
  });
  ensure(new Set(questions.map(q => q.informationSet)).size === 4);
}
export function parseBtnFlopIntervalReview(value: unknown, metadata: BtnFlopIntervalMetadata,
  questions: BtnFlopIntervalQuestion[], attempts: BtnFlopIntervalAttempt[]): BtnFlopIntervalReview {
  const r = object(value, "schemaVersion reportHash selection questions attempts counts");
  ensure(r.schemaVersion === btnFlopIntervalSchema && r.reportHash === metadata.reportHash && r.selection === btnFlopIntervalSelection &&
    Array.isArray(r.questions) && intervalEqual(r.questions, questions) && Array.isArray(r.attempts) &&
    r.attempts.length === attempts.length && attempts.length <= metadata.maximumAttempts);
  checkBtnFlopInventory(r.questions as BtnFlopIntervalQuestion[], metadata);
  const counts = { distinctQuestionsAttempted: new Set(attempts.map(a => a.questionIndex)).size, withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index questionIndex action feedback"), expected = attempts[index];
    ensure(a.index === index && a.questionIndex === expected.questionIndex && a.action === expected.action && Number.isInteger(a.questionIndex));
    const feedback = parseBtnFlopIntervalFeedback(a.feedback, questions[expected.questionIndex], expected.action);
    ensure(intervalEqual(feedback, expected.feedback));
    counts[feedback.selected.classification === "WITHIN_LIMIT" ? "withinLimit" : feedback.selected.classification === "OUTSIDE_LIMIT" ? "outsideLimit" : "strategyDependent"]++;
  });
  ensure(intervalEqual(r.counts, counts));
  return value as BtnFlopIntervalReview;
}
export async function btnFlopIntervalRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/btn-flop-interval${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new BtnFlopIntervalRequestError(response.status);
  try { return await response.json(); } catch { throw new BtnFlopIntervalProtocolError(); }
}

export async function btnFlopReviewDocument(signal: AbortSignal, metadata: BtnFlopIntervalMetadata,
  questions: BtnFlopIntervalQuestion[], attempts: BtnFlopIntervalAttempt[]): Promise<string> {
  const response = await fetch("/api/v1/trainer/research/btn-flop-interval/review/export", {
    signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reportHash: metadata.reportHash, choices: attempts.map(({ questionIndex, action }) => ({ questionIndex, action })) }),
  });
  if (!response.ok) throw new BtnFlopIntervalRequestError(response.status);
  const text = await response.text();
  ensure(new TextEncoder().encode(text).length <= 256 * 1024);
  let value: unknown;
  try { value = JSON.parse(text); } catch { throw new BtnFlopIntervalProtocolError(); }
  parseBtnFlopIntervalReview(value, metadata, questions, attempts);
  return text;
}
