import { afterEach, describe, expect, it, vi } from "vitest";
import legacySaved from "./test-data/root-preflop-drill.json";
import minimumSaved from "./test-data/minimum-preflop-drill.json";
import { rootFixtureReview as legacyReview, rootGrades as legacyGrades, rootMetadata as legacyMetadata, rootQuestion as legacyQuestion } from "./test-data/rootPreflop";
import { minimumFixtureReview, minimumGrades, minimumMetadata, minimumQuestion } from "./test-data/minimumPreflop";
import { legacyRootClient } from "./rootPreflopApi";
import { minimumPreflopClient } from "./minimumPreflopApi";
import { rootEqual, RootProtocolError, RootRequestError, type RootAction, type RootQuestion } from "./rootPreflopProtocol";

import { sequencePreflopClient } from "./sequencePreflopApi";
import { sequenceFixtureReview, sequenceGrades, sequenceMetadata, sequenceQuestion } from "./test-data/sequencePreflop";
import sequenceSaved from "./test-data/sequence-preflop-drill.json";

describe.each([
  { name: "original envelope", client: legacyRootClient, saved: legacySaved, metadata: legacyMetadata, question: legacyQuestion, rootGrades: legacyGrades, rootFixtureReview: legacyReview },
  { name: "minimum informed", client: minimumPreflopClient, saved: minimumSaved, metadata: minimumMetadata, question: minimumQuestion, rootGrades: minimumGrades, rootFixtureReview: minimumFixtureReview },
  { name: "sequence flow", client: sequencePreflopClient, saved: sequenceSaved, metadata: sequenceMetadata, question: sequenceQuestion, rootGrades: sequenceGrades, rootFixtureReview: sequenceFixtureReview },
])("$name protocol", ({ client, saved, metadata, question, rootGrades, rootFixtureReview }) => {
const { parseRootGrade, parseRootMetadata, parseRootQuestion, parseRootReview, readRootReview, rootApiBase,
  rootChoices, rootRequest, rootReviewDocument, rootSessionReview } = client;
const copy = <T,>(value: T): T => structuredClone(value);
const response = (value: unknown, status = 200) => Promise.resolve({ ok: status < 400, status,
  text: async () => typeof value === "string" ? value : JSON.stringify(value) } as Response);
afterEach(() => vi.restoreAllMocks());
it("accepts the captured metadata, public empty-board question, three grades and ordered review", () => {
  expect(parseRootMetadata(metadata)).toEqual(metadata);
  expect(parseRootQuestion(question, metadata)).toEqual(question);
  for (const grade of rootGrades) expect(parseRootGrade(grade, question, grade.move.action)).toEqual(grade);
  expect(parseRootReview(saved.review, metadata, question)).toEqual(saved.review);
  expect(question.board).toEqual([]);
  expect(question.moves.map(m => m.classification)).toEqual(["OUTSIDE_LIMIT", "WITHIN_LIMIT", "STRATEGY_DEPENDENT"]);
});
it.each(["studyHash", "evScope", "trainerAdmission", "publicationStatus", "activePlayers", "tableSeats", "availableQuestions", "maximumAttempts", "cachedLegalGrades", "startupBudget"])("rejects altered metadata %s", key => {
  expect(() => parseRootMetadata({ ...metadata, [key]: "changed" })).toThrow(RootProtocolError);
});
const corruptions: [string, (q: RootQuestion) => void][] = [
  ["future board", q => { q.board.push("2s"); }], ["hero", q => { q.heroCards = "Ah Kh"; }],
  ["case", q => { q.sourceCaseIndex = 1; }], ["actor", q => { q.context.actingSeat = "BTN"; }],
  ["history", q => { q.context.history.pop(); }], ["pot", q => { q.context.potBb += 1; }],
  ["call cost", q => { q.context.toCallBb += 1; }], ["raise cost", q => { q.context.actions[2].amountBb = 9; }],
  ["missing raise", q => { q.moves.pop(); }], ["EV scope", q => { q.evScope = "TERMINAL_EV"; }],
  ["double commitment", q => { q.moves[1].lowerEvBb += 1; }], ["loss", q => { q.moves[0].lowerLossBb = -1; }],
  ["unresolved verdict", q => { q.moves[2].classification = "WITHIN_LIMIT"; }],
  ["NaN", q => { q.moves[0].lowerEvBb = NaN; }], ["infinity", q => { q.moves[2].upperEvBb = Infinity; }],
  ["player status", q => { q.context.players[0].status = "ACTIVE"; }], ["stack", q => { q.context.players[5].remainingStackBb--; }],
];
it.each(corruptions)("rejects altered question: %s", (_, change) => {
  const q = copy(question); change(q); expect(() => parseRootQuestion(q, metadata)).toThrow(RootProtocolError);
});
it("does not let undefined extras, sparse arrays or a private hand vanish in equality", () => {
  expect(rootEqual({ extra: undefined }, {})).toBe(false); expect(rootEqual(NaN, null)).toBe(false);
  expect(rootEqual(Array(3), question.moves)).toBe(false);
  expect(() => parseRootQuestion({ ...question, villainCards: "As Ah" }, metadata)).toThrow(RootProtocolError);
  expect(() => parseRootQuestion({ ...question, hidden: undefined }, metadata)).toThrow(RootProtocolError);
});
it("binds grades to the source pin, current-decision scope and submitted move", () => {
  for (const g of [{ ...rootGrades[0], studyHash: "0".repeat(64) }, { ...rootGrades[0], evScope: "old" },
    { ...rootGrades[0], sourceCaseIndex: 1 }, rootGrades[1]])
    expect(() => parseRootGrade(g, question, "fold")).toThrow(RootProtocolError);
  expect(() => parseRootGrade(rootGrades[2], question, "raise:9" as RootAction)).toThrow(RootProtocolError);
});
it("retains order and repeated choices while counting unresolved separately", () => {
  const choices = ["raise:9.0", "call", "raise:9.0", "fold"].map(action => ({ sourceCaseIndex: 0, action: action as RootAction }));
  const review = rootFixtureReview(choices);
  expect(parseRootReview(review, metadata, question, choices).counts).toEqual({ distinctQuestions: 1, withinLimit: 1, outsideLimit: 1, strategyDependent: 2 });
  expect(rootSessionReview(metadata, question, review.attempts)).toEqual(review);
  expect(() => parseRootReview(review, metadata, question, choices.slice().reverse())).toThrow(RootProtocolError);
  const bad = copy(review); bad.attempts[1].ordinal = 0;
  expect(() => parseRootReview(bad, metadata, question)).toThrow(RootProtocolError);
  const wrongCounts = copy(review); wrongCounts.counts.withinLimit++;
  expect(() => parseRootReview(wrongCounts, metadata, question)).toThrow(RootProtocolError);
});
it("accepts empty and maximum reviews and rejects 129, missing or coerced choices", () => {
  for (const size of [0, 128]) {
    const r = rootFixtureReview(Array.from({ length: size }, () => ({ sourceCaseIndex: 0, action: "raise:9.0" })));
    expect(parseRootReview(r, metadata, question).attempts).toHaveLength(size);
  }
  expect(() => parseRootReview(rootFixtureReview(Array.from({ length: 129 }, () => ({ sourceCaseIndex: 0, action: "call" }))), metadata, question)).toThrow(RootProtocolError);
  const r = rootFixtureReview([{ sourceCaseIndex: 0, action: "call" }]);
  for (const value of [null, { ...r, attempts: Array(1) }, { ...r, attempts: [{ ...r.attempts[0], attempt: { sourceCaseIndex: "0", action: "call" } }] }])
    expect(() => parseRootReview(value, metadata, question)).toThrow(RootProtocolError);
});
it.each(["not JSON", '{"studyHash":"a","studyHash":"b"}', '{"studyHash":"a","study\\u0048ash":"b"}', " ".repeat(1048577)])("rejects malformed, duplicate or oversized review text %#", text => {
  expect(() => readRootReview(text, metadata, question)).toThrow(RootProtocolError);
});
it("uses only no-store research requests and choices-only POST bodies", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => response(metadata));
  const signal = new AbortController().signal;
  await rootRequest("", signal); await rootRequest("/grade", signal, { studyHash: metadata.studyHash, sourceCaseIndex: 0, action: "call" });
  expect(fetch.mock.calls[0]).toEqual([rootApiBase, { signal, cache: "no-store" }]);
  expect(JSON.parse(String(fetch.mock.calls[1][1]?.body))).toEqual({ studyHash: metadata.studyHash, sourceCaseIndex: 0, action: "call" });
});
it.each([400, 404, 503])("preserves HTTP failure %s without a fixture fallback", async status => {
  vi.spyOn(globalThis, "fetch").mockImplementation(() => response({}, status));
  await expect(rootRequest("", new AbortController().signal)).rejects.toEqual(new RootRequestError(status));
});
it("downloads validated server bytes unchanged and refuses non-LF or mismatched exports", async () => {
  const review = rootFixtureReview([{ sourceCaseIndex: 0, action: "raise:9.0" }]);
  const text = JSON.stringify(review, null, 2) + "\n";
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => response(text));
  const signal = new AbortController().signal;
  expect(await rootReviewDocument(signal, metadata, question, review.attempts)).toBe(text);
  expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ studyHash: metadata.studyHash, choices: rootChoices(review.attempts) });
  for (const altered of [text.replaceAll("\n", "\r\n"), text.trimEnd(), JSON.stringify(rootFixtureReview([])) + "\n"]) {
    fetch.mockImplementation(() => response(altered));
    await expect(rootReviewDocument(signal, metadata, question, review.attempts)).rejects.toThrow(RootProtocolError);
  }
});

});
