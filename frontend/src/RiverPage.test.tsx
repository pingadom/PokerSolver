import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import RiverPage from "./RiverPage";

const response = (body: unknown, ok = true) =>
  Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
const packHash = "c".repeat(64);
const board = [
  { rank: "TWO", suit: "CLUBS" },
  { rank: "THREE", suit: "DIAMONDS" },
  { rank: "FOUR", suit: "HEARTS" },
  { rank: "EIGHT", suit: "SPADES" },
  { rank: "NINE", suit: "CLUBS" },
];
const metadata = {
  spotId: "button-versus-big-blind-river-validation",
  spotHash: "b".repeat(64),
  packHash,
  publicationStatus: "VALIDATION_ONLY",
  firstSeat: "BB",
  secondSeat: "BTN",
  board,
  potBb: 20,
  remainingStackBb: 80,
  betBb: 10,
  firstRange: ["Ah As", "Th Ts"],
  secondRange: ["Kc Kd", "Qc Qd"],
  gameGapBb: 0.000956,
  availableQuestions: 8,
  rakeModel: "NO_RAKE",
  bettingTree: "SINGLE_BET_NO_RAISES",
};
const question = {
  packHash,
  spotId: metadata.spotId,
  publicationStatus: "VALIDATION_ONLY",
  board,
  potBb: 20,
  remainingStackBb: 80,
  betBb: 10,
  actingSeat: "BB",
  heroCombo: "Ah As",
  publicHistory: "",
  legalActions: ["k", "b"],
  gameGapBb: metadata.gameGapBb,
};

afterEach(() => {
  vi.restoreAllMocks();
  window.history.replaceState(null, "", "/");
});

it("plays a river decision with server-grade feedback", async () => {
  window.history.replaceState(null, "", `#river?seed=42&pack=${packHash}`);
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url);
    if (path.endsWith("/grade")) {
      expect(JSON.parse(String(init?.body))).toEqual({ seed: "42", packHash, action: "b" });
      return response({
        packHash,
        heroCombo: "Ah As",
        publicHistory: "",
        selectedAction: "b",
        selectedEvBb: 8,
        bestEvBb: 8,
        evLossBb: 0,
        actionFrequency: { k: 0.5, b: 0.5 },
        actionEvBb: { k: 7.5, b: 8 },
      });
    }
    if (path.includes("/questions/"))
      return response({ seed: path.split("/").at(-1), question });
    return response(metadata);
  });
  render(<RiverPage />);
  expect(await screen.findByText("BB acts first on the river.")).toBeInTheDocument();
  expect(screen.getByText("2♣")).toBeInTheDocument();
  expect(screen.getByText("Ah")).toBeInTheDocument();
  expect(screen.queryByText("Decision feedback")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Bet" }));
  expect(await screen.findByText("Decision feedback")).toBeInTheDocument();
  expect(screen.getAllByText(/Solver frequency 50.0%/)).toHaveLength(2);
  expect(screen.getByRole("button", { name: "Next river question" })).toBeInTheDocument();
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(1);
  await userEvent.click(screen.getByRole("button", { name: "Next river question" }));
  await waitFor(() => expect(fetch.mock.calls.filter(([url]) => String(url).includes("/questions/"))).toHaveLength(2));
  expect(await screen.findByRole("button", { name: "Bet" })).toBeEnabled();
  expect(screen.queryByText("Decision feedback")).not.toBeInTheDocument();
  expect(window.location.hash).toContain(`pack=${packHash}`);
});

it("keeps old pack links out of the decision flow", async () => {
  window.history.replaceState(null, "", `#river?seed=42&pack=${"0".repeat(64)}`);
  vi.spyOn(globalThis, "fetch").mockImplementation(() => response(metadata));
  render(<RiverPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("older river solution");
  expect(screen.queryByRole("button", { name: "Bet" })).not.toBeInTheDocument();
});

it("shows an actionable unavailable state", async () => {
  window.history.replaceState(null, "", "#river?seed=42");
  vi.spyOn(globalThis, "fetch").mockImplementation(() =>
    response({ message: "Resource was not found" }, false),
  );
  render(<RiverPage />);
  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Resource was not found"));
  expect(screen.getByRole("alert")).toHaveTextContent("Enable the local river research API");
});
