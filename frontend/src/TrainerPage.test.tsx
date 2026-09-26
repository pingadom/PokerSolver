import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import TrainerPage from "./TrainerPage";

const response = (body: unknown, ok = true) =>
  Promise.resolve({ ok, json: () => Promise.resolve(body) } as Response);
const packHash = "a".repeat(64);
const metadata = {
  spotId: "utg-versus-button-five-bet",
  spotHash: "b".repeat(64),
  packHash,
  publicationStatus: "VALIDATION_ONLY",
  heroSeat: "UTG",
  opponentSeat: "BTN",
  heroRange: ["Ac Ad", "Ah Kh"],
  opponentRange: ["As Kc", "Qc Qd"],
  effectiveStackBb: 100,
  rakeModel: "NO_RAKE",
  payoffMethod: "EXACT_ENUMERATION",
  estimatedGameGapBb: 0.000149,
  maximumCalledPayoffStandardErrorBb: 0,
  sessionLength: 10,
};
const question = {
  spotId: metadata.spotId,
  spotHash: metadata.spotHash,
  publicationStatus: "VALIDATION_ONLY",
  heroSeat: "UTG",
  opponentSeat: "BTN",
  heroCombo: "Ac Ad",
  effectiveStackBb: 100,
  rakeModel: "NO_RAKE",
  payoffMethod: "EXACT_ENUMERATION",
  potBb: 62,
  heroCommittedBb: 22,
  opponentCommittedBb: 40,
  priorActions: [
    { seat: "SB", kind: "POST_SMALL_BLIND", amountBb: 0.5 },
    { seat: "BB", kind: "POST_BIG_BLIND", amountBb: 1 },
    { seat: "UTG", kind: "RAISE_TO", amountBb: 22 },
    { seat: "BTN", kind: "RAISE_TO", amountBb: 40 },
  ],
  legalActions: ["SHOVE", "FOLD"],
};
const feedback = (action: "SHOVE" | "FOLD") => ({
  spotHash: metadata.spotHash,
  heroCombo: question.heroCombo,
  selectedAction: action,
  selectedEvBb: action === "SHOVE" ? 12 : -22,
  bestEvBb: 12,
  evLossBb: action === "SHOVE" ? 0 : 34,
  shoveFrequency: 0.9,
  foldFrequency: 0.1,
  shoveEvBb: 12,
  foldEvBb: -22,
});

afterEach(() => {
  vi.restoreAllMocks();
  window.history.replaceState(null, "", "/");
});

it("completes ten decisions with server grades and a full review", async () => {
  window.history.replaceState(null, "", `#trainer?seed=42&pack=${packHash}`);
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url);
    if (path.endsWith("/sessions/review")) {
      const body = JSON.parse(String(init?.body));
      return response({
        packHash,
        attempts: body.actions.map((action: "SHOVE" | "FOLD", index: number) => ({
          question: { sessionSeed: "42", index, packHash, question },
          feedback: feedback(action),
        })),
        totalEvLossBb: 0,
        averageEvLossBb: 0,
      });
    }
    if (path.endsWith("/sessions/grade")) {
      const body = JSON.parse(String(init?.body));
      expect(body).toEqual({ sessionSeed: "42", index: body.index, packHash, action: "SHOVE" });
      return response({
        question: { sessionSeed: "42", index: body.index, packHash, question },
        feedback: feedback("SHOVE"),
      });
    }
    if (/\/sessions\/42\/questions\/\d+$/.test(path)) {
      const index = Number(path.split("/").at(-1));
      return response({ sessionSeed: "42", index, packHash, question });
    }
    return response(metadata);
  });

  render(<TrainerPage />);
  expect(await screen.findByText("Face the five-bet")).toBeInTheDocument();
  expect(await screen.findByText("DECISION 1 OF 10")).toBeInTheDocument();
  expect(screen.getByLabelText("Six-seat table")).toHaveTextContent("BTN");
  expect(screen.getByLabelText("Six-seat table")).toHaveTextContent("raises to 40 bb");
  expect(screen.getByText("Ac")).toBeInTheDocument();
  expect(screen.queryByText("Decision feedback")).not.toBeInTheDocument();
  for (let index = 0; index < 10; index++) {
    await userEvent.click(screen.getByRole("button", { name: "Shove" }));
    expect(await screen.findByText("Decision feedback")).toBeInTheDocument();
    expect(screen.getByText(/Solver frequency 90.0%/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", {
      name: index === 9 ? "Review session" : "Next decision",
    }));
    if (index < 9)
      expect(await screen.findByText(`DECISION ${index + 2} OF 10`)).toBeInTheDocument();
  }
  expect(await screen.findByText("Your review")).toBeInTheDocument();
  expect(screen.getAllByText(/0.00 bb lost/)).toHaveLength(10);
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/sessions/grade"))).toHaveLength(10);
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/sessions/review"))).toHaveLength(1);
  expect(window.location.hash).toContain("seed=42");
});

it("shows an actionable unavailable state without exposing a decision", async () => {
  window.history.replaceState(null, "", "#trainer?seed=42");
  vi.spyOn(globalThis, "fetch").mockImplementation(() =>
    response({ message: "Resource was not found" }, false),
  );
  render(<TrainerPage />);
  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Resource was not found"));
  expect(screen.getByRole("alert")).toHaveTextContent("Enable the local research trainer");
  expect(screen.queryByRole("button", { name: "Shove" })).not.toBeInTheDocument();
});

it("detects saved links from an older pack", async () => {
  window.history.replaceState(null, "", `#trainer?seed=42&pack=${"0".repeat(64)}`);
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation((url) =>
    String(url).endsWith("/sessions/grade") ? response({ message: "unexpected grade" }, false) :
      String(url).includes("/questions/")
        ? response({ sessionSeed: "42", index: 0, packHash, question })
        : response(metadata),
  );
  render(<TrainerPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("older solution");
  await userEvent.click(screen.getByRole("button", { name: "Start with current solution" }));
  await waitFor(() => expect(window.location.hash).toContain(`pack=${packHash}`));
  expect(await screen.findByText("DECISION 1 OF 10")).toBeInTheDocument();
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/api/v1/trainer/research"))).toHaveLength(2);
});
