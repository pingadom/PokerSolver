package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/** Synthetic capacity research: synthetic exact shares, no poker strategy artifact or admission. */
class AffineSequenceFormCapacityTest {
    record State(int firstType, int secondType, String history) {}

    static final class Game implements MultiPlayerCfrGame<State> {
        final int types;
        final double[][] share;
        final double bet;

        Game(int types, int mode, double multiplier) {
            this.types = types;
            bet = 6.5 * multiplier;
            share = new double[types][types];
            var random = new SplittableRandom(261L + types);
            for (int a = 0; a < types; a++)
                for (int b = 0; b < types; b++) {
                    double value =
                            switch (mode) {
                                case 0 -> random.nextInt(1, 100) / 100.0;
                                case 1 ->
                                        .5
                                                + .85 * (a - b) / Math.max(1.0, types - 1)
                                                + .2 * (random.nextDouble() - .5);
                                case 2 -> a % 3 == b % 3 ? .5 : (a % 3 + 1) % 3 == b % 3 ? .1 : .9;
                                default -> throw new AssertionError();
                            };
                    share[a][b] = Math.max(.01, Math.min(.99, value));
                }
        }

        public int playerCount() {
            return 6;
        }

        public State initialState() {
            return new State(-1, -1, "");
        }

        public boolean isTerminal(State s) {
            return Set.of("kk", "bf", "bc", "kbf", "kbc").contains(s.history());
        }

        public int currentPlayer(State s) {
            return s.firstType() < 0
                    ? -1
                    : s.history().equals("") || s.history().equals("kb") ? 5 : 2;
        }

        public List<String> legalActions(State s) {
            return s.history().equals("") || s.history().equals("k")
                    ? List.of("k", "b")
                    : List.of("f", "c");
        }

        public String informationSet(State s) {
            return "research-one-bet:"
                    + s.history()
                    + ":type:"
                    + (currentPlayer(s) == 2 ? s.firstType() : s.secondType());
        }

        public State afterAction(State s, String a) {
            return new State(s.firstType(), s.secondType(), s.history() + a);
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State s) {
            var roots = new ArrayList<ChanceOutcome<State>>();
            for (int a = 0; a < types; a++)
                for (int b = 0; b < types; b++)
                    roots.add(new ChanceOutcome<>(new State(a, b, ""), 1.0 / types / types));
            return List.copyOf(roots);
        }

        public double[] terminalUtilities(State s) {
            double v =
                    switch (s.history()) {
                        case "kk" -> share[s.firstType()][s.secondType()] * 6.5 - 3;
                        case "bc", "kbc" ->
                                share[s.firstType()][s.secondType()] * (6.5 + 2 * bet) - (3 + bet);
                        case "bf" -> -3;
                        case "kbf" -> 3.5;
                        default -> throw new IllegalArgumentException();
                    };
            return new double[] {0, 0, v, -.5, 0, .5 - v};
        }
    }

    @Test
    void allFiftyFourStructuredOneBetGamesSolveWithinUnchangedLimits() throws Exception {
        int cases = 0;
        for (int mode = 0; mode < 3; mode++)
            for (double bet : new double[] {.5, 1, 2})
                for (int types : new int[] {2, 4, 8, 16, 24, 32}) {
                    var game = new Game(types, mode, bet);
                    var solved = FiniteTwoPlayerAffineSequenceForm.solve(game);
                    assertEquals(1 + 4 * types, solved.audit().firstFlow().sequences().size());
                    assertEquals(4 * types, solved.audit().firstLp().variables());
                    assertTrue(solved.audit().behavioralQuality().nashConvBb() < 1e-8);
                    assertTrue(
                            solved.audit().firstLp().work().arithmeticWork()
                                    <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
                    assertTrue(
                            solved.audit().secondLp().work().arithmeticWork()
                                    <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
                    assertTrue(
                            solved.audit().reductionWork().chargedUnits()
                                    <= FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK);
                    if (types == 2)
                        assertEquals(
                                FiniteTwoPlayerMaxmin.solve(game)
                                        .audit()
                                        .matrixSolution()
                                        .lowerValue(),
                                solved.audit().lowerValue(),
                                1e-8);
                    cases++;
                }
        assertEquals(54, cases);
    }

    record Controls(
            String schemaVersion, String oracle, List<BoundedLinearProgramTest.Problem> problems) {}

    @Test
    void allOneHundredSixtyReducedProgramsMatchTheIndependentOriginalInequalityOracle()
            throws Exception {
        Controls controls;
        try (var input =
                new GZIPInputStream(
                        Objects.requireNonNull(
                                getClass()
                                        .getResourceAsStream(
                                                "/affine-sequence-form-lp-controls.json.gz")))) {
            controls = SixMaxTexturePayoffTable.mapper().readValue(input, Controls.class);
        }
        assertEquals("independent-affine-sequence-form-lp-controls/v1", controls.schemaVersion());
        assertEquals(160, controls.problems().size());
        for (var p : controls.problems()) {
            BoundedLinearProgramTest.check(p, p.matrix(), p.rhs());
            var order = new ArrayList<Integer>();
            for (int i = 0; i < p.rhs().length; i++) order.add(i);
            Collections.shuffle(order, new Random(261L + p.id()));
            var matrix = new double[order.size()][];
            var rhs = new double[order.size()];
            for (int i = 0; i < order.size(); i++) {
                double scale = Math.scalb(1.0, i % 9 - 4);
                matrix[i] = p.matrix()[order.get(i)].clone();
                for (int j = 0; j < matrix[i].length; j++) matrix[i][j] *= scale;
                rhs[i] = p.rhs()[order.get(i)] * scale;
            }
            BoundedLinearProgramTest.check(p, matrix, rhs);
        }
    }
}
