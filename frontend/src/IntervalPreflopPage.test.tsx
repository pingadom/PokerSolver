import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { intervalFixture as fixture, intervalMetadata as metadata, fixtureReview } from "./test-data/interval";
import type { IntervalFeedback, IntervalQuestion, IntervalReview } from "./intervalTrainerApi";

const response = (body: unknown, status = 200) => Promise.resolve({
  ok: status < 400, status, json: () => Promise.resolve(body),
} as Response);
function mockApi(options: {
  metadataStatus?: number;
  question?: (q: IntervalQuestion) => unknown;
  grade?: (f: IntervalFeedback, init: RequestInit) => Promise<Response>;
  review?: (r: IntervalReview) => unknown;
} = {}) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url);
    const body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/grade")) {
      const f = structuredClone(fixture.attempts.find((a) => a.action === body.action)!.feedback);
      return options.grade ? options.grade(f, init!) : response(f);
    }
    if (path.endsWith("/review")) {
      const r = fixtureReview(body.actions);
      return response(options.review ? options.review(r) : r);
    }
    if (path.endsWith("/question")) {
      const q = structuredClone(fixture.question);
      return response(options.question ? options.question(q) : q);
    }
    return response(metadata, options.metadataStatus);
  });
}
function open(pin = "") {
  window.history.replaceState(null, "", `#interval-preflop${pin ? `?report=${pin}` : ""}`);
  return render(<FrontendRoot />);
}
const call = () => screen.getByRole("button", { name: "Call" });
afterEach(() => {
  window.history.replaceState(null, "", "/"); vi.restoreAllMocks(); vi.unstubAllGlobals();
});

it("routes the actual six-seat decision with four folds, visible hero and hidden opponents", async () => {
  mockApi(); open();
  await screen.findByRole("button", { name: "Call" });
  const table = screen.getByLabelText("Six-seat action table");
  expect(within(table).getAllByText("Out of hand")).toHaveLength(4);
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("9♦9♥");
  expect(screen.getByLabelText("BB player")).toHaveClass("hero", "acting", "position-0");
  expect(screen.getByLabelText("BTN player")).toHaveClass("villain", "move-raise");
  expect(screen.getByLabelText("BTN player")).toHaveTextContent("22 bb in pot");
  expect(screen.getByLabelText("BTN player")).toHaveTextContent("78 bb behind");
  expect(table).toHaveTextContent("31.5 bb"); expect(table).toHaveTextContent("13 bb to call");
  expect(table.querySelectorAll(".poker-cards")).toHaveLength(1);
  expect(table.querySelectorAll(".poker-card-backs")).toHaveLength(5);
  expect(call()).not.toHaveTextContent("EV");
  expect(screen.getByRole("note")).toHaveTextContent("one scenario");
  expect(window.location.hash).toContain(`report=${metadata.reportHash}`);
  expect(screen.queryByRole("button", { name: /Next/ })).not.toBeInTheDocument();
});
it.each([
  ["Fold", "Outside limit"], ["Call", "Within limit"], ["Raise to 50 bb", "Strategy-dependent"],
])("grades %s with all EV/loss ranges on its buttons and feedback under the table", async (move, verdict) => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Call" });
  fireEvent.click(screen.getByRole("button", { name: move }));
  expect(await screen.findByRole("heading", { name: verdict })).toBeInTheDocument();
  const controls = screen.getByLabelText("Your decision controls");
  for (const action of ["Fold", "Call", "Raise to 50 bb"]) {
    const button = within(controls).getByRole("button", { name: action });
    expect(button).toHaveTextContent("EV"); expect(button).toHaveTextContent("Loss");
    expect(button).toBeDisabled(); expect(button).toHaveAccessibleDescription(/EV/);
  }
  expect(call()).toHaveTextContent("Loss 0 – 0.000450001 bb");
  expect(within(controls).getByRole("button", { name: "Try another move" })).toBeInTheDocument();
  expect(controls.querySelector(".poker-decision-feedback")).toHaveTextContent("0.01 bb");
  expect(controls.querySelector(".preflop-frequency")).toBeNull();
  const grade = fetch.mock.calls.find(([url]) => String(url).endsWith("/grade"))!;
  expect(JSON.parse(String(grade[1]?.body))).toEqual({ reportHash: metadata.reportHash,
    action: move === "Fold" ? "fold" : move === "Call" ? "call" : "raise:50.0" });
});
it("retries the same question, reviews all three explicit moves and exports server JSON", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Call" });
  for (const move of ["Fold", "Call", "Raise to 50 bb"]) {
    fireEvent.click(screen.getByRole("button", { name: move }));
    const retry = await screen.findByRole("button", { name: "Try another move" });
    if (move !== "Raise to 50 bb") fireEvent.click(retry);
  }
  fireEvent.click(screen.getByRole("button", { name: "Review 3 attempts" }));
  const review = await screen.findByRole("region", { name: "Attempt review" });
  expect(review).toHaveTextContent("Same question · 3 attempts");
  expect(review).toHaveTextContent("Within limit: 1 · Outside limit: 1 · Strategy-dependent: 1");
  expect(review).not.toHaveTextContent(/accuracy|average|total EV/i);
  const create = vi.fn((blob: Blob) => { expect(blob).toBeInstanceOf(Blob); return "blob:review"; }); const revoke = vi.fn();
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  const anchor = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  fireEvent.click(screen.getByRole("button", { name: "Download replayable review" }));
  const text = await new Promise<string>((resolve) => {
    const reader = new FileReader(); reader.onload = () => resolve(String(reader.result));
    reader.readAsText(create.mock.calls[0][0] as unknown as Blob);
  });
  expect(JSON.parse(text)).toEqual(fixture); expect(anchor).toHaveBeenCalledOnce();
  expect(revoke).toHaveBeenCalledWith("blob:review");
  const post = fetch.mock.calls.find(([url]) => String(url).endsWith("/review"))!;
  expect(JSON.parse(String(post[1]?.body))).toEqual({ reportHash: metadata.reportHash, actions: ["fold", "call", "raise:50.0"] });
});
it("can review zero attempts without inventing grades", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Call" });
  fireEvent.click(screen.getByRole("button", { name: "Review 0 attempts" }));
  expect(await screen.findByRole("region", { name: "Attempt review" })).toHaveTextContent("Same question · 0 attempts");
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(0);
});
it("stops at 100 explicit attempts, keeping bounded review and export available", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Call" });
  for (let index = 0; index < 100; index++) {
    fireEvent.click(call());
    await screen.findByRole("heading", { name: "Within limit" });
    if (index < 99) fireEvent.click(screen.getByRole("button", { name: "Try another move" }));
  }
  expect(screen.getByText(/Attempt limit reached/)).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Try another move" })).not.toBeInTheDocument();
  fireEvent.click(call()); fireEvent.keyDown(window, { key: "2" });
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(100);
  fireEvent.click(screen.getByRole("button", { name: "Review 100 attempts" }));
  expect(await screen.findByRole("region", { name: "Attempt review" })).toHaveTextContent("Within limit: 100");
  fireEvent.click(screen.getByRole("button", { name: "Start over" }));
  await waitFor(() => expect(call()).toBeEnabled());
  expect(screen.getByRole("button", { name: "Review 0 attempts" })).toBeInTheDocument();
}, 20000);
it("blocks a stale URL pin and explicitly loads the current report", async () => {
  mockApi(); open("a".repeat(64));
  expect(await screen.findByRole("alert")).toHaveTextContent("report changed");
  expect(screen.queryByRole("button", { name: "Call" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Load current report" }));
  expect(await screen.findByRole("button", { name: "Call" })).toBeEnabled();
});
it("rejects a different question hash before practice", async () => {
  mockApi({ question: (q) => ({ ...q, reportHash: "b".repeat(64) }) }); open();
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Call" })).not.toBeInTheDocument();
});
it.each(["hash", "selected"])("rejects a stale or mismatched grade: %s", async (kind) => {
  mockApi({ grade: (f) => response(kind === "hash" ? { ...f, reportHash: "b".repeat(64) } : { ...f, selected: f.alternatives[0] }) });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Call" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Review 1 attempt" })).not.toBeInTheDocument();
});
it("rejects edited canonical review feedback and offers no download", async () => {
  mockApi({ review: (r) => ({ ...r, counts: { ...r.counts, withinLimit: 99 } }) }); open();
  fireEvent.click(await screen.findByRole("button", { name: "Call" }));
  fireEvent.click(await screen.findByRole("button", { name: "Review 1 attempt" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Download replayable review" })).not.toBeInTheDocument();
});
it("shows an honest default-disabled state without fallback questions", async () => {
  mockApi({ metadataStatus: 404 }); open();
  expect(await screen.findByRole("alert")).toHaveTextContent("not enabled");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
});
it("allows retry after a network grade failure and blocks an HTTP 400", async () => {
  let count = 0;
  mockApi({ grade: (f) => ++count === 1 ? Promise.reject(new TypeError("Network offline")) : count === 2 ? response(f) : response({}, 400) });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Call" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Network offline");
  await waitFor(() => expect(call()).toBeEnabled()); fireEvent.click(call());
  fireEvent.click(await screen.findByRole("button", { name: "Try another move" })); fireEvent.click(call());
  expect(await screen.findByRole("alert")).toHaveTextContent("server rejected");
  expect(screen.queryByRole("button", { name: "Call" })).not.toBeInTheDocument();
});
it("prevents duplicate submissions and discards a late grade when the URL changes", async () => {
  let resolve!: (value: Response) => void; let signal: AbortSignal | undefined;
  const deferred = new Promise<Response>((done) => { resolve = done; });
  const fetch = mockApi({ grade: (_f, init) => { signal = init.signal as AbortSignal; return deferred; } });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Call" }));
  fireEvent.click(call()); fireEvent.keyDown(window, { key: "2" });
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(1);
  act(() => { window.history.replaceState(null, "", `#interval-preflop?report=${"b".repeat(64)}`); window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("alert"); expect(signal?.aborted).toBe(true);
  await act(async () => { resolve(await response(fixture.attempts[1].feedback)); });
  expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Review 1 attempt" })).not.toBeInTheDocument();
});
it("aborts unmounted requests and guards modified, repeating and focused keyboard shortcuts", async () => {
  const fetch = mockApi(); const view = open(); await screen.findByRole("button", { name: "Call" });
  await waitFor(() => expect(call()).toBeEnabled());
  fireEvent.keyDown(window, { key: "2", repeat: true }); fireEvent.keyDown(window, { key: "2", ctrlKey: true });
  fireEvent.keyDown(call(), { key: "2" });
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(0);
  fireEvent.keyDown(window, { key: "2" });
  await screen.findByRole("heading", { name: "Within limit" });
  const request = fetch.mock.calls.find(([url]) => String(url).endsWith("/grade"))!;
  view.unmount(); expect(request[1]?.signal?.aborted).toBe(true);
});
