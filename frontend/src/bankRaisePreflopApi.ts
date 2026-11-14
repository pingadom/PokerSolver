import frozen from "./test-data/bank-raise-preflop-drill.json";
import { createBankClient, type RootMetadata, type RootQuestion } from "./bankPreflopProtocol";

export const bankRaiseMetadata: RootMetadata = {
  schemaVersion: frozen.snapshot.schemaVersion, studyHash: frozen.snapshot.studyHash,
  publicationStatus: "BANK_RAISE_MINIMA_TWO_HAND_FEEDBACK_RESEARCH_ONLY", trainerAdmission: false,
  model: "SUPPLIED_JOINT_PREFLOP_SELECTED_FLOP_SIZE_MENU/v1", evScope: frozen.snapshot.evScope,
  tableSeats: 6, activePlayers: 2, availableQuestions: 2, cachedLegalGrades: 6, maximumAttempts: 128,
  sourceCaseIndices: [0, 2],
  startupBudget: { physicalGames: 1, maximumParentLpSolves: 4, maximumEndpointLpSolves: 2, freshReferenceRuns: 2, freshIterations: 240000 },
};
export const bankRaisePreflopClient = createBankClient({
  frozen: { metadata: bankRaiseMetadata, questions: frozen.snapshot.questions as RootQuestion[] },
  apiBase: "/api/v1/trainer/research/bank-raise-feedback-drill", route: "#bank-raise-feedback",
  studyLabel: "QUALIFIED RAISE BOUNDS", heroHandName: "your hand", exportPrefix: "pokerlab-bank-raise-feedback-review",
  decisionLabel: "Two-hand qualified raise feedback decision",
  lossScopeNote: "Loss bounds conservatively compare all three moves; the interval endpoints need not occur together.",
});
