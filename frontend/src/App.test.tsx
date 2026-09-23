import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, it, expect } from "vitest";
import App from "./App";
import { presets, scenarioFromPreset } from "./presets";
const response = (body: unknown, ok = true) =>
  Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
it("provides valid, distinct cards in every example matchup", () => {
  for (const preset of presets) {
    const scenario = scenarioFromPreset(preset);
    const cards = [
      ...scenario.players.flatMap((player) => player.cards),
      ...scenario.board,
    ];
    expect(new Set(cards).size).toBe(cards.length);
    expect(scenario.iterations).toBe(200000);
    expect(scenario.seed).toBe("42");
  }
});
it("loads a matchup into the form without starting a run", async () => {
  Element.prototype.scrollIntoView = vi.fn();
  const fetch = vi
    .spyOn(globalThis, "fetch")
    .mockImplementation((url, init) => {
      if (init?.method === "POST")
        return response({ simulationId: "preset-run" });
      if (String(url).endsWith("/preset-run/results"))
        return response({ totalTrials: 200000, elapsedMs: 100, players: [] });
      if (String(url).endsWith("/preset-run"))
        return response({
          simulationId: "preset-run",
          status: "COMPLETED",
          completedIterations: 200000,
          requestedIterations: 200000,
          completedBatches: 2,
          totalBatches: 2,
          configuration: { seed: "42", players: [] },
        });
      return response([]);
    });
  render(<App />);
  await userEvent.click(
    screen.getByRole("button", { name: "Load Pair vs suited overcards" }),
  );
  expect(screen.getByLabelText("Player 1 hole cards")).toHaveValue("7C 7D");
  expect(screen.getByLabelText("Player 2 hole cards")).toHaveValue("AH KH");
  expect(screen.getByLabelText("Number of trials")).toHaveValue(200000);
  expect(screen.getByRole("status")).toHaveTextContent(
    "Pair vs suited overcards loaded",
  );
  expect(fetch.mock.calls.some(([, init]) => init?.method === "POST")).toBe(
    false,
  );
  await userEvent.click(screen.getByRole("button", { name: /Run simulation/ }));
  await waitFor(() => {
    const post = fetch.mock.calls.find(([, init]) => init?.method === "POST");
    expect(post).toBeDefined();
    const body = JSON.parse(String(post?.[1]?.body));
    expect(body.players).toEqual([
      { name: "77", cards: ["7C", "7D"] },
      { name: "AK suited", cards: ["AH", "KH"] },
    ]);
    expect(body.board).toEqual([]);
    expect(body.iterations).toBe(200000);
  });
});
it("replaces board and player count when switching examples", async () => {
  Element.prototype.scrollIntoView = vi.fn();
  vi.spyOn(globalThis, "fetch").mockImplementation(() => response([]));
  render(<App />);
  await userEvent.click(
    screen.getByRole("button", { name: "Load Overpair vs combo draw" }),
  );
  expect(screen.getByRole("textbox", { name: /Community cards/ })).toHaveValue(
    "JH TH 2C",
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Load Add a third player" }),
  );
  expect(screen.getByRole("textbox", { name: /Community cards/ })).toHaveValue(
    "",
  );
  expect(screen.getByLabelText("Player 3 hole cards")).toHaveValue("9H 8H");
  expect(screen.getByText("3 / 9 players")).toBeInTheDocument();
});
it("shows actionable API errors", async () => {
  vi.spyOn(globalThis, "fetch").mockImplementation((_url, init) =>
    init?.method === "POST"
      ? response({ message: "Queue capacity reached" }, false)
      : response([]),
  );
  render(<App />);
  await userEvent.click(screen.getByRole("button", { name: /Run simulation/ }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Queue capacity reached",
  );
});
it("renders completed equities and stops polling", async () => {
  const fetch = vi
    .spyOn(globalThis, "fetch")
    .mockImplementation((url, init) => {
      if (init?.method === "POST") return response({ simulationId: "test-id" });
      if (String(url).endsWith("/results"))
        return response({
          totalTrials: 100,
          elapsedMs: 200,
          players: [
            { name: "AA", wins: 70, ties: 0, losses: 30, equity: 0.7 },
            { name: "KK", wins: 30, ties: 0, losses: 70, equity: 0.3 },
          ],
        });
      if (String(url).endsWith("/test-id"))
        return response({
          simulationId: "test-id",
          status: "COMPLETED",
          completedIterations: 100,
          requestedIterations: 100,
          completedBatches: 1,
          totalBatches: 1,
          configuration: { seed: 42, players: [] },
        });
      return response([]);
    });
  render(<App />);
  await userEvent.click(screen.getByRole("button", { name: /Run simulation/ }));
  expect(await screen.findByText("70.00")).toBeInTheDocument();
  expect(screen.getByRole("table")).toHaveTextContent("AA");
  await waitFor(() =>
    expect(screen.getByText("100% complete")).toBeInTheDocument(),
  );
  const before = fetch.mock.calls.filter(([url]) =>
    String(url).endsWith("/test-id"),
  ).length;
  await new Promise((resolve) => setTimeout(resolve, 2100));
  expect(
    fetch.mock.calls.filter(([url]) => String(url).endsWith("/test-id")),
  ).toHaveLength(before);
});
