import frozen from "./test-data/sequence-preflop-drill.json";
import { createRootClient, type RootQuestion } from "./rootPreflopProtocol";

export const sequencePreflopClient = createRootClient({
  frozen: { metadata: frozen.metadata, questions: frozen.questions as RootQuestion[] },
  apiBase: "/api/v1/trainer/research/root-sequence-preflop-drill", route: "#root-sequence-preflop",
  studyLabel: "SEQUENCE SOLVER STUDY",
  exportPrefix: "pokerlab-root-sequence-preflop-review",
});
