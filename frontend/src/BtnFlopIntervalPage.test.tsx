import { Profiler } from "react";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import FrontendRoot from "./FrontendRoot";
import raw from "./test-data/btn-flop-interval-drill.json?raw";
import { btnFlopFixture as fixture, btnFlopMetadata as metadata, btnFlopFixtureReview } from "./test-data/btnFlopInterval";
import type { BtnFlopIntervalFeedback, BtnFlopIntervalQuestion, BtnFlopIntervalReview } from "./btnFlopIntervalApi";
const response=(body:unknown,status=200)=>Promise.resolve({ok:status<400,status,json:()=>Promise.resolve(body),text:()=>Promise.resolve(typeof body==="string"?body:JSON.stringify(body))} as Response);
function mockApi(options:{status?:number;question?:(q:BtnFlopIntervalQuestion)=>unknown;grade?:(f:BtnFlopIntervalFeedback,init:RequestInit)=>Promise<Response>;review?:(r:BtnFlopIntervalReview)=>unknown;document?:string}={}) {
 return vi.spyOn(globalThis,"fetch").mockImplementation((url,init)=>{
  const path=String(url),body=init?.body?JSON.parse(String(init.body)):null;
  if(path.endsWith("/review/export"))return response(options.document??raw);
  if(path.endsWith("/grade")){const f=structuredClone(fixture.attempts.find(a=>a.questionIndex===body.questionIndex&&a.action===body.action)!.feedback);return options.grade?options.grade(f,init!):response(f);}
  if(path.endsWith("/review")){const r=btnFlopFixtureReview(body.choices);return response(options.review?options.review(r):r);}
  const index=path.match(/\/questions\/(\d+)/)?.[1];
  if(index!==undefined){const q=structuredClone(fixture.questions[Number(index)]);return response(options.question?options.question(q):q);}
  return response(metadata,options.status);
 });
}
const button=(name:string)=>screen.getByRole("button",{name});
function open(query=""){window.history.replaceState(null,"",`#interval-btn-flop${query}`);return render(<FrontendRoot/>);}
afterEach(()=>{window.history.replaceState(null,"","/");vi.restoreAllMocks();vi.unstubAllGlobals();});
it("loads all four questions before showing a blue BTN hero and checked red BB with answers hidden",async()=>{
 const fetch=mockApi();open();await screen.findByRole("button",{name:"Check"});
 expect(screen.getByRole("heading",{name:"Play the button"})).toBeInTheDocument();
 expect(screen.getByLabelText("BTN player")).toHaveClass("hero","acting","position-0");
 expect(screen.getByLabelText("BB player")).toHaveClass("villain","move-check");expect(screen.getByLabelText("BB player")).toHaveTextContent("Checked");
 expect(screen.getByLabelText("Your cards")).toHaveTextContent("9♠J♠");expect(screen.getByLabelText("Flop board")).toHaveTextContent("2♠8♠T♠");
 expect(button("Check")).not.toHaveTextContent("EV");expect(button("Bet 3.25 bb")).toHaveClass("move-raise");
 expect(fetch.mock.calls.filter(([u])=>String(u).includes("/questions/"))).toHaveLength(4);
 expect(fetch.mock.calls.every(([u])=>String(u).startsWith("/api/v1/trainer/research/btn-flop-interval"))).toBe(true);
 expect(screen.getByText(/Question 1 of 4/)).toHaveTextContent("0 of 4 practised");
});
it("navigates all four hands, remembers separate answers and retains revealed ranges during retries",async()=>{
 const fetch=mockApi();open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));await screen.findByRole("heading",{name:"Outside limit"});
 fireEvent.click(button("Next question"));expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♥K♥");expect(button("Check")).not.toHaveTextContent("EV");
 fireEvent.click(button("Bet 3.25 bb"));await screen.findByRole("heading",{name:"Within limit"});
 fireEvent.click(button("Next question"));expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♠Q♠");
 fireEvent.click(button("Next question"));expect(screen.getByLabelText("Your cards")).toHaveTextContent("Q♥T♥");expect(button("Next question")).toBeDisabled();
 fireEvent.click(button("1 · 9s Js ✓"));expect(button("Check")).toBeDisabled();expect(button("Check")).toHaveTextContent("Outside limit");
 fireEvent.click(button("Try another move"));expect(button("Check")).toBeEnabled();expect(button("Bet 3.25 bb")).toHaveTextContent("Hand EV");
 fireEvent.click(button("Bet 3.25 bb"));await screen.findByRole("heading",{name:"Within limit"});expect(screen.getByLabelText("BTN player")).toHaveClass("move-raise");
 expect(fetch.mock.calls.filter(([u])=>String(u).endsWith("/grade")).map(([,i])=>JSON.parse(String(i?.body)).questionIndex)).toEqual([0,1,0]);
});
it("exports all eight owned attempts as exactly the canonical document bytes",async()=>{
 const fetch=mockApi();open();await screen.findByRole("button",{name:"Check"});
 for(let i=0;i<4;i++){fireEvent.click(button("Check"));await screen.findByRole("heading",{name:"Outside limit"});if(i<3)fireEvent.click(button("Next question"));}
 for(let i=0;i<4;i++){fireEvent.click(button(`${i+1} · ${fixture.questions[i].heroCards} ✓`));fireEvent.click(button("Try another move"));fireEvent.click(button("Bet 3.25 bb"));await screen.findByRole("heading",{name:"Within limit"});}
 fireEvent.click(button("Review 8 attempts"));expect(await screen.findByRole("region",{name:"BTN practice review"})).toHaveTextContent("4 of 4 questions practised · 8 attempts");
 const create=vi.fn((blob:Blob)=>{expect(blob.size).toBeGreaterThan(0);return "blob:btn";}),revoke=vi.fn();vi.stubGlobal("URL",class extends URL{static createObjectURL=create;static revokeObjectURL=revoke;});
 vi.spyOn(HTMLAnchorElement.prototype,"click").mockImplementation(()=>{});fireEvent.click(button("Download replayable review"));await waitFor(()=>expect(create).toHaveBeenCalledTimes(1));
 const text=await new Promise<string>(resolve=>{const r=new FileReader();r.onload=()=>resolve(String(r.result));r.readAsText(create.mock.calls[0][0]);});expect(text).toBe(raw);expect(revoke).toHaveBeenCalledWith("blob:btn");
 const choices=JSON.parse(String(fetch.mock.calls.find(([u])=>String(u).endsWith("/review/export"))![1]?.body));expect(choices).toEqual({reportHash:metadata.reportHash,choices:fixture.attempts.map(({questionIndex,action})=>({questionIndex,action}))});
});
it("supports a direct hand link and an empty review",async()=>{
 mockApi();open(`?report=${metadata.reportHash}&question=2`);await screen.findByRole("button",{name:"Check"});expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♠Q♠");
 fireEvent.click(button("Review 0 attempts"));expect(await screen.findByRole("region",{name:"BTN practice review"})).toHaveTextContent("0 of 4 questions practised · 0 attempts");
});
it.each(["?report="+"0".repeat(64),"?question=4","?question=-1","?question=1.5","?question=0&question=1","?seed=7","?question=01"])("rejects unsupported link %s",async query=>{
 mockApi();open(query);expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
 fireEvent.click(button("Load current report"));expect(await screen.findByRole("button",{name:"Check"})).toBeEnabled();
});
it("does not substitute demo data when disabled or one question is inconsistent",async()=>{
 const fetch=mockApi({status:404});open();expect(await screen.findByRole("alert")).toHaveTextContent("not enabled");expect(screen.queryByLabelText("Six-seat action table")).not.toBeInTheDocument();
 fetch.mockRestore();mockApi({question:q=>{if(q.questionIndex===3)q.context.potBb=100;return q;}});fireEvent.click(button("Load current report"));expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");expect(screen.queryByRole("button",{name:"Check"})).not.toBeInTheDocument();
});
it("rejects wrong-hand responses, changing retry ranges and forged reviews",async()=>{
 const fetch=mockApi({grade:()=>response(fixture.attempts[1].feedback)});open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
 fetch.mockRestore();const next=mockApi({review:r=>({...r,counts:{...r.counts,distinctQuestionsAttempted:4}})});fireEvent.click(button("Load current report"));await screen.findByRole("button",{name:"Check"});fireEvent.click(button("Review 0 attempts"));expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
 next.mockRestore();let calls=0;mockApi({grade:f=>{if(++calls===2)f.alternatives[1].upperEvBb=100;return response(f);}});fireEvent.click(button("Load current report"));fireEvent.click(await screen.findByRole("button",{name:"Check"}));await screen.findByRole("heading",{name:"Outside limit"});fireEvent.click(button("Try another move"));fireEvent.click(button("Check"));expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");
});
it("blocks duplicate click/keyboard and ignores a completed grade after hash change",async()=>{
 let finish!:(r:Response)=>void,signal:AbortSignal|undefined;const pending=new Promise<Response>(r=>{finish=r;});
 const fetch=mockApi({grade:(_f,i)=>{signal=i.signal as AbortSignal;return pending;}});open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));fireEvent.click(button("Check"));fireEvent.keyDown(window,{key:"1"});
 expect(fetch.mock.calls.filter(([u])=>String(u).endsWith("/grade"))).toHaveLength(1);expect(button("Next question")).toBeDisabled();
 act(()=>{window.history.replaceState(null,"",`#interval-btn-flop?report=${"0".repeat(64)}`);window.dispatchEvent(new HashChangeEvent("hashchange"));});await screen.findByRole("alert");expect(signal?.aborted).toBe(true);
 await act(async()=>finish(await response(fixture.attempts[0].feedback)));expect(screen.queryByRole("heading",{name:"Outside limit"})).not.toBeInTheDocument();
});
it("counts 100 retries globally, then allows navigation/review/reset while guarding hotkeys",async()=>{
 const fetch=mockApi();open();await screen.findByRole("button",{name:"Check"});fireEvent.keyDown(window,{key:"1",repeat:true});fireEvent.keyDown(button("Check"),{key:"1"});fireEvent.keyDown(window,{key:"1",ctrlKey:true});expect(fetch.mock.calls.filter(([u])=>String(u).endsWith("/grade"))).toHaveLength(0);
 for(let i=0;i<100;i++){fireEvent.keyDown(window,{key:"2"});await screen.findByRole("heading",{name:"Within limit"});if(i<99)fireEvent.click(button("Try another move"));}
 fireEvent.click(button("Next question"));expect(button("Check")).toBeDisabled();expect(button("Review 100 attempts")).toBeEnabled();fireEvent.keyDown(window,{key:"1"});expect(fetch.mock.calls.filter(([u])=>String(u).endsWith("/grade"))).toHaveLength(100);
 fireEvent.click(button("Start over"));await waitFor(()=>expect(button("Check")).toBeEnabled());
},20000);
it("permits network retry without inventing feedback",async()=>{
 let first=true;mockApi({grade:f=>{if(first){first=false;return Promise.reject(new TypeError("offline"));}return response(f);}});open();fireEvent.click(await screen.findByRole("button",{name:"Check"}));expect(await screen.findByRole("alert")).toHaveTextContent("offline");await waitFor(()=>expect(button("Check")).toBeEnabled());fireEvent.click(button("Check"));await screen.findByRole("heading",{name:"Outside limit"});
});

it("blocks forged exports without creating a download",async()=>{
 mockApi({document:JSON.stringify({...btnFlopFixtureReview([]),counts:{distinctQuestionsAttempted:4,withinLimit:0,outsideLimit:0,strategyDependent:0}})});
 const create=vi.fn();vi.stubGlobal("URL",class extends URL{static createObjectURL=create;});open();await screen.findByRole("button",{name:"Check"});
 fireEvent.click(button("Review 0 attempts"));await screen.findByRole("region",{name:"BTN practice review"});fireEvent.click(button("Download replayable review"));
 expect(await screen.findByRole("alert")).toHaveTextContent("inconsistent data");expect(create).not.toHaveBeenCalled();
});
it("cancels an export on hash change and ignores its late completion",async()=>{
 const fetch=mockApi();let finish!:(r:Response)=>void,signal:AbortSignal|undefined;
 const original=fetch.getMockImplementation()!;fetch.mockImplementation((url,init)=>String(url).endsWith("/review/export")?new Promise<Response>(resolve=>{finish=resolve;signal=init?.signal as AbortSignal;}):original(url,init));
 const create=vi.fn();vi.stubGlobal("URL",class extends URL{static createObjectURL=create;});open();await screen.findByRole("button",{name:"Check"});
 fireEvent.click(button("Review 0 attempts"));await screen.findByRole("region",{name:"BTN practice review"});fireEvent.click(button("Download replayable review"));expect(button("Next question")).toBeDisabled();
 act(()=>{window.history.replaceState(null,"",`#interval-btn-flop?question=1`);window.dispatchEvent(new HashChangeEvent("hashchange"));});await screen.findByRole("button",{name:"Check"});expect(signal?.aborted).toBe(true);
 await act(async()=>finish(await response(JSON.stringify(btnFlopFixtureReview([])))));expect(create).not.toHaveBeenCalled();expect(screen.getByLabelText("Your cards")).toHaveTextContent("A♥K♥");
});

it("enables the first keyboard decision in the same commit as its action buttons",async()=>{
 const fetch=mockApi();let sent=false;
 window.history.replaceState(null,"","#interval-btn-flop");
 render(<Profiler id="keyboard-ready" onRender={()=>{
  const ready=screen.queryByRole("button",{name:"Bet 3.25 bb"}) as HTMLButtonElement|null;
  if(!sent&&ready&&!ready.disabled){sent=true;window.dispatchEvent(new KeyboardEvent("keydown",{key:"2"}));}
 }}><FrontendRoot/></Profiler>);
 await screen.findByRole("heading",{name:"Within limit"});
 expect(sent).toBe(true);expect(fetch.mock.calls.filter(([u])=>String(u).endsWith("/grade"))).toHaveLength(1);
});
