import { useEffect, useState } from "react";
import App from "./App";
import RiverPage from "./RiverPage";
import TrainerPage from "./TrainerPage";

export default function FrontendRoot() {
  const [route, setRoute] = useState(() => window.location.hash.startsWith("#trainer") ? "trainer" : window.location.hash.startsWith("#river") ? "river" : "home");
  useEffect(() => {
    const changed = () => setRoute(window.location.hash.startsWith("#trainer") ? "trainer" : window.location.hash.startsWith("#river") ? "river" : "home");
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, []);
  return route === "trainer" ? <TrainerPage /> : route === "river" ? <RiverPage /> : <App />;
}
