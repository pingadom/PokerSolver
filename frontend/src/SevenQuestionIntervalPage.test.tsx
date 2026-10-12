import { fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import sevenJson from "./test-data/interval-collection-seven-review.json";
import { collectionFixture as old, collectionMetadata, fixtureGrade, collectionReview } from "./test-data/intervalCollection";
import { checkCollectionInventory, parseCollectionReview, type CollectionReview } from "./intervalCollectionApi";

const seven = sevenJson as CollectionReview;
const metadata = { ...collectionMetadata, collectionHash: seven.collectionHash, availableQuestions: 7 };
function setup(query = "") {
  const response = (body: unknown) => Promise.resolve({ ok: true, json: async () => body } as Response);
  vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/grade")) return response(fixtureGrade(body.questionIndex, body.action, seven));
    if (path.endsWith("/review")) return response(collectionReview(body.choices, seven));
    const match = path.match(/\/questions\/(\d+)\?/);
    return response(match ? seven.questions[Number(match[1])] : metadata);
  });
  window.history.replaceState(null, "", `#interval-collection${query}`);
  render(<FrontendRoot />);
}
afterEach(() => { window.history.replaceState(null, "", "/"); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it("switches the acting hero from BB to BTN and retains all eight canonical attempts", async () => {
  setup(); await screen.findByRole("button", { name: "Call" });
  const actions = ["Call", "Raise to 9 bb", "Call", "Raise to 50 bb", "Call", "Raise to 22 bb", "Raise to 22 bb"];
  for (const [index, action] of actions.entries()) {
    expect(screen.getByLabelText(`${index < 4 ? "BB" : "BTN"} player`)).toHaveClass("hero", "acting");
    expect(screen.getByLabelText(`${index < 4 ? "BTN" : "BB"} player`)).toHaveClass("villain");
    fireEvent.click(screen.getByRole("button", { name: action }));
    await screen.findByRole("heading", { name: index === 3 ? "Strategy-dependent" : "Within limit" });
    if (index === 3) {
      fireEvent.click(screen.getByRole("button", { name: "Try another move" }));
      fireEvent.click(screen.getByRole("button", { name: "Fold" }));
      await screen.findByRole("heading", { name: "Outside limit" });
    }
    if (index < 6) fireEvent.click(screen.getByRole("button", { name: "Next question" }));
  }
  expect(screen.getByText(/Question 7 of 7/)).toHaveTextContent("7 of 7 practised");
  expect(screen.getByRole("button", { name: "Next question" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Review 8 attempts" }));
  const review = await screen.findByRole("region", { name: "Collection review" });
  expect(review).toHaveTextContent("Within limit: 6 · Outside limit: 1 · Strategy-dependent: 1");
  const create = vi.fn((blob: Blob) => { void blob; return "blob:seven"; });
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = vi.fn(); });
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  fireEvent.click(within(review).getByRole("button", { name: "Download replayable review" }));
  const text = await new Promise<string>((resolve) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.readAsText(create.mock.calls[0][0] as Blob); });
  expect(JSON.parse(text)).toEqual(seven);
});
it("deep-links to a button hand with the opponent hidden and only current-question feedback", async () => {
  setup(`?collection=${seven.collectionHash}&question=4`);
  await screen.findByRole("button", { name: "Call" });
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("9♠J♠");
  expect(screen.getByLabelText("BTN player")).toHaveClass("hero", "acting");
  fireEvent.click(screen.getByRole("button", { name: "Call" }));
  await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(screen.getByRole("button", { name: "Next question" }));
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♥K♥");
  expect(screen.getByRole("button", { name: "Call" })).not.toHaveTextContent("EV");
  fireEvent.click(screen.getByRole("button", { name: "Raise to 22 bb" }));
  await screen.findByRole("heading", { name: "Within limit" });
  expect(screen.getByRole("button", { name: "Call" })).toHaveTextContent("Outside limit");
});
it("validates mixed-player inventory and rejects an old four-question review", () => {
  checkCollectionInventory(seven.questions, metadata);
  expect(parseCollectionReview(seven, metadata, seven.questions, seven.attempts)).toEqual(seven);
  expect(() => parseCollectionReview(old, metadata, seven.questions, seven.attempts)).toThrow();
});
