import raw from "./test-data/sizing-flop-collection-review.json?raw";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { sizingFlopCollectionFixture as fixture, sizingFlopCollectionMetadata as metadata, sizingFlopCollectionFixtureReview } from "./test-data/sizingFlopCollection";
import { sizingGrade, sizingDecision, sizingSessionKey } from "./sizingFlopCollectionApi";
import { sizingFlopCollectionActionLabel } from "./SizingFlopCollectionDecisionTable";
import { intervalRange } from "./intervalTrainerFormatting";

const response = (body: unknown, status = 200) => Promise.resolve({ ok: status < 400, status,
  json: () => Promise.resolve(body), text: () => Promise.resolve(typeof body === "string" ? body : JSON.stringify(body)) } as Response);
function mockApi() {
  return vi.spyOn(globalThis, "fetch").mockImplementation((url, init) => {
    const path = String(url), body = init?.body ? JSON.parse(String(init.body)) : null;
    if (path.endsWith("/review/export")) return response(raw);
    if (path.endsWith("/grade")) return response(fixture.attempts.find(a => a.questionIndex === body.questionIndex && a.action === body.action)!.feedback);
    if (path.endsWith("/review")) return response(sizingFlopCollectionFixtureReview(body.choices));
    const index = path.match(/\/questions\/(\d+)/)?.[1];
    return response(index === undefined ? metadata : fixture.questions[Number(index)]);
  });
}
function open(index = 0) {
  window.history.replaceState(null, "", `#interval-sizing-flop?collection=${metadata.collectionHash}&question=${index}`);
  return render(<FrontendRoot />);
}
const button = (name: string) => screen.getByRole("button", { name });
afterEach(() => { window.localStorage.clear(); window.history.replaceState(null, "", "/"); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it.each([[14,"2♣3♣","Outside limit"],[15,"A♥K♥","Within limit"],[16,"A♠Q♠","Within limit"],[17,"Q♥T♥","Within limit"]])("studies and plays unpaired question %s without counting inspection as an attempt", async (index,cards,verdict) => {
  const fetch=mockApi(); open(Number(index)); await screen.findByRole("button",{name:"Fold"});
  expect(screen.getByLabelText("Your cards")).toHaveTextContent(String(cards));
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("6♦Q♣T♦");
  expect(screen.getByLabelText("BTN player")).toHaveClass("hero","acting");
  expect(screen.getByLabelText("BB player")).toHaveClass("villain","move-raise");
  fireEvent.click(button("Inspect action EVs"));
  await screen.findByText("EVs shown for study. No attempt recorded.");
  expect(button("Fold")).toHaveTextContent("Hand EV");
  expect(button("Call 3.25 bb")).toHaveTextContent("Loss");
  expect(button("Review 0 attempts")).toBeEnabled();
  expect(window.localStorage.getItem(sizingSessionKey(metadata.collectionHash))).toBeNull();
  expect(screen.queryByRole("heading",{name:String(verdict)})).not.toBeInTheDocument();
  fireEvent.click(button("Call 3.25 bb"));
  await screen.findByRole("heading",{name:String(verdict)});
  expect(JSON.parse(window.localStorage.getItem(sizingSessionKey(metadata.collectionHash))!).attempts).toHaveLength(1);
  expect(fetch.mock.calls.filter(([url])=>String(url).endsWith("/grade"))).toHaveLength(2);
});

it("keeps inspected values separate across four same-pin hands and identical cards on different boards", async () => {
  mockApi(); open(11); await screen.findByRole("button",{name:"Fold"});
  fireEvent.click(button("Inspect action EVs")); await screen.findByText("EVs shown for study. No attempt recorded.");
  const oldValue=button("Call 3.25 bb").textContent;
  const picker=()=>{screen.getByText(/Choose a decision ·/).closest("details")!.open=true;return screen.getByRole("navigation",{name:"Choose a flop decision"}).querySelectorAll("button");};
  for (const i of [14,15,16,17]) {
    fireEvent.click(picker()[i]);
    expect(button("Call 3.25 bb")).not.toHaveTextContent("Hand EV");
    expect(screen.queryByText("EVs shown for study. No attempt recorded.")).not.toBeInTheDocument();
    fireEvent.click(button("Inspect action EVs")); await screen.findByText("EVs shown for study. No attempt recorded.");
    const move=sizingGrade(fixture.attempts.find(a=>a.questionIndex===i && a.action==="call")!.feedback).selected;
    expect(button("Call 3.25 bb")).toHaveTextContent(intervalRange(move.lowerEvBb,move.upperEvBb));
    expect(button("Review 0 attempts")).toBeEnabled();
  }
  fireEvent.click(picker()[11]);
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♥K♥");
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("8♦K♣K♦");
  expect(button("Call 3.25 bb").textContent).toBe(oldValue);
});

it("aborts study inspection on a link change and rejects a foreign board's inspection", async () => {
  const fetch=mockApi(),original=fetch.getMockImplementation()!;
  let finish!:(r:Response)=>void,signal:AbortSignal|undefined;
  fetch.mockImplementation((url,init)=>String(url).endsWith("/grade")?new Promise(resolve=>{finish=resolve;signal=init?.signal as AbortSignal;}):original(url,init));
  open(15); fireEvent.click(await screen.findByRole("button",{name:"Inspect action EVs"}));
  act(()=>{window.history.replaceState(null,"","#interval-sizing-flop?question=16");window.dispatchEvent(new HashChangeEvent("hashchange"));});
  await screen.findByRole("button",{name:"Inspect action EVs"});expect(signal?.aborted).toBe(true);
  await act(async()=>finish(await response(fixture.attempts.find(a=>a.questionIndex===15 && a.action==="fold")!.feedback)));
  expect(button("Call 3.25 bb")).not.toHaveTextContent("Hand EV");
  fetch.mockImplementation((url,init)=>String(url).endsWith("/grade")?response(fixture.attempts.find(a=>a.questionIndex===11 && a.action==="fold")!.feedback):original(url,init));
  fireEvent.click(button("Inspect action EVs"));
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(window.localStorage.getItem(sizingSessionKey(metadata.collectionHash))).toBeNull();
});

it("shows the weak-hand scenario, prior bet and fold/call feedback directly at the table", async () => {
  mockApi(); open(10);
  await screen.findByRole("button", {name: "Fold"});
  expect(screen.getByText(/Second supplied range set/)).toBeInTheDocument();
  expect(screen.getByLabelText("BTN player")).toHaveClass("hero", "acting");
  expect(screen.getByLabelText("BB player")).toHaveClass("villain", "move-raise");
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("8♦K♣K♦");
  fireEvent.keyDown(window, {key: "2"});
  await screen.findByRole("heading", {name: "Outside limit"});
  expect(button("Call 3.25 bb")).toHaveTextContent("Hand EV");
  expect(button("Fold")).toHaveTextContent("Loss");
  expect(button("Next question")).toBeEnabled();
  fireEvent.click(button("Try another move"));
  fireEvent.click(button("Fold"));
  await screen.findByRole("heading", {name: "Within limit"});
  expect(window.location.hash).toContain("question=10");
});

it.each([6, 7, 8, 9])("opens paired question %s with the board, actor, prior bet and exact menu", async index => {
  const fetch = mockApi(); open(index);
  await screen.findByRole("button", { name: "Fold" });
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("8♦K♣K♦");
  expect(screen.getByLabelText("BTN player")).toHaveClass("hero", "acting");
  expect(screen.getByLabelText("BB player")).toHaveClass("villain", "move-raise");
  expect(screen.getByLabelText("BB player")).toHaveTextContent("Bet 3.25 bb");
  expect(screen.getByLabelText("Six-seat action table")).toHaveTextContent("9.75 bb");
  expect(button("Call 3.25 bb")).toBeEnabled();
  expect(screen.queryByRole("button", { name: "Check" })).not.toBeInTheDocument();
  expect(button("Fold")).not.toHaveTextContent("EV");
  expect(fetch.mock.calls.filter(([url]) => String(url).includes("/questions/"))).toHaveLength(20);
  fireEvent.keyDown(window, { key: "2" });
  await screen.findByRole("heading", { name: "Within limit" });
  expect(button("Call 3.25 bb")).toHaveTextContent("Hand EV");
  expect(button("Fold")).toHaveTextContent("Loss");
  expect(JSON.parse(String(fetch.mock.calls.find(([url]) => String(url).endsWith("/grade"))![1]?.body))).toEqual({
    collectionHash: metadata.collectionHash, questionIndex: index, reportHash: sizingDecision(fixture.questions[index]).reportHash, action: "call",
  });
});

it("plays all forty moves across all twenty contexts, preserves feedback and exports exact replay bytes", async () => {
  const fetch = mockApi(); open(); await screen.findByRole("button", { name: "Check" });
  const links = () => { screen.getByText(/Choose a decision ·/).closest("details")!.open = true; return screen.getByRole("navigation", { name: "Choose a flop decision" }).querySelectorAll("button"); };
  const seen = new Set<number>();
  for (const a of fixture.attempts) {
    fireEvent.click(links()[a.questionIndex]);
    if (seen.has(a.questionIndex)) fireEvent.click(button("Try another move"));
    seen.add(a.questionIndex);
    fireEvent.click(button(sizingFlopCollectionActionLabel(a.action,fixture.questions[a.questionIndex])));
    await screen.findByRole("heading", { name: sizingGrade(a.feedback).selected.classification === "WITHIN_LIMIT" ? "Within limit" : "Outside limit" });
  }
  fireEvent.click(links()[0]);
  expect(button("Check")).toHaveTextContent("Hand EV");
  fireEvent.click(links()[19]);
  expect(button("Next question")).toBeDisabled();
  fireEvent.click(button("Review 40 attempts"));
  expect(await screen.findByRole("region", { name: "Independent flop practice review" })).toHaveTextContent("20 of 20 questions practised · 40 attempts");
  const create = vi.fn((blob: Blob) => { expect(blob.size).toBe(new TextEncoder().encode(raw).length); return "blob:expanded"; }), revoke = vi.fn();
  vi.stubGlobal("URL", class extends URL { static createObjectURL = create; static revokeObjectURL = revoke; });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  fireEvent.click(button("Download replayable review"));
  await waitFor(() => expect(create).toHaveBeenCalledOnce());
  const text = await new Promise<string>(resolve => { const r = new FileReader(); r.onload = () => resolve(String(r.result)); r.readAsText(create.mock.calls[0][0]); });
  expect(text).toBe(raw);
  expect((click.mock.instances[0] as HTMLAnchorElement).download).toBe(`pokerlab-sizing-flop-review-${metadata.collectionHash.slice(0, 12)}.json`);
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith("/grade"))).toHaveLength(40);
}, 15000);

it("aborts an outstanding grade when the collection link changes", async () => {
  const fetch = mockApi(), original = fetch.getMockImplementation()!;
  let finish!: (r: Response) => void, signal: AbortSignal | undefined;
  fetch.mockImplementation((url, init) => String(url).endsWith("/grade") ? new Promise(resolve => { finish = resolve; signal = init?.signal as AbortSignal; }) : original(url, init));
  open(6); fireEvent.click(await screen.findByRole("button", { name: "Fold" }));
  act(() => { window.history.replaceState(null, "", "#interval-sizing-flop?question=0"); window.dispatchEvent(new HashChangeEvent("hashchange")); });
  await screen.findByRole("button", { name: "Check" }); expect(signal?.aborted).toBe(true);
  await act(async () => finish(await response(fixture.attempts[6].feedback)));
  expect(screen.queryByRole("heading", { name: "Outside limit" })).not.toBeInTheDocument();
  expect(button("Check")).toBeEnabled();
});

it("changes all three same-pin hands and EVs independently, then restores the right result on return", async () => {
  mockApi(); open(10); await screen.findByRole("button", {name:"Fold"});
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("2♣3♣");
  fireEvent.click(button("Fold")); await screen.findByRole("heading",{name:"Within limit"});
  for (const [index,cards] of [[11,"A♥K♥"],[12,"A♠Q♠"],[13,"Q♥T♥"]] as const) {
    fireEvent.click(button("Next question"));
    expect(screen.getByLabelText("Your cards")).toHaveTextContent(cards);
    expect(screen.queryByRole("heading",{name:"Within limit"})).not.toBeInTheDocument();
    expect(button("Call 3.25 bb")).not.toHaveTextContent("EV");
    fireEvent.click(button("Call 3.25 bb")); await screen.findByRole("heading",{name:"Within limit"});
    const move=sizingGrade(fixture.attempts.find(a=>a.questionIndex===index && a.action==="call")!.feedback).selected;
    expect(button("Call 3.25 bb")).toHaveTextContent(intervalRange(move.lowerEvBb,move.upperEvBb));
  }
  fireEvent.click(button("Previous question"));
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♠Q♠");
  expect(await screen.findByRole("heading",{name:"Within limit"})).toBeInTheDocument();
  fireEvent.click(button("Try another move"));
  expect(button("Call 3.25 bb")).toHaveTextContent("Hand EV");
  expect(button("Call 3.25 bb")).toBeEnabled();
  fireEvent.click(button("Fold")); await screen.findByRole("heading",{name:"Outside limit"});
});

it("clears the old board, action menu and feedback across question five to six", async () => {
  mockApi(); open(5); await screen.findByRole("button",{name:"Check"});
  fireEvent.click(button("Check")); await screen.findByRole("heading",{name:"Outside limit"});
  fireEvent.click(button("Next question"));
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("8♦K♣K♦");
  expect(screen.queryByRole("button",{name:"Check"})).not.toBeInTheDocument();
  expect(screen.queryByRole("heading",{name:"Outside limit"})).not.toBeInTheDocument();
  expect(button("Call 3.25 bb")).toBeEnabled();
});

it("restores validated v7 attempts after reload and leaves old session keys alone on start over", async () => {
  const key=sizingSessionKey(metadata.collectionHash);
  window.localStorage.setItem("pokerlab:flop-collection:v6:old", "old session");
  mockApi(); const view=open(11); fireEvent.click(await screen.findByRole("button",{name:"Call 3.25 bb"}));
  await screen.findByRole("heading",{name:"Within limit"});
  expect(JSON.parse(window.localStorage.getItem(key)!).attempts).toHaveLength(1);
  view.unmount(); open(11); await screen.findByRole("heading",{name:"Within limit"});
  expect(screen.getByText("Restored 1 saved attempts.")).toBeInTheDocument();
  fireEvent.click(button("Start over")); await screen.findByRole("button",{name:"Check"});
  expect(window.localStorage.getItem(key)).toBeNull();
  expect(window.localStorage.getItem("pokerlab:flop-collection:v6:old")).toBe("old session");
});

function upload(text:string) {
  const file=new File([text],"review.json",{type:"application/json"});
  Object.defineProperty(file,"text",{value:()=>Promise.resolve(text)});
  fireEvent.change(screen.getByLabelText("Import saved review"),{target:{files:[file]}});
}
it("imports a canonical review, rechecks only its choices on the backend and resumes practice", async () => {
  const fetch=mockApi(); open(12); await screen.findByRole("button",{name:"Fold"}); upload(raw);
  expect(await screen.findByRole("region",{name:"Independent flop practice review"})).toHaveTextContent("20 of 20 questions practised · 40 attempts");
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♠Q♠");
  const body=JSON.parse(String(fetch.mock.calls.find(([url])=>String(url).endsWith("/review"))![1]?.body));
  expect(body.choices).toHaveLength(40); expect(Object.keys(body.choices[22]).sort()).toEqual(["action","questionIndex","reportHash"]);
  fireEvent.click(button("Try another move")); expect(button("Call 3.25 bb")).toBeEnabled();
});
it("rejects incompatible imports without losing current practice", async () => {
  mockApi(); open(11); fireEvent.click(await screen.findByRole("button",{name:"Call 3.25 bb"}));
  await screen.findByRole("heading",{name:"Within limit"});
  const before=window.localStorage.getItem(sizingSessionKey(metadata.collectionHash));
  upload(raw.replace("collection-review/v7","collection-review/v4"));
  expect(await screen.findByRole("alert")).toHaveTextContent("Your current session is unchanged");
  expect(window.localStorage.getItem(sizingSessionKey(metadata.collectionHash))).toBe(before);
  expect(screen.getByRole("heading",{name:"Within limit"})).toBeInTheDocument();
});
it("keeps play available when device storage is blocked", async () => {
  mockApi(); vi.spyOn(Storage.prototype,"setItem").mockImplementation(()=>{throw new DOMException("blocked");});
  open(11); fireEvent.click(await screen.findByRole("button",{name:"Call 3.25 bb"}));
  await screen.findByRole("heading",{name:"Within limit"});
  expect(screen.getByText(/Device storage is unavailable/)).toBeInTheDocument();
  expect(button("Next question")).toBeEnabled();
});
it("shows a clear unavailable message and never requests an older collection", async () => {
  const fetch=vi.spyOn(globalThis,"fetch").mockImplementation(()=>response({},404)); open();
  expect(await screen.findByRole("alert")).toHaveTextContent("twenty-decision research collection is not enabled");
  expect(fetch).toHaveBeenCalledOnce();
  expect(String(fetch.mock.calls[0][0])).toBe("/api/v1/trainer/research/flop-interval-collection-v7");
});

it("rejects a stale same-pin grade before showing another hand's EV", async () => {
  const fetch=mockApi(), original=fetch.getMockImplementation()!;
  fetch.mockImplementation((url,init)=>String(url).endsWith("/grade") ? response({
    ...fixture.attempts.find(a=>a.questionIndex===11 && a.action==="call")!.feedback,
    decision:fixture.attempts.find(a=>a.questionIndex===12 && a.action==="call")!.feedback.decision,
  }) : original(url,init));
  open(11); fireEvent.click(await screen.findByRole("button",{name:"Call 3.25 bb"}));
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByRole("heading",{name:"Within limit"})).not.toBeInTheDocument();
  expect(window.localStorage.getItem(sizingSessionKey(metadata.collectionHash))).toBeNull();
});
it("retains the hundred-attempt cap after restoring a session while allowing review and export", async () => {
  const r=sizingFlopCollectionFixtureReview(Array.from({length:100},()=>({questionIndex:11,action:"call" as const})));
  window.localStorage.setItem(sizingSessionKey(metadata.collectionHash),JSON.stringify(r));
  mockApi(); open(11); await screen.findByRole("heading",{name:"Within limit"});
  expect(button("Call 3.25 bb")).toBeDisabled(); expect(button("Review 100 attempts")).toBeEnabled();
  expect(screen.queryByRole("button",{name:"Try another move"})).not.toBeInTheDocument();
  fireEvent.click(button("Review 100 attempts"));
  expect(await screen.findByRole("region",{name:"Independent flop practice review"})).toHaveTextContent("1 of 20 questions practised · 100 attempts");
  expect(button("Download replayable review")).toBeEnabled();
});

it("quarantines a corrupt saved session and lets a new valid attempt replace it", async () => {
  window.localStorage.setItem(sizingSessionKey(metadata.collectionHash),raw.replace("collection-review/v7","collection-review/v3"));
  mockApi(); open(11); await screen.findByRole("button",{name:"Call 3.25 bb"});
  expect(screen.getByText(/Saved practice could not be restored/)).toBeInTheDocument();
  fireEvent.click(button("Call 3.25 bb")); await screen.findByRole("heading",{name:"Within limit"});
  expect(JSON.parse(window.localStorage.getItem(sizingSessionKey(metadata.collectionHash))!).attempts).toHaveLength(1);
});
it("aborts import regrading after a link change and cannot overwrite the new view", async () => {
  const fetch=mockApi(), original=fetch.getMockImplementation()!;
  let finish!: (r:Response)=>void, signal:AbortSignal|undefined;
  fetch.mockImplementation((url,init)=>String(url).endsWith("/review") ? new Promise(resolve=>{finish=resolve; signal=init?.signal as AbortSignal;}) : original(url,init));
  open(11); await screen.findByRole("button",{name:"Fold"}); upload(raw);
  await waitFor(()=>expect(signal).toBeDefined());
  act(()=>{window.history.replaceState(null,"","#interval-sizing-flop?question=0"); window.dispatchEvent(new HashChangeEvent("hashchange"));});
  await screen.findByRole("button",{name:"Check"}); expect(signal?.aborted).toBe(true);
  await act(async()=>finish(await response(fixture)));
  expect(screen.queryByRole("region",{name:"Independent flop practice review"})).not.toBeInTheDocument();
  expect(window.localStorage.getItem(sizingSessionKey(metadata.collectionHash))).toBeNull();
});

it.each([[18,"9♦9♥","call","Within limit"],[19,"A♣J♣","fold","Within limit"]] as const)("plays new pot-sized BB question %s with prices and controls on the table",async(index,cards,action,verdict)=>{
  mockApi();open(index);await screen.findByRole("button",{name:"Call 6.5 bb"});
  expect(screen.getByLabelText("Your cards")).toHaveTextContent(cards);
  expect(screen.getByLabelText("BB player")).toHaveClass("hero","acting");
  expect(screen.getByLabelText("BTN player")).toHaveClass("villain","move-raise");
  expect(screen.getByLabelText("BTN player")).toHaveTextContent("Bet 6.5 bb · Pot-sized");
  expect(screen.getByLabelText("Six-seat action table")).toHaveTextContent("13 bb");
  fireEvent.click(button("Inspect action EVs"));await screen.findByText("EVs shown for study. No attempt recorded.");
  expect(button("Call 6.5 bb")).toHaveTextContent("Hand EV");expect(button("Fold")).toHaveTextContent("Loss");
  expect(button("Review 0 attempts")).toBeEnabled();
  fireEvent.click(button(action==="call"?"Call 6.5 bb":"Fold"));await screen.findByRole("heading",{name:verdict});
  expect(screen.getByLabelText("BB player")).toHaveTextContent(action==="call"?"You chose · Call 6.5 bb":"You chose · Fold");
  expect(button("Review 1 attempt")).toBeEnabled();
});
it("clears half-pot values when entering the separate pot-size game and keeps the two same-pin hands separate",async()=>{
  mockApi();open(17);fireEvent.click(await screen.findByRole("button",{name:"Call 3.25 bb"}));
  await screen.findByRole("heading",{name:"Within limit"});fireEvent.click(button("Next question"));
  expect(button("Call 6.5 bb")).not.toHaveTextContent("Hand EV");expect(screen.getByLabelText("Your cards")).toHaveTextContent("9♦9♥");
  fireEvent.click(button("Call 6.5 bb"));await screen.findByRole("heading",{name:"Within limit"});
  fireEvent.click(button("Next question"));expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♣J♣");
  expect(button("Call 6.5 bb")).not.toHaveTextContent("Hand EV");fireEvent.click(button("Call 6.5 bb"));
  await screen.findByRole("heading",{name:"Outside limit"});fireEvent.click(button("Previous question"));
  expect(await screen.findByRole("heading",{name:"Within limit"})).toBeInTheDocument();expect(button("Call 6.5 bb")).toHaveTextContent("Hand EV");
});
