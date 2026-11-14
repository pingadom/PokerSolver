import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { bankRaiseMetadata as metadata, bankRaisePreflopClient as client } from "./bankRaisePreflopApi";
import { bankPreflopClient } from "./bankPreflopApi";
import saved from "./test-data/bank-raise-preflop-drill.json";
import old from "./test-data/bank-preflop-drill.json";
import type { RootChoice, RootGrade, RootQuestion, RootReview } from "./bankPreflopProtocol";
const questions = saved.snapshot.questions as RootQuestion[], grades = saved.grades as RootGrade[];
const response = (body: unknown, status = 200) => Promise.resolve({ ok: status < 400, status, text: async () => typeof body === "string" ? body : JSON.stringify(body) } as Response);
function fixtureReview(choices: RootChoice[]): RootReview {
  const attempts = choices.map((attempt, ordinal) => ({ ordinal, attempt, grade: grades.find(g => g.sourceCaseIndex === attempt.sourceCaseIndex && g.move.action === attempt.action)! }));
  return { studyHash: metadata.studyHash, attempts, counts: client.rootReviewCounts(attempts) };
}
function mockApi(options: { status?: number; borrowed?: boolean; grade?: (g: RootGrade, init?: RequestInit) => Promise<Response>; metadata?: unknown } = {}) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/review/export")) return response(JSON.stringify(fixtureReview(body.choices)) + "\n");
    if (path.endsWith("/review")) return response(fixtureReview(body.choices));
    if (path.endsWith("/grade")) { const g = grades.find(g => g.sourceCaseIndex === body.sourceCaseIndex && g.move.action === body.action)!; return options.grade ? options.grade(g, init) : response(g); }
    if (path.includes("/questions/")) { const index = Number(path.split("/questions/")[1].split("?")[0]); return response((options.borrowed ? old.questions : questions).find(q => q.sourceCaseIndex === index)); }
    return response(options.metadata ?? metadata, options.status);
  });
}
const button = (name: string) => screen.getByRole("button", { name });
const posts = (fetch: ReturnType<typeof mockApi>, path: string) => fetch.mock.calls.filter(([url]) => String(url).endsWith(path));
function open(query = "") { window.history.replaceState(null, "", client.route + query); return render(<FrontendRoot />); }
async function ready() { await screen.findByRole("button", { name: "Call 2 bb" }); await waitFor(() => expect(button("Call 2 bb")).toBeEnabled()); }
afterEach(() => { window.history.replaceState(null, "", "/"); localStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("loads only original IDs zero and two and displays both hands around the table", async () => {
  const fetch = mockApi(); open(); await ready();
  expect(fetch.mock.calls).toHaveLength(3);
  expect(fetch.mock.calls.some(([url]) => String(url).includes("/questions/1?"))).toBe(false);
  expect(screen.getByRole("heading", { name: "Defend pocket sevens" })).toBeInTheDocument();
  expect(screen.getByLabelText("Two-hand qualified raise feedback decision")).toBeInTheDocument();
  expect(screen.getAllByLabelText(/^(UTG|HJ|CO|BTN|SB|BB) player$/)).toHaveLength(6);
  expect(screen.getByLabelText("BB player")).toHaveClass("hero", "acting");
  expect(screen.getByLabelText("BTN player")).toHaveClass("villain", "move-raise");
  expect(screen.queryByRole("button", { name: "Practise pocket nines" })).not.toBeInTheDocument();
  expect(within(screen.getByRole("group", { name: "Hero hand" })).getAllByRole("button")).toHaveLength(2);
  for (const name of ["Fold", "Call 2 bb", "Raise to 9 bb"]) expect(button(name)).toHaveTextContent("EV");
});
it("deep-links to source two using its original ID rather than array position", async () => {
  mockApi(); open("?question=2&mode=manual"); await ready();
  expect(screen.getByRole("heading", { name: "Defend suited ace-jack" })).toBeInTheDocument();
  expect(button("Practise suited ace-jack")).toHaveAttribute("aria-pressed", "true");
  expect(window.location.hash).toContain("question=2");
});
it.each(grades)("grades original hand $sourceCaseIndex move $move.action with only server choices", async g => {
  const fetch = mockApi(); open(`?question=${g.sourceCaseIndex}&mode=manual`); await ready();
  fireEvent.click(button(g.move.action === "raise:9.0" ? "Raise to 9 bb" : g.move.action === "call" ? "Call 2 bb" : "Fold"));
  const verdict = g.move.classification === "WITHIN_LIMIT" ? "Within limit" : g.move.classification === "OUTSIDE_LIMIT" ? "Outside limit" : "Unresolved";
  await screen.findByRole("heading", { name: verdict });
  expect(JSON.parse(String(posts(fetch, "/grade")[0][1]!.body))).toEqual({ studyHash: metadata.studyHash, sourceCaseIndex: g.sourceCaseIndex, action: g.move.action });
  expect(JSON.parse(localStorage.getItem(client.rootSessionKey)!).attempts[0].grade).toEqual(g);
  expect(button("Next hand")).toBeEnabled();
});
it("manual next-hand navigation wraps zero to two to zero without requesting source one", async () => {
  const fetch = mockApi(); open("?mode=manual"); await ready();
  fireEvent.click(button("Next hand")); expect(screen.getByRole("heading", { name: "Defend suited ace-jack" })).toBeInTheDocument();
  fireEvent.click(button("Next hand")); expect(screen.getByRole("heading", { name: "Defend pocket sevens" })).toBeInTheDocument();
  expect(fetch.mock.calls).toHaveLength(3);
});
it("mixed rounds alternate only the two qualified hands", async () => {
  mockApi(); open(); await ready();
  for (let i = 0; i < 8; i++) {
    fireEvent.click(button("Next hand"));
    expect(screen.getByRole("heading", { name: i % 2 === 0 ? "Defend suited ace-jack" : "Defend pocket sevens" })).toBeInTheDocument();
    expect(window.location.hash).toContain(i % 2 === 0 ? "question=2" : "question=0");
  }
});
it("reviews a mixed session with separate original IDs and conservative loss scope", async () => {
  const fetch = mockApi(); open(); await ready();
  fireEvent.click(button("Call 2 bb")); await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(button("Practise suited ace-jack"));fireEvent.click(button("Raise to 9 bb"));await screen.findByRole("heading", { name: "Unresolved" });
  fireEvent.click(button("Review 2 attempts"));await screen.findByRole("heading", { name: "2 attempts · 2 hands" });
  expect(JSON.parse(String(posts(fetch, "/review")[0][1]!.body)).choices).toEqual([{ sourceCaseIndex: 0, action: "call" }, { sourceCaseIndex: 2, action: "raise:9.0" }]);
  fireEvent.click(screen.getByText("EV scope, public history and exact bounds"));
  expect(screen.getByText(/Loss bounds conservatively compare all three moves/)).toBeInTheDocument();
  expect(screen.queryByText(/Loss bounds compare moves jointly/)).not.toBeInTheDocument();
});
it.each(["?question=1", "?question=3", "?question=0&question=2", "?study=" + old.metadata.studyHash, "?mode=unknown"])("rejects unsupported or ambiguous route %s", async query => {
  const fetch = mockApi(); open(query); await screen.findByRole("alert");
  expect(screen.queryByRole("button", { name: "Call 2 bb" })).not.toBeInTheDocument();
  expect(fetch.mock.calls).toHaveLength(1);
});
it("keeps an unavailable server explicit and never renders frozen questions as a fallback", async () => {
  mockApi({ status: 404 });open();await screen.findByRole("alert");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
  expect(button("Load current study")).toBeEnabled();
});
it("rejects the earlier bank's borrowed full menu and changed inventory metadata", async () => {
  mockApi({ borrowed: true });open();await screen.findByRole("alert");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
});
it("restores a mixed pinned session without touching the original bank's saved session", async () => {
  const choices: RootChoice[] = [{ sourceCaseIndex: 0, action: "fold" }, { sourceCaseIndex: 2, action: "raise:9.0" }];
  localStorage.setItem(client.rootSessionKey, JSON.stringify(fixtureReview(choices)));
  localStorage.setItem(bankPreflopClient.rootSessionKey, JSON.stringify(old.review));
  mockApi();open("?question=2");await screen.findByRole("heading", { name: "Unresolved" });
  expect(screen.getByText(/Restored 2 saved attempts/)).toBeInTheDocument();
  expect(localStorage.getItem(bankPreflopClient.rootSessionKey)).toBe(JSON.stringify(old.review));
});
it("discards a copied old-bank session instead of assigning its grades to the new source", async () => {
  localStorage.setItem(client.rootSessionKey, JSON.stringify(old.review));mockApi();open();await ready();
  expect(screen.getByText(/Saved practice could not be restored/)).toBeInTheDocument();
  expect(localStorage.getItem(client.rootSessionKey)).toBeNull();expect(button("Review 0 attempts")).toBeEnabled();
});
it("enforces the 128-attempt session cap while keeping review and export available", async () => {
  const r = fixtureReview(Array.from({ length: 128 }, () => ({ sourceCaseIndex: 2, action: "raise:9.0" })));
  localStorage.setItem(client.rootSessionKey, JSON.stringify(r));mockApi();open("?question=2");
  await screen.findByText(/Attempt limit reached/);expect(button("Call 2 bb")).toBeDisabled();
  fireEvent.click(button("Review 128 attempts"));await screen.findByRole("heading", { name: "128 attempts · 1 hand" });
  expect(button("Download session review")).toBeEnabled();
});
it("aborts a late grade when an external route change invalidates the question", async () => {
  let resolve!: (r: Response) => void, signal: AbortSignal | undefined;
  const deferred = new Promise<Response>(done => { resolve = done; });
  mockApi({ grade: (_, init) => { signal = init?.signal as AbortSignal; return deferred; } });
  open("?question=2");await ready();fireEvent.click(button("Call 2 bb"));
  act(() => { window.history.replaceState(null, "", client.route + "?question=1");window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("alert");expect(signal?.aborted).toBe(true);
  await act(async () => { resolve(await response(grades.find(g => g.sourceCaseIndex === 2 && g.move.action === "call")!)); });
  expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();expect(localStorage.getItem(client.rootSessionKey)).toBeNull();
});
it("ignores modified, repeated and focused keys then grades the active source on a valid shortcut", async () => {
  const fetch = mockApi();open("?question=2");await ready();
  fireEvent.keyDown(window, { key: "3", ctrlKey: true });fireEvent.keyDown(window, { key: "3", repeat: true });fireEvent.keyDown(button("Call 2 bb"), { key: "3" });
  expect(posts(fetch, "/grade")).toHaveLength(0);fireEvent.keyDown(window, { key: "3" });await screen.findByRole("heading", { name: "Unresolved" });
  expect(JSON.parse(String(posts(fetch, "/grade")[0][1]!.body)).sourceCaseIndex).toBe(2);
});
