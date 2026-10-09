package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Constant terms, signs, forced actions and perfect-recall dependency ordering. */
class AffineSequenceFormEdgeTest {
    record State(List<Integer> actions) {}

    static final class Game implements MultiPlayerCfrGame<State> {
        final int[] actors, actionCounts;
        final double shift, scale, sum;
        final boolean recall;

        Game(
                int[] actors,
                int[] actionCounts,
                double shift,
                double scale,
                double sum,
                boolean recall) {
            this.actors = actors;
            this.actionCounts = actionCounts;
            this.shift = shift;
            this.scale = scale;
            this.sum = sum;
            this.recall = recall;
        }

        public int playerCount() {
            return 6;
        }

        public State initialState() {
            return new State(List.of());
        }

        public boolean isTerminal(State s) {
            return s.actions().size() == actors.length;
        }

        public int currentPlayer(State s) {
            return actors[s.actions().size()];
        }

        public List<String> legalActions(State s) {
            var result = new ArrayList<String>();
            for (int a = 0; a < actionCounts[s.actions().size()]; a++)
                result.add(Integer.toString(a));
            return result;
        }

        public String informationSet(State s) {
            String key =
                    (s.actions().size() == 0 ? "z-initial" : "a-later") + ":" + s.actions().size();
            if (recall)
                for (int i = 0; i < s.actions().size(); i++)
                    if (actors[i] == currentPlayer(s)) key += ":" + s.actions().get(i);
            return key;
        }

        public State afterAction(State s, String a) {
            var result = new ArrayList<>(s.actions());
            result.add(Integer.parseInt(a));
            return new State(List.copyOf(result));
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State s) {
            throw new AssertionError();
        }

        public double[] terminalUtilities(State s) {
            int row = 0, column = 0;
            for (int i = 0; i < actors.length; i++)
                if (actors[i] == 2) row += s.actions().get(i);
                else column += s.actions().get(i);
            double value = shift + scale * (row % 2 == column % 2 ? 1 : -1);
            return new double[] {-1, -2, value, 3, 0, sum - value};
        }
    }

    @Test
    void shiftedConstantForcedAndRecallGamesAgreeWithIndependentNormalForm() throws Exception {
        var results = new ArrayList<Map<String, Object>>();
        for (boolean reversed : new boolean[] {false, true})
            for (double shift : new double[] {-123.75, -3.25, 0, 7.125, 255.5})
                for (double scale : new double[] {.125, 1, 16}) {
                    var game =
                            new Game(
                                    reversed ? new int[] {2, 5} : new int[] {5, 2},
                                    new int[] {2, 2},
                                    shift,
                                    scale,
                                    4.5,
                                    false);
                    var solved = FiniteTwoPlayerAffineSequenceForm.solve(game);
                    if (Math.abs(solved.audit().lowerValue() - shift) > 1e-8
                            || Math.abs(solved.audit().upperValue() - shift) > 1e-8)
                        throw new AssertionError("Affine constant/sign/actor bounds");
                    double old =
                            FiniteTwoPlayerMaxmin.solve(game).audit().matrixSolution().lowerValue();
                    if (Math.abs(old - shift) > 1e-8)
                        throw new AssertionError("Independent normal form");
                    results.add(
                            Map.of(
                                    "case",
                                    "shifted-matrix",
                                    "shift",
                                    shift,
                                    "scale",
                                    scale,
                                    "rowActsFirst",
                                    reversed,
                                    "value",
                                    solved.audit().lowerValue()));
                }
        for (int depth : new int[] {2, 4, 16, 32}) {
            int[] actors = new int[depth], counts = new int[depth];
            for (int i = 0; i < depth; i++) {
                actors[i] = i % 2 == 0 ? 5 : 2;
                counts[i] = 1;
            }
            var game = new Game(actors, counts, -5.25, 0, 1.5, true);
            var solved = FiniteTwoPlayerAffineSequenceForm.solve(game);
            if (Math.abs(solved.audit().lowerValue() + 5.25) > 1e-8)
                throw new AssertionError("Zero independent variables");
            results.add(
                    Map.of(
                            "case",
                            "all-forced",
                            "depth",
                            depth,
                            "value",
                            solved.audit().lowerValue()));
        }
        for (int[] counts :
                List.of(
                        new int[] {1, 2},
                        new int[] {2, 1},
                        new int[] {2, 2, 1},
                        new int[] {2, 1, 2},
                        new int[] {1, 2, 2},
                        new int[] {2, 2, 2},
                        new int[] {3, 2, 2, 1})) {
            int[] actors = new int[counts.length];
            for (int i = 0; i < actors.length; i++) actors[i] = i % 2 == 0 ? 2 : 5;
            var game = new Game(actors, counts, -2.75, .75, 6.25, true);
            var solved = FiniteTwoPlayerAffineSequenceForm.solve(game);
            double old = FiniteTwoPlayerMaxmin.solve(game).audit().matrixSolution().lowerValue();
            if (Math.abs(old - solved.audit().lowerValue()) > 1e-8)
                throw new AssertionError("Forced/recall/lexical-order control");
            results.add(
                    Map.of(
                            "case",
                            "mixed-forced-and-recall",
                            "actions",
                            counts,
                            "value",
                            solved.audit().lowerValue()));
        }
        assertEquals(41, results.size());
    }
}
