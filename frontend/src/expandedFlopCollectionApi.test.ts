import raw from "./test-data/expanded-flop-collection-review.json?raw";
import { afterEach, expect, it, vi } from "vitest";
import { expandedFlopCollectionFixture as fixture, expandedFlopCollectionMetadata as metadata, expandedFlopCollectionFixtureReview } from "./test-data/expandedFlopCollection";
import { IntervalProtocolError } from "./intervalTrainerApi";
import { expandedDecision, checkExpandedFlopCollectionInventory, parseExpandedFlopCollectionMetadata, parseExpandedFlopCollectionQuestion,
  parseExpandedFlopCollectionFeedback, parseExpandedFlopCollectionReview, expandedFlopCollectionRequest, ExpandedFlopCollectionRequestError, expandedFlopReviewDocument, expandedReviewChoices,
  type ExpandedFlopCollectionMetadata, type ExpandedFlopCollectionQuestion, type ExpandedFlopCollectionFeedback } from "./expandedFlopCollectionApi";

afterEach(() => vi.restoreAllMocks());
it("accepts all four retained source protocols, all twenty grades and the complete mixed review", () => {
  expect(parseExpandedFlopCollectionMetadata(metadata)).toEqual(metadata);
  fixture.questions.forEach((q, i) => expect(parseExpandedFlopCollectionQuestion(q, metadata, i)).toEqual(q));
  checkExpandedFlopCollectionInventory(fixture.questions, metadata);
  for (const a of fixture.attempts) expect(parseExpandedFlopCollectionFeedback(a.feedback, fixture.questions[a.questionIndex], a.action)).toEqual(a.feedback);
  expect(parseExpandedFlopCollectionReview(fixture, metadata, fixture.questions, fixture.attempts)).toEqual(fixture);
});
it.each([
  ["admission", (m: ExpandedFlopCollectionMetadata) => { m.trainerAdmission = true as false; }],
  ["coverage", (m: ExpandedFlopCollectionMetadata) => { m.availableQuestions = 3; }],
  ["six active", (m: ExpandedFlopCollectionMetadata) => { m.activePlayers = 6; }],
  ["selection", (m: ExpandedFlopCollectionMetadata) => { m.selection = "random-full-hands"; }],
  ["order", (m: ExpandedFlopCollectionMetadata) => { m.questionKinds.reverse(); }],
  ["model", (m: ExpandedFlopCollectionMetadata) => { m.model = "unrestricted"; }],
  ["chance", (m: ExpandedFlopCollectionMetadata) => { m.chanceModel = "redraw"; }],
  ["EV", (m: ExpandedFlopCollectionMetadata) => { m.evScope = "incremental"; }],
  ["reach", (m: ExpandedFlopCollectionMetadata) => { m.reachUnits = "raw"; }],
  ["beliefs", (m: ExpandedFlopCollectionMetadata) => { m.beliefs = "independent"; }],
  ["source reach", (m: ExpandedFlopCollectionMetadata) => { m.sourceHistoryReachStatus = "AVAILABLE"; }],
  ["cap", (m: ExpandedFlopCollectionMetadata) => { m.maximumAttempts = 1000; }],
] as const)("rejects unsupported collection metadata: %s", (_name, change) => {
  const m = structuredClone(metadata); change(m); expect(() => parseExpandedFlopCollectionMetadata(m)).toThrow(IntervalProtocolError);
});
it.each([
  ["outer pin", (q: ExpandedFlopCollectionQuestion) => { q.collectionHash = "0".repeat(64); }],
  ["index", (q: ExpandedFlopCollectionQuestion) => { q.index = 1; }],
  ["size", (q: ExpandedFlopCollectionQuestion) => { q.totalQuestions = 3; }],
  ["typed union", (q: ExpandedFlopCollectionQuestion) => { q.decision = fixture.questions[1].decision; }],
  ["old scope", (q: ExpandedFlopCollectionQuestion) => { if (q.decision.initial) q.decision.initial.evScope = "incremental"; }],
  ["colliding cards", (q: ExpandedFlopCollectionQuestion) => { if (q.decision.initial) q.decision.initial.context.board[0] = "7h"; }],
] as const)("rejects altered typed questions: %s", (_name, change) => {
  const q = structuredClone(fixture.questions[0]); change(q); expect(() => parseExpandedFlopCollectionQuestion(q, metadata, 0)).toThrow(IntervalProtocolError);
});
it("rejects hidden fields and ambiguous unions and keeps information-set keys opaque", () => {
  const q = fixture.questions[0];
  for (const value of [null, {}, { ...q, prior: [] }, { ...q, decision: { ...q.decision, later: fixture.questions[1].decision.later } },
    { ...q, decision: { ...q.decision, initial: null } }, { ...q, decision: { ...q.decision, opponentCards: "9s Js" } }])
    expect(() => parseExpandedFlopCollectionQuestion(value, metadata, 0)).toThrow(IntervalProtocolError);
  const changed = structuredClone(q); if (changed.decision.initial) changed.decision.initial.informationSet = "opaque";
  expect(parseExpandedFlopCollectionQuestion(changed, metadata, 0)).toEqual(changed);
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
    expect(() => checkExpandedFlopCollectionInventory(qs, metadata)).toThrow(IntervalProtocolError);
  }
  expect(() => checkExpandedFlopCollectionInventory(fixture.questions.slice(0,1), metadata)).toThrow(IntervalProtocolError);
});
it.each([
  ["collection pin", (f: ExpandedFlopCollectionFeedback) => { f.collectionHash = "0".repeat(64); }],
  ["question index", (f: ExpandedFlopCollectionFeedback) => { f.questionIndex = 1; }],
  ["kind", (f: ExpandedFlopCollectionFeedback) => { f.decision = fixture.attempts[1].feedback.decision; }],
  ["EV", (f: ExpandedFlopCollectionFeedback) => { if (f.decision.initial) f.decision.initial.alternatives[0].upperEvBb = NaN; }],
  ["grade pin", (f: ExpandedFlopCollectionFeedback) => { if (f.decision.initial) f.decision.initial.reportHash = "0".repeat(64); }],
] as const)("rejects cross-question or contradictory grades: %s", (_name, change) => {
  const f = structuredClone(fixture.attempts[0].feedback); change(f);
  expect(() => parseExpandedFlopCollectionFeedback(f, fixture.questions[0], "check")).toThrow(IntervalProtocolError);
});
it("keeps menus separate and rejects client-created grade fields", () => {
  expect(() => parseExpandedFlopCollectionFeedback(fixture.attempts[0].feedback, fixture.questions[0], "fold")).toThrow();
  expect(() => parseExpandedFlopCollectionFeedback(fixture.attempts[1].feedback, fixture.questions[1], "check")).toThrow();
  expect(() => parseExpandedFlopCollectionFeedback({ ...fixture.attempts[0].feedback, evLossBb: 0 }, fixture.questions[0], "check")).toThrow();
});
it("accepts zero/100 explicit attempts and counts repeated contexts once", () => {
  for (const count of [0, 100]) {
    const review = expandedFlopCollectionFixtureReview(Array(count).fill({questionIndex:1,action:"fold"}));
    expect(parseExpandedFlopCollectionReview(review, metadata, fixture.questions, review.attempts)).toEqual(review);
    expect(review.counts.distinctQuestionsAttempted).toBe(count ? 1 : 0);
  }
  const oversized = expandedFlopCollectionFixtureReview(Array(101).fill({questionIndex:0,action:"check"}));
  expect(() => parseExpandedFlopCollectionReview(oversized, metadata, fixture.questions, oversized.attempts)).toThrow();
});
it("rejects reordered questions, edited counts, earlier grades and attempt identity", () => {
  for (const change of [
    (r: typeof fixture) => { r.questions.reverse(); },
    (r: typeof fixture) => { r.attempts[0].index = 1; },
    (r: typeof fixture) => { r.attempts[0].questionIndex = 1; },
    (r: typeof fixture) => { r.attempts[0].feedback.decision = r.attempts[1].feedback.decision; },
    (r: typeof fixture) => { r.counts.distinctQuestionsAttempted = 1; },
    (r: typeof fixture) => { expandedDecision(r.questions[1]).context.board[0] = "2d"; },
  ]) {
    const r = structuredClone(fixture); change(r);
    expect(() => parseExpandedFlopCollectionReview(r, metadata, fixture.questions, fixture.attempts)).toThrow(IntervalProtocolError);
  }
});
it.each([400,404,503])("preserves HTTP %s errors", async status => {
  vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:false,status} as Response);
  await expect(expandedFlopCollectionRequest("",new AbortController().signal)).rejects.toBeInstanceOf(ExpandedFlopCollectionRequestError);
});
it("sends only collection/question/report/action pins and rejects invalid success JSON", async () => {
  const fetch = vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,json:()=>Promise.reject(new Error("invalid JSON"))} as Response);
  const signal = new AbortController().signal, body = {collectionHash:metadata.collectionHash,questionIndex:0,reportHash:expandedDecision(fixture.questions[0]).reportHash,action:"check"};
  await expect(expandedFlopCollectionRequest("/grade",signal,body)).rejects.toBeInstanceOf(IntervalProtocolError);
  expect(fetch).toHaveBeenCalledWith("/api/v1/trainer/research/flop-interval-collection-v3/grade",{signal,cache:"no-store",method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
});

it("keeps collection and original BTN question indices separate in all four hands", () => {
  for (let i=2;i<6;i++) {
    const q=structuredClone(fixture.questions[i]);
    expect(q.sourceQuestionIndex).toBe(i-2);
    q.sourceQuestionIndex=i;
    expect(()=>parseExpandedFlopCollectionQuestion(q,metadata,i)).toThrow(IntervalProtocolError);
    const f=structuredClone(fixture.attempts[i].feedback);
    f.sourceQuestionIndex=i;
    expect(()=>parseExpandedFlopCollectionFeedback(f,fixture.questions[i],"check")).toThrow(IntervalProtocolError);
    const inner=structuredClone(fixture.attempts[i].feedback);
    inner.decision.btn!.questionIndex=i;
    expect(()=>parseExpandedFlopCollectionFeedback(inner,fixture.questions[i],"check")).toThrow(IntervalProtocolError);
  }
});
it("rejects extra BTN union members and cross-hand contexts, pins and grades",()=>{
  for(const field of ["union","cards","board","pin","key","source"]) {
    const qs=structuredClone(fixture.questions),q=qs[2];
    if(field==="union")Object.assign(q.decision,{initial:fixture.questions[0].decision.initial});
    if(field==="cards")q.decision.btn!.heroCards="Ah Kh";
    if(field==="board")q.decision.btn!.context.board[0]="2d";
    if(field==="pin")q.decision.btn!.reportHash=expandedDecision(qs[0]).reportHash;
    if(field==="key")q.decision.btn!.informationSet=qs[3].decision.btn!.informationSet;
    if(field==="source")q.sourceQuestionIndex=2;
    expect(()=>checkExpandedFlopCollectionInventory(qs,metadata)).toThrow(IntervalProtocolError);
  }
});
it("exports the exact canonical server bytes and sends only mixed-index choices",async()=>{
  const fetch=vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,text:()=>Promise.resolve(raw)} as Response);
  const signal=new AbortController().signal;
  expect(await expandedFlopReviewDocument(signal,metadata,fixture.questions,fixture.attempts)).toBe(raw);
  expect(new TextEncoder().encode(raw).length).toBeGreaterThan(40731);
  const body=JSON.parse(String(fetch.mock.calls[0][1]?.body));
  expect(body).toEqual({collectionHash:metadata.collectionHash,choices:fixture.attempts.map(a=>({questionIndex:a.questionIndex,reportHash:expandedDecision(fixture.questions[a.questionIndex]).reportHash,action:a.action}))});
  expect(Object.keys(expandedReviewChoices(fixture.attempts)[2]).sort()).toEqual(["action","questionIndex","reportHash"]);
});
it.each(["bad-json","oversized","forged-count","forged-grade","changed-source"])("rejects invalid canonical exports: %s",async kind=>{
  const changed=structuredClone(fixture);
  if(kind==="forged-count")changed.counts.distinctQuestionsAttempted=2;
  if(kind==="forged-grade")changed.attempts[2].feedback.decision.btn!.alternatives[0].upperEvBb=100;
  if(kind==="changed-source")changed.attempts[2].feedback.sourceQuestionIndex=2;
  const text=kind==="bad-json"?"{":kind==="oversized"?" ".repeat(1024*1024+1):JSON.stringify(changed);
  vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,text:()=>Promise.resolve(text)} as Response);
  await expect(expandedFlopReviewDocument(new AbortController().signal,metadata,fixture.questions,fixture.attempts)).rejects.toBeInstanceOf(IntervalProtocolError);
});

it.each([6, 7, 8, 9])("validates paired-board question %s, both index spaces and exact grades", index => {
  const q = fixture.questions[index];
  expect(q.sourceQuestionIndex).toBe(index - 6);
  expect(expandedDecision(q).context.legalActions).toEqual(["fold", "call"]);
  for (const field of ["board", "menu", "history", "contribution", "actor", "local", "union"]) {
    const changed = structuredClone(fixture.questions);
    const d = changed[index].decision.paired!;
    if (field === "board") d.context.board = ["2s", "8s", "Ts"];
    if (field === "menu") Object.assign(d.context, {legalActions: ["check", "bet"]});
    if (field === "history") d.context.flopHistory[0].action = "check";
    if (field === "contribution") d.context.players[5].committedBb -= 3.25;
    if (field === "actor") d.context.actingSeat = "BB";
    if (field === "local") d.questionIndex = (index - 5) % 4;
    if (field === "union") changed[index].decision.btn = fixture.questions[2].decision.btn as never;
    expect(() => checkExpandedFlopCollectionInventory(changed, metadata)).toThrow(IntervalProtocolError);
  }
  const bad = structuredClone(fixture.attempts[index].feedback);
  bad.decision.paired!.questionIndex = (index - 5) % 4;
  expect(() => parseExpandedFlopCollectionFeedback(bad, q, "fold")).toThrow(IntervalProtocolError);
});
