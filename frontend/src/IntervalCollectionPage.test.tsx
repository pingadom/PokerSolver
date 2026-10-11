import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { collectionFixture as fixture, collectionMetadata as metadata, fixtureGrade, collectionReview } from "./test-data/intervalCollection";
import type { CollectionFeedback, CollectionQuestion, CollectionReview } from "./intervalCollectionApi";

const response = (body: unknown, status = 200) => Promise.resolve({ ok: status < 400, status, json: async () => body } as Response);
function mockApi(options: {
  metadataStatus?: number; question?: (q: CollectionQuestion) => unknown;
  grade?: (f: CollectionFeedback, init: RequestInit) => Promise<Response>;
  review?: (r: CollectionReview) => unknown;
} = {}) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/grade")) {
      const feedback = fixtureGrade(body.questionIndex, body.action);
      return options.grade ? options.grade(feedback, init!) : response(feedback);
    }
    if (path.endsWith("/review")) {
      const review = collectionReview(body.choices);
      return response(options.review ? options.review(review) : review);
    }
    const match = path.match(/\/questions\/(\d+)\?/);
    if (match) {
      const q = structuredClone(fixture.questions[Number(match[1])]);
      return response(options.question ? options.question(q) : q);
    }
    return response(metadata, options.metadataStatus);
  });
}
function open(query = "") {
  window.history.replaceState(null, "", `#interval-collection${query ? `?${query}` : ""}`);
  return render(<FrontendRoot />);
}
const next = () => screen.getByRole("button", { name: "Next question" });
const previous = () => screen.getByRole("button", { name: "Previous question" });
const call = () => screen.getByRole("button", { name: "Call" });
afterEach(() => { window.history.replaceState(null, "", "/"); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("loads four distinct questions before practice and shows progress with the public six-seat table", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Call" });
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("7♦7♥");
  expect(screen.getByText(/Question 1 of 4/)).toHaveTextContent("0 of 4 practised");
  expect(previous()).toBeDisabled(); expect(next()).toBeEnabled();
  expect(screen.getByLabelText("BB player")).toHaveClass("hero", "acting");
  expect(screen.getByLabelText("BTN player")).toHaveClass("villain", "move-raise");
  expect(within(screen.getByLabelText("Six-seat action table")).getAllByText("Out of hand")).toHaveLength(4);
  expect(call()).not.toHaveTextContent("EV"); expect(screen.getByRole("note")).toHaveTextContent("Two active players");
  expect(fetch.mock.calls.filter(([url]) => String(url).includes("/questions/"))).toHaveLength(4);
  expect(window.location.hash).toBe(`#interval-collection?collection=${metadata.collectionHash}&question=0`);
});
it("navigates without clearing attempts and restores each question's feedback", async () => {
  const fetch = mockApi(); open(); fireEvent.click(await screen.findByRole("button", { name: "Call" }));
  await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(next()); expect(screen.getByLabelText("Your cards")).toHaveTextContent("9♦9♥");
  expect(call()).not.toHaveTextContent("EV");
  fireEvent.click(screen.getByRole("button", { name: "Raise to 9 bb" }));
  await screen.findByRole("heading", { name: "Within limit" });
  expect(call()).toHaveTextContent("Outside limit");
  expect(screen.getByText(/Question 2 of 4/)).toHaveTextContent("2 of 4 practised");
  fireEvent.click(previous()); expect(screen.getByLabelText("Your cards")).toHaveTextContent("7♦7♥");
  expect(call()).toHaveTextContent("Within limit"); expect(call()).toBeDisabled();
  expect(screen.getByRole("button", { name: "Review 2 attempts" })).toBeInTheDocument();
  expect(fetch.mock.calls.filter(([url]) => String(url).includes("/questions/"))).toHaveLength(4);
});
it("plays all four questions, handles the last question and exports the exact offline review", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Call" });
  for (const [i, action] of ["Call", "Raise to 9 bb", "Call", "Raise to 50 bb"].entries()) {
    fireEvent.click(screen.getByRole("button", { name: action }));
    await screen.findByRole("heading", { name: i === 3 ? "Strategy-dependent" : "Within limit" });
    if (i < 3) fireEvent.click(next());
  }
  expect(next()).toBeDisabled(); expect(screen.getByText(/Last question/)).toHaveTextContent("4 of 4 practised");
  fireEvent.click(screen.getByRole("button", { name: "Try another move" }));
  fireEvent.click(screen.getByRole("button", { name: "Fold" })); await screen.findByRole("heading", { name: "Outside limit" });
  fireEvent.click(screen.getByRole("button", { name: "Review 5 attempts" }));
  const review = await screen.findByRole("region", { name: "Collection review" });
  expect(review).toHaveTextContent("4 of 4 questions practised · 5 attempts");
  expect(review).toHaveTextContent("Within limit: 3 · Outside limit: 1 · Strategy-dependent: 1");
  expect(review).not.toHaveTextContent(/accuracy|average EV|total EV/i);
  const create = vi.fn(() => "blob:review"), revoke = vi.fn();
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  fireEvent.click(screen.getByRole("button", { name: "Download replayable review" }));
  const text = await new Promise<string>((resolve) => {
    const reader = new FileReader(); reader.onload = () => resolve(String(reader.result));
    reader.readAsText((create.mock.calls as unknown as [[Blob]])[0][0]);
  });
  expect(JSON.parse(text)).toEqual(fixture); expect(revoke).toHaveBeenCalledWith("blob:review");
  const post = fetch.mock.calls.find(([url]) => String(url).endsWith("/review"))!;
  expect(JSON.parse(String(post[1]?.body))).toEqual({ collectionHash: metadata.collectionHash,
    choices: fixture.attempts.map((a) => ({ questionIndex: a.questionIndex, reportHash: a.feedback.decision.reportHash, action: a.action })) });
});
it("reviews zero attempts without inventing coverage", async () => {
  mockApi(); open(); await screen.findByRole("button", { name: "Call" });
  fireEvent.click(screen.getByRole("button", { name: "Review 0 attempts" }));
  expect(await screen.findByRole("region", { name: "Collection review" })).toHaveTextContent("0 of 4 questions practised · 0 attempts");
});
it("bounds 100 retries globally, preserves navigation and resets only on request", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Call" });
  for (let i = 0; i < 100; i++) {
    fireEvent.click(call()); await screen.findByRole("heading", { name: "Within limit" });
    if (i < 99) fireEvent.click(screen.getByRole("button", { name: "Try another move" }));
  }
  expect(screen.getByText(/Question 1 of 4/)).toHaveTextContent("1 of 4 practised");
  expect(screen.getByText(/Attempt limit reached/)).toBeInTheDocument();
  fireEvent.click(next()); expect(call()).toBeDisabled(); fireEvent.keyDown(window, { key: "2" });
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(100);
  fireEvent.click(screen.getByRole("button", { name: "Review 100 attempts" }));
  expect(await screen.findByRole("region", { name: "Collection review" })).toHaveTextContent("1 of 4 questions practised · 100 attempts");
  fireEvent.click(screen.getByRole("button", { name: "Start over" }));
  await waitFor(() => expect(call()).toBeEnabled());
  expect(screen.getByText(/Question 1 of 4/)).toHaveTextContent("0 of 4 practised");
}, 20000);
it.each(["collection=stale", "question=-1", "question=4", "question=01", "question=0&question=1", "seed=42"])("rejects an invalid URL and loads the current collection explicitly: %s", async (query) => {
  mockApi(); open(query); expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Load current collection" }));
  expect(await screen.findByRole("button", { name: "Call" })).toBeEnabled();
});
it("honours a pinned deep link to the fourth decision", async () => {
  mockApi(); open(`collection=${metadata.collectionHash}&question=3`); await screen.findByRole("button", { name: "Call" });
  expect(screen.getByText(/Question 4 of 4/)).toBeInTheDocument();
  expect(screen.getByLabelText("BTN player")).toHaveTextContent("22 bb in pot"); expect(next()).toBeDisabled();
});
it("rejects a duplicate or altered public question before showing any table", async () => {
  mockApi({ question: (q) => q.index === 1 ? { ...q, decision: fixture.questions[0].decision } : q }); open();
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
});
it("shows the honest disabled state", async () => {
  mockApi({ metadataStatus: 404 }); open();
  expect(await screen.findByRole("alert")).toHaveTextContent("not enabled");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
});
it("retries transient grade errors without losing previous attempts and makes HTTP400 fatal", async () => {
  let count = 0;
  mockApi({ grade: (f) => ++count === 2 ? Promise.reject(new Error("Network offline")) : count === 4 ? response({}, 400) : response(f) });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Call" })); await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(next()); fireEvent.click(call()); expect(await screen.findByRole("alert")).toHaveTextContent("Network offline");
  await waitFor(() => expect(call()).toBeEnabled()); fireEvent.click(call()); await screen.findByRole("heading", { name: "Outside limit" });
  expect(screen.getByRole("button", { name: "Review 2 attempts" })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Try another move" })); fireEvent.click(call());
  expect(await screen.findByRole("alert")).toHaveTextContent("server rejected");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
});
it.each(["question", "ranges"])("rejects a mismatched grade under the collection pin: %s", async (kind) => {
  let count = 0;
  mockApi({ grade: (f) => {
    count++;
    if (kind === "question") f.questionIndex = 1;
    else if (count === 2) f.decision.alternatives[0].upperEvBb += 1;
    return response(f);
  } });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Call" }));
  if (kind === "ranges") {
    fireEvent.click(await screen.findByRole("button", { name: "Try another move" })); fireEvent.click(call());
  }
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Download replayable review" })).not.toBeInTheDocument();
});
it("rejects forged coverage before offering export", async () => {
  mockApi({ review: (r) => ({ ...r, counts: { ...r.counts, distinctQuestionsAttempted: 4 } }) });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Call" }));
  fireEvent.click(await screen.findByRole("button", { name: "Review 1 attempt" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Download replayable review" })).not.toBeInTheDocument();
});
it("prevents duplicates/navigation while grading and discards late results after a URL reset", async () => {
  let resolve!: (r: Response) => void; let signal: AbortSignal | undefined;
  const deferred = new Promise<Response>((done) => { resolve = done; });
  const fetch = mockApi({ grade: (_f, init) => { signal = init.signal as AbortSignal; return deferred; } });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Call" }));
  fireEvent.click(call()); fireEvent.keyDown(window, { key: "2" }); fireEvent.click(next());
  expect(next()).toBeDisabled(); expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(1);
  act(() => { window.history.replaceState(null, "", "#interval-collection?collection=stale"); window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("alert"); expect(signal?.aborted).toBe(true);
  await act(async () => { resolve(await response(fixtureGrade(0, "call"))); });
  expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
});
it("aborts unmounted work and ignores modified, repeating or focused keyboard shortcuts", async () => {
  const fetch = mockApi(); const view = open(); await screen.findByRole("button", { name: "Call" });
  fireEvent.keyDown(window, { key: "2", ctrlKey: true }); fireEvent.keyDown(window, { key: "2", repeat: true });
  fireEvent.keyDown(call(), { key: "2" });
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(0);
  fireEvent.keyDown(window, { key: "2" }); await screen.findByRole("heading", { name: "Within limit" });
  const grade = fetch.mock.calls.find(([url]) => String(url).endsWith("/grade"))!;
  view.unmount(); expect(grade[1]?.signal?.aborted).toBe(true);
});
it("discards a late initial inventory after an external question-link change", async () => {
  const fetch = mockApi(), implementation = fetch.getMockImplementation()!;
  let first = true, signal: AbortSignal | undefined, resolve!: (r: Response) => void;
  const deferred = new Promise<Response>((done) => { resolve = done; });
  fetch.mockImplementation((url, init) => {
    if (first && String(url).includes("/questions/0?")) {
      first = false; signal = init?.signal as AbortSignal; return deferred;
    }
    return implementation(url, init);
  });
  open(); await waitFor(() => expect(first).toBe(false));
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
  act(() => { window.history.replaceState(null, "", "#interval-collection?question=3"); window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("button", { name: "Call" }); expect(signal?.aborted).toBe(true);
  await act(async () => { resolve(await response(fixture.questions[0])); });
  expect(screen.getByText(/Question 4 of 4/)).toBeInTheDocument();
  expect(screen.getByLabelText("BTN player")).toHaveTextContent("22 bb in pot");
});
