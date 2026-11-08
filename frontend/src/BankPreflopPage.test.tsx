import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { bankPreflopClient as client, bankHandNames } from "./bankPreflopApi";
import { bankMetadata as metadata, bankQuestions, bankGrades, bankFixtureReview } from "./test-data/bankPreflop";
import type { RootGrade } from "./bankPreflopProtocol";
import { sevensPreflopClient } from "./sevensPreflopApi";
const response = (body: unknown, status = 200) => Promise.resolve({ ok: status < 400, status,
  text: async () => typeof body === "string" ? body : JSON.stringify(body) } as Response);
function mockApi(options: { status?: number; badQuestion?: boolean; badReview?: boolean; badExport?: boolean; grade?: (g: RootGrade) => Promise<Response> } = {}) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/review/export")) return response(JSON.stringify(bankFixtureReview(options.badExport ? [] : body.choices)) + "\n");
    if (path.endsWith("/review")) return response(bankFixtureReview(options.badReview ? [] : body.choices));
    if (path.endsWith("/grade")) { const g = bankGrades.find(g => g.sourceCaseIndex === body.sourceCaseIndex && g.move.action === body.action)!; return options.grade ? options.grade(g) : response(g); }
    if (path.includes("/questions/")) { const index = Number(path.split("/questions/")[1].split("?")[0]); return response(bankQuestions[options.badQuestion ? 0 : index]); }
    return response(metadata, options.status);
  });
}
const button = (name: string) => screen.getByRole("button", { name });
const posts = (fetch: ReturnType<typeof mockApi>, suffix: string) => fetch.mock.calls.filter(([url]) => String(url).endsWith(suffix));
function open(query = "") { window.history.replaceState(null, "", client.route + query); return render(<FrontendRoot />); }
async function ready() { await screen.findByRole("button", { name: "Call 2 bb" }); }
afterEach(() => { window.history.replaceState(null, "", "/"); localStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("loads every server question before showing six seats and inline EV actions", async () => {
  const fetch = mockApi(); open(); await ready();
  expect(fetch.mock.calls).toHaveLength(4);
  expect(screen.getAllByLabelText(/^(UTG|HJ|CO|BTN|SB|BB) player$/)).toHaveLength(6);
  expect(screen.getByLabelText("BB player")).toHaveClass("hero", "acting");
  expect(screen.getByLabelText("BTN player")).toHaveClass("villain", "move-raise");
  expect(screen.getByRole("heading", { name: "Defend pocket sevens" })).toBeInTheDocument();
  for (const name of ["Fold", "Call 2 bb", "Raise to 9 bb"]) expect(button(name)).toHaveTextContent("EV");
  expect(button("Next hand")).toBeEnabled(); expect(button("Mixed practice")).toHaveAttribute("aria-pressed", "true");
});
it.each(bankQuestions.flatMap(q => q.moves.map(m => [q.sourceCaseIndex, m.action] as const)))("grades hand %s action %s using its own server values", async (index, action) => {
  const fetch = mockApi(); open(`?question=${index}&mode=manual`); await ready();
  expect(screen.getByRole("heading", { name: `Defend ${bankHandNames[index]}` })).toBeInTheDocument();
  fireEvent.click(button(action === "call" ? "Call 2 bb" : action === "fold" ? "Fold" : "Raise to 9 bb"));
  const verdict = action === "call" ? "Within limit" : action === "fold" ? "Outside limit" : "Unresolved";
  await screen.findByRole("heading", { name: verdict });
  expect(JSON.parse(String(posts(fetch, "/grade")[0][1]?.body))).toEqual({ studyHash: metadata.studyHash, sourceCaseIndex: index, action });
  expect(button("Next hand")).toBeEnabled();
});
it("changes cards and headings without erasing history or borrowing feedback", async () => {
  const fetch = mockApi(); open(); await ready();
  fireEvent.click(button("Practise suited ace-jack"));
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♣J♣");
  fireEvent.click(button("Call 2 bb")); await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(button("Practise pocket nines"));
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("9♦9♥");
  expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
  fireEvent.click(button("Fold")); await screen.findByRole("heading", { name: "Outside limit" });
  fireEvent.click(button("Review 2 attempts"));
  const review = await screen.findByLabelText("Preflop session review");
  expect(review).toHaveTextContent("2 attempts · 2 hands");
  const rows = within(review).getAllByRole("listitem"); expect(rows[0]).toHaveTextContent("AJs"); expect(rows[1]).toHaveTextContent("99");
  expect(posts(fetch, "/grade").map(p => JSON.parse(String(p[1]?.body)).sourceCaseIndex)).toEqual([2, 1]);
});
it("cycles all hands in balanced mixed rounds without making extra fetches", async () => {
  vi.spyOn(Math, "random").mockReturnValue(0);
  const fetch = mockApi(); open(); await ready();
  const seen = [screen.getByRole("heading", { name: /Defend / }).textContent];
  for (let i = 0; i < 5; i++) { fireEvent.click(button("Next hand")); seen.push(screen.getByRole("heading", { name: /Defend / }).textContent); }
  expect(new Set(seen.slice(0, 3)).size).toBe(3); expect(new Set(seen.slice(3, 6)).size).toBe(3);
  for (let i = 1; i < seen.length; i++) expect(seen[i]).not.toBe(seen[i - 1]);
  expect(fetch.mock.calls).toHaveLength(4);
});
it("restores a mixed history without showing the last other-hand grade", async () => {
  localStorage.setItem(client.rootSessionKey, JSON.stringify(bankFixtureReview([{ sourceCaseIndex: 0, action: "fold" }, { sourceCaseIndex: 1, action: "call" }])));
  mockApi(); open("?question=2&mode=manual"); await ready();
  expect(screen.getByText(/Restored 2 saved attempts/)).toBeInTheDocument(); expect(button("Call 2 bb")).toBeEnabled();
  expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
  fireEvent.click(button("Review 2 attempts")); expect(await screen.findByRole("heading", { name: "2 attempts · 2 hands" })).toBeInTheDocument();
});
it("isolates storage and clears only this bank on reset or invalid saved data", async () => {
  localStorage.setItem(sevensPreflopClient.rootSessionKey, "keep older study");
  localStorage.setItem(client.rootSessionKey, JSON.stringify({ studyHash: sevensPreflopClient.rootStudyHash, attempts: [], counts: {} }));
  mockApi(); open(); await ready();
  expect(screen.getByText(/Saved practice could not be restored/)).toBeInTheDocument();
  fireEvent.click(button("Start over")); expect(localStorage.getItem(sevensPreflopClient.rootSessionKey)).toBe("keep older study");
  expect(localStorage.getItem(client.rootSessionKey)).toBeNull();
});
it("blocks hand switches, double clicks and shortcuts while a grade is pending", async () => {
  let resolve!: (value: Response) => void;
  const fetch = mockApi({ grade: () => new Promise(r => { resolve = r; }) }); open("?question=2"); await ready();
  fireEvent.click(button("Call 2 bb")); fireEvent.click(button("Call 2 bb")); fireEvent.keyDown(window, { key: "1" });
  expect(button("Next hand")).toBeDisabled(); expect(button("Practise pocket sevens")).toBeDisabled(); expect(posts(fetch, "/grade")).toHaveLength(1);
  await act(async () => resolve(await response(bankGrades.find(g => g.sourceCaseIndex === 2 && g.move.action === "call")!)));
  await screen.findByRole("heading", { name: "Within limit" });
});
it("rejects a borrowed other-hand grade rather than recording it", async () => {
  mockApi({ grade: () => response(bankGrades[1]) }); open("?question=2"); await ready(); fireEvent.click(button("Call 2 bb"));
  await screen.findByRole("alert"); expect(localStorage.getItem(client.rootSessionKey)).toBeNull(); expect(screen.queryByText("Your choice is recorded")).not.toBeInTheDocument();
});
it("aborts a pending grade when the page is unmounted", async () => {
  let resolve!: (value: Response) => void;
  mockApi({ grade: () => new Promise(r => { resolve = r; }) }); const view = open(); await ready(); fireEvent.click(button("Call 2 bb")); view.unmount();
  await act(async () => resolve(await response(bankGrades[1]))); expect(localStorage.getItem(client.rootSessionKey)).toBeNull();
});
it.each([404, 503])("does not use frozen questions when metadata returns %s", async status => {
  mockApi({ status }); open(); await screen.findByRole("alert"); expect(screen.queryByRole("button", { name: "Call 2 bb" })).not.toBeInTheDocument();
});
it("rejects a server that returns a valid but wrong requested hand", async () => {
  mockApi({ badQuestion: true }); open(); await screen.findByRole("alert"); expect(screen.queryByLabelText("BB player")).not.toBeInTheDocument();
});
it.each(["?question=3", "?question=-1", "?question=00", "?question=0.5", "?mode=invalid", "?mode=mixed&mode=manual", "?question=1&question=2", "?unknown=1", "?study=old"])("rejects malformed or stale route %s", async query => {
  mockApi(); open(query); await screen.findByRole("alert"); expect(screen.queryByRole("button", { name: "Next hand" })).not.toBeInTheDocument();
});
it("exports only validated source-specific choices", async () => {
  const fetch = mockApi(); const create = vi.fn(() => "blob:bank"); vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: create, revokeObjectURL: vi.fn() }));
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
  open("?question=2"); await ready(); fireEvent.click(button("Call 2 bb")); await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(button("Review 1 attempt")); await screen.findByLabelText("Preflop session review");
  await act(async () => { fireEvent.click(button("Download session review")); await Promise.resolve(); });
  await vi.waitFor(() => expect(click).toHaveBeenCalledOnce());
  expect(JSON.parse(String(posts(fetch, "/review/export")[0][1]?.body))).toEqual({ studyHash: metadata.studyHash, choices: [{ sourceCaseIndex: 2, action: "call" }] });
});
it("rejects a mismatched review and keeps the saved choices", async () => {
  mockApi({ badReview: true }); open(); await ready(); fireEvent.click(button("Call 2 bb")); await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(button("Review 1 attempt")); await screen.findByRole("alert"); expect(JSON.parse(localStorage.getItem(client.rootSessionKey)!).attempts).toHaveLength(1);
});
it("preserves the maximum mixed session and lets reset restore practice", async () => {
  localStorage.setItem(client.rootSessionKey, JSON.stringify(bankFixtureReview(Array.from({ length: 128 }, (_, i) => ({ sourceCaseIndex: i % 3, action: "call" })))));
  mockApi(); open(); await ready(); expect(button("Call 2 bb")).toBeDisabled(); fireEvent.click(button("Review 128 attempts"));
  expect(await screen.findByRole("heading", { name: "128 attempts · 3 hands" })).toBeInTheDocument(); fireEvent.click(button("Start over")); expect(button("Call 2 bb")).toBeEnabled();
});
