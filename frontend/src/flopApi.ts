export type FlopCard = { rank: string; suit: string };
export type FlopAction = "k" | "b" | "c" | "f";

export type FlopMetadata = {
  spotHash: string;
  packHash: string;
  publicationStatus: "VALIDATION_ONLY";
  flop: FlopCard[];
  potBb: number;
  remainingStackBb: number;
  flopBetBb: number;
  turnBetBb: number;
  riverBetBb: number;
  firstRange: string[];
  secondRange: string[];
  gameGapBb: number;
  rakeModel: "NO_RAKE";
  chanceModel: "FULL_PHYSICAL_DECK";
  bettingTree: "SINGLE_BET_EACH_STREET_NO_RAISES";
};

export type FlopHandFeedback = {
  street: "FLOP" | "TURN" | "RIVER";
  flopHistory: string;
  turn: FlopCard | null;
  turnHistory: string;
  river: FlopCard | null;
  riverHistory: string;
  selectedAction: FlopAction;
  selectedEvBb: number;
  bestEvBb: number;
  evLossBb: number;
  actionFrequency: Record<FlopAction, number>;
  actionEvBb: Record<FlopAction, number>;
};

export type FlopHandSnapshot = {
  packHash: string;
  publicationStatus: "VALIDATION_ONLY";
  seed: string;
  heroPlayer: 0 | 1;
  heroCombo: string;
  opponentCombo: string | null;
  flop: FlopCard[];
  turn: FlopCard | null;
  river: FlopCard | null;
  street: "FLOP" | "TURN" | "RIVER";
  flopHistory: string;
  turnHistory: string;
  riverHistory: string;
  potBb: number;
  heroRemainingStackBb: number;
  publicActions: {
    street: "FLOP" | "TURN" | "RIVER";
    player: 0 | 1;
    action: FlopAction;
  }[];
  legalActions: FlopAction[];
  feedback: FlopHandFeedback[];
  complete: boolean;
  showdown: boolean;
  heroCenteredResultBb: number | null;
  gameGapBb: number;
};

export async function flopRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1/trainer/research/flop${path}`, init);
  const body = await response.json();
  if (!response.ok)
    throw new Error(body.message ?? `Flop research request failed (${response.status})`);
  return body as T;
}

export function postFlop<T>(path: string, body: object): Promise<T> {
  return flopRequest(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
