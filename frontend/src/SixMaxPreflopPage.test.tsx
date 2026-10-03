import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import SixMaxPreflopPage from "./SixMaxPreflopPage";
import FrontendRoot from "./FrontendRoot";
import type { PreflopAction, PreflopFeedback, PreflopMetadata, PreflopQuestion } from "./sixMaxPreflopApi";

const packHash = "a".repeat(64);
const metadata: PreflopMetadata = {
  spotId: "six-seat-research", spotHash: "b".repeat(64), packHash,
  packSchema: "six-max-preflop-checkdown-pack/v1", publicationStatus: "VALIDATION_ONLY",
  solverVersion: "research", generatedAt: "2026-10-03T17:50:22Z", payoffMethod: "EXACT_ENUMERATION",
  continuationModel: "MANDATORY_CHECKDOWN", chanceModel: "EXACT_RANGE_PRODUCT",
  seats: ["UTG", "HJ", "CO", "BTN", "SB", "BB"], rangeComboCounts: [1, 1, 1, 2, 1, 1],
  stackBb: 100, smallBlindBb: 0.5, raiseToBb: [3, 100],
  rake: { fraction: 0, capBb: 0, noFlopNoDrop: true },
  nashConvBb: 0.021103, maximumPayoffStandardErrorBb: 0, sessionLength: 10,
};
const response = (body: unknown, status = 200) =>
  Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) } as Response);
function question(seed: string, index: number): PreflopQuestion {
  const root = index === 0;
  return {
    sessionSeed: seed, index, packHash, actingSeat: root ? "UTG" : "BB", heroCombo: root ? "As Ah" : "9s 9h",
    priorActions: root ? [] : [
      { seat: "UTG", action: "raise:3.0" }, { seat: "HJ", action: "fold" },
      { seat: "CO", action: "call" }, { seat: "BTN", action: "raise:100.0" }, { seat: "SB", action: "fold" },
    ],
    legalActions: root ? ["fold", "call", "raise:3.0", "raise:100.0"] : ["fold", "call"],
    players: metadata.seats.map((seat, i) => {
      const committed = root ? [0, 0, 0, 0, 0.5, 1][i] : [3, 0, 3, 100, 0.5, 1][i];
      return { seat, committedBb: committed, remainingStackBb: 100 - committed,
        status: seat === (root ? "UTG" : "BB") ? "ACTING" : !root && (seat === "HJ" || seat === "SB") ? "FOLDED" : !root && seat === "BTN" ? "ALL_IN" : "ACTIVE",
        lastAction: root ? i < 4 ? null : { seat, kind: i === 4 ? "POST_SMALL_BLIND" : "POST_BIG_BLIND", amountBb: committed }
          : { seat, kind: i === 1 || i === 4 ? "FOLD" : i === 0 || i === 3 ? "RAISE_TO" : i === 5 ? "POST_BIG_BLIND" : "CALL", amountBb: i === 1 || i === 4 ? 0 : committed },
      };
    }),
    potBb: root ? 1.5 : 107.5, toCallBb: root ? 1 : 99,
    stackBb: 100, smallBlindBb: 0.5, publicationStatus: "VALIDATION_ONLY",
  };
}
function feedback(q: PreflopQuestion, selectedAction: PreflopAction): PreflopFeedback {
  const values = Object.fromEntries(q.legalActions.map((action, i) => [action, i]));
  return { selectedAction, selectedEvBb: values[selectedAction], bestEvBb: q.legalActions.length - 1,
    evLossBb: q.legalActions.length - 1 - values[selectedAction],
    actionEvBb: values as PreflopFeedback["actionEvBb"],
    actionFrequency: Object.fromEntries(q.legalActions.map((action) => [action, 1 / q.legalActions.length])) as PreflopFeedback["actionFrequency"],
    actionPayoffStandardErrorBb: Object.fromEntries(q.legalActions.map((action) => [action, 0])) as PreflopFeedback["actionPayoffStandardErrorBb"],
  };
}
function mockApi(options: {
  failGradeOnce?: boolean;
  grade?: (q: PreflopQuestion, action: PreflopAction) => unknown;
  review?: (attempts: { question: PreflopQuestion; feedback: PreflopFeedback }[]) => unknown;
} = {}) {
  let failGrade = options.failGradeOnce;
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url);
    const body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/grade")) {
      if (failGrade) { failGrade = false; return response({ message: "Temporary grading failure" }, 503); }
      const q = question(body.sessionSeed, body.index);
      return response(options.grade ? options.grade(q, body.action) : { question: q, feedback: feedback(q, body.action) });
    }
    if (path.endsWith("/review")) {
      const attempts = body.actions.map((action: PreflopAction, index: number) => {
        const q = question(body.sessionSeed, index); return { question: q, feedback: feedback(q, action) };
      });
      return response(options.review ? options.review(attempts) : { packHash, attempts, totalEvLossBb: 17, averageEvLossBb: 1.7 });
    }
    const match = path.match(/\/sessions\/(-?\d+)\/questions\/(\d+)$/);
    return response(match ? question(match[1], Number(match[2])) : metadata);
  });
}
function link(seed = "9223372036854775807", hash = packHash) {
  window.history.replaceState(null, "", `#sixmax-preflop?seed=${seed}&pack=${hash}`);
}
afterEach(() => { window.history.replaceState(null, "", "/"); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("routes the drill and shows chips, blind posts and legal raise amounts before feedback", async () => {
  link(); mockApi(); render(<FrontendRoot />);
  expect(await screen.findByText("DECISION 1 OF 10")).toBeInTheDocument();
  const table = screen.getByLabelText("Six-seat action table");
  expect(within(table).getAllByText(/bb behind/)).toHaveLength(6);
  expect(table).toHaveTextContent("Posted 0.5 bb");
  expect(table).toHaveTextContent("Posted 1 bb");
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♠A♥");
  expect(screen.getByRole("button", { name: "Raise to 3 bb" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "All-in · 100 bb" })).toBeInTheDocument();
  expect(screen.queryByLabelText("Action values")).not.toBeInTheDocument();
  expect(table).not.toHaveTextContent("K♠");
  expect(screen.getByRole("note")).toHaveTextContent("checked down");
});

it("completes ten server-graded decisions, reviews full tables and replays the same 64-bit seed", async () => {
  link(); const fetch = mockApi(); render(<SixMaxPreflopPage />);
  for (let index = 0; index < 10; index++) {
    expect(await screen.findByText(`DECISION ${index + 1} OF 10`)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Fold" }));
    expect(await screen.findByText("Decision feedback")).toBeInTheDocument();
    if (index === 0) expect(screen.getAllByText("Policy frequency 25.0%")).toHaveLength(4);
    if (index === 1) {
      const table = screen.getByLabelText("Six-seat action table");
      expect(table).toHaveTextContent("Raised to 100 bb"); expect(table).toHaveTextContent("Folded");
      expect(table).toHaveTextContent("0 bb behind"); expect(table).toHaveTextContent("99 bb behind");
    }
    await userEvent.click(screen.getByRole("button", { name: index === 9 ? "Review session" : "Next decision" }));
  }
  expect(await screen.findByText("Your review")).toBeInTheDocument();
  expect(screen.getByText("17.00 bb")).toBeInTheDocument(); // Use the server total, not a browser sum.
  const gradeCalls = fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"));
  expect(gradeCalls.map(([, init]) => JSON.parse(String(init?.body)))).toEqual(Array.from({ length: 10 }, (_, index) => ({
    sessionSeed: "9223372036854775807", index, packHash, action: "fold",
  })));
  const reviewCall = fetch.mock.calls.find(([url]) => String(url).endsWith("/review"));
  expect(JSON.parse(String(reviewCall?.[1]?.body))).toEqual({ sessionSeed: "9223372036854775807", packHash, actions: Array(10).fill("fold") });
  await userEvent.click(screen.getByText("1. UTG · As Ah"));
  expect(screen.getAllByLabelText("Six-seat action table")).toHaveLength(10);
  await userEvent.click(screen.getByRole("button", { name: "Replay this session" }));
  expect(await screen.findByText("DECISION 1 OF 10")).toBeInTheDocument();
  expect(window.location.hash).toContain("seed=9223372036854775807");
});

it("blocks stale links, then starts a fresh session with the current artifact", async () => {
  link("42", "0".repeat(64)); const fetch = mockApi(); render(<SixMaxPreflopPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("older solution");
  expect(fetch.mock.calls.some(([url]) => String(url).includes("/questions/"))).toBe(false);
  await userEvent.click(screen.getByRole("button", { name: "Start with current solution" }));
  expect(await screen.findByText("DECISION 1 OF 10")).toBeInTheDocument();
  expect(new URLSearchParams(window.location.hash.split("?")[1]).get("pack")).toBe(packHash);
  expect(new URLSearchParams(window.location.hash.split("?")[1]).get("seed")).not.toBe("42");
});

it("rejects changed public history in a grading response", async () => {
  link("42"); mockApi({ grade: (q, action) => ({ question: { ...q, potBb: 100 }, feedback: feedback(q, action) }) });
  render(<SixMaxPreflopPage />); await screen.findByText("DECISION 1 OF 10");
  await userEvent.click(screen.getByRole("button", { name: "Call" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("solution changed");
  expect(screen.queryByText("Decision feedback")).not.toBeInTheDocument();
});

it("recovers from a question network failure without replacing the session", async () => {
  link("42"); let fail = true;
  vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
    if (String(url).includes("/questions/")) {
      if (fail) { fail = false; return Promise.reject(new Error("Connection interrupted")); }
      return response(question("42", 0));
    }
    return response(metadata);
  });
  render(<SixMaxPreflopPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("Connection interrupted");
  await userEvent.click(screen.getByRole("button", { name: "Retry connection" }));
  expect(await screen.findByText("DECISION 1 OF 10")).toBeInTheDocument();
  expect(window.location.hash).toContain("seed=42");
});

it("keeps an unanswered decision available after a grading failure", async () => {
  link("42"); mockApi({ failGradeOnce: true }); render(<SixMaxPreflopPage />);
  await screen.findByText("DECISION 1 OF 10");
  await userEvent.click(screen.getByRole("button", { name: "Call" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Temporary grading failure");
  expect(screen.getByRole("button", { name: "Call" })).toBeEnabled();
  expect(screen.queryByText("Decision feedback")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Call" }));
  await screen.findByText("Decision feedback");
  await userEvent.click(screen.getByRole("button", { name: "Next decision" }));
  expect(await screen.findByText("DECISION 2 OF 10")).toBeInTheDocument();
});

it("rejects a review containing a different public decision", async () => {
  link("42"); mockApi({ review: (attempts) => ({ packHash,
    attempts: attempts.map((attempt, i) => i === 0 ? { ...attempt, question: { ...attempt.question, heroCombo: "2s 2h" } } : attempt),
    totalEvLossBb: 0, averageEvLossBb: 0,
  }) }); render(<SixMaxPreflopPage />);
  for (let index = 0; index < 10; index++) {
    await screen.findByText(`DECISION ${index + 1} OF 10`);
    await userEvent.click(screen.getByRole("button", { name: "Fold" }));
    await screen.findByText("Decision feedback");
    await userEvent.click(screen.getByRole("button", { name: index === 9 ? "Review session" : "Next decision" }));
  }
  expect(await screen.findByRole("alert")).toHaveTextContent("solution changed");
  expect(screen.queryByText("Your review")).not.toBeInTheDocument();
});

it("refuses unsupported pack assumptions before drawing any questions", async () => {
  link("42"); const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => response({ ...metadata, continuationModel: "UNREVIEWED_POSTFLOP" }));
  render(<SixMaxPreflopPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("not supported");
  expect(fetch.mock.calls.some(([url]) => String(url).includes("/questions/"))).toBe(false);
});

it("handles a disabled API and retries metadata", async () => {
  link("42"); const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => response({ message: "Not found" }, 404));
  render(<SixMaxPreflopPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent("not enabled");
  fetch.mockImplementation((url) => response(String(url).includes("/questions/") ? question("42", 0) : metadata));
  await userEvent.click(screen.getByRole("button", { name: "Retry connection" }));
  expect(await screen.findByText("DECISION 1 OF 10")).toBeInTheDocument();
});

it("uses number shortcuts once and preserves normal Enter behavior on focused controls", async () => {
  link("42"); const fetch = mockApi(); render(<SixMaxPreflopPage />);
  await screen.findByText("DECISION 1 OF 10");
  fireEvent.keyDown(document.body, { key: "3" });
  fireEvent.keyDown(document.body, { key: "3" });
  await screen.findByText("Decision feedback");
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(1);
  expect(screen.getByLabelText("Action values")).toHaveTextContent("Raise to 3 bb · Your choice");
  fireEvent.keyDown(screen.getByRole("button", { name: "Copy session link" }), { key: "Enter" });
  expect(screen.getByText("DECISION 1 OF 10")).toBeInTheDocument();
  fireEvent.keyDown(document.body, { key: "Enter" });
  expect(await screen.findByText("DECISION 2 OF 10")).toBeInTheDocument();
});

it("copies a pack-bound session link and retains the full seed", async () => {
  link(); mockApi(); const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal("navigator", { ...navigator, clipboard: { writeText } });
  render(<SixMaxPreflopPage />); await screen.findByText("DECISION 1 OF 10");
  fireEvent.click(screen.getByRole("button", { name: "Copy session link" }));
  await waitFor(() => expect(writeText).toHaveBeenCalledWith(expect.stringContaining("seed=9223372036854775807")));
  expect(await screen.findByText(/Session link copied/)).toBeInTheDocument();
  vi.unstubAllGlobals();
});
