import { describe, it, expect } from "vitest";
import { parseScenario } from "./api";
const players = [
  { name: "AA", cards: "as ah" },
  { name: "KK", cards: "ks kh" },
];
describe("scenario validation", () => {
  it("normalises cards and preserves seed zero", () =>
    expect(parseScenario(players, "", "1000", "0")).toMatchObject({
      players: [
        { name: "AA", cards: ["AS", "AH"] },
        { name: "KK", cards: ["KS", "KH"] },
      ],
      seed: "0",
    }));
  it("rejects duplicates across board and hands", () =>
    expect(() => parseScenario(players, "AS", "1000", "")).toThrow(
      "more than once",
    ));
  it("rejects unsafe iterations and seeds", () => {
    expect(() => parseScenario(players, "", "100000001", "")).toThrow("trials");
    expect(() =>
      parseScenario(players, "", "100", "9223372036854775808"),
    ).toThrow("Seed");
  });
  it("preserves full 64-bit seeds without rounding", () => {
    expect(parseScenario(players, "", "100", "9223372036854775807").seed).toBe(
      "9223372036854775807",
    );
    expect(parseScenario(players, "", "100", "-9223372036854775808").seed).toBe(
      "-9223372036854775808",
    );
  });
  it("rejects malformed cards and duplicate names", () => {
    expect(() =>
      parseScenario([{ name: "AA", cards: "1S AH" }, players[1]], "", "1", ""),
    ).toThrow("two-character");
    expect(() =>
      parseScenario([players[0], { name: "AA", cards: "KS KH" }], "", "1", ""),
    ).toThrow("unique");
  });
});
