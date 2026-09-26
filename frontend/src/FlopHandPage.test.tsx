import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import FlopHandPage from "./FlopHandPage";

const response = (body: unknown, ok = true) =>
  Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
const packHash = "f".repeat(64);
const flop = [
  { rank: "TWO", suit: "CLUBS" },
  { rank: "THREE", suit: "DIAMONDS" },
  { rank: "EIGHT", suit: "SPADES" },
];
const turn = { rank: "FOUR", suit: "HEARTS" };
const river = { rank: "NINE", suit: "CLUBS" };
const metadata = {
  spotHash: "a".repeat(64), packHash, publicationStatus: "VALIDATION_ONLY",
  flop, potBb: 20, remainingStackBb: 80, flopBetBb: 10, turnBetBb: 10, riverBetBb: 10,
  firstRange: ["Ah As", "6s 7s"], secondRange: ["Kc Kd", "Qc Qd"],
  gameGapBb: 0.007446216, rakeModel: "NO_RAKE", chanceModel: "FULL_PHYSICAL_DECK",
  bettingTree: "SINGLE_BET_EACH_STREET_NO_RAISES",
};
const initial = {
  packHash, publicationStatus: "VALIDATION_ONLY", seed: "42", heroPlayer: 0,
  heroCombo: "Ah As", opponentCombo: null, flop, turn: null, river: null,
  street: "FLOP", flopHistory: "", turnHistory: "", riverHistory: "",
  potBb: 20, heroRemainingStackBb: 80, publicActions: [], legalActions: ["k", "b"],
  feedback: [], complete: false, showdown: false, heroCenteredResultBb: null,
  gameGapBb: metadata.gameGapBb,
};
const feedback = {
  street: "FLOP", flopHistory: "", turn: null, turnHistory: "", river: null,
  riverHistory: "", selectedAction: "k", selectedEvBb: 8, bestEvBb: 8,
  evLossBb: 0, actionFrequency: { k: 0.7, b: 0.3 }, actionEvBb: { k: 8, b: 7.5 },
};
const turnDecision = {
  ...initial, turn, street: "TURN", flopHistory: "kk", feedback: [feedback],
  publicActions: [
    { street: "FLOP", player: 0, action: "k" },
    { street: "FLOP", player: 1, action: "k" },
  ],
};
const riverDecision = {
  ...turnDecision, river, street: "RIVER", turnHistory: "kk",
  publicActions: [
    ...turnDecision.publicActions,
    { street: "TURN", player: 0, action: "k" },
    { street: "TURN", player: 1, action: "k" },
  ],
  feedback: [feedback, { ...feedback, street: "TURN", turn, flopHistory: "kk" }],
};
const complete = {
  ...riverDecision, opponentCombo: "Kc Kd", riverHistory: "kk",
  publicActions: [
    ...riverDecision.publicActions,
    { street: "RIVER", player: 0, action: "k" },
    { street: "RIVER", player: 1, action: "k" },
  ],
  legalActions: [],
  feedback: [...riverDecision.feedback, { ...feedback, street: "RIVER", turn, river,
    flopHistory: "kk", turnHistory: "kk" }],
  complete: true, showdown: true, heroCenteredResultBb: 10,
};

afterEach(() => {
  vi.restoreAllMocks();
  window.history.replaceState(null, "", "/");
});

it("plays one connected three-street hand and reveals the opponent at showdown", async () => {
  window.history.replaceState(null, "", `#flop-hand?seed=42&hero=0&pack=${packHash}`);
  const replayBodies: unknown[] = [];
  vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    if (!String(url).endsWith("/hands/replay")) return response(metadata);
    const body = JSON.parse(String(init?.body));
    replayBodies.push(body);
    if (body.actions.length === 0) return response(initial);
    if (body.actions.length === 1) return response(turnDecision);
    if (body.actions.length === 2) return response(riverDecision);
    return response(complete);
  });
  render(<FlopHandPage />);
  expect(await screen.findByText("First player: check or bet?")).toBeInTheDocument();
  expect(screen.getByLabelText("Players and table")).toHaveTextContent("20.0 bb pot");
  expect(screen.getByLabelText("You, first player")).toHaveTextContent("Your turn");
  expect(screen.getByLabelText("Opponent cards hidden")).toBeInTheDocument();
  expect(screen.getByLabelText("Turn not yet dealt")).toBeInTheDocument();
  expect(screen.getByLabelText("River not yet dealt")).toBeInTheDocument();
  expect(screen.queryByText("Kc Kd")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Check" }));
  expect(await screen.findByText("4♥")).toBeInTheDocument();
  expect(screen.getByLabelText("Players and table")).toHaveTextContent("Latest: Opponent checked · flop");
  expect(screen.queryByText("Kc Kd")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Check" }));
  expect(await screen.findByText("9♣")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Check" }));
  expect(await screen.findByText("Showdown")).toBeInTheDocument();
  expect(screen.getByText("Kc Kd")).toBeInTheDocument();
  expect(replayBodies).toEqual([0, 1, 2, 3].map((count) => ({
    seed: "42", packHash, heroPlayer: 0, actions: Array(count).fill("k"),
  })));
});

it("does not replay a hand from a stale pack link", async () => {
  window.history.replaceState(null, "", `#flop-hand?seed=42&hero=0&pack=${"0".repeat(64)}`);
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => response(metadata));
  render(<FlopHandPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("older solution");
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/hands/replay"))).toHaveLength(0);
});
