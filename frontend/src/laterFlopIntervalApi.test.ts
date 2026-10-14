import { afterEach, expect, it, vi } from "vitest";
import { laterFlopIntervalFixture as fixture, laterFlopIntervalMetadata as metadata, laterFlopFixtureReview } from "./test-data/laterFlopInterval";
import { LaterFlopIntervalProtocolError, LaterFlopIntervalRequestError, laterFlopIntervalRequest,
  parseLaterFlopIntervalMetadata, parseLaterFlopIntervalQuestion, parseLaterFlopIntervalFeedback, parseLaterFlopIntervalReview,
  type LaterFlopIntervalMetadata, type LaterFlopIntervalQuestion, type LaterFlopIntervalFeedback } from "./laterFlopIntervalApi";
import { intervalRange } from "./intervalTrainerFormatting";
import { flopIntervalFixture as initialFixture, flopIntervalMetadata as initialMetadata } from "./test-data/flopInterval";
import { parseFlopIntervalQuestion, parseFlopIntervalMetadata } from "./flopIntervalApi";

afterEach(() => vi.restoreAllMocks());
const q = fixture.question;
it("keeps the initial check/bet and later fold/call protocols distinct", () => {
  expect(() => parseLaterFlopIntervalMetadata(initialMetadata)).toThrow();
  expect(() => parseLaterFlopIntervalQuestion(initialFixture.question, metadata)).toThrow();
  expect(() => parseFlopIntervalMetadata(metadata)).toThrow();
  expect(() => parseFlopIntervalQuestion(q, initialMetadata)).toThrow();
  expect(parseFlopIntervalQuestion(initialFixture.question, initialMetadata)).toEqual(initialFixture.question);
});
it("accepts the physically replayed flop question, both grades and canonical review", () => {
  expect(parseLaterFlopIntervalMetadata(metadata)).toEqual(metadata);
  expect(parseLaterFlopIntervalQuestion(q, metadata)).toEqual(q);
  for (const a of fixture.attempts) expect(parseLaterFlopIntervalFeedback(a.feedback, q, a.action)).toEqual(a.feedback);
  expect(parseLaterFlopIntervalReview(fixture, metadata, q, fixture.attempts)).toEqual(fixture);
});
it.each([
  ["admission", (m: LaterFlopIntervalMetadata) => { m.trainerAdmission = true as false; }],
  ["six active players", (m: LaterFlopIntervalMetadata) => { m.activePlayers = 6; }],
  ["invented coverage", (m: LaterFlopIntervalMetadata) => { m.availableQuestions = 10; }],
  ["model", (m: LaterFlopIntervalMetadata) => { m.model = "general-cash"; }],
  ["redrawn chance", (m: LaterFlopIntervalMetadata) => { m.chanceModel = "redrawn-private-hands"; }],
  ["incremental EV", (m: LaterFlopIntervalMetadata) => { m.evScope = "incremental"; }],
  ["raw reach", (m: LaterFlopIntervalMetadata) => { m.reachUnits = "raw"; }],
  ["source history", (m: LaterFlopIntervalMetadata) => { m.sourceHistoryReachStatus = "AVAILABLE"; }],
  ["beliefs", (m: LaterFlopIntervalMetadata) => { m.beliefs = "source-policy"; }],
  ["attempt limit", (m: LaterFlopIntervalMetadata) => { m.maximumAttempts = 1000; }],
  ["loss limit", (m: LaterFlopIntervalMetadata) => { m.maximumLossBb = .02; }],
  ["nonfinite", (m: LaterFlopIntervalMetadata) => { m.maximumLossBb = NaN; }],
] as const)("rejects unsupported metadata: %s", (_name, mutate) => {
  const m = structuredClone(metadata); mutate(m);
  expect(() => parseLaterFlopIntervalMetadata(m)).toThrow(LaterFlopIntervalProtocolError);
});
it.each([
  ["stale pin", (v: LaterFlopIntervalQuestion) => { v.reportHash = "a".repeat(64); }],
  ["duplicate hero card", (v: LaterFlopIntervalQuestion) => { v.heroCards = "7h 7h"; }],
  ["hero-board collision", (v: LaterFlopIntervalQuestion) => { v.context.board[0] = "7h"; }],
  ["duplicate board", (v: LaterFlopIntervalQuestion) => { v.context.board[1] = "2s"; }],
  ["missing flop card", (v: LaterFlopIntervalQuestion) => { v.context.board.pop(); }],
  ["invalid card", (v: LaterFlopIntervalQuestion) => { v.context.board[0] = "Xs"; }],
  ["wrong actor", (v: LaterFlopIntervalQuestion) => { v.hero = "BTN"; }],
  ["wrong acting seat", (v: LaterFlopIntervalQuestion) => { v.context.actingSeat = "BTN"; }],
  ["missing flop history", (v: LaterFlopIntervalQuestion) => { v.context.flopHistory = []; }],
  ["wrong check actor", (v: LaterFlopIntervalQuestion) => { v.context.flopHistory[0].seat = "BTN"; }],
  ["wrong bet amount", (v: LaterFlopIntervalQuestion) => { v.context.flopHistory[1].amountBb = 4; }],
  ["wrong bet action", (v: LaterFlopIntervalQuestion) => { v.context.flopHistory[1].action = "check"; }],
  ["nonzero check", (v: LaterFlopIntervalQuestion) => { v.context.flopHistory[0].amountBb = 1; }],
  ["third flop action", (v: LaterFlopIntervalQuestion) => { v.context.flopHistory.push(v.context.flopHistory[0]); }],
  ["question provenance", (v: LaterFlopIntervalQuestion) => { v.model = "other-game"; }],
  ["third active", (v: LaterFlopIntervalQuestion) => { v.context.players[0].status = "ACTIVE"; }],
  ["missing player", (v: LaterFlopIntervalQuestion) => { v.context.players.pop(); }],
  ["repeated seat", (v: LaterFlopIntervalQuestion) => { v.context.players[0].seat = "BB"; }],
  ["wrong pot", (v: LaterFlopIntervalQuestion) => { v.context.potBb++; }],
  ["facing a bet", (v: LaterFlopIntervalQuestion) => { v.context.toCallBb = 1; }],
  ["over stack bet", (v: LaterFlopIntervalQuestion) => { v.context.betSizeBb = 100; }],
  ["zero bet", (v: LaterFlopIntervalQuestion) => { v.context.betSizeBb = 0; }],
  ["nonfinite stack", (v: LaterFlopIntervalQuestion) => { v.context.players[0].remainingStackBb = Infinity; }],
  ["stack mismatch", (v: LaterFlopIntervalQuestion) => { v.context.players[3].remainingStackBb--; }],
  ["action menu", (v: LaterFlopIntervalQuestion) => { v.context.legalActions.reverse(); }],
  ["missing history", (v: LaterFlopIntervalQuestion) => { v.context.preflopHistory = []; }],
  ["changed raise", (v: LaterFlopIntervalQuestion) => { v.context.preflopHistory[5].amountBb = 4; }],
  ["incorrect fold", (v: LaterFlopIntervalQuestion) => { v.context.preflopHistory[2].amountBb = 1; }],
  ["changed blind", (v: LaterFlopIntervalQuestion) => { v.context.preflopHistory[0].seat = "BB"; }],
  ["acting after fold", (v: LaterFlopIntervalQuestion) => { v.context.preflopHistory.push({ seat: "UTG", kind: "CALL", amountBb: 3 }); }],
  ["different slack", (v: LaterFlopIntervalQuestion) => { v.securitySlackBb = 1e-4; }],
  ["wrong reach floor", (v: LaterFlopIntervalQuestion) => { v.minimumReach = .01; }],
] as const)("rejects inconsistent physical/public context: %s", (_name, mutate) => {
  const v = structuredClone(q); mutate(v);
  expect(() => parseLaterFlopIntervalQuestion(v, metadata)).toThrow(LaterFlopIntervalProtocolError);
});
it("rejects extra hidden information and treats info keys as opaque", () => {
  for (const value of [null, {}, { ...q, opponentCards: "As Ah" }, { ...q, context: { ...q.context, prior: [] } }])
    expect(() => parseLaterFlopIntervalQuestion(value, metadata)).toThrow(LaterFlopIntervalProtocolError);
  expect(parseLaterFlopIntervalQuestion({ ...q, informationSet: "opaque-key" }, metadata).context).toEqual(q.context);
});
it.each([
  ["stale pin", (f: LaterFlopIntervalFeedback) => { f.reportHash = "b".repeat(64); }],
  ["missing alternative", (f: LaterFlopIntervalFeedback) => { f.alternatives.pop(); }],
  ["nonfinite EV", (f: LaterFlopIntervalFeedback) => { f.alternatives[0].upperEvBb = NaN; }],
  ["inverted EV", (f: LaterFlopIntervalFeedback) => { f.alternatives[0].lowerEvBb = 0; }],
  ["negative loss", (f: LaterFlopIntervalFeedback) => { f.alternatives[0].lowerLossBb = -1; }],
  ["inverted loss", (f: LaterFlopIntervalFeedback) => { f.alternatives[1].lowerLossBb = 10; }],
  ["wrong verdict", (f: LaterFlopIntervalFeedback) => { f.alternatives[1].classification = "WITHIN_LIMIT"; }],
  ["wrong selection", (f: LaterFlopIntervalFeedback) => { f.selected = f.alternatives[0]; }],
  ["incremental scope", (f: LaterFlopIntervalFeedback) => { f.evScope = "incremental"; }],
] as const)("rejects contradictory feedback: %s", (_name, mutate) => {
  const f = structuredClone(fixture.attempts[1].feedback); mutate(f);
  expect(() => parseLaterFlopIntervalFeedback(f, q, "call")).toThrow(LaterFlopIntervalProtocolError);
});
it("keeps negative profit and tiny loss ranges outward and accepts bounded reviews", () => {
  for (const a of fixture.attempts) {
    const m = a.feedback.selected;
    const displayed = intervalRange(m.lowerEvBb, m.upperEvBb).replace(" bb", "").split(" – ").map(Number);
    expect(displayed[0]).toBeLessThanOrEqual(m.lowerEvBb);
    expect(displayed[1]).toBeGreaterThanOrEqual(m.upperEvBb);
  }
  for (const count of [0, 100]) {
    const review = laterFlopFixtureReview(Array(count).fill("fold"));
    expect(parseLaterFlopIntervalReview(review, metadata, q, review.attempts)).toEqual(review);
  }
  const oversized = laterFlopFixtureReview(Array(101).fill("fold"));
  expect(() => parseLaterFlopIntervalReview(oversized, metadata, q, oversized.attempts)).toThrow(LaterFlopIntervalProtocolError);
});
it("rejects edited canonical reviews, including public board and earlier grades", () => {
  for (const mutate of [
    (r: typeof fixture) => { r.question.context.board[0] = "2d"; },
    (r: typeof fixture) => { r.attempts[0].index = 1; },
    (r: typeof fixture) => { r.attempts.reverse(); },
    (r: typeof fixture) => { r.attempts[0].feedback.selected.upperEvBb++; },
    (r: typeof fixture) => { r.counts.withinLimit++; },
  ]) {
    const r = structuredClone(fixture); mutate(r);
    expect(() => parseLaterFlopIntervalReview(r, metadata, q, fixture.attempts)).toThrow(LaterFlopIntervalProtocolError);
  }
});
it.each([400, 404, 503])("rejects HTTP %s, preserving disabled and stale-pin errors", async (status) => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: false, status } as Response);
  await expect(laterFlopIntervalRequest("", new AbortController().signal)).rejects.toBeInstanceOf(LaterFlopIntervalRequestError);
});
it("posts only explicit action/pin, disables cache and rejects malformed success JSON", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, json: () => Promise.reject(new Error("bad JSON")) } as Response);
  const signal = new AbortController().signal, body = { reportHash: metadata.reportHash, action: "fold" };
  await expect(laterFlopIntervalRequest("/grade", signal, body)).rejects.toBeInstanceOf(LaterFlopIntervalProtocolError);
  expect(fetch).toHaveBeenCalledWith("/api/v1/trainer/research/later-flop-interval/grade", {
    signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  fetch.mockRejectedValue(new TypeError("offline"));
  await expect(laterFlopIntervalRequest("", signal)).rejects.toThrow("offline");
});
