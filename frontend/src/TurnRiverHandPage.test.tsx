import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import TurnRiverHandPage from "./TurnRiverHandPage";

const response = (body: unknown, ok = true) =>
  Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
const packHash = "e".repeat(64);
const turnBoard = [
  { rank: "TWO", suit: "CLUBS" },
  { rank: "THREE", suit: "DIAMONDS" },
  { rank: "FOUR", suit: "HEARTS" },
  { rank: "EIGHT", suit: "SPADES" },
];
const river = { rank: "NINE", suit: "CLUBS" };
const metadata = {
  spotHash: "a".repeat(64), packHash, publicationStatus: "VALIDATION_ONLY",
  turnBoard, potBb: 20, remainingStackBb: 80, turnBetBb: 10, riverBetBb: 10,
  firstRange: ["Ah As", "6s 7s"], secondRange: ["Kc Kd", "Qc Qd"],
  gameGapBb: 0.000549, availableTurnQuestions: 8, availableRiverQuestions: 1104,
  rakeModel: "NO_RAKE", bettingTree: "SINGLE_BET_EACH_STREET_NO_RAISES",
};
const initial = {
  packHash, publicationStatus: "VALIDATION_ONLY", seed: "42", heroPlayer: 0,
  heroCombo: "Ah As", opponentCombo: null, turnBoard, river: null, street: "TURN",
  turnHistory: "", riverHistory: "", potBb: 20, heroRemainingStackBb: 80,
  publicActions: [], legalActions: ["k", "b"], feedback: [], complete: false,
  showdown: false, heroCenteredResultBb: null, gameGapBb: metadata.gameGapBb,
};
const turnFeedback = {
  street: "TURN", turnHistory: "", river: null, riverHistory: "",
  selectedAction: "k", selectedEvBb: 8, bestEvBb: 8, evLossBb: 0,
  actionFrequency: { k: 0.7, b: 0.3 }, actionEvBb: { k: 8, b: 7.5 },
};
const riverDecision = {
  ...initial, river, street: "RIVER", turnHistory: "kk", potBb: 20,
  publicActions: [
    { street: "TURN", player: 0, action: "k" },
    { street: "TURN", player: 1, action: "k" },
  ],
  feedback: [turnFeedback],
};
const complete = {
  ...riverDecision, opponentCombo: "Kc Kd", riverHistory: "kk",
  publicActions: [
    ...riverDecision.publicActions,
    { street: "RIVER", player: 0, action: "k" },
    { street: "RIVER", player: 1, action: "k" },
  ],
  legalActions: [], feedback: [turnFeedback, {
    ...turnFeedback, street: "RIVER", turnHistory: "kk", river,
  }],
  complete: true, showdown: true, heroCenteredResultBb: 10,
};

afterEach(() => {
  vi.restoreAllMocks();
  window.history.replaceState(null, "", "/");
});

it("plays one connected hand and reveals the opponent only at showdown", async () => {
  window.history.replaceState(null, "", `#turn-river-hand?seed=42&hero=0&pack=${packHash}`);
  const replayBodies: unknown[] = [];
  vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    if (!String(url).endsWith("/hands/replay")) return response(metadata);
    const body = JSON.parse(String(init?.body));
    replayBodies.push(body);
    if (body.actions.length === 0) return response(initial);
    if (body.actions.length === 1) return response(riverDecision);
    return response(complete);
  });
  render(<TurnRiverHandPage />);
  expect(await screen.findByText("First player: check or bet?")).toBeInTheDocument();
  expect(screen.getByLabelText("You, first player")).toHaveTextContent("Your turn");
  expect(screen.getByLabelText("Opponent cards hidden")).toBeInTheDocument();
  expect(screen.getByLabelText("River not yet dealt")).toBeInTheDocument();
  expect(screen.queryByText("Kc Kd")).not.toBeInTheDocument();
  expect(screen.queryByText("Your decisions")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Check" }));
  expect(await screen.findByText("9♣")).toBeInTheDocument();
  expect(screen.getByLabelText("Players and table")).toHaveTextContent("Latest: Opponent checked · turn");
  expect(screen.getByText("Your decisions")).toBeInTheDocument();
  expect(screen.queryByText("Kc Kd")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Check" }));
  expect(await screen.findByText("Showdown")).toBeInTheDocument();
  expect(screen.getByText("Kc Kd")).toBeInTheDocument();
  expect(screen.getByText(/Centered result for your hand:/)).toBeInTheDocument();
  expect(replayBodies).toEqual([
    { seed: "42", packHash, heroPlayer: 0, actions: [] },
    { seed: "42", packHash, heroPlayer: 0, actions: ["k"] },
    { seed: "42", packHash, heroPlayer: 0, actions: ["k", "k"] },
  ]);
});

it("keeps stale pack links out of the hand", async () => {
  window.history.replaceState(null, "", `#turn-river-hand?seed=42&hero=0&pack=${"0".repeat(64)}`);
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => response(metadata));
  render(<TurnRiverHandPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("older solution");
  expect(screen.queryByRole("button", { name: "Check" })).not.toBeInTheDocument();
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/hands/replay"))).toHaveLength(0);
});
