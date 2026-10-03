import type { MultiwaySeat } from "./multiwayApi";

export type PreflopAction = "fold" | "check" | "call" | `raise:${number}`;
export type PublicMove = {
  seat: MultiwaySeat;
  kind: "POST_SMALL_BLIND" | "POST_BIG_BLIND" | "FOLD" | "CHECK" | "CALL" | "RAISE_TO";
  amountBb: number;
};
export type PreflopPlayer = {
  seat: MultiwaySeat;
  status: "ACTING" | "ACTIVE" | "FOLDED" | "ALL_IN";
  committedBb: number;
  remainingStackBb: number;
  lastAction: PublicMove | null;
};
export type PreflopMetadata = {
  spotId: string; spotHash: string; packHash: string;
  packSchema: "six-max-preflop-checkdown-pack/v1";
  publicationStatus: "VALIDATION_ONLY";
  solverVersion: string; generatedAt: string;
  payoffMethod: "EXACT_ENUMERATION";
  continuationModel: "MANDATORY_CHECKDOWN";
  chanceModel: "EXACT_RANGE_PRODUCT";
  seats: MultiwaySeat[]; rangeComboCounts: number[];
  stackBb: number; smallBlindBb: number; raiseToBb: number[];
  rake: { fraction: number; capBb: number; noFlopNoDrop: boolean };
  nashConvBb: number; maximumPayoffStandardErrorBb: number; sessionLength: number;
};
export type PreflopQuestion = {
  sessionSeed: string; index: number; packHash: string;
  actingSeat: MultiwaySeat; heroCombo: string;
  priorActions: { seat: MultiwaySeat; action: PreflopAction }[];
  legalActions: PreflopAction[]; players: PreflopPlayer[];
  potBb: number; toCallBb: number; stackBb: number; smallBlindBb: number;
  publicationStatus: "VALIDATION_ONLY";
};
export type PreflopFeedback = {
  selectedAction: PreflopAction; selectedEvBb: number; bestEvBb: number; evLossBb: number;
  actionEvBb: Record<PreflopAction, number>;
  actionFrequency: Record<PreflopAction, number>;
  actionPayoffStandardErrorBb: Record<PreflopAction, number>;
};
export type PreflopGradedQuestion = { question: PreflopQuestion; feedback: PreflopFeedback };
export type PreflopReview = {
  packHash: string; attempts: PreflopGradedQuestion[];
  totalEvLossBb: number; averageEvLossBb: number;
};

export class PreflopRequestError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}
export async function preflopRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1/trainer/research/sixmax-preflop${path}`, init);
  let body;
  try { body = await response.json(); }
  catch { throw new PreflopRequestError(`Preflop trainer request failed (${response.status})`, response.status); }
  if (!response.ok)
    throw new PreflopRequestError(body?.message ?? `Preflop trainer request failed (${response.status})`, response.status);
  return body as T;
}
export function postPreflop<T>(path: string, body: object, signal?: AbortSignal): Promise<T> {
  return preflopRequest(path, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body), signal,
  });
}
export function actionLabel(action: PreflopAction, stackBb: number): string {
  if (action === "fold") return "Fold";
  if (action === "check") return "Check";
  if (action === "call") return "Call";
  const amount = Number(action.slice(6));
  return amount === stackBb ? `All-in · ${amount} bb` : `Raise to ${amount} bb`;
}
/** Compare all public decision contents, independent of JSON object-property order. */
export function sameQuestion(left: PreflopQuestion, right: PreflopQuestion): boolean {
  const identity = (q: PreflopQuestion) => [q.sessionSeed, q.index, q.packHash,
    q.actingSeat, q.heroCombo, q.potBb, q.toCallBb, q.stackBb, q.smallBlindBb,
    q.publicationStatus, q.legalActions, q.priorActions.map((a) => [a.seat, a.action]),
    q.players.map((p) => [p.seat, p.status, p.committedBb, p.remainingStackBb,
      p.lastAction && [p.lastAction.seat, p.lastAction.kind, p.lastAction.amountBb]]),
  ];
  return JSON.stringify(identity(left)) === JSON.stringify(identity(right));
}
