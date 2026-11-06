import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { legacyRootClient } from "./rootPreflopApi";
import { minimumPreflopClient } from "./minimumPreflopApi";
import { sequencePreflopClient } from "./sequencePreflopApi";
import { rootEqual, RootProtocolError, type RootAction, type RootClient } from "./rootPreflopProtocol";
import { rootFixtureReview, rootGrades, rootMetadata, rootQuestion } from "./test-data/rootPreflop";
import { minimumFixtureReview, minimumGrades, minimumMetadata, minimumQuestion } from "./test-data/minimumPreflop";
import { sequenceFixtureReview, sequenceGrades, sequenceMetadata, sequenceQuestion } from "./test-data/sequencePreflop";

import { batchPreflopClient } from "./batchPreflopApi";
import { batchFixtureReview, batchGrades, batchMetadata, batchQuestion } from "./test-data/batchPreflop";

const studies = [
  { name: "batch", client: batchPreflopClient, metadata: batchMetadata, question: batchQuestion, grades: batchGrades, review: batchFixtureReview,
    action: "raise:9.0" as RootAction, verdict: "Unresolved" },
  { name: "original", client: legacyRootClient, metadata: rootMetadata, question: rootQuestion, grades: rootGrades, review: rootFixtureReview,
    action: "fold" as RootAction, verdict: "Outside limit" },
  { name: "minimum", client: minimumPreflopClient, metadata: minimumMetadata, question: minimumQuestion, grades: minimumGrades, review: minimumFixtureReview,
    action: "call" as RootAction, verdict: "Within limit" },
  { name: "sequence", client: sequencePreflopClient, metadata: sequenceMetadata, question: sequenceQuestion, grades: sequenceGrades, review: sequenceFixtureReview,
    action: "raise:9.0" as RootAction, verdict: "Unresolved" },
];
const pairs = studies.flatMap(from => studies.filter(to => from !== to).map(to => ({ from, to })));
const response = (value: unknown) => Promise.resolve({ ok: true, status: 200, text: async () => JSON.stringify(value) } as Response);
function navigate(hash: string) {
  act(() => { window.history.replaceState(null, "", hash); window.dispatchEvent(new HashChangeEvent("hashchange")); });
}
function api(pending?: { client: RootClient; wait: (signal: AbortSignal) => Promise<Response> }) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const study = studies.find(s => String(url).startsWith(s.client.rootApiBase));
    if (!study) throw new Error("Unexpected API namespace");
    if (String(url).endsWith("/grade")) {
      if (pending?.client === study.client) return pending.wait(init!.signal as AbortSignal);
      return response(study.grades.find(g => g.move.action === JSON.parse(String(init?.body)).action));
    }
    if (String(url).includes("/questions/")) return response(study.question);
    return response(study.metadata);
  });
}
afterEach(() => { window.history.replaceState(null, "", "/"); localStorage.clear(); vi.restoreAllMocks(); });

it("binds the new route, source pin and public bank to the owned sequence HTTP capture", () => {
  expect(sequencePreflopClient.rootStudyHash).toBe("586e0c0777ca9fa8f48e957f126a8f24c8b9382ba9da1442f684fdcb5f40ff21");
  expect(sequencePreflopClient.route).toBe("#root-sequence-preflop");
  expect(sequencePreflopClient.rootSessionKey).toBe(`pokerlab:root-sequence-preflop:v1:${sequenceMetadata.studyHash}`);
  expect(sequenceQuestion.context).toEqual(minimumQuestion.context);
  expect(sequenceQuestion.board).toEqual([]);
  expect(sequenceMetadata.trainerAdmission).toBe(false);
  expect(new Set(studies.map(s => s.client.rootSessionKey)).size).toBe(4);
});
it("retains the full-action ceiling, commitment shift and unresolved raise", () => {
  expect(sequenceGrades[2].move.lowerEvBb).toBe(1.211141818822754);
  expect(sequenceGrades[2].move.upperEvBb).toBe(minimumGrades[2].move.upperEvBb);
  expect(sequenceGrades[2].move.upperLossBb).toBe(0.5746374641483261);
  expect(sequenceGrades[2].move.classification).toBe("STRATEGY_DEPENDENT");
  expect(sequenceGrades.slice(0, 2).map(g => g.move)).toEqual(minimumGrades.slice(0, 2).map(g => g.move));
});
it.each(pairs)("rejects $from.name source pins and grades in $to.name while checking exact public question equality", ({ from, to }) => {
  expect(() => to.client.parseRootMetadata(from.metadata)).toThrow(RootProtocolError);
  if (rootEqual(from.question, to.question)) expect(to.client.parseRootQuestion(from.question, to.metadata)).toEqual(to.question);
  else expect(() => to.client.parseRootQuestion(from.question, to.metadata)).toThrow(RootProtocolError);
  expect(() => to.client.parseRootGrade(from.grades[2], to.question, "raise:9.0")).toThrow(RootProtocolError);
  expect(() => to.client.parseRootReview(from.review([]), to.metadata, to.question)).toThrow(RootProtocolError);
});
it("keeps all four research studies reachable from the solver section", () => {
  window.history.replaceState(null, "", "#solver"); render(<FrontendRoot />);
  expect(screen.getByRole("link", { name: /complete-menu preflop practice/ })).toHaveAttribute("href", batchPreflopClient.route);
  expect(screen.getByRole("link", { name: /sequence solver research practice/ })).toHaveAttribute("href", sequencePreflopClient.route);
  expect(screen.getByRole("link", { name: /tighter raise bounds/ })).toHaveAttribute("href", minimumPreflopClient.route);
  expect(screen.getByRole("link", { name: /compare fold, call and raise EVs/ })).toHaveAttribute("href", legacyRootClient.route);
});
it.each(pairs)("isolates restore and reset from $from.name to $to.name", async ({ from, to }) => {
  const saved = studies.map(s => JSON.stringify(s.review([{ sourceCaseIndex: 0, action: s.action }])));
  studies.forEach((s, i) => localStorage.setItem(s.client.rootSessionKey, saved[i]));
  api(); window.history.replaceState(null, "", from.client.route); render(<FrontendRoot />);
  await screen.findByRole("heading", { name: from.verdict });
  navigate(to.client.route); await screen.findByRole("heading", { name: to.verdict });
  await waitFor(() => expect(window.location.hash).toBe(`${to.client.route}?study=${to.metadata.studyHash}`));
  fireEvent.click(screen.getByRole("button", { name: "Start over" }));
  expect(screen.getByRole("button", { name: "Call 2 bb" })).toBeEnabled();
  studies.forEach((s, i) => expect(localStorage.getItem(s.client.rootSessionKey)).toBe(s === to ? null : saved[i]));
});
it.each(studies.filter(s => s.name === "original" || s.name === "minimum"))("discards a $name session stored under the sequence key without deleting its real session", async foreign => {
  const saved = JSON.stringify(foreign.review([{ sourceCaseIndex: 0, action: foreign.action }]));
  localStorage.setItem(foreign.client.rootSessionKey, saved);
  localStorage.setItem(sequencePreflopClient.rootSessionKey, saved);
  api(); window.history.replaceState(null, "", sequencePreflopClient.route); render(<FrontendRoot />);
  await screen.findByRole("button", { name: "Fold" });
  expect(screen.getByText(/could not be restored/)).toBeInTheDocument();
  expect(localStorage.getItem(sequencePreflopClient.rootSessionKey)).toBeNull();
  expect(localStorage.getItem(foreign.client.rootSessionKey)).toBe(saved);
});
it.each(pairs.filter(p => p.from.name === "sequence" || p.to.name === "sequence" || p.from.name === "batch" || p.to.name === "batch"))(
  "aborts a pending $from.name grade and ignores its late reply after opening $to.name", async ({ from, to }) => {
    let resolve!: (value: Response) => void, signal!: AbortSignal;
    api({ client: from.client, wait: s => { signal = s; return new Promise(r => { resolve = r; }); } });
    window.history.replaceState(null, "", from.client.route); render(<FrontendRoot />);
    fireEvent.click(await screen.findByRole("button", { name: "Call 2 bb" }));
    navigate(to.client.route); await screen.findByRole("button", { name: "Raise to 9 bb" });
    await waitFor(() => expect(window.location.hash).toBe(`${to.client.route}?study=${to.metadata.studyHash}`));
    expect(signal.aborted).toBe(true);
    await act(async () => resolve(await response(from.grades[1])));
    expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
    expect(localStorage.getItem(from.client.rootSessionKey)).toBeNull();
    expect(localStorage.getItem(to.client.rootSessionKey)).toBeNull();
  });

it("pins complete-menu evidence separately from the earlier sequence study", () => {
  expect(batchPreflopClient.rootStudyHash).toBe("a7533a49a06fe2b7376109bce133c1b3c937dbef8915ed47af86290d36b777c8");
  expect(batchPreflopClient.route).toBe("#root-batch-preflop");
  expect(batchPreflopClient.rootSessionKey).toBe(`pokerlab:root-batch-preflop:v1:${batchMetadata.studyHash}`);
  expect(batchMetadata.startupBudget.maximumMinimumLpSolves).toBe(3);
  expect(batchMetadata.trainerAdmission).toBe(false);
  expect(batchQuestion.context).toEqual(sequenceQuestion.context);
  expect(batchQuestion.board).toEqual([]);
  expect(batchQuestion).toEqual(sequenceQuestion);
  expect(batchGrades[2].move.lowerEvBb).toBe(sequenceGrades[2].move.lowerEvBb);
  expect(batchGrades[2].move.upperEvBb).toBe(sequenceGrades[2].move.upperEvBb);
  expect(batchGrades[2].move.classification).toBe("STRATEGY_DEPENDENT");
});
it.each(studies.filter(s => s.name !== "batch"))("discards a $name session under the batch key while preserving its real session", async foreign => {
  const saved = JSON.stringify(foreign.review([{ sourceCaseIndex: 0, action: foreign.action }]));
  localStorage.setItem(foreign.client.rootSessionKey, saved);
  localStorage.setItem(batchPreflopClient.rootSessionKey, saved);
  api(); window.history.replaceState(null, "", batchPreflopClient.route); render(<FrontendRoot />);
  await screen.findByRole("button", { name: "Fold" });
  expect(screen.getByText(/could not be restored/)).toBeInTheDocument();
  expect(localStorage.getItem(batchPreflopClient.rootSessionKey)).toBeNull();
  expect(localStorage.getItem(foreign.client.rootSessionKey)).toBe(saved);
});
