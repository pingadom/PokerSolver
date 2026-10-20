import raw from "./test-data/expanded-flop-collection-review.json?raw";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { expandedFlopCollectionFixture as fixture, expandedFlopCollectionMetadata as metadata, expandedFlopCollectionFixtureReview } from "./test-data/expandedFlopCollection";
import { expandedGrade } from "./expandedFlopCollectionApi";

const response = (body: unknown, status = 200) => Promise.resolve({ ok: status < 400, status,
  json: () => Promise.resolve(body), text: () => Promise.resolve(typeof body === "string" ? body : JSON.stringify(body)) } as Response);
function mockApi() {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/review/export")) return response(raw);
    if (path.endsWith("/grade")) return response(fixture.attempts.find(a => a.questionIndex === body.questionIndex && a.action === body.action)!.feedback);
    if (path.endsWith("/review")) return response(expandedFlopCollectionFixtureReview(body.choices));
    const index = path.match(/\/questions\/(\d+)/)?.[1];
    return response(index === undefined ? metadata : fixture.questions[Number(index)]);
  });
}
function open(index = 0) {
  window.history.replaceState(null, "", `#interval-expanded-flop?collection=${metadata.collectionHash}&question=${index}`);
  return render(<FrontendRoot />);
}
const button = (name: string) => screen.getByRole("button", { name });
afterEach(() => { window.history.replaceState(null, "", "/"); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

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
  expect(fetch.mock.calls.filter(([url]) => String(url).includes("/questions/"))).toHaveLength(10);
  fireEvent.keyDown(window, { key: "2" });
  await screen.findByRole("heading", { name: "Within limit" });
  expect(button("Call 3.25 bb")).toHaveTextContent("Hand EV");
  expect(button("Fold")).toHaveTextContent("Loss");
  expect(JSON.parse(String(fetch.mock.calls.find(([url]) => String(url).endsWith("/grade"))![1]?.body))).toEqual({
    collectionHash: metadata.collectionHash, questionIndex: index, reportHash: fixture.questions[index].decision.paired!.reportHash, action: "call",
  });
});

it("plays all twenty moves across both boards, preserves feedback and exports exact replay bytes", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Check" });
  const links = () => screen.getByRole("navigation", { name: "Choose a flop decision" }).querySelectorAll("button");
  for (const a of fixture.attempts) {
    fireEvent.click(links()[a.questionIndex]);
    if (a.index >= 10) fireEvent.click(button("Try another move"));
    fireEvent.click(button(a.action === "bet" ? "Bet 3.25 bb" : a.action === "call" ? "Call 3.25 bb" : a.action === "check" ? "Check" : "Fold"));
    await screen.findByRole("heading", { name: expandedGrade(a.feedback).selected.classification === "WITHIN_LIMIT" ? "Within limit" : "Outside limit" });
  }
  fireEvent.click(links()[0]);
  expect(button("Check")).toHaveTextContent("Hand EV");
  fireEvent.click(links()[9]);
  expect(button("Next question")).toBeDisabled();
  fireEvent.click(button("Review 20 attempts"));
  expect(await screen.findByRole("region", { name: "Two-board flop practice review" })).toHaveTextContent("10 of 10 questions practised · 20 attempts");
  const create = vi.fn((blob: Blob) => { expect(blob.size).toBe(new TextEncoder().encode(raw).length); return "blob:expanded"; }), revoke = vi.fn();
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  fireEvent.click(button("Download replayable review"));
  await waitFor(() => expect(create).toHaveBeenCalledOnce());
  const text = await new Promise<string>(resolve => { const r = new FileReader(); r.onload = () => resolve(String(r.result)); r.readAsText(create.mock.calls[0][0]); });
  expect(text).toBe(raw);
  expect((click.mock.instances[0] as HTMLAnchorElement).download).toBe(`pokerlab-expanded-flop-review-${metadata.collectionHash.slice(0, 12)}.json`);
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(20);
});

it("aborts an outstanding grade when the collection link changes", async () => {
  const fetch = mockApi(), original = fetch.getMockImplementation()!;
  let finish!: (r: Response) => void, signal: AbortSignal | undefined;
  fetch.mockImplementation((url, init) => String(url).endsWith("/grade") ? new Promise(resolve => { finish = resolve; signal = init?.signal as AbortSignal; }) : original(url, init));
  open(6); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  act(() => { window.history.replaceState(null, "", "#interval-expanded-flop?question=0"); window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("button", { name: "Check" }); expect(signal?.aborted).toBe(true);
  await act(async () => finish(await response(fixture.attempts[6].feedback)));
  expect(screen.queryByRole("heading", { name: "Outside limit" })).not.toBeInTheDocument();
  expect(button("Check")).toBeEnabled();
});
