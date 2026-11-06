import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { legacyRootClient } from "./rootPreflopApi";
import { minimumPreflopClient } from "./minimumPreflopApi";
import type { RootGrade } from "./rootPreflopProtocol";
import { rootFixtureReview as legacyReview, rootGrades as legacyGrades, rootMetadata as legacyMetadata, rootQuestion as legacyQuestion } from "./test-data/rootPreflop";
import { minimumFixtureReview, minimumGrades, minimumMetadata, minimumQuestion } from "./test-data/minimumPreflop";

import { sequencePreflopClient } from "./sequencePreflopApi";
import { sequenceFixtureReview, sequenceGrades, sequenceMetadata, sequenceQuestion } from "./test-data/sequencePreflop";

describe.each([
  { name: "original envelope", client: legacyRootClient, metadata: legacyMetadata, rootQuestion: legacyQuestion, rootGrades: legacyGrades, rootFixtureReview: legacyReview },
  { name: "minimum informed", client: minimumPreflopClient, metadata: minimumMetadata, rootQuestion: minimumQuestion, rootGrades: minimumGrades, rootFixtureReview: minimumFixtureReview },
  { name: "sequence flow", client: sequencePreflopClient, metadata: sequenceMetadata, rootQuestion: sequenceQuestion, rootGrades: sequenceGrades, rootFixtureReview: sequenceFixtureReview },
])("$name table", ({ client, metadata, rootQuestion, rootGrades, rootFixtureReview }) => {
const { rootApiBase, rootSessionKey } = client;
const response = (body: unknown, status = 200) => Promise.resolve({ ok: status < 400, status,
  text: async () => typeof body === "string" ? body : JSON.stringify(body) } as Response);
function mockApi(options: { status?: number; grade?: (g: RootGrade) => Promise<Response>; badQuestion?: boolean; badReview?: boolean; badExport?: boolean } = {}) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/review/export")) return response(JSON.stringify(rootFixtureReview(options.badExport ? [] : body.choices)) + "\n");
    if (path.endsWith("/review")) return response(rootFixtureReview(options.badReview ? [] : body.choices));
    if (path.endsWith("/grade")) {
      const g = structuredClone(rootGrades.find(g => g.move.action === body.action)!);
      return options.grade ? options.grade(g) : response(g);
    }
    if (path.includes("/questions/")) return response(options.badQuestion ? { ...rootQuestion, board: ["2s", "8s", "Ts"] } : rootQuestion);
    return response(metadata, options.status);
  });
}
const button = (name: string) => screen.getByRole("button", { name });
const posts = (fetch: ReturnType<typeof mockApi>, suffix: string) => fetch.mock.calls.filter(([u]) => String(u).endsWith(suffix));
function open(query = "") { window.history.replaceState(null, "", client.route + query); return render(<FrontendRoot />); }
async function choose(action = "Call 2 bb") { fireEvent.click(await screen.findByRole("button", { name: action })); }
afterEach(() => { window.history.replaceState(null, "", "/"); localStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("loads the pinned public question before rendering six seats, a blue BB and red button", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Raise to 9 bb" });
  expect(screen.getByLabelText("BB player")).toHaveClass("hero", "acting", "position-0");
  expect(screen.getByLabelText("BTN player")).toHaveClass("villain", "move-raise");
  expect(screen.getByLabelText("BTN player")).toHaveTextContent("Raised to 3 bb");
  expect(screen.getAllByLabelText(/^(UTG|HJ|CO|BTN|SB|BB) player$/)).toHaveLength(6);
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("9♦9♥");
  expect(screen.queryByLabelText("Flop board")).not.toBeInTheDocument();
  for (const name of ["Fold", "Call 2 bb", "Raise to 9 bb"]) expect(button(name)).toHaveTextContent("EV");
  expect(button("Raise to 9 bb")).toHaveTextContent("8 bb more committed");
  expect(fetch.mock.calls).toHaveLength(2);
  expect(fetch.mock.calls.every(([url]) => String(url).startsWith(rootApiBase))).toBe(true);
  expect(window.location.hash).toBe(`${client.route}?study=${metadata.studyHash}`);
});
it.each([["Fold", "Outside limit", "fold"], ["Call 2 bb", "Within limit", "call"], ["Raise to 9 bb", "Unresolved", "raise:9.0"]])("grades %s with feedback under the controls", async (label, verdict, action) => {
  const fetch = mockApi(); open(); await choose(label);
  expect(await screen.findByRole("heading", { name: verdict })).toBeInTheDocument();
  expect(screen.getByLabelText("BB player")).toHaveClass(`move-${action.startsWith("raise") ? "raise" : action}`);
  expect(button(label)).toHaveTextContent("Your choice"); expect(button(label)).toBeDisabled();
  expect(button("Try another move")).toBeEnabled();
  expect(JSON.parse(String(posts(fetch, "/grade")[0][1]?.body))).toEqual({ studyHash: metadata.studyHash, sourceCaseIndex: 0, action });
  if (action.startsWith("raise")) expect(screen.getByText(/neither a proven mistake nor a proven safe move/)).toBeInTheDocument();
});
it("records repeated choices, keeps EVs visible on retry and reviews all three classifications", async () => {
  const fetch = mockApi(); open();
  for (const [label, verdict] of [["Raise to 9 bb", "Unresolved"], ["Call 2 bb", "Within limit"], ["Raise to 9 bb", "Unresolved"], ["Fold", "Outside limit"]]) {
    if (screen.queryByRole("button", { name: "Try another move" })) fireEvent.click(button("Try another move"));
    await choose(label); await screen.findByRole("heading", { name: verdict });
  }
  fireEvent.click(button("Review 4 attempts"));
  const review = await screen.findByLabelText("Preflop session review");
  expect(review).toHaveTextContent("Within limit: 1 · Outside limit: 1 · Unresolved: 2");
  expect(within(review).getAllByRole("listitem")).toHaveLength(4);
  expect(posts(fetch, "/grade")).toHaveLength(4);
  expect(JSON.parse(String(posts(fetch, "/review")[0][1]?.body)).choices.map((c: { action: string }) => c.action)).toEqual(["raise:9.0", "call", "raise:9.0", "fold"]);
});
it("blocks duplicate clicks and keyboard submissions while a grade is pending", async () => {
  let resolve!: (value: Response) => void;
  const fetch = mockApi({ grade: () => new Promise(r => { resolve = r; }) }); open(); await choose();
  fireEvent.click(button("Call 2 bb")); fireEvent.keyDown(window, { key: "3" });
  expect(posts(fetch, "/grade")).toHaveLength(1);
  await act(async () => resolve(await response(rootGrades[1])));
  await screen.findByRole("heading", { name: "Within limit" });
});
it("restores source-validated attempts and resets without reloading or solving", async () => {
  const saved = rootFixtureReview([{ sourceCaseIndex: 0, action: "raise:9.0" }]); localStorage.setItem(rootSessionKey, JSON.stringify(saved));
  const fetch = mockApi(); open(); await screen.findByRole("heading", { name: "Unresolved" });
  expect(screen.getByText(/Restored 1 saved attempts/)).toBeInTheDocument();
  fireEvent.click(button("Start over")); expect(button("Call 2 bb")).toBeEnabled();
  expect(localStorage.getItem(rootSessionKey)).toBeNull(); expect(fetch.mock.calls).toHaveLength(2);
});
it("discards corrupt saved data and keeps practice available without storage", async () => {
  localStorage.setItem(rootSessionKey, "broken"); mockApi();
  vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("storage off"); });
  open(); await screen.findByRole("button", { name: "Call 2 bb" });
  expect(screen.getByText(/could not be restored/)).toBeInTheDocument();
  await choose(); await screen.findByRole("heading", { name: "Within limit" });
  expect(screen.getByText(/Browser storage is unavailable/)).toBeInTheDocument();
  expect(button("Review 1 attempt")).toBeEnabled();
});
it("makes a full128-attempt session reviewable and downloadable while blocking more grades", async () => {
  localStorage.setItem(rootSessionKey, JSON.stringify(rootFixtureReview(Array.from({ length: 128 }, () => ({ sourceCaseIndex: 0, action: "raise:9.0" })))));
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Review 128 attempts" });
  expect(button("Call 2 bb")).toBeDisabled(); expect(screen.queryByRole("button", { name: "Try another move" })).not.toBeInTheDocument();
  fireEvent.keyDown(window, { key: "2" }); expect(posts(fetch, "/grade")).toHaveLength(0);
  fireEvent.click(button("Review 128 attempts")); await screen.findByLabelText("Preflop session review");
  expect(button("Download session review")).toBeEnabled();
});
it("uses unmodified validated export bytes and a source-pinned filename", async () => {
  mockApi(); const create = vi.fn(() => "blob:root"); const revoke = vi.fn();
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  open(); await choose(); await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(button("Review 1 attempt")); await screen.findByLabelText("Preflop session review");
  fireEvent.click(button("Download session review")); await waitFor(() => expect(create).toHaveBeenCalledTimes(1));
  expect((click.mock.instances[0] as HTMLAnchorElement).download).toBe(`${client.exportPrefix}-${metadata.studyHash.slice(0, 12)}.json`);
  expect(revoke).toHaveBeenCalledWith("blob:root");
});
it.each(["?study=wrong", "?study=x&study=y", "?question=0", "?extra=x", "-old"])("rejects stale or invalid deep link %s", async query => {
  mockApi(); open(query); expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Fold" })).not.toBeInTheDocument();
});
it("shows disabled API without substituting the public fixture", async () => {
  mockApi({ status: 404 }); open(); expect(await screen.findByRole("alert")).toHaveTextContent("not enabled");
  expect(screen.queryByRole("button", { name: "Call 2 bb" })).not.toBeInTheDocument();
});
it("fails closed on a leaked future board or wrong grade", async () => {
  const fetch = mockApi({ badQuestion: true }); open(); await screen.findByRole("alert");
  expect(screen.queryByLabelText("Your cards")).not.toBeInTheDocument();
  const original = mockApi; fetch.mockRestore(); original({ grade: () => response(rootGrades[0]) });
  fireEvent.click(button("Load current study")); await choose();
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(localStorage.getItem(rootSessionKey)).toBeNull();
});
it.each(["review", "export"])("refuses a mismatched %s without discarding recorded attempts", async kind => {
  mockApi({ badReview: kind === "review", badExport: kind === "export" });
  const create = vi.fn(); vi.stubGlobal("URL", class extends URL { static createObjectURL = create; });
  open(); await choose(); await screen.findByRole("heading", { name: "Within limit" });
  const before = localStorage.getItem(rootSessionKey);
  fireEvent.click(button("Review 1 attempt"));
  if (kind === "export") { await screen.findByLabelText("Preflop session review"); fireEvent.click(button("Download session review")); }
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(localStorage.getItem(rootSessionKey)).toBe(before); expect(create).not.toHaveBeenCalled();
});
it("allows retry after a transient grade error without losing or duplicating an attempt", async () => {
  const fetch = mockApi({ grade: () => response({}, 503) }); open(); await choose(); await screen.findByRole("alert");
  expect(button("Call 2 bb")).toBeEnabled();
  fetch.mockRestore(); mockApi(); fireEvent.click(button("Dismiss error")); await choose();
  await screen.findByRole("heading", { name: "Within limit" }); expect(button("Review 1 attempt")).toBeEnabled();
});
it("aborts an old grade on hash navigation and ignores its late reply", async () => {
  let resolve!: (r: Response) => void;
  const fetch = mockApi({ grade: () => new Promise(r => { resolve = r; }) }); open(); await choose();
  const signal = posts(fetch, "/grade")[0][1]?.signal;
  act(() => { window.history.replaceState(null, "", `${client.route}?study=wrong`); window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("alert"); expect(signal?.aborted).toBe(true);
  await act(async () => resolve(await response(rootGrades[1])));
  expect(localStorage.getItem(rootSessionKey)).toBeNull(); expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
});
it("supports 1–3 shortcuts and ignores repeats, modifiers and interactive targets", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Fold" });
  for (const modifiers of [{ ctrlKey: true }, { shiftKey: true }, { repeat: true }, { altKey: true }, { metaKey: true }]) fireEvent.keyDown(window, { key: "1", ...modifiers });
  fireEvent.keyDown(button("Fold"), { key: "2" }); expect(posts(fetch, "/grade")).toHaveLength(0);
  fireEvent.keyDown(window, { key: "3" }); await screen.findByRole("heading", { name: "Unresolved" });
});

});
