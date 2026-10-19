import raw from "./test-data/mixed-flop-collection-review.json?raw";
import { afterEach, expect, it, vi } from "vitest";
import { mixedFlopCollectionFixture as fixture, mixedFlopCollectionMetadata as metadata, mixedFlopCollectionFixtureReview } from "./test-data/mixedFlopCollection";
import { IntervalProtocolError } from "./intervalTrainerApi";
import { mixedDecision, checkMixedFlopCollectionInventory, parseMixedFlopCollectionMetadata, parseMixedFlopCollectionQuestion,
  parseMixedFlopCollectionFeedback, parseMixedFlopCollectionReview, mixedFlopCollectionRequest, MixedFlopCollectionRequestError, mixedFlopReviewDocument, mixedReviewChoices,
  type MixedFlopCollectionMetadata, type MixedFlopCollectionQuestion, type MixedFlopCollectionFeedback } from "./mixedFlopCollectionApi";

afterEach(() => vi.restoreAllMocks());
it("accepts all three retained source protocols, all twelve grades and the complete mixed review", () => {
  expect(parseMixedFlopCollectionMetadata(metadata)).toEqual(metadata);
  fixture.questions.forEach((q, i) => expect(parseMixedFlopCollectionQuestion(q, metadata, i)).toEqual(q));
  checkMixedFlopCollectionInventory(fixture.questions, metadata);
  for (const a of fixture.attempts) expect(parseMixedFlopCollectionFeedback(a.feedback, fixture.questions[a.questionIndex], a.action)).toEqual(a.feedback);
  expect(parseMixedFlopCollectionReview(fixture, metadata, fixture.questions, fixture.attempts)).toEqual(fixture);
});
it.each([
  ["admission", (m: MixedFlopCollectionMetadata) => { m.trainerAdmission = true as false; }],
  ["coverage", (m: MixedFlopCollectionMetadata) => { m.availableQuestions = 3; }],
  ["six active", (m: MixedFlopCollectionMetadata) => { m.activePlayers = 6; }],
  ["selection", (m: MixedFlopCollectionMetadata) => { m.selection = "random-full-hands"; }],
  ["order", (m: MixedFlopCollectionMetadata) => { m.questionKinds.reverse(); }],
  ["model", (m: MixedFlopCollectionMetadata) => { m.model = "unrestricted"; }],
  ["chance", (m: MixedFlopCollectionMetadata) => { m.chanceModel = "redraw"; }],
  ["EV", (m: MixedFlopCollectionMetadata) => { m.evScope = "incremental"; }],
  ["reach", (m: MixedFlopCollectionMetadata) => { m.reachUnits = "raw"; }],
  ["beliefs", (m: MixedFlopCollectionMetadata) => { m.beliefs = "independent"; }],
  ["source reach", (m: MixedFlopCollectionMetadata) => { m.sourceHistoryReachStatus = "AVAILABLE"; }],
  ["cap", (m: MixedFlopCollectionMetadata) => { m.maximumAttempts = 1000; }],
] as const)("rejects unsupported collection metadata: %s", (_name, change) => {
  const m = structuredClone(metadata); change(m); expect(() => parseMixedFlopCollectionMetadata(m)).toThrow(IntervalProtocolError);
});
it.each([
  ["outer pin", (q: MixedFlopCollectionQuestion) => { q.collectionHash = "0".repeat(64); }],
  ["index", (q: MixedFlopCollectionQuestion) => { q.index = 1; }],
  ["size", (q: MixedFlopCollectionQuestion) => { q.totalQuestions = 3; }],
  ["typed union", (q: MixedFlopCollectionQuestion) => { q.decision = fixture.questions[1].decision; }],
  ["old scope", (q: MixedFlopCollectionQuestion) => { if (q.decision.initial) q.decision.initial.evScope = "incremental"; }],
  ["colliding cards", (q: MixedFlopCollectionQuestion) => { if (q.decision.initial) q.decision.initial.context.board[0] = "7h"; }],
] as const)("rejects altered typed questions: %s", (_name, change) => {
  const q = structuredClone(fixture.questions[0]); change(q); expect(() => parseMixedFlopCollectionQuestion(q, metadata, 0)).toThrow(IntervalProtocolError);
});
it("rejects hidden fields and ambiguous unions and keeps information-set keys opaque", () => {
  const q = fixture.questions[0];
  for (const value of [null, {}, { ...q, prior: [] }, { ...q, decision: { ...q.decision, later: fixture.questions[1].decision.later } },
    { ...q, decision: { ...q.decision, initial: null } }, { ...q, decision: { ...q.decision, opponentCards: "9s Js" } }])
    expect(() => parseMixedFlopCollectionQuestion(value, metadata, 0)).toThrow(IntervalProtocolError);
  const changed = structuredClone(q); if (changed.decision.initial) changed.decision.initial.informationSet = "opaque";
  expect(parseMixedFlopCollectionQuestion(changed, metadata, 0)).toEqual(changed);
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
    expect(() => checkMixedFlopCollectionInventory(qs, metadata)).toThrow(IntervalProtocolError);
  }
  expect(() => checkMixedFlopCollectionInventory(fixture.questions.slice(0,1), metadata)).toThrow(IntervalProtocolError);
});
it.each([
  ["collection pin", (f: MixedFlopCollectionFeedback) => { f.collectionHash = "0".repeat(64); }],
  ["question index", (f: MixedFlopCollectionFeedback) => { f.questionIndex = 1; }],
  ["kind", (f: MixedFlopCollectionFeedback) => { f.decision = fixture.attempts[1].feedback.decision; }],
  ["EV", (f: MixedFlopCollectionFeedback) => { if (f.decision.initial) f.decision.initial.alternatives[0].upperEvBb = NaN; }],
  ["grade pin", (f: MixedFlopCollectionFeedback) => { if (f.decision.initial) f.decision.initial.reportHash = "0".repeat(64); }],
] as const)("rejects cross-question or contradictory grades: %s", (_name, change) => {
  const f = structuredClone(fixture.attempts[0].feedback); change(f);
  expect(() => parseMixedFlopCollectionFeedback(f, fixture.questions[0], "check")).toThrow(IntervalProtocolError);
});
it("keeps menus separate and rejects client-created grade fields", () => {
  expect(() => parseMixedFlopCollectionFeedback(fixture.attempts[0].feedback, fixture.questions[0], "fold")).toThrow();
  expect(() => parseMixedFlopCollectionFeedback(fixture.attempts[1].feedback, fixture.questions[1], "check")).toThrow();
  expect(() => parseMixedFlopCollectionFeedback({ ...fixture.attempts[0].feedback, evLossBb: 0 }, fixture.questions[0], "check")).toThrow();
});
it("accepts zero/100 explicit attempts and counts repeated contexts once", () => {
  for (const count of [0, 100]) {
    const review = mixedFlopCollectionFixtureReview(Array(count).fill({questionIndex:1,action:"fold"}));
    expect(parseMixedFlopCollectionReview(review, metadata, fixture.questions, review.attempts)).toEqual(review);
    expect(review.counts.distinctQuestionsAttempted).toBe(count ? 1 : 0);
  }
  const oversized = mixedFlopCollectionFixtureReview(Array(101).fill({questionIndex:0,action:"check"}));
  expect(() => parseMixedFlopCollectionReview(oversized, metadata, fixture.questions, oversized.attempts)).toThrow();
});
it("rejects reordered questions, edited counts, earlier grades and attempt identity", () => {
  for (const change of [
    (r: typeof fixture) => { r.questions.reverse(); },
    (r: typeof fixture) => { r.attempts[0].index = 1; },
    (r: typeof fixture) => { r.attempts[0].questionIndex = 1; },
    (r: typeof fixture) => { r.attempts[0].feedback.decision = r.attempts[1].feedback.decision; },
    (r: typeof fixture) => { r.counts.distinctQuestionsAttempted = 1; },
    (r: typeof fixture) => { mixedDecision(r.questions[1]).context.board[0] = "2d"; },
  ]) {
    const r = structuredClone(fixture); change(r);
    expect(() => parseMixedFlopCollectionReview(r, metadata, fixture.questions, fixture.attempts)).toThrow(IntervalProtocolError);
  }
});
it.each([400,404,503])("preserves HTTP %s errors", async status => {
  vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:false,status} as Response);
  await expect(mixedFlopCollectionRequest("",new AbortController().signal)).rejects.toBeInstanceOf(MixedFlopCollectionRequestError);
});
it("sends only collection/question/report/action pins and rejects invalid success JSON", async () => {
  const fetch = vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,json:()=>Promise.reject(new Error("invalid JSON"))} as Response);
  const signal = new AbortController().signal, body = {collectionHash:metadata.collectionHash,questionIndex:0,reportHash:mixedDecision(fixture.questions[0]).reportHash,action:"check"};
  await expect(mixedFlopCollectionRequest("/grade",signal,body)).rejects.toBeInstanceOf(IntervalProtocolError);
  expect(fetch).toHaveBeenCalledWith("/api/v1/trainer/research/flop-interval-collection-v2/grade",{signal,cache:"no-store",method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
});

it("keeps collection and original BTN question indices separate in all four hands", () => {
  for (let i=2;i<6;i++) {
    const q=structuredClone(fixture.questions[i]);
    expect(q.sourceQuestionIndex).toBe(i-2);
    q.sourceQuestionIndex=i;
    expect(()=>parseMixedFlopCollectionQuestion(q,metadata,i)).toThrow(IntervalProtocolError);
    const f=structuredClone(fixture.attempts[i].feedback);
    f.sourceQuestionIndex=i;
    expect(()=>parseMixedFlopCollectionFeedback(f,fixture.questions[i],"check")).toThrow(IntervalProtocolError);
    const inner=structuredClone(fixture.attempts[i].feedback);
    inner.decision.btn!.questionIndex=i;
    expect(()=>parseMixedFlopCollectionFeedback(inner,fixture.questions[i],"check")).toThrow(IntervalProtocolError);
  }
});
it("rejects extra BTN union members and cross-hand contexts, pins and grades",()=>{
  for(const field of ["union","cards","board","pin","key","source"]) {
    const qs=structuredClone(fixture.questions),q=qs[2];
    if(field==="union")Object.assign(q.decision,{initial:fixture.questions[0].decision.initial});
    if(field==="cards")q.decision.btn!.heroCards="Ah Kh";
    if(field==="board")q.decision.btn!.context.board[0]="2d";
    if(field==="pin")q.decision.btn!.reportHash=mixedDecision(qs[0]).reportHash;
    if(field==="key")q.decision.btn!.informationSet=qs[3].decision.btn!.informationSet;
    if(field==="source")q.sourceQuestionIndex=2;
    expect(()=>checkMixedFlopCollectionInventory(qs,metadata)).toThrow(IntervalProtocolError);
  }
});
it("exports the exact canonical server bytes and sends only mixed-index choices",async()=>{
  const fetch=vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,text:()=>Promise.resolve(raw)} as Response);
  const signal=new AbortController().signal;
  expect(await mixedFlopReviewDocument(signal,metadata,fixture.questions,fixture.attempts)).toBe(raw);
  expect(new TextEncoder().encode(raw).length).toBe(40731);
  const body=JSON.parse(String(fetch.mock.calls[0][1]?.body));
  expect(body).toEqual({collectionHash:metadata.collectionHash,choices:fixture.attempts.map(a=>({questionIndex:a.questionIndex,reportHash:mixedDecision(fixture.questions[a.questionIndex]).reportHash,action:a.action}))});
  expect(Object.keys(mixedReviewChoices(fixture.attempts)[2]).sort()).toEqual(["action","questionIndex","reportHash"]);
});
it.each(["bad-json","oversized","forged-count","forged-grade","changed-source"])("rejects invalid canonical exports: %s",async kind=>{
  const changed=structuredClone(fixture);
  if(kind==="forged-count")changed.counts.distinctQuestionsAttempted=2;
  if(kind==="forged-grade")changed.attempts[2].feedback.decision.btn!.alternatives[0].upperEvBb=100;
  if(kind==="changed-source")changed.attempts[2].feedback.sourceQuestionIndex=2;
  const text=kind==="bad-json"?"{":kind==="oversized"?" ".repeat(1024*1024+1):JSON.stringify(changed);
  vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,text:()=>Promise.resolve(text)} as Response);
  await expect(mixedFlopReviewDocument(new AbortController().signal,metadata,fixture.questions,fixture.attempts)).rejects.toBeInstanceOf(IntervalProtocolError);
});
