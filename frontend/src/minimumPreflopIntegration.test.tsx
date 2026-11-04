import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { legacyRootClient } from "./rootPreflopApi";
import { minimumPreflopClient as client } from "./minimumPreflopApi";
import { RootProtocolError } from "./rootPreflopProtocol";
import { rootFixtureReview, rootGrades, rootMetadata, rootQuestion } from "./test-data/rootPreflop";
import { minimumFixtureReview, minimumGrades, minimumMetadata, minimumQuestion } from "./test-data/minimumPreflop";

const response = (value: unknown) => Promise.resolve({ ok: true, status: 200, text: async () => JSON.stringify(value) } as Response);
function navigate(hash: string) {
  act(() => { window.history.replaceState(null, "", hash); window.dispatchEvent(new HashChangeEvent("hashchange")); });
}
function api(deferred?: (signal: AbortSignal) => Promise<Response>) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const minimum = String(url).startsWith(client.rootApiBase);
    if (String(url).endsWith("/grade")) {
      if (!minimum && deferred) return deferred(init!.signal as AbortSignal);
      return response((minimum ? minimumGrades : rootGrades).find(g => g.move.action === JSON.parse(String(init?.body)).action));
    }
    if (String(url).includes("/questions/")) return response(minimum ? minimumQuestion : rootQuestion);
    return response(minimum ? minimumMetadata : rootMetadata);
  });
}
afterEach(() => { window.history.replaceState(null, "", "/"); localStorage.clear(); vi.restoreAllMocks(); });

it("pins the new study while preserving the public preflop context", () => {
  expect(client.rootStudyHash).toBe("baa6420476438abfca0c508d19ca313d8f9dc78f643293df9b2866418f15bf6d");
  expect(minimumQuestion.board).toEqual([]);
  expect(minimumQuestion.context).toEqual(rootQuestion.context);
});
it("keeps the full-action raise ceiling and unresolved verdict while tightening its minimum", () => {
  const current = minimumGrades[2].move, original = rootGrades[2].move;
  expect(current.lowerEvBb).toBeGreaterThan(1.21);
  expect(current.upperEvBb).toBe(original.upperEvBb);
  expect(current.upperEvBb).toBeGreaterThan(current.lowerEvBb + 0.57);
  expect(current.classification).toBe("STRATEGY_DEPENDENT");
  expect(current.lowerLossBb).toBe(0);
  expect(current.upperLossBb).toBeGreaterThan(0.57);
  expect(minimumGrades.slice(0, 2).map(g => g.move)).toEqual(rootGrades.slice(0, 2).map(g => g.move));
});
it("refuses to mix source metadata, questions, grades or reviews across the two studies", () => {
  for (const [own, foreignMetadata, foreignQuestion, foreignGrade, foreignReview] of [
    [client, rootMetadata, rootQuestion, rootGrades[2], rootFixtureReview([])],
    [legacyRootClient, minimumMetadata, minimumQuestion, minimumGrades[2], minimumFixtureReview([])],
  ] as const) {
    const metadata = own === client ? minimumMetadata : rootMetadata;
    const question = own === client ? minimumQuestion : rootQuestion;
    expect(() => own.parseRootMetadata(foreignMetadata)).toThrow(RootProtocolError);
    expect(() => own.parseRootQuestion(foreignQuestion, metadata)).toThrow(RootProtocolError);
    expect(() => own.parseRootGrade(foreignGrade, question, "raise:9.0")).toThrow(RootProtocolError);
    expect(() => own.parseRootReview(foreignReview, metadata, question)).toThrow(RootProtocolError);
  }
});
it("lets the home page reach both studies", () => {
  window.history.replaceState(null, "", "#solver"); render(<FrontendRoot />);
  expect(screen.getByRole("link", { name: /Pocket nines: tighter raise bounds/ })).toHaveAttribute("href", client.route);
  expect(screen.getByRole("link", { name: /Pocket nines: compare fold, call and raise EVs/ })).toHaveAttribute("href", legacyRootClient.route);
});
it("keeps separate saved sessions on navigation in either direction", async () => {
  const old = JSON.stringify(rootFixtureReview([{ sourceCaseIndex: 0, action: "fold" }]));
  const current = JSON.stringify(minimumFixtureReview([{ sourceCaseIndex: 0, action: "raise:9.0" }]));
  localStorage.setItem(legacyRootClient.rootSessionKey, old);
  localStorage.setItem(client.rootSessionKey, current);
  api(); window.history.replaceState(null, "", client.route); render(<FrontendRoot />);
  await screen.findByRole("heading", { name: "Unresolved" });
  expect(screen.getByText(/UPDATED RAISE BOUNDS/)).toBeInTheDocument();
  navigate(legacyRootClient.route); await screen.findByRole("heading", { name: "Outside limit" });
  expect(screen.getByText(/ORIGINAL RAISE BOUNDS/)).toBeInTheDocument();
  navigate(client.route); await screen.findByRole("heading", { name: "Unresolved" });
  fireEvent.click(screen.getByRole("button", { name: "Start over" }));
  expect(localStorage.getItem(client.rootSessionKey)).toBeNull();
  expect(localStorage.getItem(legacyRootClient.rootSessionKey)).toBe(old);
});
it("rejects a legacy review placed in the new session namespace without losing the legacy session", async () => {
  const old = JSON.stringify(rootFixtureReview([{ sourceCaseIndex: 0, action: "call" }]));
  localStorage.setItem(legacyRootClient.rootSessionKey, old); localStorage.setItem(client.rootSessionKey, old);
  api(); window.history.replaceState(null, "", client.route); render(<FrontendRoot />);
  await screen.findByRole("button", { name: "Fold" });
  expect(screen.getByText(/could not be restored/)).toBeInTheDocument();
  expect(localStorage.getItem(client.rootSessionKey)).toBeNull();
  expect(localStorage.getItem(legacyRootClient.rootSessionKey)).toBe(old);
  expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
});
it("aborts a pending legacy grade when switching studies and ignores its late reply", async () => {
  let resolve!: (r: Response) => void, signal!: AbortSignal;
  api(s => { signal = s; return new Promise(r => { resolve = r; }); });
  window.history.replaceState(null, "", legacyRootClient.route); render(<FrontendRoot />);
  fireEvent.click(await screen.findByRole("button", { name: "Call 2 bb" }));
  navigate(client.route); await screen.findByText(/UPDATED RAISE BOUNDS/);
  await screen.findByRole("button", { name: "Raise to 9 bb" });
  expect(signal.aborted).toBe(true);
  await act(async () => resolve(await response(rootGrades[1])));
  expect(localStorage.getItem(legacyRootClient.rootSessionKey)).toBeNull();
  expect(localStorage.getItem(client.rootSessionKey)).toBeNull();
  expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
  expect(window.location.hash).toBe(`${client.route}?study=${minimumMetadata.studyHash}`);
});
