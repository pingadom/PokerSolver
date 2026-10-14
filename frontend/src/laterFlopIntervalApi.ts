import type { MultiwaySeat } from "./multiwayApi";
import { intervalEqual } from "./intervalTrainerApi";

export const laterFlopIntervalStatus = "LATER_BB_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY";
export const laterFlopIntervalSelection = "SINGLE_QUALIFIED_LATER_BB_FLOP_QUESTION/v1";
export const laterFlopIntervalModel = "SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1";
export const laterFlopIntervalChance = "ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1";
export const laterFlopIntervalEvScope = "ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1";
export const laterFlopIntervalReachUnits = "COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1";
export const laterFlopIntervalSchema = "pokerlab-later-bb-flop-drill/v1";
const seats: MultiwaySeat[] = ["UTG", "HJ", "CO", "BTN", "SB", "BB"];
export type LaterFlopAction = "fold" | "call";
export type LaterFlopIntervalMetadata = {
  reportHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  model: string; chanceModel: string; evScope: string; reachUnits: string;
  beliefs: string; sourceHistoryReachStatus: string;
  availableQuestions: number; activePlayers: number; maximumAttempts: number; maximumLossBb: number;
};
export type FlopPlayer = { seat: MultiwaySeat; status: "ACTING" | "ACTIVE" | "FOLDED" | "ALL_IN"; committedBb: number; remainingStackBb: number };
export type FlopPublicMove = { seat: MultiwaySeat; kind: "POST_SMALL_BLIND" | "POST_BIG_BLIND" | "FOLD" | "CHECK" | "CALL" | "RAISE_TO"; amountBb: number };
export type LaterFlopIntervalQuestion = {
  reportHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  informationSet: string; maximumLossBb: number; securitySlackBb: number; minimumReach: number;
  reachUnits: string; evScope: string;
  hero: MultiwaySeat; heroCards: string; model: string; chanceModel: string; beliefs: string; sourceHistoryReachStatus: string;
  context: { actingSeat: MultiwaySeat; board: string[]; publicPreflopHistory: string;
    flopHistory: { seat: MultiwaySeat; action: "check" | "bet"; amountBb: number }[];
    preflopHistory: FlopPublicMove[]; potBb: number; toCallBb: number; betSizeBb: number;
    players: FlopPlayer[]; legalActions: LaterFlopAction[] };
};
export type LaterFlopIntervalMove = { action: LaterFlopAction; lowerEvBb: number; upperEvBb: number;
  lowerLossBb: number; upperLossBb: number; classification: "WITHIN_LIMIT" | "OUTSIDE_LIMIT" | "STRATEGY_DEPENDENT" };
export type LaterFlopIntervalFeedback = { reportHash: string; publicationStatus: string; trainerAdmission: false;
  evScope: string; maximumLossBb: number; selected: LaterFlopIntervalMove; alternatives: LaterFlopIntervalMove[] };
export type LaterFlopIntervalAttempt = { index: number; action: LaterFlopAction; feedback: LaterFlopIntervalFeedback };
export type LaterFlopIntervalReview = { schemaVersion: string; question: LaterFlopIntervalQuestion; attempts: LaterFlopIntervalAttempt[];
  counts: { withinLimit: number; outsideLimit: number; strategyDependent: number } };

export class LaterFlopIntervalProtocolError extends Error {
  constructor() { super("The flop report changed or returned inconsistent data. Load the current report to continue."); }
}
export class LaterFlopIntervalRequestError extends Error {
  constructor(readonly status: number) {
    super(status === 404 ? "The later flop research drill is not enabled on this server."
      : status === 400 ? "The server rejected this report or action. Load the current report to continue."
        : `The flop trainer request failed (${status}). Please try again.`);
  }
}
function ensure(condition: unknown): asserts condition { if (!condition) throw new LaterFlopIntervalProtocolError(); }
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
    value.publicationStatus === laterFlopIntervalStatus && value.trainerAdmission === false);
  ensure(finite(value.maximumLossBb, Number.MIN_VALUE) && value.maximumLossBb <= .01);
}
export function parseLaterFlopIntervalMetadata(value: unknown): LaterFlopIntervalMetadata {
  const m = object(value, "reportHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus availableQuestions activePlayers maximumAttempts maximumLossBb");
  identity(m);
  ensure(m.selection === laterFlopIntervalSelection && m.model === laterFlopIntervalModel && m.chanceModel === laterFlopIntervalChance &&
    m.evScope === laterFlopIntervalEvScope && m.reachUnits === laterFlopIntervalReachUnits &&
    m.beliefs === "EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1" && m.sourceHistoryReachStatus === "UNAVAILABLE_NO_SOURCE_POLICY" &&
    m.availableQuestions === 1 && m.activePlayers === 2 && m.maximumAttempts === 100);
  return value as LaterFlopIntervalMetadata;
}
export function parseLaterFlopIntervalQuestion(value: unknown, metadata: LaterFlopIntervalMetadata): LaterFlopIntervalQuestion {
  const q = object(value, "reportHash publicationStatus trainerAdmission selection informationSet hero heroCards model chanceModel beliefs sourceHistoryReachStatus context maximumLossBb securitySlackBb minimumReach reachUnits evScope");
  identity(q);
  ensure(q.reportHash === metadata.reportHash && q.maximumLossBb === metadata.maximumLossBb &&
    q.selection === laterFlopIntervalSelection && q.reachUnits === laterFlopIntervalReachUnits && q.evScope === laterFlopIntervalEvScope &&
    q.securitySlackBb === 1e-8 && q.minimumReach === .0001 &&
    typeof q.informationSet === "string" && q.informationSet.length > 0 && q.informationSet.length <= 4096);
  const c = object(q.context, "actingSeat board publicPreflopHistory preflopHistory flopHistory potBb toCallBb betSizeBb players legalActions");
  ensure(q.hero === "BB" && c.actingSeat === q.hero && typeof q.heroCards === "string" &&
    q.model === metadata.model && q.chanceModel === metadata.chanceModel && q.beliefs === metadata.beliefs &&
    q.sourceHistoryReachStatus === metadata.sourceHistoryReachStatus);
  const hole = q.heroCards.split(" ");
  ensure(hole.length === 2 && hole.every(card) && Array.isArray(c.board) && c.board.length === 3 && c.board.every(card) &&
    new Set([...hole, ...c.board]).size === 5);
  ensure(typeof c.publicPreflopHistory === "string" && c.publicPreflopHistory.length > 0 && c.publicPreflopHistory.length <= 4096 &&
    finite(c.potBb, Number.MIN_VALUE) && finite(c.toCallBb, Number.MIN_VALUE) && c.toCallBb === c.betSizeBb && finite(c.betSizeBb, Number.MIN_VALUE) &&
    intervalEqual(c.legalActions, ["fold", "call"]));
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
    hero.remainingStackBb >= c.toCallBb &&
    Math.max(...active.map((p) => p.committedBb)) - hero.committedBb === c.toCallBb &&
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
  ensure(Array.isArray(c.flopHistory) && c.flopHistory.length === 2);
  c.flopHistory.forEach((item, index) => {
    const m = object(item, "seat action amountBb");
    ensure(m.seat === (index === 0 ? "BB" : "BTN") && m.action === (index === 0 ? "check" : "bet") &&
      m.amountBb === (index === 0 ? 0 : c.betSizeBb));
  });
  committed[3] += c.betSizeBb as number;
  ensure(players.every((p, i) => (p.status === "FOLDED") === folded[i] && p.committedBb === committed[i]));
  return value as LaterFlopIntervalQuestion;
}
export function parseLaterFlopIntervalFeedback(value: unknown, question: LaterFlopIntervalQuestion, action: LaterFlopAction): LaterFlopIntervalFeedback {
  const f = object(value, "reportHash publicationStatus trainerAdmission evScope maximumLossBb selected alternatives");
  identity(f);
  ensure(f.reportHash === question.reportHash && f.maximumLossBb === question.maximumLossBb && f.evScope === laterFlopIntervalEvScope &&
    Array.isArray(f.alternatives) && f.alternatives.length === question.context.legalActions.length);
  f.alternatives.forEach((entry, index) => {
    const m = object(entry, "action lowerEvBb upperEvBb lowerLossBb upperLossBb classification");
    ensure(m.action === question.context.legalActions[index] && finite(m.lowerEvBb) && finite(m.upperEvBb) && m.lowerEvBb <= m.upperEvBb &&
      finite(m.lowerLossBb, 0) && finite(m.upperLossBb, 0) && m.lowerLossBb <= m.upperLossBb);
    ensure(m.classification === (m.upperLossBb <= question.maximumLossBb ? "WITHIN_LIMIT" : m.lowerLossBb > question.maximumLossBb ? "OUTSIDE_LIMIT" : "STRATEGY_DEPENDENT"));
  });
  ensure(question.context.legalActions.includes(action) && intervalEqual(f.selected, f.alternatives.find((m) => (m as LaterFlopIntervalMove).action === action)));
  return value as LaterFlopIntervalFeedback;
}
export function parseLaterFlopIntervalReview(value: unknown, metadata: LaterFlopIntervalMetadata, question: LaterFlopIntervalQuestion, attempts: LaterFlopIntervalAttempt[]): LaterFlopIntervalReview {
  const r = object(value, "schemaVersion question attempts counts");
  ensure(r.schemaVersion === laterFlopIntervalSchema && intervalEqual(parseLaterFlopIntervalQuestion(r.question, metadata), question) &&
    Array.isArray(r.attempts) && r.attempts.length === attempts.length && attempts.length <= metadata.maximumAttempts);
  const counts = { withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index action feedback");
    ensure(a.index === index && a.action === attempts[index].action);
    const feedback = parseLaterFlopIntervalFeedback(a.feedback, question, attempts[index].action);
    ensure(intervalEqual(feedback, attempts[index].feedback));
    counts[feedback.selected.classification === "WITHIN_LIMIT" ? "withinLimit" : feedback.selected.classification === "OUTSIDE_LIMIT" ? "outsideLimit" : "strategyDependent"]++;
  });
  ensure(intervalEqual(r.counts, counts));
  return value as LaterFlopIntervalReview;
}
export async function laterFlopIntervalRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/later-flop-interval${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new LaterFlopIntervalRequestError(response.status);
  try { return await response.json(); } catch { throw new LaterFlopIntervalProtocolError(); }
}
