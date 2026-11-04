import frozen from "./test-data/minimum-preflop-drill.json";
import { createRootClient, type RootQuestion } from "./rootPreflopProtocol";

export const minimumPreflopClient = createRootClient({
  frozen: { metadata: frozen.metadata, questions: frozen.questions as RootQuestion[] },
  apiBase: "/api/v1/trainer/research/root-minimum-preflop-drill", route: "#root-minimum-preflop",
  exportPrefix: "pokerlab-root-minimum-preflop-review",
});
