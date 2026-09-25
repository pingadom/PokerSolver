import { useEffect, useState } from "react";
import App from "./App";
import RiverPage from "./RiverPage";
import TrainerPage from "./TrainerPage";
import TurnRiverPage from "./TurnRiverPage";

function currentRoute() {
  if (window.location.hash.startsWith("#trainer")) return "trainer";
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
  if (route === "trainer") return <TrainerPage />;
  if (route === "turn-river") return <TurnRiverPage />;
  if (route === "river") return <RiverPage />;
  return <App />;
}
