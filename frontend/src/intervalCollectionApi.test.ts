import { expect, it, vi, afterEach } from "vitest";
import {
  parseCollectionMetadata, parseCollectionQuestion, parseCollectionFeedback, parseCollectionReview,
  checkCollectionInventory, collectionRequest,
} from "./intervalCollectionApi";
import { IntervalProtocolError, IntervalRequestError, parseIntervalMetadata } from "./intervalTrainerApi";
import { collectionFixture as fixture, collectionMetadata as metadata, collectionReview } from "./test-data/intervalCollection";

const clone = <T,>(value: T): T => structuredClone(value);
afterEach(() => vi.restoreAllMocks());
it("validates the physically replayed four-question inventory, all verdicts and canonical review", () => {
  expect(parseCollectionMetadata(metadata)).toEqual(metadata);
  checkCollectionInventory(fixture.questions, metadata);
  fixture.questions.forEach((q, i) => expect(parseCollectionQuestion(q, metadata, i)).toEqual(q));
  fixture.attempts.forEach((a) => expect(parseCollectionFeedback(a.feedback, fixture.questions[a.questionIndex], a.action)).toEqual(a.feedback));
  expect(parseCollectionReview(fixture, metadata, fixture.questions, fixture.attempts)).toEqual(fixture);
  expect(() => parseIntervalMetadata(metadata)).toThrow(IntervalProtocolError);
});
it.each([
  { availableQuestions: 1 }, { availableQuestions: 17 }, { availableQuestions: 4.5 }, { activePlayers: 6 },
  { trainerAdmission: true }, { maximumAttempts: 101 }, { collectionHash: "bad" }, { selection: "random" },
  { publicationStatus: "PRODUCTION" }, { extra: 1 },
])("rejects unsupported collection metadata %j", (change) => {
  expect(() => parseCollectionMetadata({ ...metadata, ...change })).toThrow(IntervalProtocolError);
});
it.each(["pin", "index", "count", "selection", "table", "hidden", "duplicate", "order", "inner-selection"])("rejects incorrect question inventory: %s", (kind) => {
  const questions = clone(fixture.questions);
  if (kind === "pin") questions[0].collectionHash = "a".repeat(64);
  if (kind === "index") questions[0].index = 1;
  if (kind === "count") questions[0].totalQuestions = 3;
  if (kind === "selection") questions[0].selection = "other";
  if (kind === "table") questions[0].decision.table.potBb += 1;
  if (kind === "hidden") Object.assign(questions[0].decision, { opponentCards: "As Ah" });
  if (kind === "duplicate") questions[1].decision = clone(questions[0].decision);
  if (kind === "order") questions.reverse();
  if (kind === "inner-selection") questions[0].decision.selection = metadata.selection;
  expect(() => checkCollectionInventory(questions, metadata)).toThrow(IntervalProtocolError);
});
it.each(["collection", "index", "report", "selected", "range", "extra"])("rejects incorrect grade: %s", (kind) => {
  const feedback = clone(fixture.attempts[0].feedback);
  if (kind === "collection") feedback.collectionHash = "a".repeat(64);
  if (kind === "index") feedback.questionIndex = 1;
  if (kind === "report") feedback.decision.reportHash = fixture.questions[1].decision.reportHash;
  if (kind === "selected") feedback.decision.selected = feedback.decision.alternatives[0];
  if (kind === "range") feedback.decision.alternatives[0].lowerLossBb = -1;
  if (kind === "extra") Object.assign(feedback, { evLossBb: 0 });
  expect(() => parseCollectionFeedback(feedback, fixture.questions[0], "call")).toThrow(IntervalProtocolError);
});
it.each(["pin", "schema", "selection", "question-order", "missing-question", "attempt-order", "attempt-index", "target", "action", "grade", "coverage", "verdicts", "extra"])("rejects forged canonical review: %s", (kind) => {
  const review = clone(fixture);
  if (kind === "pin") review.collectionHash = "a".repeat(64);
  if (kind === "schema") review.schemaVersion = "v0";
  if (kind === "selection") review.selection = "v0";
  if (kind === "question-order") review.questions.reverse();
  if (kind === "missing-question") review.questions.pop();
  if (kind === "attempt-order") review.attempts.reverse();
  if (kind === "attempt-index") review.attempts[0].index = 2;
  if (kind === "target") review.attempts[0].questionIndex = 1;
  if (kind === "action") review.attempts[0].action = "fold";
  if (kind === "grade") review.attempts[0].feedback.decision.alternatives[0].upperEvBb += 1;
  if (kind === "coverage") review.counts.distinctQuestionsAttempted = 5;
  if (kind === "verdicts") review.counts.withinLimit += 1;
  if (kind === "extra") Object.assign(review, { accuracy: 100 });
  expect(() => parseCollectionReview(review, metadata, fixture.questions, fixture.attempts)).toThrow(IntervalProtocolError);
});
it("counts 100 retries as one question and rejects oversized attempt lists", () => {
  const choices = Array.from({ length: 100 }, () => ({ questionIndex: 0, action: "call" as const }));
  const review = collectionReview(choices);
  expect(parseCollectionReview(review, metadata, fixture.questions, review.attempts).counts.distinctQuestionsAttempted).toBe(1);
  const tooMany = collectionReview([...choices, choices[0]]);
  expect(() => parseCollectionReview(tooMany, metadata, fixture.questions, tooMany.attempts)).toThrow(IntervalProtocolError);
  const empty = collectionReview([]);
  expect(parseCollectionReview(empty, metadata, fixture.questions, []).counts.withinLimit).toBe(0);
});
it("uses the separate uncached endpoint and preserves abort/payload/error handling", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, json: async () => metadata } as Response);
  const controller = new AbortController();
  expect(await collectionRequest("", controller.signal)).toEqual(metadata);
  await collectionRequest("/grade", controller.signal, { action: "call" });
  expect(fetch.mock.calls[1]).toEqual(["/api/v1/trainer/research/interval-collection/grade", {
    signal: controller.signal, cache: "no-store", method: "POST", headers: { "Content-Type": "application/json" }, body: '{"action":"call"}',
  }]);
  fetch.mockResolvedValueOnce({ ok: false, status: 404 } as Response);
  await expect(collectionRequest("", controller.signal)).rejects.toBeInstanceOf(IntervalRequestError);
  fetch.mockResolvedValueOnce({ ok: true, json: async () => { throw new Error(); } } as unknown as Response);
  await expect(collectionRequest("", controller.signal)).rejects.toBeInstanceOf(IntervalProtocolError);
});
