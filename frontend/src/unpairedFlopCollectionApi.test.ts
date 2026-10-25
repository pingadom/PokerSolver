import raw from "./test-data/unpaired-flop-collection-review.json?raw";
import legacy from "./test-data/call-flop-collection-review.json";
import { afterEach, expect, it, vi } from "vitest";
import { IntervalProtocolError } from "./intervalTrainerApi";
import { unpairedFlopCollectionFixture as fixture, unpairedFlopCollectionMetadata as metadata, unpairedFlopCollectionFixtureReview } from "./test-data/unpairedFlopCollection";
import { parseUnpairedFlopCollectionMetadata, parseUnpairedFlopCollectionQuestion, parseUnpairedFlopCollectionFeedback,
  parseUnpairedFlopCollectionReview, checkUnpairedFlopCollectionInventory, unpairedDecision, unpairedGrade, unpairedQuestionIdentity,
  unpairedFlopCollectionRequest, unpairedFlopReviewDocument, unpairedReviewChoices, readUnpairedFlopReview, UnpairedFlopCollectionRequestError } from "./unpairedFlopCollectionApi";

afterEach(() => vi.restoreAllMocks());
it("validates eighteen physically qualified questions, thirty-six grades and exact saved review", () => {
  expect(parseUnpairedFlopCollectionMetadata(metadata)).toEqual(metadata);
  checkUnpairedFlopCollectionInventory(fixture.questions, metadata);
  fixture.questions.forEach((q,i) => expect(parseUnpairedFlopCollectionQuestion(q,metadata,i)).toEqual(q));
  fixture.attempts.forEach(a => expect(parseUnpairedFlopCollectionFeedback(a.feedback,fixture.questions[a.questionIndex],a.action)).toEqual(a.feedback));
  expect(readUnpairedFlopReview(raw,metadata,fixture.questions)).toEqual(fixture);
});
it.each(["scope", "admission", "beliefs", "legacy", "coverage", "order", "pin", "input-count", "sharing", "budget", "unknown"])("rejects incompatible metadata: %s", kind => {
  const m = structuredClone(metadata);
  if (kind === "scope") m.activePlayers = 6;
  if (kind === "admission") Object.assign(m,{trainerAdmission:true});
  if (kind === "beliefs") m.beliefs = "same ranges";
  if (kind === "legacy") m.legacyCollectionHash = "0".repeat(64);
  if (kind === "coverage") m.availableQuestions = 15;
  if (kind === "order") m.questionKinds.reverse();
  if (kind === "pin") m.collectionHash = "0".repeat(64);
  if (kind === "input-count") m.independentInputSets = 4;
  if (kind === "sharing") m.addedReferenceSharing = "three different ranges";
  if (kind === "budget") m.startupBudget.freshReferenceRuns = 2;
  if (kind === "unknown") Object.assign(m,{fallback:true});
  expect(() => parseUnpairedFlopCollectionMetadata(m)).toThrow(IntervalProtocolError);
});
it.each(["cards", "board", "input", "pin", "menu", "actor", "pot", "union", "local-index", "global-index", "kind", "nested-count", "unknown"])("rejects changed shared source: %s", kind => {
  const qs = structuredClone(fixture.questions), q = qs[15], shared = q.decision.shared!, d = shared.decision;
  if (kind === "cards") d.heroCards = "As Qs";
  if (kind === "board") d.context.board[0] = "2s";
  if (kind === "input") d.beliefs = "same ranges";
  if (kind === "pin") d.reportHash = unpairedDecision(qs[10]).reportHash;
  if (kind === "menu") Object.assign(d.context,{legalActions:["check","bet"]});
  if (kind === "actor") d.context.actingSeat = "BB";
  if (kind === "pot") d.context.potBb = 100;
  if (kind === "union") Object.assign(q.decision,{legacy:qs[0].decision.legacy});
  if (kind === "local-index") shared.index = 15;
  if (kind === "global-index") q.sourceQuestionIndex = 15;
  if (kind === "kind") Object.assign(q.decision,{kind:"LEGACY_FOURTEEN_QUALIFIED_COLLECTION"});
  if (kind === "nested-count") shared.totalQuestions = 18;
  if (kind === "unknown") Object.assign(d.context,{villainCards:"secret"});
  expect(() => checkUnpairedFlopCollectionInventory(qs,metadata)).toThrow(IntervalProtocolError);
});
it.each(["missing", "duplicate", "reordered", "mixed", "old-weak-index"])("rejects changed inventory: %s", kind => {
  const qs = structuredClone(fixture.questions);
  if (kind === "missing") qs.pop();
  if (kind === "duplicate") qs[16] = structuredClone(qs[15]);
  if (kind === "reordered") [qs[15],qs[16]] = [qs[16],qs[15]];
  if (kind === "mixed") qs[17].decision = structuredClone(qs[9].decision);
  if (kind === "old-weak-index") qs[10].decision.legacy!.decision.legacy!.sourceQuestionIndex = 10;
  expect(() => checkUnpairedFlopCollectionInventory(qs,metadata)).toThrow(IntervalProtocolError);
});
it.each(["ev", "loss", "verdict", "pin", "selected", "union", "index", "stale-same-pin", "question-shape"])("rejects corrupted or stale grades: %s", kind => {
  const attempt = fixture.attempts.find(a => a.questionIndex === 15 && a.action === "call")!;
  const f = structuredClone(attempt.feedback), shared = f.decision.shared!;
  if (kind === "ev") shared.alternatives[0].upperEvBb = NaN;
  if (kind === "loss") shared.alternatives[1].lowerLossBb = 100;
  if (kind === "verdict") shared.selected.classification = "OUTSIDE_LIMIT";
  if (kind === "pin") shared.reportHash = "0".repeat(64);
  if (kind === "selected") shared.selected = shared.alternatives[0];
  if (kind === "union") Object.assign(f.decision,{legacy:fixture.attempts[0].feedback.decision.legacy});
  if (kind === "index") f.questionIndex = 16;
  if (kind === "stale-same-pin") f.decision.shared = structuredClone(fixture.attempts.find(a => a.questionIndex === 16 && a.action === "call")!.feedback.decision.shared);
  if (kind === "question-shape") Object.assign(f.decision,{shared:fixture.questions[15].decision.shared});
  expect(() => parseUnpairedFlopCollectionFeedback(f,fixture.questions[15],"call")).toThrow(IntervalProtocolError);
});
it("keys the four same-pin questions separately and preserves all nested legacy indices", () => {
  expect(new Set(fixture.questions.slice(14).map(q => unpairedDecision(q).reportHash)).size).toBe(1);
  expect(new Set(fixture.questions.slice(14).map(unpairedQuestionIdentity)).size).toBe(4);
  expect(fixture.questions[10].sourceQuestionIndex).toBe(10);
  expect(fixture.questions[10].decision.legacy!.sourceQuestionIndex).toBe(10);
  expect(fixture.questions[10].decision.legacy!.decision.legacy!.sourceQuestionIndex).toBe(0);
  expect(fixture.questions.slice(14).map(q=>q.sourceQuestionIndex)).toEqual([0,1,2,3]);
  expect(() => parseUnpairedFlopCollectionFeedback(fixture.attempts[30].feedback,fixture.questions[15],"check")).toThrow(IntervalProtocolError);
});
it.each([0,100])("replays %s repeated attempts separately from coverage", count => {
  const r = unpairedFlopCollectionFixtureReview(Array.from({length:count},()=>({questionIndex:16,action:"call" as const})));
  expect(parseUnpairedFlopCollectionReview(r,metadata,fixture.questions,r.attempts)).toEqual(r);
  expect(r.counts.distinctQuestionsAttempted).toBe(count?1:0);
});
it.each(["coverage", "grade", "source", "order", "attempt-cap", "fraction", "negative", "old-version"])("rejects corrupted imported review: %s", kind => {
  const r = structuredClone(fixture);
  if (kind === "coverage") r.counts.distinctQuestionsAttempted = 36;
  if (kind === "grade") unpairedGrade(r.attempts[30].feedback).selected.upperEvBb = 100;
  if (kind === "source") r.questions[15].decision.shared!.decision.reportHash = "0".repeat(64);
  if (kind === "order") r.attempts.reverse();
  if (kind === "attempt-cap") r.attempts = Array(101).fill(r.attempts[0]);
  if (kind === "fraction") r.attempts[0].questionIndex = .5;
  if (kind === "negative") r.attempts[0].questionIndex = -1;
  const text = JSON.stringify(kind === "old-version" ? legacy : r);
  expect(() => readUnpairedFlopReview(text,metadata,fixture.questions)).toThrow(IntervalProtocolError);
});
it("exports canonical bytes and sends only pinned choices", async () => {
  const fetch = vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:true,text:()=>Promise.resolve(raw)} as Response);
  expect(await unpairedFlopReviewDocument(new AbortController().signal,metadata,fixture.questions,fixture.attempts)).toBe(raw);
  expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({collectionHash:metadata.collectionHash,choices:unpairedReviewChoices(fixture.attempts)});
  expect(Object.keys(unpairedReviewChoices(fixture.attempts)[30]).sort()).toEqual(["action","questionIndex","reportHash"]);
  expect(String(fetch.mock.calls[0][0])).toContain("collection-v6/review/export");
});
it.each(["json", "cap", "root", "escaped", "nested"])("rejects invalid or duplicate JSON bytes: %s", kind => {
  let text=raw;
  if (kind === "json") text="{";
  if (kind === "cap") text=" ".repeat(1024*1024+1);
  if (kind === "root") text='{"collectionHash":"ignored",'+text.slice(1);
  if (kind === "escaped") text='{"\\u0063ollectionHash":"ignored",'+text.slice(1);
  if (kind === "nested") text=JSON.stringify(fixture).replace('"reportHash":','"reportHash":"ignored","reportHash":');
  expect(()=>readUnpairedFlopReview(text,metadata,fixture.questions)).toThrow(IntervalProtocolError);
});
it("reports disabled sources without falling back and rejects invalid successful JSON", async () => {
  const fetch=vi.spyOn(globalThis,"fetch").mockResolvedValue({ok:false,status:404} as Response);
  await expect(unpairedFlopCollectionRequest("",new AbortController().signal)).rejects.toBeInstanceOf(UnpairedFlopCollectionRequestError);
  expect(fetch).toHaveBeenCalledOnce();
  fetch.mockResolvedValue({ok:true,json:()=>Promise.reject(new SyntaxError())} as Response);
  await expect(unpairedFlopCollectionRequest("",new AbortController().signal)).rejects.toBeInstanceOf(IntervalProtocolError);
});
