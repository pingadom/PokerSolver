import { afterEach, expect, it, vi } from "vitest";
import { intervalFixture as fixture, intervalMetadata as metadata, fixtureReview } from "./test-data/interval";
import {
  IntervalProtocolError, IntervalRequestError, intervalEqual, intervalRequest,
  parseIntervalMetadata, parseIntervalQuestion, parseIntervalFeedback, parseIntervalReview,
  type IntervalQuestion, type IntervalFeedback, type IntervalMetadata,
} from "./intervalTrainerApi";
import { intervalRange } from "./intervalTrainerFormatting";

afterEach(() => vi.restoreAllMocks());
const q = fixture.question;
it("accepts the saved, physically replayed report and all three different verdicts", () => {
  expect(parseIntervalMetadata(metadata)).toEqual(metadata);
  expect(parseIntervalQuestion(q, metadata)).toEqual(q);
  for (const a of fixture.attempts) expect(parseIntervalFeedback(a.feedback, q, a.action)).toEqual(a.feedback);
  expect(parseIntervalReview(fixture, metadata, q, fixture.attempts)).toEqual(fixture);
  expect(intervalEqual({ x: 1, y: 2 }, { y: 2, x: 1 })).toBe(true);
});
it.each([
  ["admission", (m: IntervalMetadata) => { m.trainerAdmission = true as false; }],
  ["coverage", (m: IntervalMetadata) => { m.availableQuestions = 10; }],
  ["six active", (m: IntervalMetadata) => { m.activePlayers = 6; }],
  ["model", (m: IntervalMetadata) => { m.model = "unrestricted"; }],
  ["beliefs", (m: IntervalMetadata) => { m.beliefs = "source-policy"; }],
  ["source reach", (m: IntervalMetadata) => { m.sourceHistoryReachStatus = "AVAILABLE"; }],
  ["attempt cap", (m: IntervalMetadata) => { m.maximumAttempts = 1000; }],
  ["nonfinite limit", (m: IntervalMetadata) => { m.maximumLossBb = NaN; }],
] as const)("rejects unsupported metadata: %s", (_name, mutate) => {
  const m = structuredClone(metadata); mutate(m);
  expect(() => parseIntervalMetadata(m)).toThrow(IntervalProtocolError);
});
it.each([
  ["hash", (v: IntervalQuestion) => { v.reportHash = "a".repeat(64); }],
  ["duplicate cards", (v: IntervalQuestion) => { v.heroCards = "9h 9h"; }],
  ["malformed cards", (v: IntervalQuestion) => { v.heroCards = "Xs Xd"; }],
  ["wrong actor", (v: IntervalQuestion) => { v.table.actingSeat = "BTN"; }],
  ["missing player", (v: IntervalQuestion) => { v.table.players.pop(); }],
  ["repeated seat", (v: IntervalQuestion) => { v.table.players[0].seat = "BB"; }],
  ["third active", (v: IntervalQuestion) => { v.table.players[0].status = "ACTIVE"; }],
  ["bad pot", (v: IntervalQuestion) => { v.table.potBb++; }],
  ["bad call", (v: IntervalQuestion) => { v.table.toCallBb++; }],
  ["nonfinite stack", (v: IntervalQuestion) => { v.table.players[0].remainingStackBb = Infinity; }],
  ["inconsistent raise", (v: IntervalQuestion) => { v.table.players[3].lastAction!.amountBb = 23; }],
  ["duplicate actions", (v: IntervalQuestion) => { v.legalActions.push("call"); }],
  ["illegal check", (v: IntervalQuestion) => { v.legalActions.push("check"); }],
  ["over stack", (v: IntervalQuestion) => { v.legalActions.push("raise:101"); }],
  ["negative raise", (v: IntervalQuestion) => { v.legalActions.push("raise:-1"); }],
  ["loss limit", (v: IntervalQuestion) => { v.maximumLossBb = 1; }],
] as const)("rejects inconsistent public question: %s", (_name, mutate) => {
  const v = structuredClone(q); mutate(v);
  expect(() => parseIntervalQuestion(v, metadata)).toThrow(IntervalProtocolError);
});
it("rejects null, missing fields and hidden private card additions", () => {
  for (const value of [null, {}, { ...q, villainCards: "As Ah" }])
    expect(() => parseIntervalQuestion(value, metadata)).toThrow(IntervalProtocolError);
});
it.each([
  ["stale hash", (f: IntervalFeedback) => { f.reportHash = "b".repeat(64); }],
  ["missing action", (f: IntervalFeedback) => { f.alternatives.pop(); }],
  ["nonfinite EV", (f: IntervalFeedback) => { f.alternatives[1].lowerEvBb = NaN; }],
  ["inverted EV", (f: IntervalFeedback) => { f.alternatives[1].lowerEvBb = 10; }],
  ["negative loss", (f: IntervalFeedback) => { f.alternatives[1].lowerLossBb = -1; }],
  ["inverted loss", (f: IntervalFeedback) => { f.alternatives[1].lowerLossBb = 2; }],
  ["incorrect verdict", (f: IntervalFeedback) => { f.alternatives[2].classification = "WITHIN_LIMIT"; }],
  ["wrong selection", (f: IntervalFeedback) => { f.selected = f.alternatives[2]; }],
  ["changed selected endpoint", (f: IntervalFeedback) => { f.selected.upperEvBb++; }],
  ["wrong scope", (f: IntervalFeedback) => { f.evScope = "total-hand-profit"; }],
] as const)("rejects malformed grade: %s", (_name, mutate) => {
  const f = structuredClone(fixture.attempts[1].feedback); mutate(f);
  expect(() => parseIntervalFeedback(f, q, "call")).toThrow(IntervalProtocolError);
});
it("keeps small losses and tiny positive fold EV visible with outward endpoints", () => {
  const call = fixture.attempts[1].feedback.selected;
  expect(intervalRange(call.lowerLossBb, call.upperLossBb)).toBe("0 – 0.000450001 bb");
  const fold = fixture.attempts[0].feedback.selected;
  expect(intervalRange(fold.lowerEvBb, fold.upperEvBb)).toBe("0 – 1.98952e-13 bb");
  for (const a of fixture.attempts) {
    for (const [lower, upper] of [[a.feedback.selected.lowerEvBb, a.feedback.selected.upperEvBb], [a.feedback.selected.lowerLossBb, a.feedback.selected.upperLossBb]]) {
      const displayed = intervalRange(lower, upper).replace(" bb", "").split(" – ").map(Number);
      expect(displayed[0]).toBeLessThanOrEqual(lower);
      expect(displayed[1]).toBeGreaterThanOrEqual(upper);
    }
  }
});
it("validates empty and bounded 100-attempt canonical reviews", () => {
  for (const count of [0, 100]) {
    const review = fixtureReview(Array(count).fill("call"));
    expect(parseIntervalReview(review, metadata, q, review.attempts)).toEqual(review);
  }
  const oversized = fixtureReview(Array(101).fill("call"));
  expect(() => parseIntervalReview(oversized, metadata, q, oversized.attempts)).toThrow(IntervalProtocolError);
});
it("rounds both signs outwards at decimal boundaries, carry points and finite number extremes", () => {
  for (const value of [0, -0, 0.01, -0.01, 9.9999999999, -9.9999999999, 1e-8, -1e-8,
    Number.MIN_VALUE, -Number.MIN_VALUE, Number.MAX_VALUE, -Number.MAX_VALUE]) {
    const displayed = intervalRange(value, value).replace(" bb", "").split(" – ");
    expect(displayed.join(" ")).not.toMatch(/NaN|Infinity/);
    expect(Number(displayed[0])).toBeLessThanOrEqual(value);
    expect(Number(displayed[1])).toBeGreaterThanOrEqual(value);
  }
});
it("rejects edited review counts, question, attempt order and earlier feedback", () => {
  const mutations = [
    (r: typeof fixture) => { r.counts.withinLimit++; },
    (r: typeof fixture) => { r.question.securitySlackBb *= 2; },
    (r: typeof fixture) => { r.attempts[0].index = 1; },
    (r: typeof fixture) => { r.attempts.reverse(); },
    (r: typeof fixture) => { r.attempts[0].feedback.selected.upperEvBb++; },
  ];
  for (const mutate of mutations) {
    const r = structuredClone(fixture); mutate(r);
    expect(() => parseIntervalReview(r, metadata, q, fixture.attempts)).toThrow(IntervalProtocolError);
  }
});
it.each([400, 404, 503])("handles HTTP %s without trusting an error body", async (status) => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: false, status } as Response);
  await expect(intervalRequest("", new AbortController().signal)).rejects.toMatchObject({ status });
  await expect(intervalRequest("", new AbortController().signal)).rejects.toBeInstanceOf(IntervalRequestError);
});
it("rejects malformed successful JSON and preserves network errors", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, json: () => Promise.reject(new Error("bad json")) } as Response);
  await expect(intervalRequest("", new AbortController().signal)).rejects.toBeInstanceOf(IntervalProtocolError);
  fetch.mockRejectedValue(new TypeError("offline"));
  await expect(intervalRequest("", new AbortController().signal)).rejects.toThrow("offline");
});
