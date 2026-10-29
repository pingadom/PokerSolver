import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import saved from "./test-data/size-menu-btn-drill.json";
import { btnReviewCounts, btnSessionKey, type BtnAction, type BtnChoice, type BtnGrade } from "./sizeMenuBtnApi";
import { intervalRange } from "./intervalTrainerFormatting";

const PIN = saved.metadata.studyHash;
const response = (value: unknown, status = 200) => Promise.resolve({ ok: status < 400, status,
  text: async () => typeof value === "string" ? value : JSON.stringify(value) } as Response);
function grade(index: number, action: string) { return saved.grades.find(g => g.sourceCaseIndex === index && g.move.action === action)!; }
function review(choices: BtnChoice[]) {
  const attempts = choices.map((attempt, ordinal) => ({ ordinal, attempt, grade: structuredClone(grade(attempt.sourceCaseIndex, attempt.action)) as BtnGrade }));
  return { studyHash: PIN, attempts, counts: btnReviewCounts(attempts) };
}
function mockApi() {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/grade")) return response(grade(body.sourceCaseIndex, body.action));
    if (path.endsWith("/review") || path.endsWith("/review/export")) return response(review(body.choices));
    const index = path.match(/\/questions\/(\d+)/)?.[1];
    return response(index === undefined ? saved.metadata : saved.questions[Number(index)]);
  });
}
function open(index = 0) {
  window.history.replaceState(null, "", `#btn-sizing-flop?study=${PIN}&hand=${index}`);
  return render(<FrontendRoot />);
}
const button = (name: string) => screen.getByRole("button", { name });
const postCalls = (fetch: ReturnType<typeof mockApi>, path: string) => fetch.mock.calls.filter(([url]) => String(url).endsWith(path));
function file(text: string) {
  const f = new File([text], "review.json", { type: "application/json" });
  Object.defineProperty(f, "text", { value: () => Promise.resolve(text) }); return f;
}
afterEach(() => { window.localStorage.clear(); window.history.replaceState(null, "", "/"); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it.each([[0, "2♣3♣", "Outside limit", "Within limit"], [1, "A♥K♥", "Within limit", "Outside limit"],
  [2, "A♠Q♠", "Within limit", "Outside limit"], [3, "Q♥T♥", "Within limit", "Outside limit"]])(
  "shows and plays both choices for source hand %s at the table", async (index, cards, callVerdict, foldVerdict) => {
    const i = Number(index), fetch = mockApi(); open(i);
    await screen.findByRole("button", { name: "Fold" });
    expect(screen.getByLabelText("Your cards")).toHaveTextContent(String(cards));
    expect(screen.getByLabelText("Flop board")).toHaveTextContent("6♦Q♣T♦");
    expect(screen.getByLabelText("BTN player")).toHaveClass("hero", "acting");
    expect(screen.getByLabelText("BB player")).toHaveClass("villain", "move-raise");
    expect(screen.getByLabelText("BB player")).toHaveTextContent("Bet 1.625 bb · ¼ pot");
    for (const seat of ["UTG", "HJ", "CO", "SB"]) expect(screen.getByLabelText(`${seat} player`)).toHaveClass("folded");
    const value = grade(i, "call").move;
    expect(button("Call 1.625 bb")).toHaveTextContent(intervalRange(value.lowerEvBb, value.upperEvBb));
    expect(button("Fold")).toHaveTextContent("Hand EV");
    expect(screen.queryByRole("heading", { name: String(callVerdict) })).not.toBeInTheDocument();
    expect(postCalls(fetch, "/grade")).toHaveLength(0);
    expect(window.localStorage.getItem(btnSessionKey(PIN))).toBeNull();
    fireEvent.keyDown(window, { key: "2" });
    await screen.findByRole("heading", { name: String(callVerdict) });
    expect(screen.getByLabelText("BTN player")).toHaveTextContent("You chose · Call 1.625 bb");
    expect(button("Next hand").closest(".poker-hero-controls")).toBeInTheDocument();
    fireEvent.click(button("Try another move")); fireEvent.click(button("Fold"));
    await screen.findByRole("heading", { name: String(foldVerdict) });
    expect(JSON.parse(window.localStorage.getItem(btnSessionKey(PIN))!).attempts).toHaveLength(2);
    expect(postCalls(fetch, "/grade")).toHaveLength(2);
  });

it("keeps values, histories and feedback scoped to each hand while next wraps to the first", async () => {
  mockApi(); open(3); fireEvent.click(await screen.findByRole("button", { name: "Call 1.625 bb" }));
  await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(button("Next hand"));
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("2♣3♣");
  expect(screen.queryByRole("heading", { name: "Within limit" })).not.toBeInTheDocument();
  expect(button("Call 1.625 bb")).toBeEnabled();
  const nav = within(screen.getByRole("navigation", { name: "Choose a BTN hand" }));
  fireEvent.click(nav.getByRole("button", { name: "Q♥ T♥ ✓" }));
  expect(screen.getByRole("heading", { name: "Within limit" })).toBeInTheDocument();
  expect(window.location.hash).toContain("hand=3");
});

it("preserves repeated attempt order and exports a canonical review after server validation", async () => {
  const fetch = mockApi(); open(); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  await screen.findByRole("heading", { name: "Within limit" });
  fireEvent.click(button("Try another move")); fireEvent.click(button("Call 1.625 bb"));
  await screen.findByRole("heading", { name: "Outside limit" });
  fireEvent.click(button("Review 2 attempts"));
  await screen.findByRole("heading", { name: "1 of 4 hands practised · 2 attempts" });
  const create = vi.fn(() => "blob:review"), revoke = vi.fn();
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  fireEvent.click(button("Download review"));
  await waitFor(() => expect(create).toHaveBeenCalledTimes(1));
  expect(click).toHaveBeenCalledTimes(1); expect(revoke).toHaveBeenCalledWith("blob:review");
  const body = JSON.parse(String(postCalls(fetch, "/review/export")[0][1]?.body));
  expect(body).toEqual({ studyHash: PIN, choices: [{ sourceCaseIndex: 0, action: "fold" }, { sourceCaseIndex: 0, action: "call" }] });
});

it("restores only study-pinned validated local attempts and start over clears only that session", async () => {
  mockApi();
  const initial = review([{ sourceCaseIndex: 1, action: "call" }]);
  window.localStorage.setItem(btnSessionKey(PIN), JSON.stringify(initial));
  window.localStorage.setItem("pokerlab:other-session", "keep");
  open(1); await screen.findByRole("heading", { name: "Within limit" });
  expect(screen.getByText("Restored 1 saved attempts.")).toBeInTheDocument();
  fireEvent.click(button("Start over")); await screen.findByRole("button", { name: "Review 0 attempts" });
  expect(window.localStorage.getItem(btnSessionKey(PIN))).toBeNull();
  expect(window.localStorage.getItem("pokerlab:other-session")).toBe("keep");
});

it("ignores corrupt local attempts without treating them as earned feedback", async () => {
  mockApi(); const bad = review([{ sourceCaseIndex: 0, action: "fold" }]); bad.attempts[0].grade.move.lowerEvBb = 999;
  window.localStorage.setItem(btnSessionKey(PIN), JSON.stringify(bad)); open();
  await screen.findByRole("button", { name: "Review 0 attempts" });
  expect(screen.getByText(/Saved practice could not be restored/)).toBeInTheDocument();
  expect(button("Fold")).toBeEnabled();
});

it("does not duplicate a pending grade and aborts its late response after a deep-link change", async () => {
  const fetch = mockApi(), original = fetch.getMockImplementation()!;
  let finish!: (value: Response) => void, signal: AbortSignal | undefined;
  fetch.mockImplementation((url, init) => String(url).endsWith("/grade") ? new Promise(resolve => { finish = resolve; signal = init?.signal as AbortSignal; }) : original(url, init));
  open(); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  fireEvent.keyDown(window, { key: "1" }); fireEvent.click(button("Fold"));
  expect(postCalls(fetch, "/grade")).toHaveLength(1); expect(button("Next hand")).toBeDisabled();
  act(() => { window.history.replaceState(null, "", "#btn-sizing-flop?hand=3"); window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("button", { name: "Fold" }); expect(signal?.aborted).toBe(true);
  await act(async () => finish(await response(grade(0, "fold"))));
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("Q♥T♥");
  expect(button("Review 0 attempts")).toBeEnabled();
  expect(window.localStorage.getItem(btnSessionKey(PIN))).toBeNull();
});

it("retains a transiently failed choice for retry without recording it twice", async () => {
  const fetch = mockApi(), original = fetch.getMockImplementation()!;
  let reject = true;
  fetch.mockImplementation((url, init) => String(url).endsWith("/grade") && reject ? response({}, 503) : original(url, init));
  open(); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("503");
  expect(button("Review 0 attempts")).toBeEnabled(); reject = false;
  fireEvent.click(button("Dismiss error")); fireEvent.click(button("Fold"));
  await screen.findByRole("heading", { name: "Within limit" });
  expect(JSON.parse(window.localStorage.getItem(btnSessionKey(PIN))!).attempts).toHaveLength(1);
});

it("fails closed on a grade for a different hand and recovers by loading the current study", async () => {
  const fetch = mockApi(), original = fetch.getMockImplementation()!;
  fetch.mockImplementation((url, init) => String(url).endsWith("/grade") ? response(grade(3, "call")) : original(url, init));
  open(); fireEvent.click(await screen.findByRole("button", { name: "Call 1.625 bb" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Fold" })).not.toBeInTheDocument();
  expect(window.localStorage.getItem(btnSessionKey(PIN))).toBeNull();
  fetch.mockImplementation(original); fireEvent.click(button("Load current study"));
  await screen.findByRole("button", { name: "Fold" });
});

it.each(["?hand=4", "?hand=0.5", "?hand=00", "?hand=1&hand=2", "?unknown=x", "?study=wrong", "-old"])("rejects invalid or stale deep link %s", async suffix => {
  mockApi(); window.history.replaceState(null, "", "#btn-sizing-flop" + suffix); render(<FrontendRoot />);
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("button", { name: "Fold" })).not.toBeInTheDocument();
});

it("reports disabled API separately from transient server errors", async () => {
  vi.spyOn(globalThis, "fetch").mockImplementation(() => response({}, 404)); open();
  expect(await screen.findByRole("alert")).toHaveTextContent("not enabled");
  expect(button("Load current study")).toBeEnabled();
});

it("ignores modified, repeated and form-target keyboard events", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Fold" });
  for (const modifiers of [{ ctrlKey: true }, { shiftKey: true }, { repeat: true }, { altKey: true }, { metaKey: true }])
    fireEvent.keyDown(window, { key: "1", ...modifiers });
  fireEvent.keyDown(screen.getByLabelText("Import review"), { key: "2" });
  fireEvent.keyDown(button("Next hand"), { key: "1" });
  expect(postCalls(fetch, "/grade")).toHaveLength(0);
});

it("revalidates imported choices on the server before replacing the session", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Fold" });
  const imported = review([{ sourceCaseIndex: 3, action: "call" }, { sourceCaseIndex: 0, action: "fold" }]);
  fireEvent.change(screen.getByLabelText("Import review"), { target: { files: [file(JSON.stringify(imported))] } });
  await screen.findByRole("heading", { name: "2 of 4 hands practised · 2 attempts" });
  expect(postCalls(fetch, "/review")).toHaveLength(1);
  expect(JSON.parse(window.localStorage.getItem(btnSessionKey(PIN))!)).toEqual(imported);
});

it("rejects a corrupt import without erasing the current attempts or hiding the table", async () => {
  mockApi(); open(); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  await screen.findByRole("heading", { name: "Within limit" });
  const before = window.localStorage.getItem(btnSessionKey(PIN));
  fireEvent.change(screen.getByLabelText("Import review"), { target: { files: [file("not json")] } });
  expect(await screen.findByRole("alert")).toHaveTextContent("Your current attempts are unchanged");
  expect(screen.getByRole("heading", { name: "Within limit" })).toBeInTheDocument();
  expect(window.localStorage.getItem(btnSessionKey(PIN))).toBe(before);
});

it("keeps a full128-attempt session reviewable and navigable while blocking more grades", async () => {
  const fetch = mockApi();
  const full = review(Array.from({ length: 128 }, () => ({ sourceCaseIndex: 0, action: "fold" as BtnAction })));
  window.localStorage.setItem(btnSessionKey(PIN), JSON.stringify(full)); open();
  await screen.findByRole("button", { name: "Review 128 attempts" });
  expect(button("Fold")).toBeDisabled(); expect(button("Call 1.625 bb")).toBeDisabled();
  expect(screen.queryByRole("button", { name: "Try another move" })).not.toBeInTheDocument();
  fireEvent.click(button("Next hand")); expect(button("Call 1.625 bb")).toBeDisabled();
  fireEvent.keyDown(window, { key: "2" }); expect(postCalls(fetch, "/grade")).toHaveLength(0);
  fireEvent.click(button("Review 128 attempts"));
  await screen.findByRole("heading", { name: "1 of 4 hands practised · 128 attempts" });
  expect(button("Download review")).toBeEnabled();
});

it("allows attempts to remain in memory when device storage is unavailable", async () => {
  mockApi(); vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("unavailable"); });
  open(); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  await screen.findByRole("heading", { name: "Within limit" });
  expect(screen.getByText(/Device storage is unavailable/)).toBeInTheDocument();
  expect(button("Review 1 attempt")).toBeEnabled();
});
