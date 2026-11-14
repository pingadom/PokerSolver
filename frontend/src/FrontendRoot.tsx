import { lazy, Suspense, useEffect, useState } from "react";
import App from "./App";
import RiverPage from "./RiverPage";
import TrainerPage from "./TrainerPage";
import TurnRiverPage from "./TurnRiverPage";
import TurnRiverHandPage from "./TurnRiverHandPage";
import FlopHandPage from "./FlopHandPage";
import MultiwayPage from "./MultiwayPage";
import SixMaxPreflopPage from "./SixMaxPreflopPage";
import IntervalPreflopPage from "./IntervalPreflopPage";
import IntervalCollectionPage from "./IntervalCollectionPage";
import FlopIntervalPage from "./FlopIntervalPage";
import LaterFlopIntervalPage from "./LaterFlopIntervalPage";
import BtnFlopIntervalPage from "./BtnFlopIntervalPage";
import FlopCollectionPage from "./FlopCollectionPage";

import MixedFlopCollectionPage from "./MixedFlopCollectionPage";

import ExpandedFlopCollectionPage from "./ExpandedFlopCollectionPage";

import QualifiedFlopCollectionPage from "./QualifiedFlopCollectionPage";
const CallFlopCollectionPage = lazy(() => import("./CallFlopCollectionPage"));

const UnpairedFlopCollectionPage = lazy(() => import("./UnpairedFlopCollectionPage"));

const SizingFlopCollectionPage = lazy(() => import("./SizingFlopCollectionPage"));
const SizeMenuBtnPage = lazy(() => import("./SizeMenuBtnPage"));
const RootPreflopPage = lazy(() => import("./RootPreflopPage"));
const MinimumPreflopPage = lazy(() => import("./MinimumPreflopPage"));
const SequencePreflopPage = lazy(() => import("./SequencePreflopPage"));

const BankPreflopPage = lazy(() => import("./BankPreflopPage"));
const BankRaisePreflopPage = lazy(() => import("./BankRaisePreflopPage"));
const SevensPreflopPage = lazy(() => import("./SevensPreflopPage"));
const BatchPreflopPage = lazy(() => import("./BatchPreflopPage"));

function currentRoute() {
  if (window.location.hash.startsWith("#bank-raise-feedback")) return "bank-raise-feedback";
  if (window.location.hash.startsWith("#root-hand-bank")) return "root-hand-bank";
  if (window.location.hash.startsWith("#root-sevens-preflop")) return "root-sevens-preflop";
  if (window.location.hash.startsWith("#root-batch-preflop")) return "root-batch-preflop";
  if (window.location.hash.startsWith("#root-sequence-preflop")) return "root-sequence-preflop";
  if (window.location.hash.startsWith("#root-minimum-preflop")) return "root-minimum-preflop";
  if (window.location.hash.startsWith("#root-preflop")) return "root-preflop";
  if (window.location.hash.startsWith("#btn-sizing-flop")) return "btn-sizing-flop";
  if (window.location.hash.startsWith("#interval-sizing-flop")) return "interval-sizing-flop";
  if (window.location.hash.startsWith("#interval-unpaired-flop")) return "interval-unpaired-flop";
  if (window.location.hash.startsWith("#interval-call-flop")) return "interval-call-flop";
  if (window.location.hash.startsWith("#interval-qualified-flop")) return "interval-qualified-flop";
  if (window.location.hash.startsWith("#interval-expanded-flop")) return "interval-expanded-flop";
  if (window.location.hash.startsWith("#interval-mixed-flop")) return "interval-mixed-flop";
  if (window.location.hash.startsWith("#interval-btn-flop")) return "interval-btn-flop";
  if (window.location.hash.startsWith("#interval-flop-collection")) return "interval-flop-collection";
  if (window.location.hash.startsWith("#interval-flop-response")) return "interval-flop-response";
  if (window.location.hash.startsWith("#interval-flop")) return "interval-flop";
  if (window.location.hash.startsWith("#interval-collection")) return "interval-collection";
  if (window.location.hash.startsWith("#interval-preflop")) return "interval-preflop";
  if (window.location.hash.startsWith("#sixmax-preflop")) return "sixmax-preflop";
  if (window.location.hash.startsWith("#trainer")) return "trainer";
  if (window.location.hash.startsWith("#multiway")) return "multiway";
  if (window.location.hash.startsWith("#flop-hand")) return "flop-hand";
  if (window.location.hash.startsWith("#turn-river-hand")) return "turn-river-hand";
  if (window.location.hash.startsWith("#turn-river")) return "turn-river";
  if (window.location.hash.startsWith("#river")) return "river";
  return "home";
}

export default function FrontendRoot() {
  const [route, setRoute] = useState(currentRoute);
  useEffect(() => {
    const changed = () => setRoute(currentRoute());
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, []);
  if (route === "bank-raise-feedback") return <Suspense fallback={<p role="status">Loading hand practice…</p>}><BankRaisePreflopPage /></Suspense>;
  if (route === "root-hand-bank") return <Suspense fallback={<p role="status">Loading hand practice…</p>}><BankPreflopPage /></Suspense>;
  if (route === "root-sevens-preflop") return <Suspense fallback={<p role="status">Loading preflop practice…</p>}><SevensPreflopPage /></Suspense>;
  if (route === "root-batch-preflop") return <Suspense fallback={<p role="status">Loading preflop practice…</p>}><BatchPreflopPage /></Suspense>;
  if (route === "root-sequence-preflop") return <Suspense fallback={<p role="status">Loading preflop practice…</p>}><SequencePreflopPage /></Suspense>;
  if (route === "root-minimum-preflop") return <Suspense fallback={<p role="status">Loading preflop practice…</p>}><MinimumPreflopPage /></Suspense>;
  if (route === "root-preflop") return <Suspense fallback={<p role="status">Loading preflop practice…</p>}><RootPreflopPage key={route} /></Suspense>;
  if (route === "btn-sizing-flop") return <Suspense fallback={<p role="status">Loading BTN practice…</p>}><SizeMenuBtnPage /></Suspense>;
  if (route === "interval-sizing-flop") return <Suspense fallback={<p role="status">Loading flop practice…</p>}><SizingFlopCollectionPage /></Suspense>;
  if (route === "interval-unpaired-flop") return <Suspense fallback={<p role="status">Loading flop practice…</p>}><UnpairedFlopCollectionPage /></Suspense>;
  if (route === "interval-call-flop") return <Suspense fallback={<p role="status">Loading flop practice…</p>}><CallFlopCollectionPage /></Suspense>;
  if (route === "interval-qualified-flop") return <QualifiedFlopCollectionPage />;
  if (route === "interval-expanded-flop") return <ExpandedFlopCollectionPage />;
  if (route === "interval-mixed-flop") return <MixedFlopCollectionPage />;
  if (route === "interval-btn-flop") return <BtnFlopIntervalPage />;
  if (route === "trainer") return <TrainerPage />;
  if (route === "multiway") return <MultiwayPage />;
  if (route === "sixmax-preflop") return <SixMaxPreflopPage />;
  if (route === "interval-preflop") return <IntervalPreflopPage />;
  if (route === "interval-collection") return <IntervalCollectionPage />;
  if (route === "interval-flop") return <FlopIntervalPage />;
  if (route === "interval-flop-response") return <LaterFlopIntervalPage />;
  if (route === "interval-flop-collection") return <FlopCollectionPage />;
  if (route === "flop-hand") return <FlopHandPage />;
  if (route === "turn-river-hand") return <TurnRiverHandPage />;
  if (route === "turn-river") return <TurnRiverPage />;
  if (route === "river") return <RiverPage />;
  return <App />;
}
