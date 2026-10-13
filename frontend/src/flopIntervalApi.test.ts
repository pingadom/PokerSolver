import { afterEach, expect, it, vi } from "vitest";
import { flopIntervalFixture as fixture, flopIntervalMetadata as metadata, flopFixtureReview } from "./test-data/flopInterval";
import { FlopIntervalProtocolError, FlopIntervalRequestError, flopIntervalRequest,
  parseFlopIntervalMetadata, parseFlopIntervalQuestion, parseFlopIntervalFeedback, parseFlopIntervalReview,
  type FlopIntervalMetadata, type FlopIntervalQuestion, type FlopIntervalFeedback } from "./flopIntervalApi";
import { intervalRange } from "./intervalTrainerFormatting";

afterEach(() => vi.restoreAllMocks());
const q = fixture.question;
it("accepts the physically replayed flop question, both grades and canonical review", () => {
  expect(parseFlopIntervalMetadata(metadata)).toEqual(metadata);
  expect(parseFlopIntervalQuestion(q, metadata)).toEqual(q);
  for (const a of fixture.attempts) expect(parseFlopIntervalFeedback(a.feedback, q, a.action)).toEqual(a.feedback);
  expect(parseFlopIntervalReview(fixture, metadata, q, fixture.attempts)).toEqual(fixture);
});
it.each([
  ["admission", (m: FlopIntervalMetadata) => { m.trainerAdmission = true as false; }],
  ["six active players", (m: FlopIntervalMetadata) => { m.activePlayers = 6; }],
  ["invented coverage", (m: FlopIntervalMetadata) => { m.availableQuestions = 10; }],
  ["model", (m: FlopIntervalMetadata) => { m.model = "general-cash"; }],
  ["redrawn chance", (m: FlopIntervalMetadata) => { m.chanceModel = "redrawn-private-hands"; }],
  ["incremental EV", (m: FlopIntervalMetadata) => { m.evScope = "incremental"; }],
  ["raw reach", (m: FlopIntervalMetadata) => { m.reachUnits = "raw"; }],
  ["source history", (m: FlopIntervalMetadata) => { m.sourceHistoryReachStatus = "AVAILABLE"; }],
  ["beliefs", (m: FlopIntervalMetadata) => { m.beliefs = "source-policy"; }],
  ["attempt limit", (m: FlopIntervalMetadata) => { m.maximumAttempts = 1000; }],
  ["loss limit", (m: FlopIntervalMetadata) => { m.maximumLossBb = .02; }],
  ["nonfinite", (m: FlopIntervalMetadata) => { m.maximumLossBb = NaN; }],
] as const)("rejects unsupported metadata: %s", (_name, mutate) => {
  const m = structuredClone(metadata); mutate(m);
  expect(() => parseFlopIntervalMetadata(m)).toThrow(FlopIntervalProtocolError);
});
it.each([
  ["stale pin", (v: FlopIntervalQuestion) => { v.reportHash = "a".repeat(64); }],
  ["duplicate hero card", (v: FlopIntervalQuestion) => { v.context.heroCards = "7h 7h"; }],
  ["hero-board collision", (v: FlopIntervalQuestion) => { v.context.board[0] = "7h"; }],
  ["duplicate board", (v: FlopIntervalQuestion) => { v.context.board[1] = "2s"; }],
  ["missing flop card", (v: FlopIntervalQuestion) => { v.context.board.pop(); }],
  ["invalid card", (v: FlopIntervalQuestion) => { v.context.board[0] = "Xs"; }],
  ["wrong actor", (v: FlopIntervalQuestion) => { v.context.hero = "BTN"; }],
  ["third active", (v: FlopIntervalQuestion) => { v.context.players[0].status = "ACTIVE"; }],
  ["missing player", (v: FlopIntervalQuestion) => { v.context.players.pop(); }],
  ["repeated seat", (v: FlopIntervalQuestion) => { v.context.players[0].seat = "BB"; }],
  ["wrong pot", (v: FlopIntervalQuestion) => { v.context.potBb++; }],
  ["facing a bet", (v: FlopIntervalQuestion) => { v.context.toCallBb = 1; }],
  ["over stack bet", (v: FlopIntervalQuestion) => { v.context.betSizeBb = 100; }],
  ["zero bet", (v: FlopIntervalQuestion) => { v.context.betSizeBb = 0; }],
  ["nonfinite stack", (v: FlopIntervalQuestion) => { v.context.players[0].remainingStackBb = Infinity; }],
  ["stack mismatch", (v: FlopIntervalQuestion) => { v.context.players[3].remainingStackBb--; }],
  ["action menu", (v: FlopIntervalQuestion) => { v.context.legalActions.reverse(); }],
  ["missing history", (v: FlopIntervalQuestion) => { v.context.preflopHistory = []; }],
  ["changed raise", (v: FlopIntervalQuestion) => { v.context.preflopHistory[5].amountBb = 4; }],
  ["incorrect fold", (v: FlopIntervalQuestion) => { v.context.preflopHistory[2].amountBb = 1; }],
  ["changed blind", (v: FlopIntervalQuestion) => { v.context.preflopHistory[0].seat = "BB"; }],
  ["acting after fold", (v: FlopIntervalQuestion) => { v.context.preflopHistory.push({ seat: "UTG", kind: "CALL", amountBb: 3 }); }],
  ["different slack", (v: FlopIntervalQuestion) => { v.securitySlackBb = 1e-4; }],
  ["wrong reach floor", (v: FlopIntervalQuestion) => { v.minimumReach = .01; }],
] as const)("rejects inconsistent physical/public context: %s", (_name, mutate) => {
  const v = structuredClone(q); mutate(v);
  expect(() => parseFlopIntervalQuestion(v, metadata)).toThrow(FlopIntervalProtocolError);
});
it("rejects extra hidden information and treats info keys as opaque", () => {
  for (const value of [null, {}, { ...q, opponentCards: "As Ah" }, { ...q, context: { ...q.context, prior: [] } }])
    expect(() => parseFlopIntervalQuestion(value, metadata)).toThrow(FlopIntervalProtocolError);
  expect(parseFlopIntervalQuestion({ ...q, informationSet: "opaque-key" }, metadata).context).toEqual(q.context);
});
it.each([
  ["stale pin", (f: FlopIntervalFeedback) => { f.reportHash = "b".repeat(64); }],
  ["missing alternative", (f: FlopIntervalFeedback) => { f.alternatives.pop(); }],
  ["nonfinite EV", (f: FlopIntervalFeedback) => { f.alternatives[0].upperEvBb = NaN; }],
  ["inverted EV", (f: FlopIntervalFeedback) => { f.alternatives[0].lowerEvBb = 0; }],
  ["negative loss", (f: FlopIntervalFeedback) => { f.alternatives[0].lowerLossBb = -1; }],
  ["inverted loss", (f: FlopIntervalFeedback) => { f.alternatives[1].lowerLossBb = 10; }],
  ["wrong verdict", (f: FlopIntervalFeedback) => { f.alternatives[1].classification = "WITHIN_LIMIT"; }],
  ["wrong selection", (f: FlopIntervalFeedback) => { f.selected = f.alternatives[0]; }],
  ["incremental scope", (f: FlopIntervalFeedback) => { f.evScope = "incremental"; }],
] as const)("rejects contradictory feedback: %s", (_name, mutate) => {
  const f = structuredClone(fixture.attempts[1].feedback); mutate(f);
  expect(() => parseFlopIntervalFeedback(f, q, "bet")).toThrow(FlopIntervalProtocolError);
});
it("keeps negative profit and tiny loss ranges outward and accepts bounded reviews", () => {
  for (const a of fixture.attempts) {
    const m = a.feedback.selected;
    const displayed = intervalRange(m.lowerEvBb, m.upperEvBb).replace(" bb", "").split(" – ").map(Number);
    expect(displayed[0]).toBeLessThanOrEqual(m.lowerEvBb);
    expect(displayed[1]).toBeGreaterThanOrEqual(m.upperEvBb);
  }
  for (const count of [0, 100]) {
    const review = flopFixtureReview(Array(count).fill("check"));
    expect(parseFlopIntervalReview(review, metadata, q, review.attempts)).toEqual(review);
  }
  const oversized = flopFixtureReview(Array(101).fill("check"));
  expect(() => parseFlopIntervalReview(oversized, metadata, q, oversized.attempts)).toThrow(FlopIntervalProtocolError);
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
    expect(() => parseFlopIntervalReview(r, metadata, q, fixture.attempts)).toThrow(FlopIntervalProtocolError);
  }
});
it.each([400, 404, 503])("rejects HTTP %s, preserving disabled and stale-pin errors", async (status) => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: false, status } as Response);
  await expect(flopIntervalRequest("", new AbortController().signal)).rejects.toBeInstanceOf(FlopIntervalRequestError);
});
it("posts only explicit action/pin, disables cache and rejects malformed success JSON", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, json: () => Promise.reject(new Error("bad JSON")) } as Response);
  const signal = new AbortController().signal, body = { reportHash: metadata.reportHash, action: "check" };
  await expect(flopIntervalRequest("/grade", signal, body)).rejects.toBeInstanceOf(FlopIntervalProtocolError);
  expect(fetch).toHaveBeenCalledWith("/api/v1/trainer/research/flop-interval/grade", {
    signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
  fetch.mockRejectedValue(new TypeError("offline"));
  await expect(flopIntervalRequest("", signal)).rejects.toThrow("offline");
});
