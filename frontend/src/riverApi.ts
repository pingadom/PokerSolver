export type RiverCard = {
  rank: string;
  suit: string;
};

export type RiverMetadata = {
  spotId: string;
  spotHash: string;
  packHash: string;
  publicationStatus: "VALIDATION_ONLY";
  firstSeat: string;
  secondSeat: string;
  board: RiverCard[];
  potBb: number;
  remainingStackBb: number;
  betBb: number;
  firstRange: string[];
  secondRange: string[];
  gameGapBb: number;
  availableQuestions: number;
  rakeModel: "NO_RAKE";
  bettingTree: "SINGLE_BET_NO_RAISES";
};

export type RiverAction = "k" | "b" | "c" | "f";

export type RiverQuestion = {
  packHash: string;
  spotId: string;
  publicationStatus: "VALIDATION_ONLY";
  board: RiverCard[];
  potBb: number;
  remainingStackBb: number;
  betBb: number;
  actingSeat: string;
  heroCombo: string;
  publicHistory: string;
  legalActions: RiverAction[];
  gameGapBb: number;
};

export type RiverQuestionResponse = { seed: string; question: RiverQuestion };

export type RiverFeedback = {
  packHash: string;
  heroCombo: string;
  publicHistory: string;
  selectedAction: RiverAction;
  selectedEvBb: number;
  bestEvBb: number;
  evLossBb: number;
  actionFrequency: Record<RiverAction, number>;
  actionEvBb: Record<RiverAction, number>;
};

export async function riverRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1/trainer/research/river${path}`, init);
  const body = await response.json();
  if (!response.ok)
    throw new Error(body.message ?? `River trainer request failed (${response.status})`);
  return body as T;
}

export function postRiver<T>(path: string, body: object): Promise<T> {
  return riverRequest(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
