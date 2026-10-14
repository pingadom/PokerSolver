import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { flopCollectionFixture as fixture, flopCollectionMetadata as metadata, flopCollectionFixtureReview } from "./test-data/flopCollection";
import { collectionDecision, collectionGrade, type FlopCollectionFeedback, type FlopCollectionQuestion, type FlopCollectionReview } from "./flopCollectionApi";

const response = (body: unknown, status=200) => Promise.resolve({ok:status<400,status,json:()=>Promise.resolve(body)} as Response);
function mockApi(options: {status?:number; question?:(q:FlopCollectionQuestion)=>unknown;
  grade?:(f:FlopCollectionFeedback, init:RequestInit)=>Promise<Response>; review?:(r:FlopCollectionReview)=>unknown}={}) {
  return vi.spyOn(globalThis,"fetch").mockImplementation((url,init) => {
    const path=String(url), body=init?.body ? JSON.parse(String(init.body)) : null;
    if(path.endsWith("/grade")) {
      const f=structuredClone(fixture.attempts.find(a=>a.questionIndex===body.questionIndex&&a.action===body.action)!.feedback);
      return options.grade ? options.grade(f,init!) : response(f);
    }
    if(path.endsWith("/review")) {const r=flopCollectionFixtureReview(body.choices);return response(options.review?options.review(r):r);}
    const index=path.match(/\/questions\/(\d+)/)?.[1];
    if(index!==undefined) {const q=structuredClone(fixture.questions[Number(index)]);return response(options.question?options.question(q):q);}
    return response(metadata,options.status);
  });
}
function open(query="") {window.history.replaceState(null,"",`#interval-flop-collection${query}`);return render(<FrontendRoot/>);}
const button=(name:string)=>screen.getByRole("button",{name});
afterEach(()=>{window.history.replaceState(null,"","/");vi.restoreAllMocks();vi.unstubAllGlobals();});
it("loads both source contexts before presenting the initial table without exposing answers",async()=>{
  const fetch=mockApi();open();await screen.findByRole("button",{name:"Check"});
  expect(screen.getByRole("heading",{name:"Flop practice"})).toBeInTheDocument();
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("2♠8♠T♠");
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("7♦7♥");
  expect(screen.getByLabelText("Six-seat action table")).toHaveTextContent("6.5 bb");
  expect(screen.getByText(/Question 1 of 2/)).toHaveTextContent("0 of 2 practised");
  expect(button("Check")).not.toHaveTextContent("EV");
  expect(button("Previous question")).toBeDisabled();expect(button("Next question")).toBeEnabled();
  expect(fetch.mock.calls.filter(([url])=>String(url).includes("/questions/"))).toHaveLength(2);
  expect(fetch.mock.calls.every(([url])=>String(url).startsWith("/api/v1/trainer/research/flop-interval-collection"))).toBe(true);
});
it("moves between the initial and facing-bet tables with the correct public state and menus",async()=>{
  mockApi();open();await screen.findByRole("button",{name:"Check"});fireEvent.click(button("Next question"));
  expect(button("Fold")).toBeEnabled();expect(button("Call 3.25 bb")).toBeEnabled();
  expect(screen.queryByRole("button",{name:"Check"})).not.toBeInTheDocument();
  expect(screen.getByLabelText("Six-seat action table")).toHaveTextContent("9.75 bb");
  expect(screen.getByLabelText("BTN player")).toHaveClass("villain","move-raise");
  expect(screen.getByLabelText("BTN player")).toHaveTextContent("Bet 3.25 bb");
  expect(screen.getByLabelText("BB player")).toHaveClass("hero","acting","move-check");
  expect(button("Next question")).toBeDisabled();expect(window.location.hash).toContain("question=1");
  fireEvent.click(button("Previous question"));expect(button("Check")).toBeEnabled();
});
it("keeps grades and revealed ranges scoped to their own question while revisiting and retrying",async()=>{
  const fetch=mockApi();open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));
  await screen.findByRole("heading",{name:"Within limit"});fireEvent.click(button("Next question"));
  expect(button("Fold")).not.toHaveTextContent("EV");fireEvent.click(button("Call 3.25 bb"));
  await screen.findByRole("heading",{name:"Outside limit"});expect(screen.getByText(/Question 2 of 2/)).toHaveTextContent("2 of 2 practised");
  expect(screen.getByLabelText("BB player")).toHaveClass("move-call");fireEvent.click(button("Previous question"));
  expect(button("Check")).toBeDisabled();expect(button("Check")).toHaveTextContent("Within limit");
  fireEvent.click(button("Try another move"));expect(button("Bet 3.25 bb")).toBeEnabled();expect(button("Check")).toHaveTextContent("Hand EV");
  fireEvent.click(button("Bet 3.25 bb"));await screen.findByRole("heading",{name:"Outside limit"});
  const grades=fetch.mock.calls.filter(([url])=>String(url).endsWith("/grade")).map(([,init])=>JSON.parse(String(init?.body)));
  expect(grades.map(g=>g.questionIndex)).toEqual([0,1,0]);
  expect(grades.map(g=>g.reportHash)).toEqual([collectionDecision(fixture.questions[0]).reportHash,collectionDecision(fixture.questions[1]).reportHash,collectionDecision(fixture.questions[0]).reportHash]);
});
it("exports the exact mixed four-attempt review and counts contexts once",async()=>{
  mockApi();open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));
  await screen.findByRole("heading",{name:"Within limit"});fireEvent.click(button("Next question"));fireEvent.click(button("Fold"));
  await screen.findByRole("heading",{name:"Within limit"});fireEvent.click(button("Previous question"));fireEvent.click(button("Try another move"));fireEvent.click(button("Bet 3.25 bb"));
  await screen.findByRole("heading",{name:"Outside limit"});fireEvent.click(button("Next question"));fireEvent.click(button("Try another move"));fireEvent.click(button("Call 3.25 bb"));
  await screen.findByRole("heading",{name:"Outside limit"});fireEvent.click(button("Review 4 attempts"));
  const review=await screen.findByRole("region",{name:"Collection review"});
  expect(review).toHaveTextContent("2 of 2 questions practised · 4 attempts");expect(review).toHaveTextContent("Within limit: 2 · Outside limit: 2");
  const create=vi.fn((blob:Blob)=>{expect(blob).toBeInstanceOf(Blob);return "blob:flop-collection";}),revoke=vi.fn();
  vi.stubGlobal("URL",class extends URL{static createObjectURL=create;static revokeObjectURL=revoke;});
  vi.spyOn(HTMLAnchorElement.prototype,"click").mockImplementation(()=>{});fireEvent.click(button("Download replayable review"));
  const text=await new Promise<string>(resolve=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result));reader.readAsText(create.mock.calls[0][0]);});
  expect(JSON.parse(text)).toEqual(fixture);expect(revoke).toHaveBeenCalledWith("blob:flop-collection");
});
it("supports a deep link to the later question and an empty review",async()=>{
  const fetch=mockApi();open(`?collection=${metadata.collectionHash}&question=1`);await screen.findByRole("button",{name:"Fold"});
  fireEvent.click(button("Review 0 attempts"));expect(await screen.findByRole("region",{name:"Collection review"})).toHaveTextContent("0 of 2 questions practised · 0 attempts");
  expect(fetch.mock.calls.filter(([url])=>String(url).endsWith("/grade"))).toHaveLength(0);
});
it.each(["?collection="+"0".repeat(64),"?question=2","?question=-1","?question=1.5","?question=0&question=1","?seed=7"])("blocks unsupported deep links: %s",async query=>{
  mockApi();open(query);expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
  fireEvent.click(button("Load current collection"));expect(await screen.findByRole("button",{name:"Check"})).toBeEnabled();
});
it("shows default-disabled state without fixture fallback",async()=>{
  mockApi({status:404});open();expect(await screen.findByRole("alert")).toHaveTextContent("not enabled");
  expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
});
it("rejects a contradictory second source before showing the first",async()=>{
  mockApi({question:q=>{if(q.decision.later)q.decision.later.heroCards="9d 9h";return q;}});open();
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");expect(screen.queryByRole("button",{name:"Check"})).not.toBeInTheDocument();
});
it("rejects cross-question grades and edited review counts",async()=>{
  const fetch=mockApi({grade:()=>response(fixture.attempts[1].feedback)});open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");expect(screen.queryByRole("button",{name:"Download replayable review"})).not.toBeInTheDocument();
  fetch.mockRestore();mockApi({review:r=>({...r,counts:{...r.counts,distinctQuestionsAttempted:2}})});
  fireEvent.click(button("Load current collection"));await screen.findByRole("button",{name:"Check"});fireEvent.click(button("Review 0 attempts"));
  expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
});
it("prevents duplicate submissions and ignores grades after navigation or unmount",async()=>{
  let resolve!:(r:Response)=>void,signal:AbortSignal|undefined;const pending=new Promise<Response>(done=>{resolve=done;});
  const fetch=mockApi({grade:(_f,init)=>{signal=init.signal as AbortSignal;return pending;}}),view=open();
  fireEvent.click(await screen.findByRole("button",{name:"Check"}));fireEvent.click(button("Check"));fireEvent.keyDown(window,{key:"1"});
  expect(fetch.mock.calls.filter(([url])=>String(url).endsWith("/grade"))).toHaveLength(1);expect(button("Next question")).toBeDisabled();
  act(()=>{window.history.replaceState(null,"",`#interval-flop-collection?collection=${"0".repeat(64)}`);window.dispatchEvent(new HashChangeEvent("hashchange"));});
  await screen.findByRole("alert");expect(signal?.aborted).toBe(true);await act(async()=>{resolve(await response(fixture.attempts[0].feedback));});
  expect(screen.queryByRole("heading",{name:"Within limit"})).not.toBeInTheDocument();view.unmount();
});
it("allows only 100 attempts while preserving navigation/review/reset and keyboard guards",async()=>{
  const fetch=mockApi();open();await screen.findByRole("button",{name:"Check"});
  fireEvent.keyDown(window,{key:"1",repeat:true});fireEvent.keyDown(button("Check"),{key:"1"});fireEvent.keyDown(window,{key:"1",ctrlKey:true});
  expect(fetch.mock.calls.filter(([url])=>String(url).endsWith("/grade"))).toHaveLength(0);
  for(let i=0;i<100;i++){fireEvent.click(button("Check"));await screen.findByRole("heading",{name:"Within limit"});if(i<99)fireEvent.click(button("Try another move"));}
  fireEvent.click(button("Next question"));expect(button("Fold")).toBeDisabled();expect(button("Review 100 attempts")).toBeEnabled();
  fireEvent.keyDown(window,{key:"1"});expect(fetch.mock.calls.filter(([url])=>String(url).endsWith("/grade"))).toHaveLength(100);
  fireEvent.click(button("Start over"));await waitFor(()=>expect(button("Check")).toBeEnabled());
},20000);
it("blocks changed ranges on a retry and permits network retry without inventing feedback",async()=>{
  let count=0;mockApi({grade:f=>{count++;if(count===1)return Promise.reject(new TypeError("offline"));if(count===3)collectionGrade(f).alternatives[1].upperEvBb=100;return response(f);}});
  open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));expect(await screen.findByRole("alert")).toHaveTextContent("offline");
  await waitFor(()=>expect(button("Check")).toBeEnabled());fireEvent.click(button("Check"));await screen.findByRole("heading",{name:"Within limit"});
  fireEvent.click(button("Try another move"));fireEvent.click(button("Check"));expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
});
