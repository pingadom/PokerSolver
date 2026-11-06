import frozen from "./test-data/batch-preflop-drill.json";
import { createRootClient, type RootQuestion } from "./rootPreflopProtocol";

export const batchPreflopClient = createRootClient({
  frozen: { metadata: frozen.metadata, questions: frozen.questions as RootQuestion[] },
  apiBase: "/api/v1/trainer/research/root-batch-preflop-drill", route: "#root-batch-preflop",
  studyLabel: "COMPLETE-MENU STUDY",
  exportPrefix: "pokerlab-root-batch-preflop-review",
});
