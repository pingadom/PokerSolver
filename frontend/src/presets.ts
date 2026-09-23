import { parseScenario } from "./api";

export type Preset = {
  id: string;
  title: string;
  street: "Preflop" | "Flop";
  question: string;
  players: { name: string; cards: string }[];
  board: string;
};

export const presets: Preset[] = [
  {
    id: "pair-vs-suited-overcards",
    title: "Pair vs suited overcards",
    street: "Preflop",
    question: "How often do A-K suited catch up to pocket sevens?",
    players: [
      { name: "77", cards: "7C 7D" },
      { name: "AK suited", cards: "AH KH" },
    ],
    board: "",
  },
  {
    id: "suited-vs-offsuit",
    title: "Suited vs offsuit",
    street: "Preflop",
    question: "What does a shared suit add when both hands have A-K?",
    players: [
      { name: "AK suited", cards: "AH KH" },
      { name: "AK offsuit", cards: "AD KS" },
    ],
    board: "",
  },
  {
    id: "dominated-ace",
    title: "The kicker matters",
    street: "Preflop",
    question: "How does A-Q fare when the other player has A-K?",
    players: [
      { name: "AK", cards: "AS KS" },
      { name: "AQ", cards: "AH QD" },
    ],
    board: "",
  },
  {
    id: "big-pair-baseline",
    title: "Big pair baseline",
    street: "Preflop",
    question: "Start with aces against kings, then compare the multiway hand.",
    players: [
      { name: "AA", cards: "AS AD" },
      { name: "KK", cards: "KC KD" },
    ],
    board: "",
  },
  {
    id: "multiway",
    title: "Add a third player",
    street: "Preflop",
    question: "How does 9-8 suited change the AA vs KK result?",
    players: [
      { name: "AA", cards: "AS AD" },
      { name: "KK", cards: "KC KD" },
      { name: "98 suited", cards: "9H 8H" },
    ],
    board: "",
  },
  {
    id: "combo-draw",
    title: "Overpair vs combo draw",
    street: "Flop",
    question: "Can a straight and flush draw overtake aces by the river?",
    players: [
      { name: "AA", cards: "AS AD" },
      { name: "KQ hearts", cards: "KH QH" },
    ],
    board: "JH TH 2C",
  },
];

export function scenarioFromPreset(preset: Preset) {
  return parseScenario(preset.players, preset.board, "200000", "42");
}
