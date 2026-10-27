import raw from "./test-data/sizing-flop-collection-review.json?raw";
import oldReview from "./test-data/unpaired-flop-collection-review.json";
import { afterEach, expect, it, vi } from "vitest";
import { IntervalProtocolError } from "./intervalTrainerApi";
import { sizingFlopCollectionFixture as fixture, sizingFlopCollectionMetadata as metadata, sizingFlopCollectionFixtureReview } from "./test-data/sizingFlopCollection";
import { parseSizingFlopCollectionMetadata, parseSizingFlopCollectionQuestion, parseSizingFlopCollectionFeedback,
  checkSizingFlopCollectionInventory, parseSizingFlopCollectionReview, sizingDecision, sizingGrade, sizingQuestionIdentity,
  sizingFlopCollectionRequest, sizingFlopReviewDocument, sizingReviewChoices, readSizingFlopReview, sizingSessionKey, SizingFlopCollectionRequestError } from "./sizingFlopCollectionApi";
import { unpairedSessionKey } from "./unpairedFlopCollectionApi";

afterEach(() => vi.restoreAllMocks());
it("validates all twenty questions, forty grades and exact source-composed review bytes", () => {
  expect(parseSizingFlopCollectionMetadata(metadata)).toEqual(metadata);
  checkSizingFlopCollectionInventory(fixture.questions,metadata);
  fixture.questions.forEach((q,i) => expect(parseSizingFlopCollectionQuestion(q,metadata,i)).toEqual(q));
  fixture.attempts.forEach(a => expect(parseSizingFlopCollectionFeedback(a.feedback,fixture.questions[a.questionIndex],a.action)).toEqual(a.feedback));
  expect(readSizingFlopReview(raw,metadata,fixture.questions)).toEqual(fixture);
  expect(fixture.questions.slice(0,18).map(q=>q.decision.legacy)).toEqual(oldReview.questions);
  expect(fixture.questions.slice(18).map(q=>q.decision.pot!.sourceIndex)).toEqual([3,5]);
});
it.each(["pin","legacy","scope","admission","order","coverage","budget","sharing","unknown"])("rejects changed v7 metadata: %s",kind => {
  const m=structuredClone(metadata);
  if(kind==="pin") m.collectionHash="0".repeat(64);
  if(kind==="legacy") m.legacyCollectionHash="0".repeat(64);
  if(kind==="scope") m.activePlayers=6;
  if(kind==="admission") Object.assign(m,{trainerAdmission:true});
  if(kind==="order") m.questionKinds.reverse();
  if(kind==="coverage") m.availableQuestions=18;
  if(kind==="budget") m.startupBudget.freshIterations=1212000;
  if(kind==="sharing") m.addedReferenceSharing="same policies";
  if(kind==="unknown") Object.assign(m,{fallback:true});
  expect(()=>parseSizingFlopCollectionMetadata(m)).toThrow(IntervalProtocolError);
});
it.each(["cards","board","price","bet","pot","source","pin","actor","menu","index","union","kind","extra"])("rejects altered pot-size question: %s",kind=>{
  const q=structuredClone(fixture.questions[18]),d=q.decision.pot!;
  if(kind==="cards") d.heroCards="Ac Jc";
  if(kind==="board") d.table.board[0]="2s";
  if(kind==="price") d.table.toCallBb=3.25;
  if(kind==="bet") d.table.betSizeBb=3.25;
  if(kind==="pot") d.table.potBb=9.75;
  if(kind==="source") d.sourceIndex=5;
  if(kind==="pin") d.reportHash=sizingDecision(fixture.questions[15]).reportHash;
  if(kind==="actor") d.table.actingSeat="BTN";
  if(kind==="menu") Object.assign(d.table,{legalActions:["check","bet"]});
  if(kind==="index") q.sourceQuestionIndex=18;
  if(kind==="union") Object.assign(q.decision,{legacy:fixture.questions[0].decision.legacy});
  if(kind==="kind") Object.assign(q.decision,{kind:"LEGACY_EIGHTEEN_QUALIFIED_COLLECTION"});
  if(kind==="extra") Object.assign(d,{opponentCards:"secret"});
  expect(()=>parseSizingFlopCollectionQuestion(q,metadata,18)).toThrow(IntervalProtocolError);
});
it.each(["ev","loss","verdict","price","hand","source","action","union","index","same-pin-other-hand"])("rejects corrupted or misbound pot-size grade: %s",kind=>{
  const a=fixture.attempts.find(a=>a.questionIndex===18&&a.action==="call")!,f=structuredClone(a.feedback),d=f.decision.pot!;
  if(kind==="ev") d.selected.lowerEvBb=NaN;
  if(kind==="loss") d.alternatives[0].upperLossBb=99;
  if(kind==="verdict") d.selected.classification="OUTSIDE_LIMIT";
  if(kind==="price") d.question.table.toCallBb=3.25;
  if(kind==="hand") d.question.heroCards="Ac Jc";
  if(kind==="source") d.question.sourceIndex=5;
  if(kind==="action") d.action="fold";
  if(kind==="union") Object.assign(f.decision,{legacy:fixture.attempts[0].feedback.decision.legacy});
  if(kind==="index") f.questionIndex=19;
  if(kind==="same-pin-other-hand") f.decision.pot=structuredClone(fixture.attempts.find(a=>a.questionIndex===19&&a.action==="call")!.feedback.decision.pot);
  expect(()=>parseSizingFlopCollectionFeedback(f,fixture.questions[18],"call")).toThrow(IntervalProtocolError);
});
it("binds inventory order, source-specific identity and empty/repeated review coverage",()=>{
  const qs=structuredClone(fixture.questions);qs[19]=structuredClone(qs[18]);
  expect(()=>checkSizingFlopCollectionInventory(qs,metadata)).toThrow(IntervalProtocolError);
  expect(new Set(fixture.questions.slice(18).map(sizingQuestionIdentity)).size).toBe(2);
  expect(new Set(fixture.questions.slice(18).map(q=>sizingDecision(q).reportHash)).size).toBe(1);
  for(const choices of [[],Array.from({length:100},()=>({questionIndex:18,action:"call" as const}))]) {
    const r=sizingFlopCollectionFixtureReview(choices);
    expect(parseSizingFlopCollectionReview(r,metadata,fixture.questions)).toEqual(r);
  }
  const r=sizingFlopCollectionFixtureReview([{questionIndex:18,action:"call"}]);r.counts.distinctQuestionsAttempted=2;
  expect(()=>parseSizingFlopCollectionReview(r,metadata,fixture.questions)).toThrow(IntervalProtocolError);
});
it.each([raw+"{}"," ".repeat(1048577),JSON.stringify(oldReview),raw.replace('"collectionHash"','"collectionHash":"0","collectionHash"'),raw.replace('"collectionHash"','"collectionHash":"0","collectionHash"'.replace('collectionHash','collection\\u0048ash'))])("rejects malformed, duplicate, oversize and older-version imports",text=>{
  expect(()=>readSizingFlopReview(text,metadata,fixture.questions)).toThrow(IntervalProtocolError);
});
it("keeps v6 storage separate and submits only explicit choices to the v7 endpoint",async()=>{
  expect(sizingSessionKey(metadata.collectionHash)).not.toBe(unpairedSessionKey(metadata.legacyCollectionHash));
  const attempts=fixture.attempts.slice(-4),choices=sizingReviewChoices(attempts);
  expect(choices.every(c=>Object.keys(c).sort().join(" ")==="action questionIndex reportHash")).toBe(true);
  const fetch=vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,status:200,json:async()=>metadata} as Response);
  await sizingFlopCollectionRequest("/review",new AbortController().signal,{collectionHash:metadata.collectionHash,choices});
  expect(fetch.mock.calls[0][0]).toBe("/api/v1/trainer/research/flop-interval-collection-v7/review");
  expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({collectionHash:metadata.collectionHash,choices});
  expect(sizingGrade(attempts[1].feedback).selected.classification).toBe("WITHIN_LIMIT");
});
it("verifies exact exported attempts and reports a disabled API without fallback",async()=>{
  const signal=new AbortController().signal;
  const fetch=vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,status:200,text:async()=>raw} as Response);
  expect(await sizingFlopReviewDocument(signal,metadata,fixture.questions,fixture.attempts)).toBe(raw);
  fetch.mockResolvedValue({ok:false,status:404} as Response);
  await expect(sizingFlopCollectionRequest("",signal)).rejects.toBeInstanceOf(SizingFlopCollectionRequestError);
});
