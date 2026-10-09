package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Synthetic fixed-size preflop raise-tree research: no cards, ranges, rake or admission. */
class AffineMultiRaiseTest {
    record State(int firstType, int secondType, List<String> history) {}

    static final class Game implements MultiPlayerCfrGame<State> {
        private final int types, mode;
        private final double[][] share;
        private final double[] sizes;

        Game(int types, int mode, double[] sizes) {
            this.types = types;
            this.mode = mode;
            this.sizes = sizes.clone();
            share = new double[types][types];
            var random = new SplittableRandom(261 + types);
            for (int a = 0; a < types; a++)
                for (int b = 0; b < types; b++)
                    share[a][b] =
                            switch (mode) {
                                case 0 -> random.nextInt(1, 100) / 100.0;
                                case 1 ->
                                        Math.max(
                                                .01,
                                                Math.min(
                                                        .99,
                                                        .5
                                                                + .45
                                                                        * (a - b)
                                                                        / Math.max(1, types - 1)));
                                case 2 -> a % 3 == b % 3 ? .5 : (a % 3 + 1) % 3 == b % 3 ? .1 : .9;
                                default -> throw new AssertionError();
                            };
        }

        public int playerCount() {
            return 6;
        }

        public State initialState() {
            return new State(-1, -1, List.of());
        }

        public boolean isTerminal(State s) {
            return !s.history().isEmpty() && !s.history().getLast().equals("raise");
        }

        public int currentPlayer(State s) {
            return s.firstType() < 0 ? -1 : s.history().size() % 2 == 0 ? 5 : 2;
        }

        public List<String> legalActions(State s) {
            return s.history().size() < sizes.length
                    ? List.of("fold", "call", "raise")
                    : List.of("fold", "call");
        }

        public String informationSet(State s) {
            return "synthetic-preflop:"
                    + String.join("-", s.history())
                    + ":type:"
                    + (currentPlayer(s) == 2 ? s.firstType() : s.secondType());
        }

        public State afterAction(State s, String action) {
            var next = new ArrayList<>(s.history());
            next.add(action);
            return new State(s.firstType(), s.secondType(), List.copyOf(next));
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State s) {
            var weights = new double[types][types];
            double total = 0;
            for (int a = 0; a < types; a++)
                for (int b = 0; b < types; b++) {
                    weights[a][b] = 1 + (a == b ? types : 0);
                    total += weights[a][b];
                }
            var result = new ArrayList<ChanceOutcome<State>>();
            for (int a = 0; a < types; a++)
                for (int b = 0; b < types; b++)
                    result.add(
                            new ChanceOutcome<>(new State(a, b, List.of()), weights[a][b] / total));
            return List.copyOf(result);
        }

        public double[] terminalUtilities(State s) {
            double first = 2.5, second = 1;
            int actor = -1;
            for (int step = 0; step < s.history().size(); step++) {
                actor = step % 2 == 0 ? 5 : 2;
                String action = s.history().get(step);
                if (action.equals("raise")) {
                    if (actor == 2) first = sizes[step];
                    else second = sizes[step];
                }
                if (action.equals("call")) {
                    double matched = Math.max(first, second);
                    first = matched;
                    second = matched;
                }
            }
            boolean fold = s.history().getLast().equals("fold");
            double value =
                    fold
                            ? (actor == 2 ? -first : second + .5)
                            : share[s.firstType()][s.secondType()] * (first + second + .5) - first;
            return new double[] {0, 0, value, -.5, 0, .5 - value};
        }
    }

    record Expected(String id, double lower, double upper) {}

    @Test
    void syntheticRaiseTreesMatchIndependentOriginalFullFlowOracleAndRejectTheSequenceCap()
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        List<Expected> expected;
        try (var input =
                Objects.requireNonNull(
                        getClass().getResourceAsStream("/affine-multiraise-values.json"))) {
            expected =
                    mapper.readValue(
                            input,
                            mapper.getTypeFactory()
                                    .constructCollectionType(List.class, Expected.class));
        }
        assertEquals(15, expected.size());
        for (var control : expected) {
            var parts = control.id().split(":");
            int mode = Integer.parseInt(parts[2]), types = Integer.parseInt(parts[4]);
            var game = new Game(types, mode, new double[] {8, 22, 100});
            var solved = FiniteTwoPlayerAffineSequenceForm.solve(game);
            assertEquals(control.lower(), solved.audit().lowerValue(), 1e-8, control.id());
            assertEquals(control.upper(), solved.audit().upperValue(), 1e-8, control.id());
            assertTrue(solved.audit().behavioralQuality().nashConvBb() < 1e-8);
            if (types == 1)
                assertEquals(
                        FiniteTwoPlayerMaxmin.solve(game).audit().matrixSolution().lowerValue(),
                        control.lower(),
                        1e-8);
            if (types == 2)
                assertThrows(
                        IllegalArgumentException.class, () -> FiniteTwoPlayerMaxmin.solve(game));
        }
        for (int mode = 0; mode < 3; mode++) {
            var game = new Game(24, mode, new double[] {8, 22, 100});
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FiniteTwoPlayerAffineSequenceForm.solve(game));
        }
    }
}
