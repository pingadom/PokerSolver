import { afterEach, describe, expect, it, vi } from "vitest";
import { bankRaiseMetadata as metadata, bankRaisePreflopClient as client } from "./bankRaisePreflopApi";
import frozen from "./test-data/bank-raise-preflop-drill.json";
import old from "./test-data/bank-preflop-drill.json";
import { bankPreflopClient, initialBankQueue, nextBankHand } from "./bankPreflopApi";
import { RootProtocolError, RootRequestError, type RootChoice, type RootGrade, type RootQuestion, type RootReview } from "./bankPreflopProtocol";
const questions = frozen.snapshot.questions as RootQuestion[], grades = frozen.grades as RootGrade[];
const copy = <T,>(v: T): T => structuredClone(v);
function review(choices: RootChoice[]): RootReview {
  const attempts = choices.map((attempt, ordinal) => ({ ordinal, attempt, grade: grades.find(g => g.sourceCaseIndex === attempt.sourceCaseIndex && g.move.action === attempt.action)! }));
  return { studyHash: metadata.studyHash, attempts, counts: client.rootReviewCounts(attempts) };
}
afterEach(() => vi.restoreAllMocks());

describe.each(questions)("qualified original source $sourceCaseIndex", q => {
  it("accepts its complete public menu and each cached server grade", () => {
    expect(client.parseRootMetadata(metadata)).toEqual(metadata);
    expect(client.parseRootQuestion(q, metadata)).toEqual(q);
    for (const g of grades.filter(g => g.sourceCaseIndex === q.sourceCaseIndex)) expect(client.parseRootGrade(g, q, g.move.action)).toEqual(g);
  });
  const changes: [string, (q: RootQuestion) => void][] = [
    ["unqualified source 1", q => { q.sourceCaseIndex = 1; }], ["borrowed hero", q => { q.heroCards = "As Ah"; }],
    ["future board", q => { q.board.push("Qc"); }], ["pot", q => { q.context.potBb++; }],
    ["price", q => { q.context.toCallBb++; }], ["missing seat", q => { q.context.players.pop(); }],
    ["missing action", q => { q.moves.pop(); }], ["extra blind shift", q => { q.moves[2].lowerEvBb++; }],
    ["loss floor", q => { q.moves[2].lowerLossBb++; }], ["loss ceiling", q => { q.moves[2].upperLossBb++; }],
    ["raise frequency claim", q => { q.moves[2].classification = "WITHIN_LIMIT"; }],
    ["full action ceiling", q => { q.moves[2].upperEvBb++; }], ["NaN", q => { q.moves[0].lowerEvBb = NaN; }],
    ["infinity", q => { q.moves[0].upperEvBb = Infinity; }], ["EV scope", q => { q.evScope = "TERMINAL"; }],
  ];
  it.each(changes)("rejects changed %s", (_, change) => { const value = copy(q); change(value); expect(() => client.parseRootQuestion(value, metadata)).toThrow(RootProtocolError); });
  it("rejects the old bank menu despite matching original source ID and hero", () => {
    expect(() => client.parseRootQuestion(old.questions.find(x => x.sourceCaseIndex === q.sourceCaseIndex), metadata)).toThrow(RootProtocolError);
    const g = grades.find(g => g.sourceCaseIndex === q.sourceCaseIndex && g.move.action === "raise:9.0")!;
    expect(() => client.parseRootGrade({ ...g, studyHash: old.metadata.studyHash }, q, "raise:9.0")).toThrow(RootProtocolError);
    expect(() => client.parseRootQuestion({ ...q, opponentCards: "As Ah" }, metadata)).toThrow(RootProtocolError);
  });
});
it.each(["schemaVersion", "studyHash", "publicationStatus", "trainerAdmission", "evScope", "availableQuestions", "cachedLegalGrades", "startupBudget", "sourceCaseIndices"])("rejects altered metadata %s", field => {
  expect(() => client.parseRootMetadata({ ...metadata, [field]: "changed" })).toThrow(RootProtocolError);
});
it("keeps sparse question IDs immutable and distinct from array positions", () => {
  expect(client.sourceCaseIndices).toEqual([0, 2]);
  expect(() => (client.sourceCaseIndices as number[]).push(1)).toThrow();
  expect(client.parseRootQuestion(questions[1], metadata).sourceCaseIndex).toBe(2);
  expect(() => client.parseRootQuestion({ ...questions[0], sourceCaseIndex: 1 }, metadata)).toThrow(RootProtocolError);
});
it("preserves mixed repeated attempts, original IDs and independent counts", () => {
  const choices: RootChoice[] = [{ sourceCaseIndex: 2, action: "call" }, { sourceCaseIndex: 0, action: "raise:9.0" }, { sourceCaseIndex: 2, action: "fold" }, { sourceCaseIndex: 2, action: "raise:9.0" }];
  const result = review(choices);
  expect(result.counts).toEqual({ distinctQuestions: 2, withinLimit: 1, outsideLimit: 1, strategyDependent: 2 });
  expect(client.parseRootReview(result, metadata, questions[1], choices)).toEqual(result);
  expect(() => client.parseRootReview(result, metadata, questions[1], choices.slice().reverse())).toThrow(RootProtocolError);
  expect(() => client.parseRootReview({ ...result, counts: { ...result.counts, distinctQuestions: 3 } }, metadata, questions[1])).toThrow(RootProtocolError);
});
it.each([0, 128])("validates %s cached attempts", n => {
  const r = review(Array.from({ length: n }, () => ({ sourceCaseIndex: 2, action: "raise:9.0" })));
  expect(client.rootSessionReview(metadata, questions[0], r.attempts)).toEqual(r);
});
it("rejects overflow, unqualified hand, forged ordinals and cross-hand grades", () => {
  const r = review([{ sourceCaseIndex: 2, action: "call" }]);
  for (const changed of [
    review(Array.from({ length: 129 }, () => ({ sourceCaseIndex: 2, action: "call" }))),
    { ...r, attempts: [{ ...r.attempts[0], ordinal: 4 }] },
    { ...r, attempts: [{ ...r.attempts[0], attempt: { sourceCaseIndex: 1, action: "call" } }] },
    { ...r, attempts: [{ ...r.attempts[0], grade: grades[1] }] },
  ]) expect(() => client.parseRootReview(changed, metadata, questions[0])).toThrow(RootProtocolError);
});
it("isolates sessions, exports and frozen protocols from the original bank", () => {
  expect(client.rootSessionKey).not.toBe(bankPreflopClient.rootSessionKey);
  expect(client.exportPrefix).toBe("pokerlab-bank-raise-feedback-review");
  expect(() => client.parseRootMetadata(old.metadata)).toThrow(RootProtocolError);
  expect(() => client.readRootReview(JSON.stringify(old.review), metadata, questions[0])).toThrow(RootProtocolError);
});
it.each(['{"a":1,"a":2}', '{"a":1,"\\u0061":2}', '{} {}', 'null', 'NaN', ' '.repeat(1024 * 1024 + 1)])("rejects malformed saved wire data %#", text => {
  expect(() => client.readRootReview(text, metadata, questions[0])).toThrow(RootProtocolError);
});
it("never uses the frozen protocol pin as a fallback for failed network requests", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: false, status: 404 } as Response);
  await expect(client.rootRequest("", new AbortController().signal)).rejects.toBeInstanceOf(RootRequestError);
  expect(fetch.mock.calls[0][0]).toBe("/api/v1/trainer/research/bank-raise-feedback-drill");
});
it("posts choices only and validates the matching exported review", async () => {
  const r = review([{ sourceCaseIndex: 2, action: "call" }]); const signal = new AbortController().signal;
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, text: async () => JSON.stringify(r) + "\n" } as Response);
  expect(await client.rootReviewDocument(signal, metadata, questions[1], r.attempts)).toBe(JSON.stringify(r) + "\n");
  expect(JSON.parse(fetch.mock.calls[0][1]!.body as string)).toEqual({ studyHash: metadata.studyHash, choices: [{ sourceCaseIndex: 2, action: "call" }] });
  expect(fetch.mock.calls[0][1]!.cache).toBe("no-store");
});
it("alternates both declared hands across shuffled cycle boundaries without introducing 99", () => {
  for (const random of [() => 0, () => .4, () => .99]) {
    let current = 0, remaining = initialBankQueue(current, random, client.sourceCaseIndices); const sequence = [current];
    for (let i = 0; i < 20; i++) { const next = nextBankHand(current, remaining, random, client.sourceCaseIndices); expect(next.index).not.toBe(current); current = next.index; remaining = next.remaining; sequence.push(current); }
    expect(sequence.every(i => i === 0 || i === 2)).toBe(true);
    for (let i = 0; i + 2 <= sequence.length; i += 2) expect(new Set(sequence.slice(i, i + 2)).size).toBe(2);
  }
});
