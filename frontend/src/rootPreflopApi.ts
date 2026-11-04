import frozen from "./test-data/root-preflop-drill.json";
import { createRootClient, type RootQuestion } from "./rootPreflopProtocol";
export { RootProtocolError, RootRequestError, rootEqual, rootMaximumAttempts } from "./rootPreflopProtocol";
export type { RootAction, RootMove, RootMetadata, RootQuestion, RootGrade, RootChoice, RootAttempt, RootReview } from "./rootPreflopProtocol";

export const legacyRootClient = createRootClient({
  frozen: { metadata: frozen.metadata, questions: frozen.questions as RootQuestion[] },
  apiBase: "/api/v1/trainer/research/root-preflop-drill", route: "#root-preflop",
  exportPrefix: "pokerlab-root-preflop-review",
});
export const { rootStudyHash, rootApiBase, parseRootMetadata, parseRootQuestion, parseRootGrade, rootReviewCounts, parseRootReview, rootSessionReview, rootChoices, rootSessionKey, readRootReview, rootRequest, rootReviewDocument } = legacyRootClient;
