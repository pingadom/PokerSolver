import type { PreflopPlayer, PreflopAction } from "./sixMaxPreflopApi";
import type { MultiwaySeat } from "./multiwayApi";

export const intervalStatus = "SELECTED_QUESTION_INTERVAL_DRILL_RESEARCH_ONLY";
export const intervalSelection = "SINGLE_QUALIFIED_SELECTED_QUESTION/v1";
export const intervalModel = "TWO_ACTIVE_SUPPLIED_JOINT_PREFLOP_MANDATORY_CHECKDOWN/v1";
export const intervalBeliefs = "EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1";
export const intervalReach = "UNAVAILABLE_NO_SOURCE_POLICY";
export const intervalSchema = "pokerlab-supplied-interval-drill/v1";
const evScope = "OPTIMAL_HERO_CONTINUATION_FIXED_OPPONENT_INCREMENTAL_DECISION_EV/v1";
const seats: MultiwaySeat[] = ["UTG", "HJ", "CO", "BTN", "SB", "BB"];
export type IntervalMetadata = {
  reportHash: string; publicationStatus: string; trainerAdmission: false;
  selection: string; model: string; beliefs: string; sourceHistoryReachStatus: string;
  availableQuestions: number; activePlayers: number; maximumAttempts: number; maximumLossBb: number;
};
export type IntervalQuestion = Omit<IntervalMetadata, "availableQuestions" | "activePlayers" | "maximumAttempts"> & {
  informationSet: string; hero: MultiwaySeat; heroCards: string;
  history: { seat: MultiwaySeat; action: PreflopAction }[];
  table: { actingSeat: MultiwaySeat; potBb: number; toCallBb: number; players: PreflopPlayer[] };
  legalActions: PreflopAction[]; securitySlackBb: number; minimumReach: number;
};
export type IntervalMove = {
  action: PreflopAction; lowerEvBb: number; upperEvBb: number;
  lowerLossBb: number; upperLossBb: number;
  classification: "WITHIN_LIMIT" | "OUTSIDE_LIMIT" | "STRATEGY_DEPENDENT";
};
export type IntervalFeedback = {
  reportHash: string; publicationStatus: string; trainerAdmission: false;
  evScope: string; maximumLossBb: number; selected: IntervalMove; alternatives: IntervalMove[];
};
export type IntervalAttempt = { index: number; action: PreflopAction; feedback: IntervalFeedback };
export type IntervalReview = {
  schemaVersion: string; question: IntervalQuestion; attempts: IntervalAttempt[];
  counts: { withinLimit: number; outsideLimit: number; strategyDependent: number };
};

export class IntervalProtocolError extends Error {
  constructor() { super("The interval report changed or returned inconsistent data. Load the current report to continue."); }
}
export class IntervalRequestError extends Error {
  constructor(readonly status: number) {
    super(status === 404 ? "The interval research drill is not enabled on this server."
      : status === 400 ? "The server rejected this report or action. Load the current report to continue."
        : `The interval trainer request failed (${status}). Please try again.`);
  }
}
function ensure(condition: unknown): asserts condition {
  if (!condition) throw new IntervalProtocolError();
}
function object(value: unknown, keys: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  const result = value as Record<string, unknown>;
  ensure(Object.keys(result).sort().join(" ") === keys.split(" ").sort().join(" "));
  return result;
}
function finite(value: unknown, minimum = -Infinity): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= minimum;
}
function action(value: unknown): value is PreflopAction {
  return typeof value === "string" && (value === "fold" || value === "call" || value === "check" ||
    /^raise:\d+(?:\.\d+)?$/.test(value) && finite(Number(value.slice(6)), Number.MIN_VALUE));
}
function identity(value: Record<string, unknown>) {
  ensure(typeof value.reportHash === "string" && /^[a-f0-9]{64}$/.test(value.reportHash));
  ensure(value.publicationStatus === intervalStatus && value.trainerAdmission === false);
}
function research(value: Record<string, unknown>) {
  identity(value);
  ensure(value.selection === intervalSelection && value.model === intervalModel &&
    value.beliefs === intervalBeliefs && value.sourceHistoryReachStatus === intervalReach);
  ensure(finite(value.maximumLossBb, 0));
}
/** Stable equality keeps replay comparisons independent of JSON property order. */
export function intervalEqual(left: unknown, right: unknown): boolean {
  const stable = (value: unknown): unknown => Array.isArray(value) ? value.map(stable)
    : value !== null && typeof value === "object" ? Object.fromEntries(
      Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, entry]) => [key, stable(entry)])) : value;
  return JSON.stringify(stable(left)) === JSON.stringify(stable(right));
}
export function parseIntervalMetadata(value: unknown): IntervalMetadata {
  const m = object(value, "reportHash publicationStatus trainerAdmission selection model beliefs sourceHistoryReachStatus availableQuestions activePlayers maximumAttempts maximumLossBb");
  research(m);
  ensure(m.availableQuestions === 1 && m.activePlayers === 2 && m.maximumAttempts === 100);
  return value as IntervalMetadata;
}
export function parseIntervalQuestion(value: unknown, metadata: IntervalMetadata): IntervalQuestion {
  const q = object(value, "reportHash publicationStatus trainerAdmission selection model beliefs sourceHistoryReachStatus informationSet hero heroCards history table legalActions maximumLossBb securitySlackBb minimumReach");
  research(q);
  ensure(q.reportHash === metadata.reportHash && q.maximumLossBb === metadata.maximumLossBb);
  ensure(typeof q.informationSet === "string" && q.informationSet.length > 0 &&
    seats.includes(q.hero as MultiwaySeat) && typeof q.heroCards === "string" &&
    /^[2-9TJQKA][cdhs] [2-9TJQKA][cdhs]$/.test(q.heroCards) && q.heroCards.slice(0, 2) !== q.heroCards.slice(3));
  ensure(finite(q.securitySlackBb, 0) && finite(q.minimumReach, Number.MIN_VALUE) && q.minimumReach <= 1);
  ensure(Array.isArray(q.legalActions) && q.legalActions.length > 0 && q.legalActions.every(action) &&
    new Set(q.legalActions).size === q.legalActions.length);
  ensure(Array.isArray(q.history) && q.history.length > 0);
  const history = q.history.map((item) => {
    const h = object(item, "seat action");
    ensure(seats.includes(h.seat as MultiwaySeat) && action(h.action));
    return h;
  });
  const t = object(q.table, "actingSeat potBb toCallBb players");
  ensure(t.actingSeat === q.hero && finite(t.potBb, 0) && finite(t.toCallBb, 0));
  ensure(Array.isArray(t.players) && t.players.length === 6);
  const players = t.players.map((item, index) => {
    const p = object(item, "seat status committedBb remainingStackBb lastAction");
    ensure(p.seat === seats[index] && ["ACTING", "ACTIVE", "FOLDED", "ALL_IN"].includes(String(p.status)) &&
      finite(p.committedBb, 0) && finite(p.remainingStackBb, 0));
    ensure((p.status === "ACTING") === (p.seat === q.hero));
    const last = history.filter((h) => h.seat === p.seat).at(-1);
    if (p.lastAction === null) ensure(!last);
    else {
      const move = object(p.lastAction, "seat kind amountBb");
      ensure(move.seat === p.seat && finite(move.amountBb, 0));
      const expectedKind = last?.action === "fold" ? "FOLD" : last?.action === "call" ? "CALL"
        : last?.action === "check" ? "CHECK" : last ? "RAISE_TO" : p.seat === "SB" ? "POST_SMALL_BLIND" : "POST_BIG_BLIND";
      ensure(move.kind === expectedKind);
      if (last?.action === "fold" || last?.action === "check") ensure(move.amountBb === 0);
      else if (typeof last?.action === "string" && last.action.startsWith("raise:")) ensure(move.amountBb === Number(last.action.slice(6)));
      else ensure(move.amountBb === p.committedBb);
    }
    ensure((p.status === "FOLDED") === (last?.action === "fold"));
    if (p.status === "ALL_IN") ensure(p.remainingStackBb === 0);
    return p as unknown as PreflopPlayer;
  });
  const hero = players.find((p) => p.seat === q.hero)!;
  const toCallBb = t.toCallBb;
  const active = players.filter((p) => p.status !== "FOLDED");
  ensure(active.length === 2 && Math.abs(players.reduce((sum, p) => sum + p.committedBb, 0) - t.potBb) < 1e-9 &&
    Math.abs(Math.max(...active.map((p) => p.committedBb)) - hero.committedBb - t.toCallBb) < 1e-9);
  ensure(q.legalActions.every((a) => a !== "check" || t.toCallBb === 0));
  ensure(q.legalActions.every((a) => !a.startsWith("raise:") ||
    Number(a.slice(6)) > hero.committedBb + toCallBb && Number(a.slice(6)) <= hero.committedBb + hero.remainingStackBb));
  return value as IntervalQuestion;
}
export function parseIntervalFeedback(value: unknown, question: IntervalQuestion, selectedAction: PreflopAction): IntervalFeedback {
  const f = object(value, "reportHash publicationStatus trainerAdmission evScope maximumLossBb selected alternatives");
  identity(f);
  ensure(f.reportHash === question.reportHash && f.evScope === evScope && f.maximumLossBb === question.maximumLossBb);
  ensure(Array.isArray(f.alternatives) && f.alternatives.length === question.legalActions.length);
  f.alternatives.forEach((entry, index) => {
    const move = object(entry, "action lowerEvBb upperEvBb lowerLossBb upperLossBb classification");
    ensure(move.action === question.legalActions[index] && finite(move.lowerEvBb) && finite(move.upperEvBb) &&
      move.lowerEvBb <= move.upperEvBb && finite(move.lowerLossBb, 0) && finite(move.upperLossBb, 0) && move.lowerLossBb <= move.upperLossBb);
    const expected = move.upperLossBb <= question.maximumLossBb ? "WITHIN_LIMIT"
      : move.lowerLossBb > question.maximumLossBb ? "OUTSIDE_LIMIT" : "STRATEGY_DEPENDENT";
    ensure(move.classification === expected);
  });
  ensure(question.legalActions.includes(selectedAction) &&
    intervalEqual(f.selected, f.alternatives.find((m) => (m as IntervalMove).action === selectedAction)));
  return value as IntervalFeedback;
}
export function parseIntervalReview(value: unknown, metadata: IntervalMetadata, question: IntervalQuestion, attempts: IntervalAttempt[]): IntervalReview {
  const r = object(value, "schemaVersion question attempts counts");
  ensure(r.schemaVersion === intervalSchema && intervalEqual(parseIntervalQuestion(r.question, metadata), question));
  ensure(Array.isArray(r.attempts) && r.attempts.length === attempts.length && attempts.length <= metadata.maximumAttempts);
  const counts = { withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  r.attempts.forEach((entry, index) => {
    const a = object(entry, "index action feedback");
    ensure(a.index === index && a.action === attempts[index].action);
    const f = parseIntervalFeedback(a.feedback, question, attempts[index].action);
    ensure(intervalEqual(f, attempts[index].feedback));
    counts[f.selected.classification === "WITHIN_LIMIT" ? "withinLimit" : f.selected.classification === "OUTSIDE_LIMIT" ? "outsideLimit" : "strategyDependent"]++;
  });
  ensure(intervalEqual(r.counts, counts));
  return value as IntervalReview;
}
export async function intervalRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(`/api/v1/trainer/research/interval${path}`, {
    signal, cache: "no-store", ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) throw new IntervalRequestError(response.status);
  try { return await response.json(); }
  catch { throw new IntervalProtocolError(); }
}
