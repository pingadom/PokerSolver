import raw from "./test-data/qualified-flop-collection-review.json?raw";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { qualifiedFlopCollectionFixture as fixture, qualifiedFlopCollectionMetadata as metadata, qualifiedFlopCollectionFixtureReview } from "./test-data/qualifiedFlopCollection";
import { qualifiedGrade, qualifiedDecision } from "./qualifiedFlopCollectionApi";

const response = (body: unknown, status = 200) => Promise.resolve({ ok: status < 400, status,
  json: () => Promise.resolve(body), text: () => Promise.resolve(typeof body === "string" ? body : JSON.stringify(body)) } as Response);
function mockApi() {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/review/export")) return response(raw);
    if (path.endsWith("/grade")) return response(fixture.attempts.find(a => a.questionIndex === body.questionIndex && a.action === body.action)!.feedback);
    if (path.endsWith("/review")) return response(qualifiedFlopCollectionFixtureReview(body.choices));
    const index = path.match(/\/questions\/(\d+)/)?.[1];
    return response(index === undefined ? metadata : fixture.questions[Number(index)]);
  });
}
function open(index = 0) {
  window.history.replaceState(null, "", `#interval-qualified-flop?collection=${metadata.collectionHash}&question=${index}`);
  return render(<FrontendRoot />);
}
const button = (name: string) => screen.getByRole("button", { name });
afterEach(() => { window.history.replaceState(null, "", "/"); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("shows the weak-hand scenario, prior bet and fold/call feedback directly at the table", async () => {
  mockApi(); open(10);
  await screen.findByRole("button", {name: "Fold"});
  expect(screen.getByText(/Weak-hand scenario · Different supplied ranges/)).toBeInTheDocument();
  expect(screen.getByLabelText("BTN player")).toHaveClass("hero", "acting");
  expect(screen.getByLabelText("BB player")).toHaveClass("villain", "move-raise");
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("8♦K♣K♦");
  fireEvent.keyDown(window, {key: "2"});
  await screen.findByRole("heading", {name: "Outside limit"});
  expect(button("Call 3.25 bb")).toHaveTextContent("Hand EV");
  expect(button("Fold")).toHaveTextContent("Loss");
  expect(button("Next question")).toBeDisabled();
  fireEvent.click(button("Try another move"));
  fireEvent.click(button("Fold"));
  await screen.findByRole("heading", {name: "Within limit"});
  expect(window.location.hash).toContain("question=10");
});

it.each([6, 7, 8, 9])("opens paired question %s with the board, actor, prior bet and exact menu", async index => {
  const fetch = mockApi(); open(index);
  await screen.findByRole("button", { name: "Fold" });
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("8♦K♣K♦");
  expect(screen.getByLabelText("BTN player")).toHaveClass("hero", "acting");
  expect(screen.getByLabelText("BB player")).toHaveClass("villain", "move-raise");
  expect(screen.getByLabelText("BB player")).toHaveTextContent("Bet 3.25 bb");
  expect(screen.getByLabelText("Six-seat action table")).toHaveTextContent("9.75 bb");
  expect(button("Call 3.25 bb")).toBeEnabled();
  expect(screen.queryByRole("button", { name: "Check" })).not.toBeInTheDocument();
  expect(button("Fold")).not.toHaveTextContent("EV");
  expect(fetch.mock.calls.filter(([url]) => String(url).includes("/questions/"))).toHaveLength(11);
  fireEvent.keyDown(window, { key: "2" });
  await screen.findByRole("heading", { name: "Within limit" });
  expect(button("Call 3.25 bb")).toHaveTextContent("Hand EV");
  expect(button("Fold")).toHaveTextContent("Loss");
  expect(JSON.parse(String(fetch.mock.calls.find(([url]) => String(url).endsWith("/grade"))![1]?.body))).toEqual({
    collectionHash: metadata.collectionHash, questionIndex: index, reportHash: qualifiedDecision(fixture.questions[index]).reportHash, action: "call",
  });
});

it("plays all twenty-two moves across both boards, preserves feedback and exports exact replay bytes", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Check" });
  const links = () => screen.getByRole("navigation", { name: "Choose a flop decision" }).querySelectorAll("button");
  const seen = new Set<number>();
  for (const a of fixture.attempts) {
    fireEvent.click(links()[a.questionIndex]);
    if (seen.has(a.questionIndex)) fireEvent.click(button("Try another move"));
    seen.add(a.questionIndex);
    fireEvent.click(button(a.action === "bet" ? "Bet 3.25 bb" : a.action === "call" ? "Call 3.25 bb" : a.action === "check" ? "Check" : "Fold"));
    await screen.findByRole("heading", { name: qualifiedGrade(a.feedback).selected.classification === "WITHIN_LIMIT" ? "Within limit" : "Outside limit" });
  }
  fireEvent.click(links()[0]);
  expect(button("Check")).toHaveTextContent("Hand EV");
  fireEvent.click(links()[10]);
  expect(button("Next question")).toBeDisabled();
  fireEvent.click(button("Review 22 attempts"));
  expect(await screen.findByRole("region", { name: "Independent flop practice review" })).toHaveTextContent("11 of 11 questions practised · 22 attempts");
  const create = vi.fn((blob: Blob) => { expect(blob.size).toBe(new TextEncoder().encode(raw).length); return "blob:expanded"; }), revoke = vi.fn();
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  fireEvent.click(button("Download replayable review"));
  await waitFor(() => expect(create).toHaveBeenCalledOnce());
  const text = await new Promise<string>(resolve => { const r = new FileReader(); r.onload = () => resolve(String(r.result)); r.readAsText(create.mock.calls[0][0]); });
  expect(text).toBe(raw);
  expect((click.mock.instances[0] as HTMLAnchorElement).download).toBe(`pokerlab-qualified-flop-review-${metadata.collectionHash.slice(0, 12)}.json`);
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(22);
});

it("aborts an outstanding grade when the collection link changes", async () => {
  const fetch = mockApi(), original = fetch.getMockImplementation()!;
  let finish!: (r: Response) => void, signal: AbortSignal | undefined;
  fetch.mockImplementation((url, init) => String(url).endsWith("/grade") ? new Promise(resolve => { finish = resolve; signal = init?.signal as AbortSignal; }) : original(url, init));
  open(6); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  act(() => { window.history.replaceState(null, "", "#interval-qualified-flop?question=0"); window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("button", { name: "Check" }); expect(signal?.aborted).toBe(true);
  await act(async () => finish(await response(fixture.attempts[6].feedback)));
  expect(screen.queryByRole("heading", { name: "Outside limit" })).not.toBeInTheDocument();
  expect(button("Check")).toBeEnabled();
});
