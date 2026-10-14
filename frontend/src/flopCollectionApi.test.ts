import { afterEach, expect, it, vi } from "vitest";
import { flopCollectionFixture as fixture, flopCollectionMetadata as metadata, flopCollectionFixtureReview } from "./test-data/flopCollection";
import { IntervalProtocolError } from "./intervalTrainerApi";
import { collectionDecision, checkFlopCollectionInventory, parseFlopCollectionMetadata, parseFlopCollectionQuestion,
  parseFlopCollectionFeedback, parseFlopCollectionReview, flopCollectionRequest, FlopCollectionRequestError,
  type FlopCollectionMetadata, type FlopCollectionQuestion, type FlopCollectionFeedback } from "./flopCollectionApi";

afterEach(() => vi.restoreAllMocks());
it("accepts both retained source protocols, all four grades and the complete mixed review", () => {
  expect(parseFlopCollectionMetadata(metadata)).toEqual(metadata);
  fixture.questions.forEach((q, i) => expect(parseFlopCollectionQuestion(q, metadata, i)).toEqual(q));
  checkFlopCollectionInventory(fixture.questions, metadata);
  for (const a of fixture.attempts) expect(parseFlopCollectionFeedback(a.feedback, fixture.questions[a.questionIndex], a.action)).toEqual(a.feedback);
  expect(parseFlopCollectionReview(fixture, metadata, fixture.questions, fixture.attempts)).toEqual(fixture);
});
it.each([
  ["admission", (m: FlopCollectionMetadata) => { m.trainerAdmission = true as false; }],
  ["coverage", (m: FlopCollectionMetadata) => { m.availableQuestions = 3; }],
  ["six active", (m: FlopCollectionMetadata) => { m.activePlayers = 6; }],
  ["selection", (m: FlopCollectionMetadata) => { m.selection = "random-full-hands"; }],
  ["order", (m: FlopCollectionMetadata) => { m.questionKinds.reverse(); }],
  ["model", (m: FlopCollectionMetadata) => { m.model = "unrestricted"; }],
  ["chance", (m: FlopCollectionMetadata) => { m.chanceModel = "redraw"; }],
  ["EV", (m: FlopCollectionMetadata) => { m.evScope = "incremental"; }],
  ["reach", (m: FlopCollectionMetadata) => { m.reachUnits = "raw"; }],
  ["beliefs", (m: FlopCollectionMetadata) => { m.beliefs = "independent"; }],
  ["source reach", (m: FlopCollectionMetadata) => { m.sourceHistoryReachStatus = "AVAILABLE"; }],
  ["cap", (m: FlopCollectionMetadata) => { m.maximumAttempts = 1000; }],
] as const)("rejects unsupported collection metadata: %s", (_name, change) => {
  const m = structuredClone(metadata); change(m); expect(() => parseFlopCollectionMetadata(m)).toThrow(IntervalProtocolError);
});
it.each([
  ["outer pin", (q: FlopCollectionQuestion) => { q.collectionHash = "0".repeat(64); }],
  ["index", (q: FlopCollectionQuestion) => { q.index = 1; }],
  ["size", (q: FlopCollectionQuestion) => { q.totalQuestions = 3; }],
  ["typed union", (q: FlopCollectionQuestion) => { q.decision = fixture.questions[1].decision; }],
  ["old scope", (q: FlopCollectionQuestion) => { if (q.decision.initial) q.decision.initial.evScope = "incremental"; }],
  ["colliding cards", (q: FlopCollectionQuestion) => { if (q.decision.initial) q.decision.initial.context.board[0] = "7h"; }],
] as const)("rejects altered typed questions: %s", (_name, change) => {
  const q = structuredClone(fixture.questions[0]); change(q); expect(() => parseFlopCollectionQuestion(q, metadata, 0)).toThrow(IntervalProtocolError);
});
it("rejects hidden fields and ambiguous unions and keeps information-set keys opaque", () => {
  const q = fixture.questions[0];
  for (const value of [null, {}, { ...q, prior: [] }, { ...q, decision: { ...q.decision, later: fixture.questions[1].decision.later } },
    { ...q, decision: { ...q.decision, initial: null } }, { ...q, decision: { ...q.decision, opponentCards: "9s Js" } }])
    expect(() => parseFlopCollectionQuestion(value, metadata, 0)).toThrow(IntervalProtocolError);
  const changed = structuredClone(q); if (changed.decision.initial) changed.decision.initial.informationSet = "opaque";
  expect(parseFlopCollectionQuestion(changed, metadata, 0)).toEqual(changed);
});
it("validates source-pair cards, board, history, target identity, pins and bet sizing", () => {
  for (const field of ["cards", "board", "history", "pin", "key", "bet"]) {
    const qs = structuredClone(fixture.questions), a = qs[0].decision.initial!, b = qs[1].decision.later!;
    if (field === "cards") a.context.heroCards = "9d 9h";
    if (field === "board") a.context.board[0] = "2d";
    if (field === "history") a.context.publicHistory = "other";
    if (field === "pin") a.reportHash = b.reportHash;
    if (field === "key") a.informationSet = b.informationSet;
    if (field === "bet") a.context.betSizeBb = 4;
    expect(() => checkFlopCollectionInventory(qs, metadata)).toThrow(IntervalProtocolError);
  }
  expect(() => checkFlopCollectionInventory(fixture.questions.slice(0,1), metadata)).toThrow(IntervalProtocolError);
});
it.each([
  ["collection pin", (f: FlopCollectionFeedback) => { f.collectionHash = "0".repeat(64); }],
  ["question index", (f: FlopCollectionFeedback) => { f.questionIndex = 1; }],
  ["kind", (f: FlopCollectionFeedback) => { f.decision = fixture.attempts[1].feedback.decision; }],
  ["EV", (f: FlopCollectionFeedback) => { if (f.decision.initial) f.decision.initial.alternatives[0].upperEvBb = NaN; }],
  ["grade pin", (f: FlopCollectionFeedback) => { if (f.decision.initial) f.decision.initial.reportHash = "0".repeat(64); }],
] as const)("rejects cross-question or contradictory grades: %s", (_name, change) => {
  const f = structuredClone(fixture.attempts[0].feedback); change(f);
  expect(() => parseFlopCollectionFeedback(f, fixture.questions[0], "check")).toThrow(IntervalProtocolError);
});
it("keeps menus separate and rejects client-created grade fields", () => {
  expect(() => parseFlopCollectionFeedback(fixture.attempts[0].feedback, fixture.questions[0], "fold")).toThrow();
  expect(() => parseFlopCollectionFeedback(fixture.attempts[1].feedback, fixture.questions[1], "check")).toThrow();
  expect(() => parseFlopCollectionFeedback({ ...fixture.attempts[0].feedback, evLossBb: 0 }, fixture.questions[0], "check")).toThrow();
});
it("accepts zero/100 explicit attempts and counts repeated contexts once", () => {
  for (const count of [0, 100]) {
    const review = flopCollectionFixtureReview(Array(count).fill({questionIndex:1,action:"fold"}));
    expect(parseFlopCollectionReview(review, metadata, fixture.questions, review.attempts)).toEqual(review);
    expect(review.counts.distinctQuestionsAttempted).toBe(count ? 1 : 0);
  }
  const oversized = flopCollectionFixtureReview(Array(101).fill({questionIndex:0,action:"check"}));
  expect(() => parseFlopCollectionReview(oversized, metadata, fixture.questions, oversized.attempts)).toThrow();
});
it("rejects reordered questions, edited counts, earlier grades and attempt identity", () => {
  for (const change of [
    (r: typeof fixture) => { r.questions.reverse(); },
    (r: typeof fixture) => { r.attempts[0].index = 1; },
    (r: typeof fixture) => { r.attempts[0].questionIndex = 1; },
    (r: typeof fixture) => { r.attempts[0].feedback.decision = r.attempts[1].feedback.decision; },
    (r: typeof fixture) => { r.counts.distinctQuestionsAttempted = 1; },
    (r: typeof fixture) => { collectionDecision(r.questions[1]).context.board[0] = "2d"; },
  ]) {
    const r = structuredClone(fixture); change(r);
    expect(() => parseFlopCollectionReview(r, metadata, fixture.questions, fixture.attempts)).toThrow(IntervalProtocolError);
  }
});
it.each([400,404,503])("preserves HTTP %s errors", async status => {
  vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:false,status} as Response);
  await expect(flopCollectionRequest("",new AbortController().signal)).rejects.toBeInstanceOf(FlopCollectionRequestError);
});
it("sends only collection/question/report/action pins and rejects invalid success JSON", async () => {
  const fetch = vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,json:()=>Promise.reject(new Error("invalid JSON"))} as Response);
  const signal = new AbortController().signal, body = {collectionHash:metadata.collectionHash,questionIndex:0,reportHash:collectionDecision(fixture.questions[0]).reportHash,action:"check"};
  await expect(flopCollectionRequest("/grade",signal,body)).rejects.toBeInstanceOf(IntervalProtocolError);
  expect(fetch).toHaveBeenCalledWith("/api/v1/trainer/research/flop-interval-collection/grade",{signal,cache:"no-store",method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
});
