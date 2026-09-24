import { useEffect, useState } from "react";
import App from "./App";
import TrainerPage from "./TrainerPage";

export default function FrontendRoot() {
  const [trainer, setTrainer] = useState(() => window.location.hash.startsWith("#trainer"));
  useEffect(() => {
    const changed = () => setTrainer(window.location.hash.startsWith("#trainer"));
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, []);
  return trainer ? <TrainerPage /> : <App />;
}
