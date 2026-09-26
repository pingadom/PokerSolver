export type TrainerAction = "SHOVE" | "FOLD";
export type Seat = "UTG" | "HJ" | "CO" | "BTN" | "SB" | "BB";
export type TrainerMetadata = {
  spotId: string;
  spotHash: string;
  packHash: string;
  publicationStatus: "VALIDATION_ONLY";
  heroSeat: Seat;
  opponentSeat: Seat;
  heroRange: string[];
  opponentRange: string[];
  effectiveStackBb: number;
  rakeModel: string;
  payoffMethod: string;
  estimatedGameGapBb: number;
  maximumCalledPayoffStandardErrorBb: number;
  sessionLength: number;
};
export type PriorAction = {
  seat: Seat;
  kind: "POST_SMALL_BLIND" | "POST_BIG_BLIND" | "RAISE_TO" | "FOLD";
  amountBb: number;
};
export type TrainerQuestion = {
  spotId: string;
  spotHash: string;
  publicationStatus: string;
  heroSeat: Seat;
  opponentSeat: Seat;
  heroCombo: string;
  effectiveStackBb: number;
  rakeModel: string;
  payoffMethod: string;
  potBb: number;
  heroCommittedBb: number;
  opponentCommittedBb: number;
  priorActions: PriorAction[];
  legalActions: TrainerAction[];
};
export type SessionQuestion = {
  sessionSeed: string;
  index: number;
  packHash: string;
  question: TrainerQuestion;
};
export type TrainerFeedback = {
  spotHash: string;
  heroCombo: string;
  selectedAction: TrainerAction;
  selectedEvBb: number;
  bestEvBb: number;
  evLossBb: number;
  shoveFrequency: number;
  foldFrequency: number;
  shoveEvBb: number;
  foldEvBb: number;
};
export type GradedQuestion = { question: SessionQuestion; feedback: TrainerFeedback };
export type TrainerReview = {
  packHash: string;
  attempts: GradedQuestion[];
  totalEvLossBb: number;
  averageEvLossBb: number;
};

export async function trainerRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1/trainer/research${path}`, init);
  const body = await response.json();
  if (!response.ok)
    throw new Error(body.message ?? `Trainer request failed (${response.status})`);
  return body as T;
}

export function postTrainer<T>(path: string, body: object): Promise<T> {
  return trainerRequest(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
