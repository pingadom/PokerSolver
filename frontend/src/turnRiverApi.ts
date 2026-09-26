export type TurnRiverCard = { rank: string; suit: string };
export type TurnRiverAction = "k" | "b" | "c" | "f";

export type TurnRiverMetadata = {
  spotHash: string;
  packHash: string;
  publicationStatus: "VALIDATION_ONLY";
  turnBoard: TurnRiverCard[];
  potBb: number;
  remainingStackBb: number;
  turnBetBb: number;
  riverBetBb: number;
  firstRange: string[];
  secondRange: string[];
  gameGapBb: number;
  availableTurnQuestions: number;
  availableRiverQuestions: number;
  rakeModel: "NO_RAKE";
  bettingTree: "SINGLE_BET_EACH_STREET_NO_RAISES";
};

export type TurnRiverQuestion = {
  packHash: string;
  publicationStatus: "VALIDATION_ONLY";
  street: "TURN" | "RIVER";
  turnBoard: TurnRiverCard[];
  river: TurnRiverCard | null;
  potBb: number;
  remainingStackBb: number;
  turnBetBb: number;
  riverBetBb: number;
  actingPlayer: 0 | 1;
  heroCombo: string;
  turnHistory: string;
  riverHistory: string;
  legalActions: TurnRiverAction[];
  gameGapBb: number;
};

export type TurnRiverQuestionResponse = { seed: string; question: TurnRiverQuestion };

export type TurnRiverFeedback = {
  packHash: string;
  street: "TURN" | "RIVER";
  heroCombo: string;
  turnHistory: string;
  river: TurnRiverCard | null;
  riverHistory: string;
  selectedAction: TurnRiverAction;
  selectedEvBb: number;
  bestEvBb: number;
  evLossBb: number;
  actionFrequency: Record<TurnRiverAction, number>;
  actionEvBb: Record<TurnRiverAction, number>;
};

export type TurnRiverHandFeedback = {
  street: "TURN" | "RIVER";
  turnHistory: string;
  river: TurnRiverCard | null;
  riverHistory: string;
  selectedAction: TurnRiverAction;
  selectedEvBb: number;
  bestEvBb: number;
  evLossBb: number;
  actionFrequency: Record<TurnRiverAction, number>;
  actionEvBb: Record<TurnRiverAction, number>;
};

export type TurnRiverHandSnapshot = {
  packHash: string;
  publicationStatus: "VALIDATION_ONLY";
  seed: string;
  heroPlayer: 0 | 1;
  heroCombo: string;
  opponentCombo: string | null;
  turnBoard: TurnRiverCard[];
  river: TurnRiverCard | null;
  street: "TURN" | "RIVER";
  turnHistory: string;
  riverHistory: string;
  potBb: number;
  heroRemainingStackBb: number;
  publicActions: { street: "TURN" | "RIVER"; player: 0 | 1; action: TurnRiverAction }[];
  legalActions: TurnRiverAction[];
  feedback: TurnRiverHandFeedback[];
  complete: boolean;
  showdown: boolean;
  heroCenteredResultBb: number | null;
  gameGapBb: number;
};

export async function turnRiverRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1/trainer/research/turn-river${path}`, init);
  const body = await response.json();
  if (!response.ok)
    throw new Error(body.message ?? `Turn-river trainer request failed (${response.status})`);
  return body as T;
}

export function postTurnRiver<T>(path: string, body: object): Promise<T> {
  return turnRiverRequest(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
