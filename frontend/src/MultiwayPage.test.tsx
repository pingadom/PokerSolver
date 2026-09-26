import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import MultiwayPage from "./MultiwayPage";

const response = (body: unknown, ok = true) =>
  Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
const packHash = "a".repeat(64);
const metadata = {
  spotId: "six-seat-fixture", spotHash: "b".repeat(64), packHash,
  packSchema: "multiway-call-pack/v1",
  publicationStatus: "VALIDATION_ONLY", solverVersion: "research", payoffMethod: "EXACT_ENUMERATION",
  seats: ["UTG", "HJ", "CO", "BTN", "SB", "BB"],
  committedBb: [20, 0, 0, 0, 0, 0], stackBb: 20,
  stacksBb: [20, 20, 20, 20, 20, 20], deadMoneyBb: 1.5,
  rakeModel: "NO_RAKE", nashConvBb: 0.000058, maximumPayoffStandardErrorBb: 0,
  sessionLength: 10,
};
const question = (seed: string, index: number, playerFilter = 0) => ({
  sessionSeed: seed, index, playerFilter, packHash, aggressorSeat: "UTG",
  actingPlayer: playerFilter || 3, actingSeat: playerFilter === 1 ? "HJ" : "BTN",
  heroCombo: "Ah As",
  priorResponses: playerFilter === 1 ? [] : [
    { seat: "HJ", action: "CALL" }, { seat: "CO", action: "FOLD" },
  ],
  potBb: 41.5, callCostBb: 20, stackBb: 20,
  publicationStatus: "VALIDATION_ONLY", legalActions: ["CALL", "FOLD"],
});
const feedback = {
  selectedAction: "CALL", selectedEvBb: 2, bestEvBb: 2, evLossBb: 0,
  callEvBb: 2, foldEvBb: -1, callFrequency: 0.7, foldFrequency: 0.3,
};

afterEach(() => {
  vi.restoreAllMocks();
  window.history.replaceState(null, "", "/");
});

it("shows all six public actions and completes a server-graded session", async () => {
  window.history.replaceState(null, "", `#multiway?seed=42&player=0&pack=${packHash}`);
  const gradeBodies: unknown[] = [];
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url);
    if (path.endsWith("/review")) {
      const body = JSON.parse(String(init?.body));
      return response({ packHash, attempts: body.actions.map((action: string, index: number) => ({
        question: question(body.sessionSeed, index, body.player),
        feedback: { ...feedback, selectedAction: action },
      })), totalEvLossBb: 0, averageEvLossBb: 0 });
    }
    if (path.endsWith("/grade")) {
      const body = JSON.parse(String(init?.body));
      gradeBodies.push(body);
      return response({ question: question(body.sessionSeed, body.index, body.player), feedback });
    }
    const match = path.match(/\/sessions\/(-?\d+)\/questions\/(\d+)\?player=(\d+)/);
    if (match) return response(question(match[1], Number(match[2]), Number(match[3])));
    return response(metadata);
  });
  render(<MultiwayPage />);
  expect(await screen.findByText("DECISION 1 OF 10")).toBeInTheDocument();
  const table = screen.getByLabelText("Six-seat action table");
  expect(table).toHaveTextContent("Shoved");
  expect(table).toHaveTextContent("Called");
  expect(table).toHaveTextContent("Folded");
  expect(table).toHaveTextContent("Your turn");
  expect(table).toHaveTextContent("Waiting");
  expect(table).toHaveTextContent("Ah");
  expect(table).not.toHaveTextContent("Kc");
  for (let index = 0; index < 10; index++) {
    await userEvent.click(screen.getByRole("button", { name: "Call" }));
    expect(await screen.findByText("Decision feedback")).toBeInTheDocument();
    expect(screen.getByText("Solver frequency 70.0%")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", {
      name: index === 9 ? "Review session" : "Next decision",
    }));
    if (index < 9) expect(await screen.findByText(`DECISION ${index + 2} OF 10`)).toBeInTheDocument();
  }
  expect(await screen.findByText("Your review")).toBeInTheDocument();
  expect(gradeBodies).toEqual(Array.from({ length: 10 }, (_, index) => ({
    sessionSeed: "42", index, player: 0, packHash, action: "CALL",
  })));
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/review"))).toHaveLength(1);
});

it("switches to a fixed responding seat with a fresh session", async () => {
  window.history.replaceState(null, "", `#multiway?seed=42&player=0&pack=${packHash}`);
  vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
    const match = String(url).match(/\/sessions\/(-?\d+)\/questions\/(\d+)\?player=(\d+)/);
    if (match) return response(question(match[1], Number(match[2]), Number(match[3])));
    return response(metadata);
  });
  render(<MultiwayPage />);
  expect(await screen.findByText("BTN: call or fold?")).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByLabelText("Practice seat"), "1");
  expect(await screen.findByText("HJ: call or fold?")).toBeInTheDocument();
  expect(window.location.hash).toContain("player=1");
  expect(window.location.hash).not.toContain("seed=42");
});

it("refuses to draw questions from a stale pack link", async () => {
  window.history.replaceState(null, "", `#multiway?seed=42&player=0&pack=${"0".repeat(64)}`);
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => response(metadata));
  render(<MultiwayPage />);
  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("older solution"));
  expect(fetch.mock.calls.filter(([url]) => String(url).includes("/questions/"))).toHaveLength(0);
  expect(screen.queryByRole("button", { name: "Call" })).not.toBeInTheDocument();
});

it("shows each unequal stack and explains side-pot exposure at the decision", async () => {
  window.history.replaceState(null, "", `#multiway?seed=42&player=5&pack=${packHash}`);
  const sidePotMetadata = {
    ...metadata,
    packSchema: "multiway-side-pot-pack/v1",
    stacksBb: [30, 10, 20, 15, 25, 5],
    stackBb: 30,
  };
  vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
    const match = String(url).match(/\/sessions\/(-?\d+)\/questions\/(\d+)\?player=(\d+)/);
    if (match) return response({
      ...question(match[1], Number(match[2]), Number(match[3])),
      actingPlayer: 5, actingSeat: "BB", callCostBb: 4, stackBb: 5,
    });
    return response(sidePotMetadata);
  });
  render(<MultiwayPage />);
  expect(await screen.findByText("BB: call or fold?")).toBeInTheDocument();
  const table = screen.getByLabelText("Six-seat action table");
  expect(table).toHaveTextContent("30 bb stack");
  expect(table).toHaveTextContent("5 bb stack");
  expect(screen.getByText("Unequal stacks")).toBeInTheDocument();
  expect(screen.getByText(/A short caller can win the main pot/)).toBeInTheDocument();
  expect(screen.getByText("4.0 bb")).toBeInTheDocument();
  expect(screen.queryByText("30 bb each")).not.toBeInTheDocument();
});
