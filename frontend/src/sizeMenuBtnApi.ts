import frozen from "./test-data/size-menu-btn-drill.json";
import type { MultiwaySeat } from "./multiwayApi";

export const btnStudyHash = "c55d598cedc094e836d2d4f4f5ca97897721218f9bc557a9830cc75b45fac5bc";
export const btnApiBase = "/api/v1/trainer/research/size-menu-btn-drill";
export const btnMaximumAttempts = 128;
export type BtnAction = "fold" | "call";
export type BtnMove = {
  action: BtnAction; lowerEvBb: number; upperEvBb: number; lowerLossBb: number; upperLossBb: number;
  classification: "WITHIN_LIMIT" | "OUTSIDE_LIMIT" | "STRATEGY_DEPENDENT";
};
export type BtnMetadata = {
  schemaVersion: string; studyHash: string; publicationStatus: string; trainerAdmission: false;
  model: string; evScope: string; tableSeats: number; activePlayers: number;
  availableQuestions: number; cachedLegalGrades: number; maximumAttempts: number;
  startupBudget: { physicalGames: number; maximumParentSolveCalls: number; maximumParentLpSolves: number;
    maximumIntervalLpSolves: number; freshReferenceRuns: number; freshIterations: number };
};
export type BtnQuestion = {
  sourceCaseIndex: number; heroCards: string;
  context: {
    actingSeat: MultiwaySeat; board: string[]; publicPreflopHistory: string;
    preflopHistory: { seat: MultiwaySeat; kind: string; amountBb: number }[];
    flopHistory: { seat: MultiwaySeat; action: string; amountBb: number }[];
    potBb: number; toCallBb: number;
    players: { seat: MultiwaySeat; status: string; committedBb: number; remainingStackBb: number }[];
    actions: { action: BtnAction; amountBb: number }[];
  };
  moves: BtnMove[];
};
export type BtnGrade = { studyHash: string; sourceCaseIndex: number; move: BtnMove };
export type BtnChoice = { sourceCaseIndex: number; action: BtnAction };
export type BtnAttempt = { ordinal: number; attempt: BtnChoice; grade: BtnGrade };
export type BtnReview = { studyHash: string; attempts: BtnAttempt[];
  counts: { distinctQuestions: number; withinLimit: number; outsideLimit: number; strategyDependent: number } };
type Bank = { metadata: BtnMetadata; questions: BtnQuestion[]; grades: BtnGrade[]; review: BtnReview };
const bank = frozen as Bank;

export class BtnProtocolError extends Error {
  constructor() { super("This drill returned inconsistent data. Reload the current study to continue."); }
}
export class BtnRequestError extends Error {
  constructor(readonly status: number) {
    super(status === 404 ? "This BTN sizing drill is not enabled on this server."
      : status === 400 ? "This study or choice was rejected. Reload the current study."
        : `The drill request failed (${status}). Please try again.`);
  }
}
function ensure(value: unknown): asserts value { if (!value) throw new BtnProtocolError(); }

/** Exact structure and finite numbers; unlike stringify equality, undefined and NaN cannot vanish. */
export function btnEqual(left: unknown, right: unknown): boolean {
  if (typeof left !== typeof right) return false;
  if (typeof left === "number") return Number.isFinite(left) && Number.isFinite(right) && left === right;
  if (left === null || right === null || typeof left !== "object") return left === right;
  if (Array.isArray(left)) return Array.isArray(right) && left.length === right.length &&
    Array.from({ length: left.length }, (_, i) => i).every(i => Object.hasOwn(left, i) && Object.hasOwn(right, i) && btnEqual(left[i], right[i]));
  if (Array.isArray(right)) return false;
  const a = left as Record<string, unknown>, b = right as Record<string, unknown>;
  return Object.keys(a).length === Object.keys(b).length && Object.keys(a).every(k => Object.hasOwn(b, k) && btnEqual(a[k], b[k]));
}
function object(value: unknown, keys: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  ensure(Object.keys(value).sort().join(" ") === keys.split(" ").sort().join(" "));
  return value as Record<string, unknown>;
}
function sourceIndex(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 && value < 4;
}
function choice(value: unknown): BtnChoice {
  const c = object(value, "sourceCaseIndex action");
  ensure(sourceIndex(c.sourceCaseIndex) && (c.action === "fold" || c.action === "call"));
  return value as BtnChoice;
}
export function parseBtnMetadata(value: unknown): BtnMetadata {
  ensure(btnEqual(value, bank.metadata)); return value as BtnMetadata;
}
export function parseBtnQuestion(value: unknown, metadata: BtnMetadata, index: number): BtnQuestion {
  parseBtnMetadata(metadata);
  ensure(sourceIndex(index) && btnEqual(value, bank.questions[index])); return value as BtnQuestion;
}
export function checkBtnInventory(questions: BtnQuestion[], metadata: BtnMetadata) {
  ensure(Array.isArray(questions) && questions.length === 4);
  for (let i = 0; i < 4; i++) parseBtnQuestion(questions[i], metadata, i);
}
export function parseBtnGrade(value: unknown, question: BtnQuestion, action: BtnAction): BtnGrade {
  parseBtnQuestion(question, bank.metadata, question.sourceCaseIndex);
  const move = question.moves.find(m => m.action === action);
  ensure(move && btnEqual(value, { studyHash: btnStudyHash, sourceCaseIndex: question.sourceCaseIndex, move }));
  return value as BtnGrade;
}
export function btnReviewCounts(attempts: BtnAttempt[]): BtnReview["counts"] {
  const counts = { distinctQuestions: new Set(attempts.map(a => a.attempt.sourceCaseIndex)).size,
    withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
  for (const { grade } of attempts) {
    if (grade.move.classification === "WITHIN_LIMIT") counts.withinLimit++;
    else if (grade.move.classification === "OUTSIDE_LIMIT") counts.outsideLimit++;
    else counts.strategyDependent++;
  }
  return counts;
}
export function parseBtnReview(value: unknown, metadata: BtnMetadata, questions: BtnQuestion[], expected?: BtnChoice[]): BtnReview {
  checkBtnInventory(questions, metadata);
  const r = object(value, "studyHash attempts counts");
  ensure(r.studyHash === metadata.studyHash && Array.isArray(r.attempts) && r.attempts.length <= btnMaximumAttempts);
  r.attempts.forEach((value, ordinal) => {
    const a = object(value, "ordinal attempt grade");
    const c = choice(a.attempt);
    ensure(a.ordinal === ordinal);
    parseBtnGrade(a.grade, questions[c.sourceCaseIndex], c.action);
  });
  const attempts = r.attempts as BtnAttempt[];
  ensure(btnEqual(r.counts, btnReviewCounts(attempts)) && (!expected || btnEqual(attempts.map(a => a.attempt), expected)));
  return value as BtnReview;
}
export function btnSessionReview(metadata: BtnMetadata, questions: BtnQuestion[], attempts: BtnAttempt[]): BtnReview {
  return parseBtnReview({ studyHash: metadata.studyHash, attempts, counts: btnReviewCounts(attempts) }, metadata, questions);
}
export function btnChoices(attempts: BtnAttempt[]): BtnChoice[] { return attempts.map(a => a.attempt); }
export function btnSessionKey(pin: string) { return `pokerlab:btn-sizing:v1:${pin}`; }

/** Bound text before parsing and reject duplicate keys, including escaped aliases. */
function strictJson(text: string): unknown {
  ensure(new TextEncoder().encode(text).length <= 1024 * 1024);
  let result: unknown;
  try { result = JSON.parse(text); } catch { throw new BtnProtocolError(); }
  const frames: (Set<string> | null)[] = [];
  for (const token of text.matchAll(/"(?:\\[\s\S]|[^"\\])*"|[{}[\]]/g)) {
    const raw = token[0];
    if (raw === "{") frames.push(new Set()); else if (raw === "[") frames.push(null);
    else if (raw === "}" || raw === "]") frames.pop(); else {
      let next = token.index + raw.length; while (/\s/.test(text[next] ?? "")) next++;
      if (text[next] === ":") { const keys = frames.at(-1), key = JSON.parse(raw) as string; ensure(keys && !keys.has(key)); keys.add(key); }
    }
  }
  return result;
}
export function readBtnReview(text: string, metadata: BtnMetadata, questions: BtnQuestion[], expected?: BtnChoice[]) {
  return parseBtnReview(strictJson(text), metadata, questions, expected);
}
export async function btnRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
  const response = await fetch(btnApiBase + path, { signal, cache: "no-store",
    ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}) });
  if (!response.ok) throw new BtnRequestError(response.status);
  return strictJson(await response.text());
}
export async function btnReviewDocument(signal: AbortSignal, metadata: BtnMetadata, questions: BtnQuestion[], attempts: BtnAttempt[]) {
  const response = await fetch(btnApiBase + "/review/export", { signal, cache: "no-store", method: "POST",
    headers: { "Content-Type": "application/json" }, body: JSON.stringify({ studyHash: metadata.studyHash, choices: btnChoices(attempts) }) });
  if (!response.ok) throw new BtnRequestError(response.status);
  const text = await response.text(); readBtnReview(text, metadata, questions, btnChoices(attempts)); return text;
}
