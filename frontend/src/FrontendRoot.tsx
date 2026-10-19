import { useEffect, useState } from "react";
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

function currentRoute() {
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
