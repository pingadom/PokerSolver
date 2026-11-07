import RootPreflopPage from "./RootPreflopPage";
import { batchPreflopClient } from "./batchPreflopApi";

export default function BatchPreflopPage() { return <RootPreflopPage client={batchPreflopClient} />; }
