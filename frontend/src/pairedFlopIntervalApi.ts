import type { MultiwaySeat } from "./multiwayApi";
import { intervalEqual } from "./intervalTrainerApi";

export const pairedFlopIntervalStatus = "FOUR_PAIRED_BTN_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY";
export const pairedFlopIntervalSelection = "ALL_FOUR_SUPPLIED_PAIRED_BTN_HANDS_FACING_BET/v1";
export const pairedFlopIntervalModel = "SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1";
export const pairedFlopIntervalChance = "ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1";
export const pairedFlopIntervalEvScope = "ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1";
export const pairedFlopIntervalReachUnits = "COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1";
export const pairedFlopIntervalSchema = "pokerlab-paired-four-hand-flop-drill/v1";
const seats: MultiwaySeat[] = ["UTG", "HJ", "CO", "BTN", "SB", "BB"];
export const pairedFlopHands = ["9s Js", "Ah Kh", "As Qs", "Qh Th"] as const;
export type PairedFlopAction = "fold" | "call";
export type PairedFlopIntervalMetadata = {
  reportHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  model: string; chanceModel: string; evScope: string; reachUnits: string;
  beliefs: string; sourceHistoryReachStatus: string;
  availableQuestions: number; activePlayers: number; maximumAttempts: number;
};
export type FlopPlayer = { seat: MultiwaySeat; status: "ACTING" | "ACTIVE" | "FOLDED" | "ALL_IN"; committedBb: number; remainingStackBb: number };
export type FlopPublicMove = { seat: MultiwaySeat; kind: "POST_SMALL_BLIND" | "POST_BIG_BLIND" | "FOLD" | "CHECK" | "CALL" | "RAISE_TO"; amountBb: number };
export type PairedFlopIntervalQuestion = {
  questionIndex: number;
  reportHash: string; publicationStatus: string; trainerAdmission: false; selection: string;
  informationSet: string; maximumLossBb: number; securitySlackBb: number; minimumReach: number;
  reachUnits: string; evScope: string;
  hero: MultiwaySeat; heroCards: string; model: string; chanceModel: string; beliefs: string; sourceHistoryReachStatus: string;
  context: { actingSeat: MultiwaySeat; board: string[]; publicPreflopHistory: string;
    flopHistory: { seat: MultiwaySeat; action: "check" | "bet"; amountBb: number }[];
    preflopHistory: FlopPublicMove[]; potBb: number; toCallBb: number; betSizeBb: number;
    players: FlopPlayer[]; legalActions: PairedFlopAction[] };
};
export type PairedFlopIntervalMove = { action: PairedFlopAction; lowerEvBb: number; upperEvBb: number;
  lowerLossBb: number; upperLossBb: number; classification: "WITHIN_LIMIT" | "OUTSIDE_LIMIT" | "STRATEGY_DEPENDENT" };
export type PairedFlopIntervalFeedback = { questionIndex: number; reportHash: string; publicationStatus: string; trainerAdmission: false;
  evScope: string; maximumLossBb: number; selected: PairedFlopIntervalMove; alternatives: PairedFlopIntervalMove[] };
export class PairedFlopIntervalProtocolError extends Error {
  constructor() { super("The flop report changed or returned inconsistent data. Load the current report to continue."); }
}
function ensure(condition: unknown): asserts condition { if (!condition) throw new PairedFlopIntervalProtocolError(); }
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
    value.publicationStatus === pairedFlopIntervalStatus && value.trainerAdmission === false);
}
export function parsePairedFlopIntervalMetadata(value: unknown): PairedFlopIntervalMetadata {
  const m = object(value, "reportHash publicationStatus trainerAdmission selection model chanceModel evScope reachUnits beliefs sourceHistoryReachStatus availableQuestions activePlayers maximumAttempts");
  identity(m);
  ensure(m.selection === pairedFlopIntervalSelection && m.model === pairedFlopIntervalModel && m.chanceModel === pairedFlopIntervalChance &&
    m.evScope === pairedFlopIntervalEvScope && m.reachUnits === pairedFlopIntervalReachUnits &&
    m.beliefs === "EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1" && m.sourceHistoryReachStatus === "UNAVAILABLE_NO_SOURCE_POLICY" &&
    m.availableQuestions === 4 && m.activePlayers === 2 && m.maximumAttempts === 100);
  return value as PairedFlopIntervalMetadata;
}
export function parsePairedFlopIntervalQuestion(value: unknown, metadata: PairedFlopIntervalMetadata, index: number): PairedFlopIntervalQuestion {
  const q = object(value, "questionIndex reportHash publicationStatus trainerAdmission selection informationSet hero heroCards model chanceModel beliefs sourceHistoryReachStatus context maximumLossBb securitySlackBb minimumReach reachUnits evScope");
  identity(q);
  ensure(q.reportHash === metadata.reportHash && Number.isInteger(index) && index >= 0 && index < 4 && q.questionIndex === index &&
    q.heroCards === pairedFlopHands[index] && finite(q.maximumLossBb, Number.MIN_VALUE) && q.maximumLossBb <= .01 &&
    q.selection === pairedFlopIntervalSelection && q.reachUnits === pairedFlopIntervalReachUnits && q.evScope === pairedFlopIntervalEvScope &&
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
    hero.remainingStackBb >= (c.toCallBb as number) &&
    players[5].committedBb === hero.committedBb + (c.toCallBb as number) &&
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
  ensure(checked.seat === "BB" && checked.action === "bet" && checked.amountBb === c.betSizeBb);
  committed[5] += c.betSizeBb as number;
  ensure(intervalEqual(c.board, ["8d", "Kc", "Kd"]));
  ensure(players.every((p, i) => (p.status === "FOLDED") === folded[i] && p.committedBb === committed[i]));
  return value as PairedFlopIntervalQuestion;
}
export function parsePairedFlopIntervalFeedback(value: unknown, question: PairedFlopIntervalQuestion, action: PairedFlopAction): PairedFlopIntervalFeedback {
  const f = object(value, "questionIndex reportHash publicationStatus trainerAdmission evScope maximumLossBb selected alternatives");
  identity(f);
  ensure(f.reportHash === question.reportHash && f.questionIndex === question.questionIndex && f.maximumLossBb === question.maximumLossBb && f.evScope === pairedFlopIntervalEvScope &&
    Array.isArray(f.alternatives) && f.alternatives.length === question.context.legalActions.length);
  f.alternatives.forEach((entry, index) => {
    const m = object(entry, "action lowerEvBb upperEvBb lowerLossBb upperLossBb classification");
    ensure(m.action === question.context.legalActions[index] && finite(m.lowerEvBb) && finite(m.upperEvBb) && m.lowerEvBb <= m.upperEvBb &&
      finite(m.lowerLossBb, 0) && finite(m.upperLossBb, 0) && m.lowerLossBb <= m.upperLossBb);
    ensure(m.classification === (m.upperLossBb <= question.maximumLossBb ? "WITHIN_LIMIT" : m.lowerLossBb > question.maximumLossBb ? "OUTSIDE_LIMIT" : "STRATEGY_DEPENDENT"));
  });
  ensure(question.context.legalActions.includes(action) && intervalEqual(f.selected, f.alternatives.find((m) => (m as PairedFlopIntervalMove).action === action)));
  return value as PairedFlopIntervalFeedback;
}
export function checkPairedFlopInventory(questions: PairedFlopIntervalQuestion[], metadata: PairedFlopIntervalMetadata) {
  ensure(questions.length === metadata.availableQuestions);
  const first = questions[0];
  questions.forEach((q, i) => {
    parsePairedFlopIntervalQuestion(q, metadata, i);
    ensure(q.maximumLossBb === first.maximumLossBb && intervalEqual(q.context, first.context));
  });
  ensure(new Set(questions.map(q => q.informationSet)).size === 4);
}
