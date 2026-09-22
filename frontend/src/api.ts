export type Status =
  "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
export type Scenario = {
  players: { name: string; cards: string[] }[];
  board: string[];
  iterations: number;
  batchSize: number;
  seed?: number;
};
export type Simulation = {
  simulationId: string;
  status: Status;
  completedIterations: number;
  requestedIterations: number;
  completedBatches: number;
  totalBatches: number;
  createdAt: string;
  errorMessage?: string;
  configuration: { seed: number; players: { playerName: string }[] };
};
export type Results = {
  simulationId: string;
  status: "COMPLETED";
  totalTrials: number;
  elapsedMs: number;
  players: {
    name: string;
    wins: number;
    ties: number;
    losses: number;
    equity: number;
  }[];
};
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1/simulations${path}`, init);
  const body = await response.json();
  if (!response.ok)
    throw new Error(body.message ?? `Request failed (${response.status})`);
  return body as T;
}
export function parseScenario(
  players: { name: string; cards: string }[],
  board: string,
  iterations: string,
  seed: string,
): Scenario {
  const parse = (text: string) =>
    text.trim() ? text.trim().toUpperCase().split(/\s+/) : [];
  const hands = players.map((p) => ({
    name: p.name.trim(),
    cards: parse(p.cards),
  }));
  if (
    hands.length < 2 ||
    hands.length > 9 ||
    hands.some((p) => !p.name || p.name.length > 80 || p.cards.length !== 2)
  )
    throw new Error("Enter 2–9 named players with two cards each.");
  if (new Set(hands.map((p) => p.name)).size !== hands.length)
    throw new Error("Each player needs a unique name.");
  const community = parse(board);
  if (community.length > 5)
    throw new Error("The board can contain at most five cards.");
  const cards = [...hands.flatMap((p) => p.cards), ...community];
  if (cards.some((c) => !/^[2-9TJQKA][SHDC]$/.test(c)))
    throw new Error(
      "Use two-character cards, such as AS, TH or 7C, separated by spaces.",
    );
  if (new Set(cards).size !== cards.length)
    throw new Error("A card cannot appear more than once.");
  const count = Number(iterations);
  if (!Number.isSafeInteger(count) || count < 1 || count > 100_000_000)
    throw new Error("Choose 1 to 100,000,000 trials.");
  if (seed.trim() && !Number.isSafeInteger(Number(seed)))
    throw new Error(
      "Seed must be an integer within JavaScript’s safe number range.",
    );
  return {
    players: hands,
    board: community,
    iterations: count,
    batchSize: 100_000,
    ...(seed.trim() ? { seed: Number(seed) } : {}),
  };
}
