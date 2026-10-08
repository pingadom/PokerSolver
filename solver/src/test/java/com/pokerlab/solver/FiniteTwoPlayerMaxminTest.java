package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerMaxminTest {
    private static class MatrixGame implements MultiPlayerCfrGame<List<Integer>> {
        final double[][] values;

        MatrixGame(double[][] values) {
            this.values = values;
        }

        public int playerCount() {
            return 6;
        }

        public List<Integer> initialState() {
            return List.of();
        }

        public boolean isTerminal(List<Integer> s) {
            return s.size() == 2;
        }

        public double[] terminalUtilities(List<Integer> s) {
            double v = values[s.get(0)][s.get(1)];
            return new double[] {v, 4 - v, -1, -1, -1, -1};
        }

        public int currentPlayer(List<Integer> s) {
            return s.size();
        }

        public List<String> legalActions(List<Integer> s) {
            return java.util.stream.IntStream.range(
                            0, s.isEmpty() ? values.length : values[0].length)
                    .mapToObj(Integer::toString)
                    .toList();
        }

        public String informationSet(List<Integer> s) {
            return "hidden-choice";
        }

        public List<Integer> afterAction(List<Integer> s, String a) {
            var next = new ArrayList<>(s);
            next.add(Integer.parseInt(a));
            return next;
        }

        public List<ChanceOutcome<List<Integer>>> chanceOutcomes(List<Integer> s) {
            throw new AssertionError();
        }
    }

    private static class RecallGame extends MatrixGame {
        final boolean recall;

        RecallGame(boolean recall) {
            super(new double[][] {{1, -1}, {-1, 1}});
            this.recall = recall;
        }

        public boolean isTerminal(List<Integer> s) {
            return s.size() == 3;
        }

        public int currentPlayer(List<Integer> s) {
            return s.size() == 1 ? 1 : 0;
        }

        public List<String> legalActions(List<Integer> s) {
            return List.of("0", "1");
        }

        public String informationSet(List<Integer> s) {
            return s.isEmpty()
                    ? "root"
                    : s.size() == 1 ? "villain" : "again" + (recall ? s.getFirst() : "");
        }

        public double[] terminalUtilities(List<Integer> s) {
            double v = s.get(0).equals(s.get(2)) ? values[s.get(0)][s.get(1)] : -2;
            return new double[] {v, -v, 0, 0, 0, 0};
        }
    }

    @Test
    void doesNotLetTheOpponentSeeHiddenActionsOrGiveFoldedSeatsDecisions() throws Exception {
        var result = FiniteTwoPlayerMaxmin.solve(new MatrixGame(new double[][] {{1, -1}, {-1, 1}}));
        assertEquals(0, result.audit().behavioralQuality().nashConvBb(), 1e-12);
        assertEquals(.5, result.strategy().get("1:hidden-choice").get("0"), 1e-12);
        assertEquals(2, result.strategy().size());
        assertEquals(4, result.audit().constantSum());
        assertEquals(2, result.audit().firstPlans());
        assertEquals(2, result.audit().secondPlans());
        assertThrows(UnsupportedOperationException.class, () -> result.strategy().clear());
    }

    @Test
    void conditionsMixedPlansOnOwnPriorActionsAtLaterInformationSets() throws Exception {
        var result = FiniteTwoPlayerMaxmin.solve(new RecallGame(true));
        assertEquals(.5, result.strategy().get("0:root").get("0"), 1e-12);
        assertEquals(1, result.strategy().get("0:again0").get("0"), 1e-12);
        assertEquals(1, result.strategy().get("0:again1").get("1"), 1e-12);
        assertEquals(0, result.audit().behavioralQuality().nashConvBb(), 1e-12);
        assertEquals(
                List.of(new FiniteTwoPlayerMaxmin.OwnAction("0:root", "0")),
                result.audit().informationSets().stream()
                        .filter(i -> i.key().equals("0:again0"))
                        .findFirst()
                        .orElseThrow()
                        .ownHistory());
        // Averaging all plan mass at again0 would incorrectly include plans that chose root1.
    }

    @Test
    void rejectsImperfectRecallThirdDecisionMakersAndNonConstantSumPayoffs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerMaxmin.solve(new RecallGame(false)));
        var third =
                new RecallGame(true) {
                    public int currentPlayer(List<Integer> s) {
                        return s.size();
                    }
                };
        assertThrows(IllegalArgumentException.class, () -> FiniteTwoPlayerMaxmin.solve(third));
        var general =
                new MatrixGame(new double[][] {{1, -1}, {-1, 1}}) {
                    public double[] terminalUtilities(List<Integer> s) {
                        return new double[] {s.getFirst(), s.getLast(), 0, 0, 0, 0};
                    }
                };
        assertThrows(IllegalArgumentException.class, () -> FiniteTwoPlayerMaxmin.solve(general));
        var inactive =
                new MatrixGame(new double[][] {{1, -1}, {-1, 1}}) {
                    public double[] terminalUtilities(List<Integer> s) {
                        double[] u = super.terminalUtilities(s);
                        u[2] = s.getFirst();
                        return u;
                    }
                };
        assertThrows(IllegalArgumentException.class, () -> FiniteTwoPlayerMaxmin.solve(inactive));
    }

    @Test
    void rejectsInconsistentActionsUnboundedTreesAndExcessivePlanCounts() {
        var inconsistent =
                new MatrixGame(new double[][] {{1, -1}, {-1, 1}}) {
                    public List<String> legalActions(List<Integer> s) {
                        return s.size() == 1 && s.getFirst() == 1
                                ? List.of("1", "0")
                                : super.legalActions(s);
                    }
                };
        assertThrows(
                IllegalArgumentException.class, () -> FiniteTwoPlayerMaxmin.solve(inconsistent));
        var infinite =
                new RecallGame(true) {
                    public boolean isTerminal(List<Integer> s) {
                        return false;
                    }

                    public String informationSet(List<Integer> s) {
                        return s.toString();
                    }
                };
        assertThrows(IllegalArgumentException.class, () -> FiniteTwoPlayerMaxmin.solve(infinite));
        var tooMany =
                new RecallGame(true) {
                    public List<String> legalActions(List<Integer> s) {
                        return List.of("0", "1", "2", "3", "4");
                    }

                    public double[] terminalUtilities(List<Integer> s) {
                        return new double[6];
                    }
                };
        assertThrows(IllegalArgumentException.class, () -> FiniteTwoPlayerMaxmin.solve(tooMany));
    }

    @Test
    void chanceWeightsAreUsedAndMalformedChanceSupportFailsBeforeOptimization() throws Exception {
        class ChanceGame extends MatrixGame {
            final double probability;

            ChanceGame(double probability) {
                super(new double[][] {{1, -1}, {-1, 1}});
                this.probability = probability;
            }

            public List<Integer> initialState() {
                return List.of(-1);
            }

            public boolean isTerminal(List<Integer> s) {
                return s.size() == 3;
            }

            public int currentPlayer(List<Integer> s) {
                return s.getFirst() == -1 ? -1 : s.size() - 1;
            }

            public List<String> legalActions(List<Integer> s) {
                return List.of("0", "1");
            }

            public String informationSet(List<Integer> s) {
                return "hidden-world";
            }

            public double[] terminalUtilities(List<Integer> s) {
                double v = values[s.get(1)][s.get(2)] + (s.get(0) == 0 ? 2 : -2);
                return new double[] {v, -v, 0, 0, 0, 0};
            }

            public List<ChanceOutcome<List<Integer>>> chanceOutcomes(List<Integer> s) {
                return List.of(
                        new ChanceOutcome<>(List.of(0), probability),
                        new ChanceOutcome<>(List.of(1), 1 - probability));
            }
        }
        var chance = new ChanceGame(.75);
        var solved = FiniteTwoPlayerMaxmin.solve(chance);
        assertEquals(1, solved.audit().matrixSolution().lowerValue(), 1e-12);
        var bad = new ChanceGame(2);
        assertThrows(IllegalArgumentException.class, () -> FiniteTwoPlayerMaxmin.solve(bad));
    }
}
