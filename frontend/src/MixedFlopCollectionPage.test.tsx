import raw from "./test-data/mixed-flop-collection-review.json?raw";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import { mixedFlopCollectionFixture as fixture, mixedFlopCollectionMetadata as metadata, mixedFlopCollectionFixtureReview } from "./test-data/mixedFlopCollection";
import { mixedDecision, mixedGrade, type MixedFlopCollectionFeedback, type MixedFlopCollectionQuestion, type MixedFlopCollectionReview } from "./mixedFlopCollectionApi";

const response = (body: unknown, status=200) => Promise.resolve({ok:status<400,status,json:()=>Promise.resolve(body),text:()=>Promise.resolve(typeof body==="string"?body:JSON.stringify(body))} as Response);
function mockApi(options: {status?:number; question?:(q:MixedFlopCollectionQuestion)=>unknown;
  grade?:(f:MixedFlopCollectionFeedback, init:RequestInit)=>Promise<Response>; review?:(r:MixedFlopCollectionReview)=>unknown; document?:string}={}) {
  return vi.spyOn(globalThis,"fetch").mockImplementation((url,init) => {
    const path=String(url), body=init?.body ? JSON.parse(String(init.body)) : null;
    if(path.endsWith("/review/export"))return response(options.document??raw);
    if(path.endsWith("/grade")) {
      const f=structuredClone(fixture.attempts.find(a=>a.questionIndex===body.questionIndex&&a.action===body.action)!.feedback);
      return options.grade ? options.grade(f,init!) : response(f);
    }
    if(path.endsWith("/review")) {const r=mixedFlopCollectionFixtureReview(body.choices);return response(options.review?options.review(r):r);}
    const index=path.match(/\/questions\/(\d+)/)?.[1];
    if(index!==undefined) {const q=structuredClone(fixture.questions[Number(index)]);return response(options.question?options.question(q):q);}
    return response(metadata,options.status);
  });
}
function open(query="") {window.history.replaceState(null,"",`#interval-mixed-flop${query}`);return render(<FrontendRoot/>);}
const button=(name:string)=>screen.getByRole("button",{name});
afterEach(()=>{window.history.replaceState(null,"","/");vi.restoreAllMocks();vi.unstubAllGlobals();});
it("loads both source contexts before presenting the initial table without exposing answers",async()=>{
  const fetch=mockApi();open();await screen.findByRole("button",{name:"Check"});
  expect(screen.getByRole("heading",{name:"Play both sides"})).toBeInTheDocument();
  expect(screen.getByLabelText("Flop board")).toHaveTextContent("2♠8♠T♠");
  expect(screen.getByLabelText("Your cards")).toHaveTextContent("7♦7♥");
  expect(screen.getByLabelText("Six-seat action table")).toHaveTextContent("6.5 bb");
  expect(screen.getByText(/Question 1 of 6/)).toHaveTextContent("0 of 6 practised");
  expect(button("Check")).not.toHaveTextContent("EV");
  expect(button("Previous question")).toBeDisabled();expect(button("Next question")).toBeEnabled();
  expect(fetch.mock.calls.filter(([url])=>String(url).includes("/questions/"))).toHaveLength(6);
  expect(fetch.mock.calls.every(([url])=>String(url).startsWith("/api/v1/trainer/research/flop-interval-collection-v2"))).toBe(true);
});
it("moves between the initial and facing-bet tables with the correct public state and menus",async()=>{
  mockApi();open();await screen.findByRole("button",{name:"Check"});fireEvent.click(button("Next question"));
  expect(button("Fold")).toBeEnabled();expect(button("Call 3.25 bb")).toBeEnabled();
  expect(screen.queryByRole("button",{name:"Check"})).not.toBeInTheDocument();
  expect(screen.getByLabelText("Six-seat action table")).toHaveTextContent("9.75 bb");
  expect(screen.getByLabelText("BTN player")).toHaveClass("villain","move-raise");
  expect(screen.getByLabelText("BTN player")).toHaveTextContent("Bet 3.25 bb");
  expect(screen.getByLabelText("BB player")).toHaveClass("hero","acting","move-check");
  expect(button("Next question")).toBeEnabled();expect(window.location.hash).toContain("question=1");
  fireEvent.click(button("Previous question"));expect(button("Check")).toBeEnabled();
});
it("keeps grades and revealed ranges scoped to their own question while revisiting and retrying",async()=>{
  const fetch=mockApi();open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));
  await screen.findByRole("heading",{name:"Within limit"});fireEvent.click(button("Next question"));
  expect(button("Fold")).not.toHaveTextContent("EV");fireEvent.click(button("Call 3.25 bb"));
  await screen.findByRole("heading",{name:"Outside limit"});expect(screen.getByText(/Question 2 of 6/)).toHaveTextContent("2 of 6 practised");
  expect(screen.getByLabelText("BB player")).toHaveClass("move-call");fireEvent.click(button("Previous question"));
  expect(button("Check")).toBeDisabled();expect(button("Check")).toHaveTextContent("Within limit");
  fireEvent.click(button("Try another move"));expect(button("Bet 3.25 bb")).toBeEnabled();expect(button("Check")).toHaveTextContent("Hand EV");
  fireEvent.click(button("Bet 3.25 bb"));await screen.findByRole("heading",{name:"Outside limit"});
  const grades=fetch.mock.calls.filter(([url])=>String(url).endsWith("/grade")).map(([,init])=>JSON.parse(String(init?.body)));
  expect(grades.map(g=>g.questionIndex)).toEqual([0,1,0]);
  expect(grades.map(g=>g.reportHash)).toEqual([mixedDecision(fixture.questions[0]).reportHash,mixedDecision(fixture.questions[1]).reportHash,mixedDecision(fixture.questions[0]).reportHash]);
});
it("supports a deep link to the later question and an empty review",async()=>{
  const fetch=mockApi();open(`?collection=${metadata.collectionHash}&question=1`);await screen.findByRole("button",{name:"Fold"});
  fireEvent.click(button("Review 0 attempts"));expect(await screen.findByRole("region",{name:"Mixed flop practice review"})).toHaveTextContent("0 of 6 questions practised · 0 attempts");
  expect(fetch.mock.calls.filter(([url])=>String(url).endsWith("/grade"))).toHaveLength(0);
});
it.each(["?collection="+"0".repeat(64),"?question=6","?question=-1","?question=1.5","?question=0&question=1","?seed=7"])("blocks unsupported deep links: %s",async query=>{
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
  act(()=>{window.history.replaceState(null,"",`#interval-mixed-flop?collection=${"0".repeat(64)}`);window.dispatchEvent(new HashChangeEvent("hashchange"));});
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
  let count=0;mockApi({grade:f=>{count++;if(count===1)return Promise.reject(new TypeError("offline"));if(count===3)mixedGrade(f).alternatives[1].upperEvBb=100;return response(f);}});
  open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));expect(await screen.findByRole("alert")).toHaveTextContent("offline");
  await waitFor(()=>expect(button("Check")).toBeEnabled());fireEvent.click(button("Check"));await screen.findByRole("heading",{name:"Within limit"});
  fireEvent.click(button("Try another move"));fireEvent.click(button("Check"));expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
});

it("switches from BB to BTN with the correct cards, actor, checked opponent and direct decision links",async()=>{
 mockApi();open();await screen.findByRole("button",{name:"Check"});
 expect(screen.getByLabelText("BB player")).toHaveClass("hero","acting");
 fireEvent.click(button("3 · BTN · 9s Js"));
 expect(screen.getByLabelText("BTN player")).toHaveClass("hero","acting","position-0");
 expect(screen.getByLabelText("BB player")).toHaveClass("villain","move-check");
 expect(screen.getByLabelText("Your cards")).toHaveTextContent("9♠J♠");
 expect(screen.queryByRole("button",{name:"Fold"})).not.toBeInTheDocument();
 expect(button("Bet 3.25 bb")).toHaveClass("move-raise");
 for(const [name,cards] of [["4 · BTN · Ah Kh","A♥K♥"],["5 · BTN · As Qs","A♠Q♠"],["6 · BTN · Qh Th","Q♥T♥"]]){
  fireEvent.click(button(name));expect(screen.getByLabelText("Your cards")).toHaveTextContent(cards);
 }
 expect(button("Next question")).toBeDisabled();expect(window.location.hash).toContain("question=5");
 fireEvent.click(button("2 · BB · Facing bet"));expect(button("Fold")).toBeEnabled();
 expect(screen.getByLabelText("BB player")).toHaveClass("hero","acting");
 expect(screen.getByLabelText("BTN player")).toHaveClass("villain","move-raise");
});
it("plays all twelve owned moves and downloads exactly the canonical 40731-byte review",async()=>{
 const fetch=mockApi();open();await screen.findByRole("button",{name:"Check"});
 const labels=["BB · First move","BB · Facing bet","BTN · 9s Js","BTN · Ah Kh","BTN · As Qs","BTN · Qh Th"];
 for(const a of fixture.attempts){
  const i=a.questionIndex,seen=a.index>=6;
  fireEvent.click(button(`${i+1} · ${labels[i]}${seen?" ✓":""}`));
  if(seen)fireEvent.click(button("Try another move"));
  fireEvent.click(button(a.action==="bet"?"Bet 3.25 bb":a.action==="call"?"Call 3.25 bb":a.action==="check"?"Check":"Fold"));
  await screen.findByRole("heading",{name:mixedGrade(a.feedback).selected.classification==="WITHIN_LIMIT"?"Within limit":"Outside limit"});
 }
 fireEvent.click(button("Review 12 attempts"));
 expect(await screen.findByRole("region",{name:"Mixed flop practice review"})).toHaveTextContent("6 of 6 questions practised · 12 attempts");
 const create=vi.fn((blob:Blob)=>{expect(blob.size).toBe(40731);return "blob:mixed";}),revoke=vi.fn();
 vi.stubGlobal("URL",class extends URL{static createObjectURL=create;static revokeObjectURL=revoke;});
 const click=vi.spyOn(HTMLAnchorElement.prototype,"click").mockImplementation(()=>{});
 fireEvent.click(button("Download replayable review"));await waitFor(()=>expect(create).toHaveBeenCalledTimes(1));
 const text=await new Promise<string>(resolve=>{const r=new FileReader();r.onload=()=>resolve(String(r.result));r.readAsText(create.mock.calls[0][0]);});
 expect(text).toBe(raw);expect(revoke).toHaveBeenCalledWith("blob:mixed");expect((click.mock.instances[0] as HTMLAnchorElement).download).toBe(`pokerlab-mixed-flop-review-${metadata.collectionHash.slice(0,12)}.json`);
 const grades=fetch.mock.calls.filter(([u])=>String(u).endsWith("/grade")).map(([,i])=>JSON.parse(String(i?.body)));
 expect(grades).toEqual(fixture.attempts.map(a=>({collectionHash:metadata.collectionHash,questionIndex:a.questionIndex,reportHash:mixedDecision(fixture.questions[a.questionIndex]).reportHash,action:a.action})));
 const choices=grades.map(({questionIndex,reportHash,action})=>({questionIndex,reportHash,action}));
 for(const suffix of ["/review","/review/export"]){expect(JSON.parse(String(fetch.mock.calls.find(([u])=>String(u).endsWith(suffix))![1]?.body))).toEqual({collectionHash:metadata.collectionHash,choices});}
});
it("blocks forged exports without creating a download",async()=>{
 mockApi({document:JSON.stringify({...mixedFlopCollectionFixtureReview([]),counts:{distinctQuestionsAttempted:6,withinLimit:0,outsideLimit:0,strategyDependent:0}})});
 const create=vi.fn();vi.stubGlobal("URL",class extends URL{static createObjectURL=create;});open();await screen.findByRole("button",{name:"Check"});
 fireEvent.click(button("Review 0 attempts"));await screen.findByRole("region",{name:"Mixed flop practice review"});fireEvent.click(button("Download replayable review"));
 expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");expect(create).not.toHaveBeenCalled();
});
it("cancels export on route change and ignores its late completion",async()=>{
 const fetch=mockApi();let finish!:(r:Response)=>void,signal:AbortSignal|undefined;
 const original=fetch.getMockImplementation()!;fetch.mockImplementation((url,init)=>String(url).endsWith("/review/export")?new Promise<Response>(resolve=>{finish=resolve;signal=init?.signal as AbortSignal;}):original(url,init));
 const create=vi.fn();vi.stubGlobal("URL",class extends URL{static createObjectURL=create;});open();await screen.findByRole("button",{name:"Check"});
 fireEvent.click(button("Review 0 attempts"));await screen.findByRole("region",{name:"Mixed flop practice review"});fireEvent.click(button("Download replayable review"));expect(button("Next question")).toBeDisabled();
 act(()=>{window.history.replaceState(null,"",`#interval-mixed-flop?question=3`);window.dispatchEvent(new HashChangeEvent("hashchange"));});await screen.findByRole("button",{name:"Check"});expect(signal?.aborted).toBe(true);
 await act(async()=>finish(await response(JSON.stringify(mixedFlopCollectionFixtureReview([])))));expect(create).not.toHaveBeenCalled();expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♥K♥");
});

it.each([0,1,2,3,4,5])("opens a pinned direct link to collection question %s",async index=>{
 const fetch=mockApi();open(`?collection=${metadata.collectionHash}&question=${index}`);
 await screen.findByRole("button",{name:index===1?"Fold":"Check"});
 const q=fixture.questions[index],d=q.decision,hero=d.kind==="INITIAL_CHECK_BET"?d.initial.context.hero:d.kind==="BB_FACING_BET"?d.later.hero:d.btn.hero;
 expect(screen.getByLabelText(`${hero} player`)).toHaveClass("hero","acting");
 expect(screen.getByText(new RegExp(`Question ${index+1} of 6`))).toBeInTheDocument();
 expect(fetch.mock.calls.filter(([u])=>String(u).includes("/questions/"))).toHaveLength(6);
});
it("waits for the complete inventory and aborts unfinished loads when unmounted",async()=>{
 const fetch=mockApi(),original=fetch.getMockImplementation()!;
 let finish!:(r:Response)=>void,signal:AbortSignal|undefined;
 fetch.mockImplementation((url,init)=>String(url).includes("/questions/5?")?new Promise<Response>(resolve=>{finish=resolve;signal=init?.signal as AbortSignal;}):original(url,init));
 const view=open();await waitFor(()=>expect(fetch.mock.calls.filter(([u])=>String(u).includes("/questions/"))).toHaveLength(6));
 expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
 view.unmount();expect(signal?.aborted).toBe(true);
 await act(async()=>finish(await response(fixture.questions[5])));
 expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
});
