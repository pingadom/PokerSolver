import RootPreflopPage from "./RootPreflopPage";
import { minimumPreflopClient } from "./minimumPreflopApi";

export default function MinimumPreflopPage() {
  return <RootPreflopPage client={minimumPreflopClient} />;
}
