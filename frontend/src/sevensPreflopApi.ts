import frozen from "./test-data/sevens-preflop-drill.json";
import { createRootClient, type RootQuestion } from "./rootPreflopProtocol";

export const sevensPreflopClient = createRootClient({
  frozen: { metadata: frozen.metadata, questions: frozen.questions as RootQuestion[] },
  apiBase: "/api/v1/trainer/research/root-sevens-preflop-drill", route: "#root-sevens-preflop",
  studyLabel: "POCKET SEVENS STUDY", heroHandName: "pocket sevens",
  exportPrefix: "pokerlab-root-sevens-preflop-review",
});
