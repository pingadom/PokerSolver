import RootPreflopPage from "./RootPreflopPage";
import { sequencePreflopClient } from "./sequencePreflopApi";

export default function SequencePreflopPage() {
  return <RootPreflopPage client={sequencePreflopClient} />;
}
