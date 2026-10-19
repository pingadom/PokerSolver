import { afterEach, expect, it, vi } from "vitest";
import raw from "./test-data/btn-flop-interval-drill.json?raw";
import { btnFlopFixture as fixture, btnFlopMetadata as metadata, btnFlopFixtureReview } from "./test-data/btnFlopInterval";
import { parseBtnFlopIntervalMetadata, parseBtnFlopIntervalQuestion, parseBtnFlopIntervalFeedback,
  parseBtnFlopIntervalReview, checkBtnFlopInventory, btnFlopIntervalRequest, btnFlopReviewDocument,
  BtnFlopIntervalProtocolError, BtnFlopIntervalRequestError, type BtnFlopIntervalQuestion } from "./btnFlopIntervalApi";
afterEach(() => vi.restoreAllMocks());
it("accepts the owned four-question inventory, all eight moves and complete mixed review", () => {
  expect(parseBtnFlopIntervalMetadata(metadata)).toEqual(metadata);
  fixture.questions.forEach((q, i) => expect(parseBtnFlopIntervalQuestion(q, metadata, i)).toEqual(q));
  checkBtnFlopInventory(fixture.questions, metadata);
  fixture.attempts.forEach(a => expect(parseBtnFlopIntervalFeedback(a.feedback, fixture.questions[a.questionIndex], a.action)).toEqual(a.feedback));
  expect(parseBtnFlopIntervalReview(fixture, metadata, fixture.questions, fixture.attempts)).toEqual(fixture);
});
it.each(["publicationStatus", "trainerAdmission", "selection", "model", "chanceModel", "evScope", "reachUnits", "beliefs", "sourceHistoryReachStatus", "availableQuestions", "activePlayers", "maximumAttempts"])("rejects altered metadata: %s", field => {
  const m = structuredClone(metadata) as unknown as Record<string, unknown>; m[field] = "edited";
  expect(() => parseBtnFlopIntervalMetadata(m)).toThrow(BtnFlopIntervalProtocolError);
});
it.each([0,1,2,3])("binds hand %i to its own index, cards, seat and public physics", i => {
  const mutations: ((q: BtnFlopIntervalQuestion) => void)[] = [
    q => {q.questionIndex = (i+1)%4;}, q => {q.reportHash = "0".repeat(64);}, q => {q.hero = "BB";},
    q => {q.heroCards = fixture.questions[(i+1)%4].heroCards;}, q => {q.context.actingSeat = "BB";},
    q => {q.context.board[0] = q.heroCards.split(" ")[0];}, q => {q.context.potBb = 100;},
    q => {q.context.toCallBb = 3.25;}, q => {q.context.betSizeBb = 1000;},
    q => {q.context.flopHistory[0].seat = "BTN";}, q => {q.context.flopHistory[0].amountBb = 1;},
    q => {q.context.preflopHistory[0].amountBb = 100;}, q => {q.context.players[3].status = "ACTIVE";},
    q => {q.context.players[5].status = "FOLDED";}, q => {q.context.legalActions.reverse();},
    q => {q.securitySlackBb = .01;}, q => {q.minimumReach = 0;}, q => {q.maximumLossBb = NaN;},
  ];
  for (const change of mutations) {
    const q = structuredClone(fixture.questions[i]); change(q);
    expect(() => parseBtnFlopIntervalQuestion(q, metadata, i)).toThrow(BtnFlopIntervalProtocolError);
  }
  expect(() => parseBtnFlopIntervalQuestion({...fixture.questions[i], opponentCards:"7d 7h"},metadata,i)).toThrow();
  expect(() => parseBtnFlopIntervalQuestion({...fixture.questions[i], context:{...fixture.questions[i].context,worlds:[]}},metadata,i)).toThrow();
});
it("rejects incomplete/reordered inventory, inconsistent contexts and colliding information sets", () => {
  for (const kind of ["size", "order", "key", "limit", "board"]) {
    const qs = structuredClone(fixture.questions);
    if(kind==="size")qs.pop(); if(kind==="order")qs.reverse(); if(kind==="key")qs[1].informationSet=qs[0].informationSet;
    if(kind==="limit")qs[1].maximumLossBb=.005; if(kind==="board")qs[1].context.board[0]="2d";
    expect(()=>checkBtnFlopInventory(qs,metadata)).toThrow();
  }
});
it("rejects wrong-question grades, hidden data, invalid endpoints, classification and selection", () => {
  for(const kind of ["pin","index","selected","nan","loss","verdict","menu","hidden"]) {
    const f=structuredClone(fixture.attempts[0].feedback);
    if(kind==="pin")f.reportHash="0".repeat(64); if(kind==="index")f.questionIndex=1;
    if(kind==="selected")f.selected=f.alternatives[1]; if(kind==="nan")f.alternatives[1].upperEvBb=NaN;
    if(kind==="loss")f.alternatives[1].upperLossBb=-1; if(kind==="verdict")f.alternatives[0].classification="WITHIN_LIMIT";
    if(kind==="menu")f.alternatives.reverse();
    expect(()=>parseBtnFlopIntervalFeedback(kind==="hidden"?{...f,policy:[]}:f,fixture.questions[0],"check")).toThrow();
  }
});
it("accepts 0/100 choices and rejects 101 plus edited prior attempts, questions and counts", () => {
  for(const size of [0,100]) {
    const r=btnFlopFixtureReview(Array.from({length:size},()=>({questionIndex:2,action:"bet" as const})));
    expect(parseBtnFlopIntervalReview(r,metadata,fixture.questions,r.attempts)).toEqual(r);
    expect(r.counts.distinctQuestionsAttempted).toBe(size?1:0);
  }
  const r=btnFlopFixtureReview(Array.from({length:101},()=>({questionIndex:2,action:"bet" as const})));
  expect(()=>parseBtnFlopIntervalReview(r,metadata,fixture.questions,r.attempts)).toThrow();
  for(const kind of ["order","count","attempt","grade","pin"]) {
    const changed=structuredClone(fixture);
    if(kind==="order")changed.questions.reverse(); if(kind==="count")changed.counts.distinctQuestionsAttempted=8;
    if(kind==="attempt")changed.attempts[0].index=1; if(kind==="grade")changed.attempts[0].feedback=changed.attempts[1].feedback;
    if(kind==="pin")changed.reportHash="0".repeat(64);
    expect(()=>parseBtnFlopIntervalReview(changed,metadata,fixture.questions,fixture.attempts)).toThrow();
  }
});
it("retains the canonical document bytes and sends only pinned explicit choices", async()=>{
  const fetch=vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,text:()=>Promise.resolve(raw)} as Response);
  const signal=new AbortController().signal;
  expect(await btnFlopReviewDocument(signal,metadata,fixture.questions,fixture.attempts)).toBe(raw);
  expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({reportHash:metadata.reportHash,choices:fixture.attempts.map(({questionIndex,action})=>({questionIndex,action}))});
  expect(fetch.mock.calls[0][1]?.cache).toBe("no-store");
});
it.each(["invalid", "oversize", "changed"])("rejects unsafe canonical export: %s", async kind=>{
  const text=kind==="invalid"?"{} {}":kind==="oversize"?" ".repeat(256*1024+1):JSON.stringify({...fixture,counts:{...fixture.counts,withinLimit:100}});
  vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,text:()=>Promise.resolve(text)} as Response);
  await expect(btnFlopReviewDocument(new AbortController().signal,metadata,fixture.questions,fixture.attempts)).rejects.toBeInstanceOf(BtnFlopIntervalProtocolError);
});
it.each([400,404,503])("preserves HTTP failures: %s", async status=>{
  vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:false,status} as Response);
  await expect(btnFlopIntervalRequest("",new AbortController().signal)).rejects.toBeInstanceOf(BtnFlopIntervalRequestError);
  await expect(btnFlopReviewDocument(new AbortController().signal,metadata,fixture.questions,[])).rejects.toBeInstanceOf(BtnFlopIntervalRequestError);
});
it("rejects invalid success JSON",async()=>{
  vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,json:()=>Promise.reject(new Error("bad JSON"))} as Response);
  await expect(btnFlopIntervalRequest("",new AbortController().signal)).rejects.toBeInstanceOf(BtnFlopIntervalProtocolError);
});
