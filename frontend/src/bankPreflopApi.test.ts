import { afterEach, describe, expect, it, vi } from "vitest";
import { bankPreflopClient as client, initialBankQueue, nextBankHand } from "./bankPreflopApi";
import { RootProtocolError, RootRequestError, type RootQuestion } from "./bankPreflopProtocol";
import { bankMetadata as metadata, bankQuestions, bankGrades, bankFixtureReview } from "./test-data/bankPreflop";
import frozen from "./test-data/bank-preflop-drill.json";
import { sevensPreflopClient } from "./sevensPreflopApi";
const copy = <T,>(v: T): T => structuredClone(v);
afterEach(() => vi.restoreAllMocks());
describe.each(bankQuestions)("bank hand $sourceCaseIndex", question => {
  it("accepts only its public context and all three server grades", () => {
    expect(client.parseRootMetadata(metadata)).toEqual(metadata);
    expect(client.parseRootQuestion(question, metadata)).toEqual(question);
    for (const grade of bankGrades.filter(g => g.sourceCaseIndex === question.sourceCaseIndex))
      expect(client.parseRootGrade(grade, question, grade.move.action)).toEqual(grade);
  });
  const changes: [string, (q: RootQuestion) => void][] = [
    ["future board", q => q.board.push("Qc")], ["hero", q => { q.heroCards = "As Ah"; }],
    ["index", q => { q.sourceCaseIndex = (q.sourceCaseIndex + 1) % 3; }],
    ["pot", q => { q.context.potBb++; }], ["call", q => { q.context.toCallBb++; }],
    ["raise cost", q => { q.context.actions[2].amountBb++; }], ["history", q => { q.context.history.pop(); }],
    ["missing raise", q => { q.moves.pop(); }], ["extra EV shift", q => { q.moves[1].lowerEvBb++; }],
    ["raise ambiguity", q => { q.moves[2].classification = "WITHIN_LIMIT"; }],
    ["loss", q => { q.moves[0].upperLossBb++; }], ["NaN", q => { q.moves[0].lowerEvBb = NaN; }],
    ["infinity", q => { q.moves[1].upperEvBb = Infinity; }], ["scope", q => { q.evScope = "TERMINAL_PROFIT"; }],
    ["actor", q => { q.context.actingSeat = "BTN"; }], ["status", q => { q.context.players[0].status = "ACTIVE"; }],
  ];
  it.each(changes)("rejects changed %s", (_, change) => {
    const q = copy(question); change(q); expect(() => client.parseRootQuestion(q, metadata)).toThrow(RootProtocolError);
  });
  it("rejects borrowed call values, pins, indices and extra hidden fields", () => {
    const grade = bankGrades.find(g => g.sourceCaseIndex === question.sourceCaseIndex && g.move.action === "call")!;
    const other = bankGrades.find(g => g.sourceCaseIndex !== question.sourceCaseIndex && g.move.action === "call")!;
    for (const g of [other, { ...grade, move: other.move }, { ...grade, studyHash: sevensPreflopClient.rootStudyHash }, { ...grade, evScope: "old" }])
      expect(() => client.parseRootGrade(g, question, "call")).toThrow(RootProtocolError);
    expect(() => client.parseRootQuestion({ ...question, opponentCards: "As Ah" }, metadata)).toThrow(RootProtocolError);
  });
});
it.each(["studyHash", "schemaVersion", "evScope", "trainerAdmission", "publicationStatus", "activePlayers", "tableSeats", "availableQuestions", "cachedLegalGrades", "maximumAttempts", "startupBudget"])("rejects altered metadata %s", field => {
  expect(() => client.parseRootMetadata({ ...metadata, [field]: "wrong" })).toThrow(RootProtocolError);
});
it("retains every hand, repeated choice and distinct count in the captured eleven-attempt review", () => {
  expect(client.parseRootReview(frozen.review, metadata, bankQuestions[2])).toEqual(frozen.review);
  expect(frozen.review.counts).toEqual({ distinctQuestions: 3, withinLimit: 4, outsideLimit: 3, strategyDependent: 4 });
  const choices = [{ sourceCaseIndex: 2, action: "call" as const }, { sourceCaseIndex: 0, action: "raise:9.0" as const }, { sourceCaseIndex: 1, action: "fold" as const }];
  const review = bankFixtureReview(choices);
  expect(client.rootSessionReview(metadata, bankQuestions[0], review.attempts)).toEqual(review);
  expect(() => client.parseRootReview(review, metadata, bankQuestions[0], choices.slice().reverse())).toThrow(RootProtocolError);
});
it.each([0, 128])("validates %s mixed review attempts", count => {
  const review = bankFixtureReview(Array.from({ length: count }, (_, i) => ({ sourceCaseIndex: i % 3, action: "call" })));
  expect(client.parseRootReview(review, metadata, bankQuestions[0]).attempts).toHaveLength(count);
});
it("rejects overflow, wrong ordinals, cross-hand grades and fabricated counts", () => {
  expect(() => client.parseRootReview(bankFixtureReview(Array.from({ length: 129 }, () => ({ sourceCaseIndex: 0, action: "call" }))), metadata, bankQuestions[0])).toThrow(RootProtocolError);
  const review = bankFixtureReview([{ sourceCaseIndex: 2, action: "call" }]);
  for (const change of [
    () => ({ ...review, counts: { ...review.counts, distinctQuestions: 3 } }),
    () => ({ ...review, attempts: [{ ...review.attempts[0], ordinal: 2 }] }),
    () => ({ ...review, attempts: [{ ...review.attempts[0], grade: bankGrades[1] }] }),
    () => ({ ...review, attempts: Array(1) }),
  ]) expect(() => client.parseRootReview(change(), metadata, bankQuestions[0])).toThrow(RootProtocolError);
});
it.each([-1, 3, .5, "0", false, null])("rejects invalid question index %s", index => {
  expect(() => client.parseRootQuestion({ ...bankQuestions[0], sourceCaseIndex: index }, metadata)).toThrow(RootProtocolError);
});
it.each([["duplicate", '{"studyHash":1,"studyHash":2}'], ["escaped duplicate", '{"a":1,"\\u0061":2}'], ["trailing", '{} {}'], ["null", 'null'], ["NaN", 'NaN'], ["byte cap", ' '.repeat(1024 * 1024 + 1)]])("rejects malformed saved JSON %s", (_, text) => {
  expect(() => client.readRootReview(text, metadata, bankQuestions[0])).toThrow(RootProtocolError);
});
it("isolates storage and export names from the older sevens study", () => {
  expect(client.rootSessionKey).not.toBe(sevensPreflopClient.rootSessionKey);
  expect(client.exportPrefix).toBe("pokerlab-root-hand-bank-review");
  expect(() => client.parseRootMetadata({ ...metadata, studyHash: sevensPreflopClient.rootStudyHash })).toThrow(RootProtocolError);
});
it("never substitutes frozen data for an unavailable server", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: false, status: 404 } as Response);
  await expect(client.rootRequest("", new AbortController().signal)).rejects.toBeInstanceOf(RootRequestError);
});
it("produces balanced shuffled rounds without immediate repeats at boundaries", () => {
  for (const random of [() => 0, () => .4, () => .99]) {
    let current = 0, remaining = initialBankQueue(current, random); const sequence = [current];
    for (let i = 0; i < 20; i++) { const next = nextBankHand(current, remaining, random); expect(next.index).not.toBe(current); current = next.index; remaining = next.remaining; sequence.push(current); }
    for (let i = 0; i + 3 <= sequence.length; i += 3) expect(new Set(sequence.slice(i, i + 3)).size).toBe(3);
  }
});
