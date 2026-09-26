export type MultiwayAction = "CALL" | "FOLD";
export type MultiwaySeat = "UTG" | "HJ" | "CO" | "BTN" | "SB" | "BB";

export type MultiwayMetadata = {
  spotId: string;
  spotHash: string;
  packHash: string;
  publicationStatus: "VALIDATION_ONLY";
  solverVersion: string;
  payoffMethod: "EXACT_ENUMERATION";
  seats: MultiwaySeat[];
  committedBb: number[];
  stackBb: number;
  deadMoneyBb: number;
  rakeModel: "NO_RAKE";
  nashConvBb: number;
  maximumPayoffStandardErrorBb: number;
  sessionLength: number;
};

export type MultiwayQuestion = {
  sessionSeed: string;
  index: number;
  playerFilter: number;
  packHash: string;
  aggressorSeat: MultiwaySeat;
  actingPlayer: number;
  actingSeat: MultiwaySeat;
  heroCombo: string;
  priorResponses: { seat: MultiwaySeat; action: MultiwayAction }[];
  potBb: number;
  callCostBb: number;
  stackBb: number;
  publicationStatus: "VALIDATION_ONLY";
  legalActions: MultiwayAction[];
};

export type MultiwayFeedback = {
  selectedAction: MultiwayAction;
  selectedEvBb: number;
  bestEvBb: number;
  evLossBb: number;
  callEvBb: number;
  foldEvBb: number;
  callFrequency: number;
  foldFrequency: number;
};

export type MultiwayGradedQuestion = { question: MultiwayQuestion; feedback: MultiwayFeedback };
export type MultiwayReview = {
  packHash: string;
  attempts: MultiwayGradedQuestion[];
  totalEvLossBb: number;
  averageEvLossBb: number;
};

export async function multiwayRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1/trainer/research/multiway${path}`, init);
  const body = await response.json();
  if (!response.ok)
    throw new Error(body.message ?? `Six-seat trainer request failed (${response.status})`);
  return body as T;
}

export function postMultiway<T>(path: string, body: object): Promise<T> {
  return multiwayRequest(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
