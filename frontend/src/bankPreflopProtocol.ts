import type { MultiwaySeat } from "./multiwayApi";

export const rootMaximumAttempts = 128;
export type RootAction = "fold" | "call" | "raise:9.0";
export type RootMove = { action: RootAction; lowerEvBb: number; upperEvBb: number; lowerLossBb: number; upperLossBb: number;
  classification: "WITHIN_LIMIT" | "OUTSIDE_LIMIT" | "STRATEGY_DEPENDENT" };
export type RootMetadata = {
  schemaVersion: string; studyHash: string; publicationStatus: string; trainerAdmission: boolean;
  model: string; evScope: string; tableSeats: number; activePlayers: number; availableQuestions: number;
  cachedLegalGrades: number; maximumAttempts: number; startupBudget: Record<string, number>;
};
export type RootQuestion = { sourceCaseIndex: number; heroCards: string; board: string[]; evScope: string;
  context: { actingSeat: MultiwaySeat; publicHistory: string;
    history: { seat: MultiwaySeat; kind: string; amountBb: number }[];
    potBb: number; toCallBb: number;
    players: { seat: MultiwaySeat; status: string; committedBb: number; remainingStackBb: number }[];
    actions: { action: RootAction; amountBb: number }[] };
  moves: RootMove[] };
export type RootGrade = { studyHash: string; sourceCaseIndex: number; evScope: string; move: RootMove };
export type RootChoice = { sourceCaseIndex: number; action: RootAction };
export type RootAttempt = { ordinal: number; attempt: RootChoice; grade: RootGrade };
export type RootReview = { studyHash: string; attempts: RootAttempt[];
  counts: { distinctQuestions: number; withinLimit: number; outsideLimit: number; strategyDependent: number } };

export class RootProtocolError extends Error {
  constructor() { super("This preflop study returned inconsistent data. Reload the current study to continue."); }
}
export class RootRequestError extends Error {
  constructor(readonly status: number) {
    super(status === 404 ? "The preflop research drill is not enabled on this server."
      : status === 400 ? "This study or choice was rejected. Reload the current study."
        : `The preflop request failed (${status}). Please try again.`);
  }
}
function ensure(value: unknown): asserts value { if (!value) throw new RootProtocolError(); }

/** The public bank is a protocol pin, never a fallback for a failed API request. */
export function rootEqual(left: unknown, right: unknown): boolean {
  if (typeof left !== typeof right) return false;
  if (typeof left === "number") return Number.isFinite(left) && Number.isFinite(right) && left === right;
  if (left === null || right === null || typeof left !== "object") return left === right;
  if (Array.isArray(left)) return Array.isArray(right) && left.length === right.length &&
    Array.from({ length: left.length }, (_, i) => i).every(i => Object.hasOwn(left, i) && Object.hasOwn(right, i) && rootEqual(left[i], right[i]));
  if (Array.isArray(right)) return false;
  const a = left as Record<string, unknown>, b = right as Record<string, unknown>;
  return Object.keys(a).length === Object.keys(b).length && Object.keys(a).every(k => Object.hasOwn(b, k) && rootEqual(a[k], b[k]));
}
function object(value: unknown, keys: string): Record<string, unknown> {
  ensure(value !== null && typeof value === "object" && !Array.isArray(value));
  ensure(Object.keys(value).sort().join(" ") === keys.split(" ").sort().join(" "));
  return value as Record<string, unknown>;
}
function choice(value: unknown): RootChoice {
  const c = object(value, "sourceCaseIndex action");
  ensure(typeof c.sourceCaseIndex === "number" && Number.isInteger(c.sourceCaseIndex) && c.sourceCaseIndex >= 0 && c.sourceCaseIndex < 3 && ["fold", "call", "raise:9.0"].includes(String(c.action)) && typeof c.action === "string");
  return value as RootChoice;
}
/** Each study has its own immutable public protocol pin, route and session namespace.
 * Network failures never fall back to this bank, and grades must come from the server. */
export function createBankClient(config: {
  frozen: { metadata: RootMetadata; questions: RootQuestion[] };
  apiBase: string; route: string; exportPrefix: string; studyLabel?: string; heroHandName?: string;
}) {
  const { frozen, apiBase: rootApiBase, route, exportPrefix } = config;
  const rootStudyHash = frozen.metadata.studyHash;
  function parseRootMetadata(value: unknown): RootMetadata {
    ensure(rootEqual(value, frozen.metadata)); return value as RootMetadata;
  }
  function parseRootQuestion(value: unknown, metadata: RootMetadata): RootQuestion {
    parseRootMetadata(metadata);
    const q = object(value, "sourceCaseIndex heroCards board context evScope moves");
    ensure(typeof q.sourceCaseIndex === "number" && Number.isInteger(q.sourceCaseIndex) && q.sourceCaseIndex >= 0 && q.sourceCaseIndex < 3);
    ensure(rootEqual(value, frozen.questions[q.sourceCaseIndex])); return value as RootQuestion;
  }
  function parseRootGrade(value: unknown, question: RootQuestion, action: RootAction): RootGrade {
    parseRootQuestion(question, frozen.metadata);
    const move = question.moves.find(m => m.action === action);
    ensure(move && rootEqual(value, { studyHash: rootStudyHash, sourceCaseIndex: question.sourceCaseIndex, evScope: question.evScope, move }));
    return value as RootGrade;
  }
  function rootReviewCounts(attempts: RootAttempt[]): RootReview["counts"] {
    const counts = { distinctQuestions: new Set(attempts.map(a => a.attempt.sourceCaseIndex)).size, withinLimit: 0, outsideLimit: 0, strategyDependent: 0 };
    for (const { grade } of attempts) {
      if (grade.move.classification === "WITHIN_LIMIT") counts.withinLimit++;
      else if (grade.move.classification === "OUTSIDE_LIMIT") counts.outsideLimit++;
      else counts.strategyDependent++;
    }
    return counts;
  }
  function parseRootReview(value: unknown, metadata: RootMetadata, question: RootQuestion, expected?: RootChoice[]): RootReview {
    parseRootQuestion(question, metadata);
    const r = object(value, "studyHash attempts counts");
    ensure(r.studyHash === metadata.studyHash && Array.isArray(r.attempts) && r.attempts.length <= rootMaximumAttempts);
    for (let i = 0; i < r.attempts.length; i++) {
      const a = object(r.attempts[i], "ordinal attempt grade"), c = choice(a.attempt);
      ensure(a.ordinal === i); parseRootGrade(a.grade, frozen.questions[c.sourceCaseIndex], c.action);
    }
    const attempts = r.attempts as RootAttempt[];
    ensure(rootEqual(r.counts, rootReviewCounts(attempts)) && (!expected || rootEqual(rootChoices(attempts), expected)));
    return value as RootReview;
  }
  function rootSessionReview(metadata: RootMetadata, question: RootQuestion, attempts: RootAttempt[]): RootReview {
    return parseRootReview({ studyHash: metadata.studyHash, attempts, counts: rootReviewCounts(attempts) }, metadata, question);
  }
  function rootChoices(attempts: RootAttempt[]): RootChoice[] { return attempts.map(a => a.attempt); }
  const rootSessionKey = `pokerlab:${route.slice(1)}:v1:${rootStudyHash}`;

  /** Reject duplicate keys (including escaped aliases) before trusting JSON-derived protocol fields. */
  function strictJson(text: string): unknown {
    ensure(new TextEncoder().encode(text).length <= 1024 * 1024);
    let result: unknown;
    try { result = JSON.parse(text); } catch { throw new RootProtocolError(); }
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
  function readRootReview(text: string, metadata: RootMetadata, question: RootQuestion, expected?: RootChoice[]) {
    return parseRootReview(strictJson(text), metadata, question, expected);
  }
  async function rootRequest(path: string, signal: AbortSignal, body?: object): Promise<unknown> {
    const response = await fetch(rootApiBase + path, { signal, cache: "no-store",
      ...(body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}) });
    if (!response.ok) throw new RootRequestError(response.status);
    return strictJson(await response.text());
  }
  async function rootReviewDocument(signal: AbortSignal, metadata: RootMetadata, question: RootQuestion, attempts: RootAttempt[]) {
    const response = await fetch(rootApiBase + "/review/export", { signal, cache: "no-store", method: "POST",
      headers: { "Content-Type": "application/json" }, body: JSON.stringify({ studyHash: metadata.studyHash, choices: rootChoices(attempts) }) });
    if (!response.ok) throw new RootRequestError(response.status);
    const text = await response.text();
    ensure(text.endsWith("\n") && !text.includes("\r"));
    readRootReview(text, metadata, question, rootChoices(attempts)); return text;
  }
  return { route, exportPrefix, heroHandName: config.heroHandName ?? "pocket nines", studyLabel: config.studyLabel ?? "ORIGINAL RAISE BOUNDS", rootStudyHash, rootApiBase,
    parseRootMetadata, parseRootQuestion, parseRootGrade, rootReviewCounts, parseRootReview, rootSessionReview, rootChoices, rootSessionKey, readRootReview, rootRequest, rootReviewDocument };
}
export type BankClient = ReturnType<typeof createBankClient>;
