import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { laterFlopIntervalFixture as fixture, laterFlopIntervalMetadata as metadata, laterFlopFixtureReview } from "./test-data/laterFlopInterval";
import type { LaterFlopIntervalFeedback, LaterFlopIntervalQuestion, LaterFlopIntervalReview } from "./laterFlopIntervalApi";

const response = (body: unknown, status = 200) => Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) } as Response);
function mockApi(options: { status?: number; question?: (q: LaterFlopIntervalQuestion) => unknown;
  grade?: (f: LaterFlopIntervalFeedback, init: RequestInit) => Promise<Response>; review?: (r: LaterFlopIntervalReview) => unknown } = {}) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/grade")) {
      const f = structuredClone(fixture.attempts.find((a) => a.action === body.action)!.feedback);
      return options.grade ? options.grade(f, init!) : response(f);
    }
    if (path.endsWith("/review")) { const r = laterFlopFixtureReview(body.actions); return response(options.review ? options.review(r) : r); }
    if (path.endsWith("/question")) { const q = structuredClone(fixture.question); return response(options.question ? options.question(q) : q); }
    return response(metadata, options.status);
  });
}
function open(pin = "") {
  window.history.replaceState(null, "", `#interval-flop-response${pin ? `?report=${pin}` : ""}`);
  return render(<FrontendRoot />);
}
const check = () => screen.getByRole("button", { name: "Fold" });
afterEach(() => { window.history.replaceState(null, "", "/"); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("routes the six-seat flop with public board, visible hero and hidden opponents", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Fold" });
  const table = screen.getByLabelText("Six-seat action table");
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("2♠8♠T♠");
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("7♦7♥");
  expect(screen.getByLabelText("BB player")).toHaveClass("hero", "acting", "position-0");
  expect(screen.getByLabelText("BTN player")).toHaveClass("villain", "move-raise");
  expect(screen.getByLabelText("BTN player")).toHaveTextContent("Bet 3.25 bb");
  expect(within(table).getAllByText("Out of hand")).toHaveLength(4);
  expect(table).toHaveTextContent("9.75 bb"); expect(table).toHaveTextContent("3.25 bb to call");
  expect(table.querySelectorAll(".poker-card-backs")).toHaveLength(5);
  expect(check()).not.toHaveTextContent("EV");
  expect(screen.getByRole("note")).toHaveTextContent("one flop bet then exact turn/river checkdown");
  expect(window.location.hash).toBe(`#interval-flop-response?report=${metadata.reportHash}`);
  expect(fetch.mock.calls.every(([url]) => String(url).startsWith("/api/v1/trainer/research/later-flop-interval"))).toBe(true);
});
it.each([["Fold", "Within limit", "move-fold"], ["Call 3.25 bb", "Outside limit", "move-call"]])(
  "grades %s with both action ranges, feedback and retry in the table controls", async (move, verdict, colour) => {
    const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Fold" });
    fireEvent.click(screen.getByRole("button", { name: move }));
    expect(await screen.findByRole("heading", { name: verdict })).toBeInTheDocument();
    const controls = screen.getByLabelText("Your decision controls");
    for (const label of ["Fold", "Call 3.25 bb"]) {
      const button = within(controls).getByRole("button", { name: label });
      expect(button).toHaveTextContent("Hand EV"); expect(button).toHaveTextContent("Loss");
      expect(button).toHaveAccessibleDescription(/Hand EV/); expect(button).toBeDisabled();
    }
    expect(screen.getByLabelText("BB player")).toHaveClass(colour);
    expect(controls.querySelector(".poker-decision-feedback")).toHaveTextContent("0.01 bb");
    expect(within(controls).getByRole("button", { name: "Try another move" })).toBeInTheDocument();
    expect(screen.getByText(/Hand EV is final chip profit/)).toBeInTheDocument();
    expect(screen.getByText(/conservative bounds from separate action endpoints/)).toBeInTheDocument();
    expect(screen.queryByText(/EV is incremental/)).not.toBeInTheDocument();
    const post = fetch.mock.calls.find(([url]) => String(url).endsWith("/grade"))!;
    expect(JSON.parse(String(post[1]?.body))).toEqual({ reportHash: metadata.reportHash, action: move === "Fold" ? "fold" : "call" });
  });
it("retains revealed ranges for another move and exports the exact server review", async () => {
  mockApi(); open(); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  fireEvent.click(await screen.findByRole("button", { name: "Try another move" }));
  expect(check()).toHaveTextContent("Hand EV"); expect(check()).toBeEnabled();
  fireEvent.click(screen.getByRole("button", { name: "Call 3.25 bb" }));
  await screen.findByRole("heading", { name: "Outside limit" });
  fireEvent.click(screen.getByRole("button", { name: "Review 2 attempts" }));
  const review = await screen.findByRole("region", { name: "Attempt review" });
  expect(review).toHaveTextContent("Within limit: 1 · Outside limit: 1 · Strategy-dependent: 0");
  const create = vi.fn((blob: Blob) => { expect(blob).toBeInstanceOf(Blob); return "blob:flop-review"; }), revoke = vi.fn();
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  const anchor = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  fireEvent.click(screen.getByRole("button", { name: "Download replayable review" }));
  const text = await new Promise<string>((resolve) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.readAsText(create.mock.calls[0][0] as unknown as Blob); });
  expect(JSON.parse(text)).toEqual(fixture); expect(anchor).toHaveBeenCalledOnce(); expect(revoke).toHaveBeenCalledWith("blob:flop-review");
});
it("supports an empty review without inventing an action", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Fold" });
  fireEvent.click(screen.getByRole("button", { name: "Review 0 attempts" }));
  expect(await screen.findByRole("region", { name: "Attempt review" })).toHaveTextContent("0 attempts");
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(0);
});
it("stops at 100 attempts while leaving review/export and reset available", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Fold" });
  for (let i = 0; i < 100; i++) {
    fireEvent.click(check()); await screen.findByRole("heading", { name: "Within limit" });
    if (i < 99) fireEvent.click(screen.getByRole("button", { name: "Try another move" }));
  }
  expect(screen.getByText(/Attempt limit reached/)).toBeInTheDocument();
  fireEvent.click(check()); fireEvent.keyDown(window, { key: "1" });
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(100);
  expect(screen.queryByRole("button", { name: "Try another move" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Start over" }));
  await waitFor(() => expect(check()).toBeEnabled());
  expect(check()).not.toHaveTextContent("EV");
}, 20000);
it("blocks stale URL pins and reloads the current report explicitly", async () => {
  mockApi(); open("a".repeat(64)); expect(await screen.findByRole("alert")).toHaveTextContent("report changed");
  expect(screen.queryByLabelText("Flop board")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Load current report" }));
  expect(await screen.findByRole("button", { name: "Fold" })).toBeEnabled();
});
it("shows the honest default-disabled state without sample fallback", async () => {
  mockApi({ status: 404 }); open(); expect(await screen.findByRole("alert")).toHaveTextContent("not enabled");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
});
it("rejects a board collision before practice", async () => {
  mockApi({ question: (q) => ({ ...q, context: { ...q.context, board: ["7d", "8s", "Ts"] } }) });
  open(); expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Fold" })).not.toBeInTheDocument();
});
it.each(["hash", "selection", "changed-ranges"])("rejects contradictory grades: %s", async (kind) => {
  let count = 0;
  mockApi({ grade: (f) => {
    count++;
    if (kind === "hash") f.reportHash = "b".repeat(64);
    if (kind === "selection") f.selected = f.alternatives[1];
    if (kind === "changed-ranges" && count > 1) f.alternatives[1].upperEvBb = -2;
    return response(f);
  } });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  if (kind === "changed-ranges") { fireEvent.click(await screen.findByRole("button", { name: "Try another move" })); fireEvent.click(check()); }
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Download replayable review" })).not.toBeInTheDocument();
});
it("rejects edited server review counts", async () => {
  mockApi({ review: (r) => ({ ...r, counts: { ...r.counts, withinLimit: 99 } }) }); open();
  fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  fireEvent.click(await screen.findByRole("button", { name: "Review 1 attempt" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Download replayable review" })).not.toBeInTheDocument();
});
it("allows network retry but blocks a rejected stale action", async () => {
  let count = 0; mockApi({ grade: (f) => ++count === 1 ? Promise.reject(new TypeError("offline")) : count === 2 ? response(f) : response({}, 400) });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("offline");
  await waitFor(() => expect(check()).toBeEnabled()); fireEvent.click(check());
  fireEvent.click(await screen.findByRole("button", { name: "Try another move" })); fireEvent.click(check());
  expect(await screen.findByRole("alert")).toHaveTextContent("server rejected");
});
it("prevents duplicate actions and ignores late grades after a report change", async () => {
  let resolve!: (value: Response) => void, signal: AbortSignal | undefined;
  const deferred = new Promise<Response>((done) => { resolve = done; });
  const fetch = mockApi({ grade: (_f, init) => { signal = init.signal as AbortSignal; return deferred; } });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Fold" })); fireEvent.click(check()); fireEvent.keyDown(window, { key: "1" });
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(1);
  act(() => { window.history.replaceState(null, "", `#interval-flop-response?report=${"b".repeat(64)}`); window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("alert"); expect(signal?.aborted).toBe(true);
  await act(async () => { resolve(await response(fixture.attempts[0].feedback)); });
  expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
});
it("guards keyboard shortcuts and aborts on unmount", async () => {
  const fetch = mockApi(), view = open(); await screen.findByRole("button", { name: "Fold" });
  await waitFor(() => expect(check()).toBeEnabled());
  fireEvent.keyDown(window, { key: "1", repeat: true }); fireEvent.keyDown(window, { key: "1", ctrlKey: true }); fireEvent.keyDown(check(), { key: "1" });
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(0);
  fireEvent.keyDown(window, { key: "1" }); await screen.findByRole("heading", { name: "Within limit" });
  const request = fetch.mock.calls.find(([url]) => String(url).endsWith("/grade"))!;
  view.unmount(); expect(request[1]?.signal?.aborted).toBe(true);
});
