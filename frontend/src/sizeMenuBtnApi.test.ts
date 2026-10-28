import { describe, expect, it, vi } from "vitest";
import saved from "./test-data/size-menu-btn-drill.json";
import { btnApiBase, btnChoices, btnEqual, btnMaximumAttempts, btnRequest, btnReviewCounts, btnReviewDocument, btnSessionKey, btnSessionReview,
  BtnProtocolError, BtnRequestError, checkBtnInventory, parseBtnGrade, parseBtnMetadata, parseBtnQuestion, parseBtnReview, readBtnReview,
  type BtnChoice, type BtnGrade, type BtnMetadata, type BtnQuestion, type BtnReview } from "./sizeMenuBtnApi";

const metadata = saved.metadata as BtnMetadata, questions = saved.questions as BtnQuestion[], review = saved.review as BtnReview;
const choices = btnChoices(review.attempts);
const copy = <T,>(value: T): T => structuredClone(value);
function fromChoices(items: BtnChoice[]) {
  const attempts = items.map((attempt, ordinal) => ({ ordinal, attempt,
    grade: saved.grades.find(g => g.sourceCaseIndex === attempt.sourceCaseIndex && g.move.action === attempt.action)! as BtnGrade }));
  return { studyHash: metadata.studyHash, attempts, counts: btnReviewCounts(attempts) };
}
const response = (value: unknown, status = 200) => Promise.resolve({ ok: status < 400, status,
  text: async () => typeof value === "string" ? value : JSON.stringify(value) } as Response);

it("validates the real HTTP-captured inventory, all eight grades and canonical review", () => {
  expect(parseBtnMetadata(metadata)).toEqual(metadata);
  checkBtnInventory(questions, metadata);
  questions.forEach((q, i) => expect(parseBtnQuestion(q, metadata, i)).toEqual(q));
  saved.grades.forEach(g => expect(parseBtnGrade(g, questions[g.sourceCaseIndex], g.move.action as "fold" | "call")).toEqual(g));
  expect(parseBtnReview(review, metadata, questions, choices)).toEqual(review);
  expect(readBtnReview(JSON.stringify(review), metadata, questions, choices)).toEqual(review);
  expect(review.counts).toEqual({ distinctQuestions: 4, withinLimit: 4, outsideLimit: 4, strategyDependent: 0 });
});

it.each(["studyHash", "trainerAdmission", "activePlayers", "availableQuestions", "maximumAttempts", "cachedLegalGrades", "startupBudget", "model", "evScope"])("rejects changed metadata %s", field => {
  const value = copy(metadata) as unknown as Record<string, unknown>;
  value[field] = field === "startupBudget" ? { ...metadata.startupBudget, freshIterations: 240001 } : "wrong";
  expect(() => parseBtnMetadata(value)).toThrow(BtnProtocolError);
});

const corruptions: [string, (q: BtnQuestion) => void][] = [
  ["index", q => { q.sourceCaseIndex = 1; }], ["cards", q => { q.heroCards = "Ah Kh"; }],
  ["board", q => { q.context.board[0] = "6h"; }], ["price", q => { q.context.toCallBb = 3.25; }],
  ["pot", q => { q.context.potBb = 13; }], ["actor", q => { q.context.actingSeat = "BB"; }],
  ["history", q => { q.context.flopHistory[0].action = "bet:0.5"; }],
  ["stack", q => { q.context.players[5].remainingStackBb = 99; }],
  ["status", q => { q.context.players[0].status = "ACTIVE"; }],
  ["EV", q => { q.moves[1].lowerEvBb += .01; }], ["nonfinite", q => { q.moves[0].lowerEvBb = NaN; }],
  ["infinite", q => { q.moves[1].upperEvBb = Infinity; }], ["loss", q => { q.moves[1].lowerLossBb = -1; }],
  ["grade", q => { q.moves[1].classification = "WITHIN_LIMIT"; }],
];
it.each(corruptions)("rejects altered question %s", (_, change) => {
  const q = copy(questions[0]); change(q);
  expect(() => parseBtnQuestion(q, metadata, 0)).toThrow(BtnProtocolError);
});

it("rejects incomplete, reordered, duplicated and sparse inventories and undefined extras", () => {
  for (const value of [questions.slice(1), [questions[1], questions[0], ...questions.slice(2)], Array(4), questions.map(() => questions[0])])
    expect(() => checkBtnInventory(value as BtnQuestion[], metadata)).toThrow(BtnProtocolError);
  expect(() => parseBtnMetadata({ ...metadata, extra: undefined })).toThrow(BtnProtocolError);
  expect(btnEqual({a: undefined}, {})).toBe(false);
  expect(btnEqual(NaN, null)).toBe(false);
  expect(btnEqual(Array(4), questions)).toBe(false);
  expect(() => parseBtnQuestion(questions[0], metadata, .5)).toThrow(BtnProtocolError);
});

it("binds every grade to both source index and submitted action", () => {
  expect(() => parseBtnGrade(saved.grades[2], questions[0], "fold")).toThrow(BtnProtocolError);
  expect(() => parseBtnGrade(saved.grades[1], questions[0], "fold")).toThrow(BtnProtocolError);
  expect(() => parseBtnGrade({ ...saved.grades[0], studyHash: "0".repeat(64) }, questions[0], "fold")).toThrow(BtnProtocolError);
  expect(() => parseBtnGrade({ ...saved.grades[0], extra: undefined }, questions[0], "fold")).toThrow(BtnProtocolError);
  const value = copy(saved.grades[0]); value.move.lowerEvBb = -999;
  expect(() => parseBtnGrade(value, questions[0], "fold")).toThrow(BtnProtocolError);
});

it.each([0, 1, 128])("retains honest counts for %i ordered repeated attempts", count => {
  const c: BtnChoice[] = Array.from({ length: count }, () => ({ sourceCaseIndex: 0, action: "fold" }));
  const value = fromChoices(c);
  expect(parseBtnReview(value, metadata, questions, c)).toEqual(value);
  expect(value.counts).toEqual({ distinctQuestions: count ? 1 : 0, withinLimit: count, outsideLimit: 0, strategyDependent: 0 });
  expect(btnSessionReview(metadata, questions, value.attempts)).toEqual(value);
});

it("rejects forged grades, attempt order, counts, foreign pins and excess attempts in reviews", () => {
  const bad: BtnReview[] = [];
  let value = copy(review); value.counts.distinctQuestions = 8; bad.push(value);
  value = copy(review); value.attempts[0].ordinal = 1; bad.push(value);
  value = copy(review); value.attempts[0].grade = value.attempts[1].grade; bad.push(value);
  value = copy(review); value.attempts[0].attempt.sourceCaseIndex = 3; bad.push(value);
  value = copy(review); value.studyHash = "0".repeat(64); bad.push(value);
  bad.push(fromChoices(Array.from({ length: btnMaximumAttempts + 1 }, () => ({ sourceCaseIndex: 0, action: "fold" }))));
  bad.forEach(v => expect(() => parseBtnReview(v, metadata, questions)).toThrow(BtnProtocolError));
  expect(() => parseBtnReview(review, metadata, questions, [...choices].reverse())).toThrow(BtnProtocolError);
});

describe("strict saved JSON", () => {
  const text = JSON.stringify(review);
  it.each([
    text + " {}", text.replace('"studyHash":', '"studyHash":"0","studyHash":'),
    text.replace('"sourceCaseIndex":0', '"sourceCaseIndex":0,"sourceCase\\u0049ndex":0'),
    text.replace('"ordinal":0', '"ordinal":"0"'), text.replace('"lowerEvBb":-3.00000001', '"lowerEvBb":null'),
    " ".repeat(1024 * 1024 + 1), "null", "[]",
  ])("rejects malformed, ambiguous, oversized or inconsistent text %#", value => {
    expect(() => readBtnReview(value, metadata, questions)).toThrow(BtnProtocolError);
  });
});

it("sends only pinned choices and validates downloaded review before returning it", async () => {
  const signal = new AbortController().signal;
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => response(review));
  await btnRequest("/review", signal, { studyHash: metadata.studyHash, choices });
  expect(fetch.mock.calls[0][0]).toBe(btnApiBase + "/review");
  expect(fetch.mock.calls[0][1]).toMatchObject({ signal, cache: "no-store", method: "POST" });
  expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({ studyHash: metadata.studyHash, choices });
  expect(await btnReviewDocument(signal, metadata, questions, review.attempts)).toBe(JSON.stringify(review));
  fetch.mockImplementation(() => response(fromChoices([])));
  await expect(btnReviewDocument(signal, metadata, questions, review.attempts)).rejects.toBeInstanceOf(BtnProtocolError);
  expect(btnSessionKey(metadata.studyHash)).toContain(metadata.studyHash);
});

it.each([400, 404, 503])("preserves HTTP failure %i without accepting its body", async status => {
  vi.spyOn(globalThis, "fetch").mockImplementation(() => response({}, status));
  await expect(btnRequest("", new AbortController().signal)).rejects.toEqual(new BtnRequestError(status));
});

it("rejects invalid and duplicate JSON directly from the server", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => response("not json"));
  await expect(btnRequest("", new AbortController().signal)).rejects.toBeInstanceOf(BtnProtocolError);
  fetch.mockImplementation(() => response('{"studyHash":"a","studyHash":"b"}'));
  await expect(btnRequest("", new AbortController().signal)).rejects.toBeInstanceOf(BtnProtocolError);
});
