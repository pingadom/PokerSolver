package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerAffineSequenceFormTest {
    @Test
    void kuhnPokerRecoversTheKnownValueAndAnIndependentBestResponseCertificate() throws Exception {
        var kuhn = new KuhnPoker();
        var game =
                new MultiPlayerCfrGame<KuhnPoker.State>() {
                    public int playerCount() {
                        return 2;
                    }

                    public KuhnPoker.State initialState() {
                        return kuhn.initialState();
                    }

                    public boolean isTerminal(KuhnPoker.State s) {
                        return kuhn.isTerminal(s);
                    }

                    public double[] terminalUtilities(KuhnPoker.State s) {
                        double v = kuhn.terminalUtility(s);
                        return new double[] {v, -v};
                    }

                    public int currentPlayer(KuhnPoker.State s) {
                        return kuhn.currentPlayer(s);
                    }

                    public List<String> legalActions(KuhnPoker.State s) {
                        return kuhn.legalActions(s);
                    }

                    public String informationSet(KuhnPoker.State s) {
                        return kuhn.informationSet(s);
                    }

                    public KuhnPoker.State afterAction(KuhnPoker.State s, String a) {
                        return kuhn.afterAction(s, a);
                    }

                    public List<ChanceOutcome<KuhnPoker.State>> chanceOutcomes(KuhnPoker.State s) {
                        return kuhn.chanceOutcomes(s);
                    }
                };
        var solved = FiniteTwoPlayerAffineSequenceForm.solve(game);
        assertEquals(-1.0 / 18, solved.audit().lowerValue(), 1e-10);
        assertEquals(
                0,
                MultiPlayerInformationSetBestResponse.assess(
                                game, new CfrSolution(1, solved.strategy()))
                        .nashConvBb(),
                1e-10);
        assertEquals(
                FiniteTwoPlayerMaxmin.solve(game).audit().matrixSolution().lowerValue(),
                solved.audit().lowerValue(),
                1e-9);
    }

    private static class MatrixGame implements MultiPlayerCfrGame<List<Integer>> {
        int terminalCalls, initialCalls;

        public int playerCount() {
            return 6;
        }

        public List<Integer> initialState() {
            initialCalls++;
            return List.of();
        }

        public boolean isTerminal(List<Integer> s) {
            return s.size() == 2;
        }

        public double[] terminalUtilities(List<Integer> s) {
            terminalCalls++;
            double v = s.getFirst().equals(s.getLast()) ? 1 : -1;
            return new double[] {v, 4 - v, -1, -1, -1, -1};
        }

        public int currentPlayer(List<Integer> s) {
            return s.size();
        }

        public List<String> legalActions(List<Integer> s) {
            return List.of("0", "1");
        }

        public String informationSet(List<Integer> s) {
            return "hidden-choice";
        }

        public List<Integer> afterAction(List<Integer> s, String a) {
            var next = new ArrayList<>(s);
            next.add(Integer.parseInt(a));
            return List.copyOf(next);
        }

        public List<ChanceOutcome<List<Integer>>> chanceOutcomes(List<Integer> s) {
            throw new AssertionError();
        }
    }

    private static class Recall extends MatrixGame {
        final boolean recall;

        Recall(boolean recall) {
            this.recall = recall;
        }

        public boolean isTerminal(List<Integer> s) {
            return s.size() == 3;
        }

        public int currentPlayer(List<Integer> s) {
            return s.size() == 1 ? 1 : 0;
        }

        public String informationSet(List<Integer> s) {
            return s.isEmpty()
                    ? "root"
                    : s.size() == 1 ? "villain" : "again" + (recall ? s.getFirst() : "");
        }

        public double[] terminalUtilities(List<Integer> s) {
            double v = s.getFirst().equals(s.getLast()) ? (s.get(0).equals(s.get(1)) ? 1 : -1) : -2;
            return new double[] {v, -v, 0, 0, 0, 0};
        }
    }

    record State(int firstType, int secondType, int stage, int firstAction, int secondAction) {}

    /** Joint type worlds are explicit; opponent action and type never enter the player's key. */
    static class Hidden implements MultiPlayerCfrGame<State> {
        final int types;
        final int[][][][] values;

        Hidden(int types) {
            this.types = types;
            values = new int[types][types][2][2];
            var random = new SplittableRandom(261L + types);
            for (int a = 0; a < types; a++)
                for (int b = 0; b < types; b++)
                    for (int i = 0; i < 2; i++)
                        for (int j = 0; j < 2; j++) values[a][b][i][j] = random.nextInt(-9, 10);
        }

        public int playerCount() {
            return 2;
        }

        public State initialState() {
            return new State(-1, -1, 0, -1, -1);
        }

        public boolean isTerminal(State s) {
            return s.stage() == 3;
        }

        public double[] terminalUtilities(State s) {
            double v = values[s.firstType()][s.secondType()][s.firstAction()][s.secondAction()];
            return new double[] {v, -v};
        }

        public int currentPlayer(State s) {
            return s.stage() == 0 ? -1 : s.stage() == 1 ? 0 : 1;
        }

        public List<String> legalActions(State s) {
            return List.of("a", "b");
        }

        public String informationSet(State s) {
            return "hidden:type:" + (s.stage() == 1 ? s.firstType() : s.secondType());
        }

        public State afterAction(State s, String a) {
            int action = a.equals("a") ? 0 : 1;
            return s.stage() == 1
                    ? new State(s.firstType(), s.secondType(), 2, action, -1)
                    : new State(s.firstType(), s.secondType(), 3, s.firstAction(), action);
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State s) {
            var result = new ArrayList<ChanceOutcome<State>>();
            for (int a = 0; a < types; a++)
                for (int b = 0; b < types; b++)
                    result.add(
                            new ChanceOutcome<>(new State(a, b, 1, -1, -1), 1.0 / types / types));
            return List.copyOf(result);
        }
    }

    @Test
    void hiddenChoiceUsesAnImmutableSnapshotAndKeepsFoldedSeatUtilities() throws Exception {
        var game = new MatrixGame();
        var result = FiniteTwoPlayerAffineSequenceForm.solve(game);
        assertEquals(1, game.initialCalls);
        assertEquals(4, game.terminalCalls); // Independent checks use the snapshot, not the input.
        assertEquals(4, result.audit().constantSum());
        assertEquals(0, result.audit().lowerValue(), 1e-12);
        assertEquals(.5, result.strategy().get("1:hidden-choice").get("0"), 1e-12);
        assertEquals(
                List.of(0.0, 4.0, -1.0, -1.0, -1.0, -1.0),
                result.audit().behavioralQuality().profileUtilitiesBb());
        assertEquals(3, result.audit().firstFlow().sequences().size());
        assertEquals(0, result.audit().behavioralQuality().nashConvBb(), 1e-12);
        assertThrows(UnsupportedOperationException.class, () -> result.strategy().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.strategy().get("0:hidden-choice").clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.audit().firstFlow().realization().clear());
        assertTrue(
                Arrays.stream(
                                FiniteTwoPlayerAffineSequenceForm.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(k -> java.lang.reflect.Modifier.isPrivate(k.getModifiers())));
    }

    @Test
    void ownPriorActionsDetermineRealizationConservationAndBehavioralRatios() throws Exception {
        var result = FiniteTwoPlayerAffineSequenceForm.solve(new Recall(true));
        assertEquals(.5, result.strategy().get("0:root").get("0"), 1e-12);
        assertEquals(1, result.strategy().get("0:again0").get("0"), 1e-12);
        assertEquals(1, result.strategy().get("0:again1").get("1"), 1e-12);
        var info =
                result.audit().informationSets().stream()
                        .filter(i -> i.key().equals("0:again0"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                List.of(new FiniteTwoPlayerSequenceForm.OwnAction("0:root", "0")),
                info.ownHistory());
        assertEquals(0, result.audit().firstFlow().maximumResidual(), 1e-12);
        assertEquals(
                FiniteTwoPlayerMaxmin.solve(new Recall(true)).audit().matrixSolution().lowerValue(),
                result.audit().lowerValue(),
                1e-9);
    }

    @Test
    void largeHiddenGamesMatchIndependentHighsValuesWithoutEnumeratingExponentialPlans()
            throws Exception {
        int[] counts = {2, 4, 6, 8, 16, 32};
        double[] expected = {
            1.5833333333333335,
            -.950678913738019,
            -.26879084967320255,
            .3606807489998519,
            .30452264072265106,
            -.09824686288415223
        };
        for (int i = 0; i < counts.length; i++) {
            var game = new Hidden(counts[i]);
            var result = FiniteTwoPlayerAffineSequenceForm.solve(game);
            assertEquals(expected[i], result.audit().lowerValue(), 1e-8);
            assertEquals(expected[i], result.audit().upperValue(), 1e-8);
            assertEquals(2 * counts[i] + 1, result.audit().firstFlow().sequences().size());
            assertEquals(2 * counts[i], result.strategy().size());
            assertTrue(result.audit().behavioralQuality().nashConvBb() < 1e-8);
            if (counts[i] <= 6)
                assertEquals(
                        FiniteTwoPlayerMaxmin.solve(game).audit().matrixSolution().lowerValue(),
                        result.audit().lowerValue(),
                        1e-8);
            else
                assertThrows(
                        IllegalArgumentException.class, () -> FiniteTwoPlayerMaxmin.solve(game));
        }
        // 32 private types give 2^32 complete plans per player, but only 65 own sequences.
    }

    @Test
    void jointChanceCorrelationIsPreservedRatherThanMultiplyingPrivateMarginals() throws Exception {
        class Correlated extends Hidden {
            final boolean independent;

            Correlated(boolean independent) {
                super(2);
                this.independent = independent;
            }

            public double[] terminalUtilities(State s) {
                double v =
                        (s.firstAction() == s.secondAction() ? 1 : -1)
                                + (s.firstType() == s.secondType() ? 2 : -2);
                return new double[] {v, -v};
            }

            public List<ChanceOutcome<State>> chanceOutcomes(State s) {
                if (!independent)
                    return List.of(
                            new ChanceOutcome<>(new State(0, 0, 1, -1, -1), .75),
                            new ChanceOutcome<>(new State(1, 1, 1, -1, -1), .25));
                return List.of(
                        new ChanceOutcome<>(new State(0, 0, 1, -1, -1), .5625),
                        new ChanceOutcome<>(new State(0, 1, 1, -1, -1), .1875),
                        new ChanceOutcome<>(new State(1, 0, 1, -1, -1), .1875),
                        new ChanceOutcome<>(new State(1, 1, 1, -1, -1), .0625));
            }
        }
        var joint = FiniteTwoPlayerAffineSequenceForm.solve(new Correlated(false));
        var product = FiniteTwoPlayerAffineSequenceForm.solve(new Correlated(true));
        assertEquals(2, joint.audit().lowerValue(), 1e-12);
        assertEquals(.5, product.audit().lowerValue(), 1e-12);
        assertNotEquals(joint.audit().snapshotHash(), product.audit().snapshotHash());
        assertNotEquals(joint.audit().affineReductionHash(), product.audit().affineReductionHash());
    }

    @Test
    void zeroOwnReachStillHasACompleteLegalStrategyAndBestResponseCheck() throws Exception {
        var game =
                new Recall(true) {
                    public double[] terminalUtilities(List<Integer> s) {
                        double v = s.getFirst() == 0 ? 5 : (s.get(1).equals(s.get(2)) ? 1 : -1);
                        return new double[] {v, -v, 0, 0, 0, 0};
                    }
                };
        var result = FiniteTwoPlayerAffineSequenceForm.solve(game);
        assertEquals(1, result.strategy().get("0:root").get("0"), 1e-12);
        assertEquals(Map.of("0", .5, "1", .5), result.strategy().get("0:again1"));
        assertEquals(5, result.audit().lowerValue(), 1e-12);
        assertEquals(0, result.audit().behavioralQuality().nashConvBb(), 1e-12);
    }

    @Test
    void sequenceAndInformationSetBudgetsAreSeparateFromTheExpandedTreeBudget() throws Exception {
        class Wide extends Hidden {
            final int choices;

            Wide(int types, int choices) {
                super(types);
                this.choices = choices;
            }

            public List<String> legalActions(State s) {
                return java.util.stream.IntStream.range(0, s.stage() == 1 ? choices : 1)
                        .mapToObj(Integer::toString)
                        .toList();
            }

            public String informationSet(State s) {
                return s.stage() == 1 ? "own-type:" + s.firstType() : "villain";
            }

            public State afterAction(State s, String a) {
                return s.stage() == 1
                        ? new State(s.firstType(), 0, 2, Integer.parseInt(a), -1)
                        : new State(s.firstType(), 0, 3, s.firstAction(), 0);
            }

            public double[] terminalUtilities(State s) {
                return new double[] {-s.firstAction(), s.firstAction()};
            }

            public List<ChanceOutcome<State>> chanceOutcomes(State s) {
                return java.util.stream.IntStream.range(0, types)
                        .mapToObj(t -> new ChanceOutcome<>(new State(t, 0, 1, -1, -1), 1.0 / types))
                        .toList();
            }
        }
        var maximum = FiniteTwoPlayerAffineSequenceForm.solve(new Wide(8, 16));
        assertEquals(129, maximum.audit().firstFlow().sequences().size());
        assertEquals(0, maximum.audit().lowerValue(), 1e-12);
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerAffineSequenceForm.solve(new Wide(9, 16)));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerAffineSequenceForm.solve(new Wide(65, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerAffineSequenceForm.solve(new Wide(1, 17)));
    }

    @Test
    void forgettingEarlierPrivateInformationIsRejectedEvenWhenTheActionWasRemembered() {
        var game =
                new Hidden(2) {
                    public boolean isTerminal(State s) {
                        return s.stage() == 4;
                    }

                    public int currentPlayer(State s) {
                        return s.stage() == 3 ? 0 : super.currentPlayer(s);
                    }

                    public String informationSet(State s) {
                        return s.stage() == 3
                                ? "forgot-type:own-action:" + s.firstAction()
                                : super.informationSet(s);
                    }

                    public State afterAction(State s, String a) {
                        if (s.stage() == 3)
                            return new State(
                                    s.firstType(),
                                    s.secondType(),
                                    4,
                                    s.firstAction(),
                                    s.secondAction());
                        return super.afterAction(s, a);
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerAffineSequenceForm.solve(game));
    }

    @Test
    void repeatedSolvesAreDeterministicAndSnapshotIdentityIncludesAllPayoffs() throws Exception {
        var first = FiniteTwoPlayerAffineSequenceForm.solve(new MatrixGame());
        assertEquals(
                first.audit(), FiniteTwoPlayerAffineSequenceForm.solve(new MatrixGame()).audit());
        var changed =
                FiniteTwoPlayerAffineSequenceForm.solve(
                        new MatrixGame() {
                            public double[] terminalUtilities(List<Integer> s) {
                                var u = super.terminalUtilities(s);
                                u[0] += 1;
                                u[1] -= 1;
                                return u;
                            }
                        });
        assertNotEquals(first.audit().snapshotHash(), changed.audit().snapshotHash());
        assertNotEquals(first.audit().affineReductionHash(), changed.audit().affineReductionHash());
        assertEquals(first.audit().lowerValue() + 1, changed.audit().lowerValue(), 1e-12);
    }

    @Test
    void imperfectRecallThirdActorsAndNonConstantOrNonfinitePayoffsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerAffineSequenceForm.solve(new Recall(false)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerAffineSequenceForm.solve(
                                new Recall(true) {
                                    public int currentPlayer(List<Integer> s) {
                                        return s.size();
                                    }
                                }));
        for (int position : new int[] {0, 2})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerAffineSequenceForm.solve(
                                    new MatrixGame() {
                                        public double[] terminalUtilities(List<Integer> s) {
                                            var u = super.terminalUtilities(s);
                                            u[position] += s.getFirst();
                                            return u;
                                        }
                                    }));
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 1_000_001})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerAffineSequenceForm.solve(
                                    new MatrixGame() {
                                        public double[] terminalUtilities(List<Integer> s) {
                                            return new double[] {bad, -bad, 0, 0, 0, 0};
                                        }
                                    }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerAffineSequenceForm.solve(
                                new MatrixGame() {
                                    public double[] terminalUtilities(List<Integer> s) {
                                        return new double[2];
                                    }
                                }));
    }

    @Test
    void malformedActionsChanceLabelsAndUnboundedTreesFailBeforeOptimization() {
        for (List<String> actions : List.of(List.<String>of(), List.of("0", "0"), List.of("", "1")))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerAffineSequenceForm.solve(
                                    new MatrixGame() {
                                        public List<String> legalActions(List<Integer> s) {
                                            return actions;
                                        }
                                    }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerAffineSequenceForm.solve(
                                new MatrixGame() {
                                    public String informationSet(List<Integer> s) {
                                        return "x".repeat(1025);
                                    }
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerAffineSequenceForm.solve(
                                new MatrixGame() {
                                    public List<String> legalActions(List<Integer> s) {
                                        return s.size() == 1 && s.getFirst() == 1
                                                ? List.of("1", "0")
                                                : super.legalActions(s);
                                    }
                                }));
        for (double p : new double[] {0, -1, Double.NaN, .1, 2})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerAffineSequenceForm.solve(
                                    new Hidden(2) {
                                        public List<ChanceOutcome<State>> chanceOutcomes(State s) {
                                            return List.of(
                                                    new ChanceOutcome<>(
                                                            new State(0, 0, 1, -1, -1), p));
                                        }
                                    }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerAffineSequenceForm.solve(
                                new Recall(true) {
                                    public boolean isTerminal(List<Integer> s) {
                                        return false;
                                    }

                                    public String informationSet(List<Integer> s) {
                                        return s.toString();
                                    }
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerAffineSequenceForm.solve(new Hidden(128)));
    }
}
