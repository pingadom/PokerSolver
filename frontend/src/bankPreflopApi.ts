import frozen from "./test-data/bank-preflop-drill.json";
import { createBankClient, type RootQuestion } from "./bankPreflopProtocol";
export const bankPreflopClient = createBankClient({
  frozen: { metadata: frozen.metadata, questions: frozen.questions as RootQuestion[] },
  apiBase: "/api/v1/trainer/research/root-hand-bank-drill", route: "#root-hand-bank",
  studyLabel: "THREE-HAND STUDY", heroHandName: "your hand", exportPrefix: "pokerlab-root-hand-bank-review",
});
export const bankHandNames = ["pocket sevens", "pocket nines", "suited ace-jack"] as const;
export const bankHandLabels = ["77", "99", "AJs"] as const;
/** Balanced shuffled cycles, with no immediate repeat at a cycle boundary. */
export function nextBankHand(current: number, remaining: number[], random = Math.random, sourceIndices: readonly number[] = [0, 1, 2]) {
  const queue = remaining.slice();
  if (!queue.length) {
    queue.push(...sourceIndices);
    for (let i = queue.length - 1; i > 0; i--) { const j = Math.floor(random() * (i + 1)); [queue[i], queue[j]] = [queue[j], queue[i]]; }
    if (queue[0] === current) [queue[0], queue[1]] = [queue[1], queue[0]];
  }
  return { index: queue[0], remaining: queue.slice(1) };
}

export function initialBankQueue(current: number, random = Math.random, sourceIndices: readonly number[] = [0, 1, 2]) {
  const others = sourceIndices.filter(i => i !== current);
  return random() < .5 ? others : others.reverse();
}
