import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import TurnRiverPage from "./TurnRiverPage";

const response = (body: unknown, ok = true) =>
  Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
const packHash = "d".repeat(64);
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
const turnQuestion = {
  packHash, publicationStatus: "VALIDATION_ONLY", street: "TURN", turnBoard,
  river: null, potBb: 20, remainingStackBb: 80, turnBetBb: 10, riverBetBb: 10,
  actingPlayer: 0, heroCombo: "Ah As", turnHistory: "", riverHistory: "",
  legalActions: ["k", "b"], gameGapBb: metadata.gameGapBb,
};

afterEach(() => {
  vi.restoreAllMocks();
  window.history.replaceState(null, "", "/");
});

it("keeps the future river hidden on a turn question and grades on the server", async () => {
  window.history.replaceState(null, "", `#turn-river?seed=42&pack=${packHash}`);
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url);
    if (path.endsWith("/grade")) {
      expect(JSON.parse(String(init?.body))).toEqual({ seed: "42", packHash, action: "b" });
      return response({
        packHash, street: "TURN", heroCombo: "Ah As", turnHistory: "",
        river: null, riverHistory: "", selectedAction: "b", selectedEvBb: 8,
        bestEvBb: 8, evLossBb: 0, actionFrequency: { k: 0.5, b: 0.5 },
        actionEvBb: { k: 7.5, b: 8 },
      });
    }
    if (path.includes("/questions/")) return response({ seed: "42", question: turnQuestion });
    return response(metadata);
  });
  render(<TurnRiverPage />);
  expect(await screen.findByText("The first player acts on the turn.")).toBeInTheDocument();
  expect(screen.getByLabelText("River not yet dealt")).toBeInTheDocument();
  expect(screen.queryByText("9♣")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Bet" }));
  expect(await screen.findByText("Decision feedback")).toBeInTheDocument();
  expect(screen.getByText(/EV loss:/)).toBeInTheDocument();
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(1);
});

it("shows the public river and prior turn action on a river question", async () => {
  window.history.replaceState(null, "", `#turn-river?seed=42&pack=${packHash}`);
  vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
    if (String(url).includes("/questions/")) return response({
      seed: "42",
      question: { ...turnQuestion, street: "RIVER", river, turnHistory: "bc",
        riverHistory: "", potBb: 40, remainingStackBb: 70 },
    });
    return response(metadata);
  });
  render(<TurnRiverPage />);
  expect(await screen.findByText("The first player acts on the river.")).toBeInTheDocument();
  expect(screen.getByText("9♣")).toBeInTheDocument();
  expect(screen.getByText("Turn: a bet was called.")).toBeInTheDocument();
  expect(screen.getByText("40.0 bb")).toBeInTheDocument();
});

it("refuses a stale saved-pack link", async () => {
  window.history.replaceState(null, "", `#turn-river?seed=42&pack=${"0".repeat(64)}`);
  vi.spyOn(globalThis, "fetch").mockImplementation(() => response(metadata));
  render(<TurnRiverPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("older turn-river solution");
  expect(screen.queryByRole("button", { name: "Bet" })).not.toBeInTheDocument();
});
