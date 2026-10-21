import raw from "./test-data/qualified-flop-collection-review.json?raw";
import { afterEach, expect, it, vi } from "vitest";
import { IntervalProtocolError } from "./intervalTrainerApi";
import { qualifiedFlopCollectionFixture as fixture, qualifiedFlopCollectionMetadata as metadata, qualifiedFlopCollectionFixtureReview } from "./test-data/qualifiedFlopCollection";
import { parseQualifiedFlopCollectionMetadata, parseQualifiedFlopCollectionQuestion, parseQualifiedFlopCollectionFeedback,
  parseQualifiedFlopCollectionReview, checkQualifiedFlopCollectionInventory, qualifiedDecision, qualifiedGrade,
  qualifiedFlopCollectionRequest, qualifiedFlopReviewDocument, qualifiedReviewChoices, QualifiedFlopCollectionRequestError } from "./qualifiedFlopCollectionApi";

afterEach(() => vi.restoreAllMocks());
it("validates all eleven questions, all twenty-two grades and the canonical review", () => {
  expect(parseQualifiedFlopCollectionMetadata(metadata)).toEqual(metadata);
  checkQualifiedFlopCollectionInventory(fixture.questions, metadata);
  fixture.questions.forEach((q, i) => expect(parseQualifiedFlopCollectionQuestion(q, metadata, i)).toEqual(q));
  for (const a of fixture.attempts) expect(parseQualifiedFlopCollectionFeedback(a.feedback, fixture.questions[a.questionIndex], a.action)).toEqual(a.feedback);
  expect(parseQualifiedFlopCollectionReview(fixture, metadata, fixture.questions, fixture.attempts)).toEqual(fixture);
});
it.each(["scope", "admission", "beliefs", "legacy", "coverage", "order", "unknown"])("rejects incompatible metadata: %s", kind => {
  const m = structuredClone(metadata);
  if (kind === "scope") m.activePlayers = 6;
  if (kind === "admission") Object.assign(m, {trainerAdmission: true});
  if (kind === "beliefs") m.beliefs = "EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1";
  if (kind === "legacy") m.legacyCollectionHash = "invalid";
  if (kind === "coverage") m.availableQuestions = 10;
  if (kind === "order") m.questionKinds.reverse();
  if (kind === "unknown") Object.assign(m, {hiddenRanges: []});
  expect(() => parseQualifiedFlopCollectionMetadata(m)).toThrow(IntervalProtocolError);
});
it.each(["cards", "board", "input", "pin", "menu", "actor", "pot", "union", "index", "unknown"])("rejects changed weak source: %s", kind => {
  const qs = structuredClone(fixture.questions), q = qs[10], weak = q.decision.weak!;
  if (kind === "cards") weak.heroCards = "As Qs";
  if (kind === "board") weak.context.board[0] = "2s";
  if (kind === "input") weak.beliefs = "same-ranges";
  if (kind === "pin") weak.reportHash = qualifiedDecision(qs[6]).reportHash;
  if (kind === "menu") Object.assign(weak.context, {legalActions: ["check", "bet"]});
  if (kind === "actor") weak.context.actingSeat = "BB";
  if (kind === "pot") weak.context.potBb = 100;
  if (kind === "union") Object.assign(q.decision, {legacy: qs[0].decision.legacy});
  if (kind === "index") q.sourceQuestionIndex = 10;
  if (kind === "unknown") Object.assign(weak.context, {villainCards: "secret"});
  expect(() => checkQualifiedFlopCollectionInventory(qs, metadata)).toThrow(IntervalProtocolError);
});
it.each(["ev", "loss", "verdict", "pin", "selected", "union", "index"])("rejects corrupted weak grades: %s", kind => {
  const f = structuredClone(fixture.attempts[20].feedback), weak = f.decision.weak!;
  if (kind === "ev") weak.alternatives[0].upperEvBb = NaN;
  if (kind === "loss") weak.alternatives[1].lowerLossBb = 0;
  if (kind === "verdict") weak.selected.classification = "OUTSIDE_LIMIT";
  if (kind === "pin") weak.reportHash = "0".repeat(64);
  if (kind === "selected") weak.selected = weak.alternatives[1];
  if (kind === "union") Object.assign(f.decision, {legacy: fixture.attempts[0].feedback.decision.legacy});
  if (kind === "index") f.questionIndex = 9;
  expect(() => parseQualifiedFlopCollectionFeedback(f, fixture.questions[10], "fold")).toThrow(IntervalProtocolError);
});
it("keeps both legacy index spaces and all four original source parsers", () => {
  for (let i = 0; i < 10; i++) {
    const q = structuredClone(fixture.questions[i]);
    q.decision.legacy!.sourceQuestionIndex = 100;
    expect(() => parseQualifiedFlopCollectionQuestion(q, metadata, i)).toThrow(IntervalProtocolError);
  }
});
it.each([0, 100])("counts %s repeated attempts independently of question coverage", count => {
  const review = qualifiedFlopCollectionFixtureReview(Array.from({length: count}, () => ({questionIndex: 10, action: "fold" as const})));
  expect(parseQualifiedFlopCollectionReview(review, metadata, fixture.questions, review.attempts)).toEqual(review);
  expect(review.counts.distinctQuestionsAttempted).toBe(count ? 1 : 0);
});
it.each(["coverage", "grade", "source", "order", "attempt-cap"])("rejects changed review: %s", kind => {
  const r = structuredClone(fixture);
  if (kind === "coverage") r.counts.distinctQuestionsAttempted = 22;
  if (kind === "grade") qualifiedGrade(r.attempts[20].feedback).selected.upperEvBb = 100;
  if (kind === "source") r.questions[10].decision.weak!.reportHash = "0".repeat(64);
  if (kind === "order") r.attempts.reverse();
  if (kind === "attempt-cap") r.attempts = Array(101).fill(r.attempts[0]);
  expect(() => parseQualifiedFlopCollectionReview(r, metadata, fixture.questions, fixture.attempts)).toThrow(IntervalProtocolError);
});
it("exports exact canonical bytes and sends only pinned choices", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: true, text: () => Promise.resolve(raw)} as Response);
  expect(await qualifiedFlopReviewDocument(new AbortController().signal, metadata, fixture.questions, fixture.attempts)).toBe(raw);
  expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual({collectionHash: metadata.collectionHash, choices: qualifiedReviewChoices(fixture.attempts)});
  expect(Object.keys(qualifiedReviewChoices(fixture.attempts)[20]).sort()).toEqual(["action", "questionIndex", "reportHash"]);
});
it.each(["json", "cap", "counts", "weak-pin"])("rejects invalid exported bytes: %s", async kind => {
  const r = structuredClone(fixture);
  if (kind === "counts") r.counts.withinLimit++;
  if (kind === "weak-pin") r.questions[10].decision.weak!.reportHash = "0".repeat(64);
  const text = kind === "json" ? "{" : kind === "cap" ? " ".repeat(1024*1024+1) : JSON.stringify(r);
  vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: true, text: () => Promise.resolve(text)} as Response);
  await expect(qualifiedFlopReviewDocument(new AbortController().signal, metadata, fixture.questions, fixture.attempts)).rejects.toBeInstanceOf(IntervalProtocolError);
});
it("rejects unavailable servers and malformed successful JSON", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: false, status: 404} as Response);
  await expect(qualifiedFlopCollectionRequest("", new AbortController().signal)).rejects.toBeInstanceOf(QualifiedFlopCollectionRequestError);
  fetch.mockResolvedValue({ok: true, json: () => Promise.reject(new SyntaxError())} as Response);
  await expect(qualifiedFlopCollectionRequest("", new AbortController().signal)).rejects.toBeInstanceOf(IntervalProtocolError);
});
it.each(["root", "escaped", "nested"])("rejects duplicate export fields which offline replay cannot accept: %s", async kind => {
  let text = JSON.stringify(fixture);
  if (kind === "root") text = '{"collectionHash":"ignored",' + text.slice(1);
  if (kind === "escaped") text = '{"\\u0063ollectionHash":"ignored",' + text.slice(1);
  if (kind === "nested") text = text.replace('"reportHash":', '"reportHash":"ignored","reportHash":');
  vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: true, text: () => Promise.resolve(text)} as Response);
  await expect(qualifiedFlopReviewDocument(new AbortController().signal, metadata, fixture.questions, fixture.attempts)).rejects.toBeInstanceOf(IntervalProtocolError);
});
