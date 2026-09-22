import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, it, expect } from "vitest";
import App from "./App";
const response = (body: unknown, ok = true) =>
  Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
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
