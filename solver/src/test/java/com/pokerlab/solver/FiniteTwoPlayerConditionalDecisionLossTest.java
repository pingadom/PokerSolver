package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervalsTest.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerConditionalDecisionLossTest {
    // The opponent's binary choice is concealed; hero must use one continuation across both worlds.
    static Snapshot correlated(int hero, int mode) {
        int opponent = hero == 3 ? 5 : 3;
        var hidden = new ArrayList<Node>();
        for (int x = 0; x < 2; x++) {
            Node a, b;
            if (mode == 0) {
                a = terminal(hero, -2 - x);
                b = terminal(hero, -2.1 - x);
            } else if (mode == 1) {
                a =
                        decision(
                                hero,
                                "after:a",
                                List.of("left", "right"),
                                terminal(hero, -2 - x),
                                terminal(hero, -3 + x));
                b =
                        decision(
                                hero,
                                "after:b",
                                List.of("left", "right"),
                                terminal(hero, -1.9 - x),
                                terminal(hero, -2.9 + x));
            } else {
                a =
                        decision(
                                hero,
                                "after:a",
                                List.of("left", "right"),
                                terminal(hero, -3 + x),
                                terminal(hero, -2 - x));
                b = terminal(hero, -2.4);
            }
            hidden.add(decision(hero, "question", List.of("a", "b"), a, b));
        }
        return new Snapshot(
                6,
                decision(
                        hero,
                        "root",
                        List.of("enter", "exit"),
                        decision(
                                opponent,
                                "secret",
                                List.of("left", "right"),
                                hidden.toArray(Node[]::new)),
                        terminal(hero, 0)));
    }

    static FiniteTwoPlayerConditionalDecisionLoss.Question question(int hero) {
        return new FiniteTwoPlayerConditionalDecisionLoss.Question(hero, hero + ":question");
    }

    @Test
    void sharedMovementGivesZeroLossDespiteOverlappingIndividualEvIntervals() throws Exception {
        var audit =
                FiniteTwoPlayerConditionalDecisionLoss.solve(correlated(5, 0), question(5)).audit();
        var a = audit.moves().get(0);
        var b = audit.moves().get(1);
        assertEquals(0, a.lowerLoss(), 1e-10);
        assertEquals(0, a.upperLoss(), 1e-10);
        assertEquals(.9, a.conservativeUpperLoss(), 1e-10);
        assertEquals(.1, b.lowerLoss(), 1e-10);
        assertEquals(.1, b.upperLoss(), 1e-10);
        assertTrue(a.upperControls().stream().allMatch(c -> c.objective() < 0));
        assertFalse(audit.trainerAdmission());
    }

    @Test
    void selectedAndOtherMultipleContinuationsAreOptimizedTogetherForEitherActor()
            throws Exception {
        for (int hero : List.of(3, 5)) {
            var audit =
                    FiniteTwoPlayerConditionalDecisionLoss.solve(
                                    correlated(hero, 1), question(hero))
                            .audit();
            var a = audit.moves().get(0);
            var b = audit.moves().get(1);
            assertEquals(.1, a.lowerLoss(), 1e-10);
            assertEquals(.1, a.upperLoss(), 1e-10);
            assertEquals(.6, a.conservativeUpperLoss(), 1e-10);
            assertEquals(0, a.conservativeLowerLoss(), 1e-10);
            assertEquals(0, b.lowerLoss(), 1e-10);
            assertEquals(0, b.upperLoss(), 1e-10);
            assertEquals(2, a.lowerControls().size());
            assertEquals(2, a.upperControls().size());
            assertTrue(
                    audit.actions().stream()
                            .allMatch(r -> r.interval().numeratorPlans().size() == 2));
        }
    }

    @Test
    void maxOfMinUpperUsesOneSelectedContinuationAcrossHiddenWorlds() throws Exception {
        var audit =
                FiniteTwoPlayerConditionalDecisionLoss.solve(correlated(5, 2), question(5)).audit();
        var a = audit.moves().get(0);
        var b = audit.moves().get(1);
        assertEquals(0, a.lowerLoss(), 1e-10);
        assertEquals(.1, a.upperLoss(), 1e-10);
        assertEquals(0, b.lowerLoss(), 1e-10);
        assertEquals(.4, b.upperLoss(), 1e-10);
        var witness = a.upperControls().get(a.upperControl()).witness();
        assertEquals(.5, witness.selected().posterior().get(0).probability(), 1e-10);
        assertEquals(-2.5, witness.actionUtilities().get("a"), 1e-10);
        assertEquals(-2.4, witness.actionUtilities().get("b"), 1e-10);
    }

    @Test
    void everyWitnessRecomputesActualLossAndWinningControlsMatchBothEndpoints() throws Exception {
        var audit =
                FiniteTwoPlayerConditionalDecisionLoss.solve(correlated(5, 1), question(5)).audit();
        for (var move : audit.moves()) {
            var controls = new ArrayList<>(move.lowerControls());
            controls.addAll(move.upperControls());
            for (var c : controls) {
                var w = c.witness();
                assertEquals(
                        Math.max(
                                0,
                                Collections.max(w.actionUtilities().values())
                                        - w.actionUtilities().get(move.action())),
                        w.decisionLoss());
                assertEquals(1, w.selected().questionReach(), 1e-10);
                assertTrue(w.selected().globalHeroBestResponse() <= 2e-8);
                assertTrue(w.selected().posteriorTotalVariation() <= 1e-8);
            }
            assertEquals(
                    move.lowerLoss(),
                    move.lowerControls().get(move.lowerControl()).witness().decisionLoss(),
                    1e-10);
            assertEquals(
                    move.upperLoss(),
                    move.upperControls().get(move.upperControl()).witness().decisionLoss(),
                    1e-10);
        }
    }

    @Test
    void unreachableQuestionStillRejectsRatherThanShrinkingTheFace() {
        var error =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () ->
                                FiniteTwoPlayerConditionalDecisionLoss.solve(
                                        FiniteTwoPlayerConditionalActionIntervalsTest.posteriorGame(
                                                5, false, false),
                                        question(5)));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                error.reason());
    }

    @Test
    void oneLegalMoveHasZeroLossWithoutInventingJointLpCertificates() throws Exception {
        var game =
                new Snapshot(
                        6,
                        decision(
                                3,
                                "past",
                                List.of("go"),
                                decision(5, "question", List.of("only"), terminal(5, -2))));
        var r = FiniteTwoPlayerConditionalDecisionLoss.solve(game, question(5)).audit();
        var m = r.moves().getFirst();
        assertEquals(0, m.lowerLoss());
        assertEquals(0, m.upperLoss());
        assertEquals(-1, m.lowerControl());
        assertEquals(-1, m.upperControl());
        assertTrue(m.lowerControls().isEmpty());
        assertTrue(m.upperControls().isEmpty());
        assertEquals(4, r.work().intervalLpSolves()); // Existing reach + action interval controls.
    }

    @Test
    void allIntervalAndJointControlsShareTheOriginalCompilerPivotAndArithmeticCaps()
            throws Exception {
        var game = correlated(5, 1);
        var q = question(5);
        var work = FiniteTwoPlayerConditionalDecisionLoss.solve(game, q).audit().work();
        assertEquals(18, work.intervalLpSolves()); // Two 5-LP intervals and eight joint controls.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalDecisionLoss.solve(
                                game,
                                q,
                                1e-8,
                                .01,
                                work.compilerUnits() - 1,
                                BoundedLinearProgram.MAX_PIVOTS,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerConditionalDecisionLoss.solve(
                                game,
                                q,
                                1e-8,
                                .01,
                                8_000_000,
                                work.intervalLpPivots() - 1,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerConditionalDecisionLoss.solve(
                                game,
                                q,
                                1e-8,
                                .01,
                                8_000_000,
                                BoundedLinearProgram.MAX_PIVOTS,
                                work.intervalLpArithmeticWork() - 1));
    }

    @Test
    void invalidQuestionsLimitsAndCallerMutationCannotCreateAnOwnedResult() throws Exception {
        var game = correlated(5, 0);
        var q = question(5);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalDecisionLoss.solve(
                                game,
                                new FiniteTwoPlayerConditionalDecisionLoss.Question(
                                        3, "3:question")));
        for (double value : List.of(0.0, Double.NaN, Double.POSITIVE_INFINITY, 1.01))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FiniteTwoPlayerConditionalDecisionLoss.solve(game, q, 1e-8, value));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerConditionalDecisionLoss.solve(game, q, 0, .01));
        var r = FiniteTwoPlayerConditionalDecisionLoss.solve(game, q).audit();
        assertThrows(UnsupportedOperationException.class, () -> r.actions().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> r.moves().get(0).lowerControls().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> r.moves().get(0).lowerControls().get(0).witness().actionUtilities().clear());
        assertTrue(
                Arrays.stream(
                                FiniteTwoPlayerConditionalDecisionLoss.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void allMovesAndJointControlsUseOneOriginalImmutableSnapshot() throws Exception {
        var snapshot = correlated(5, 0);
        int[] calls = new int[2];
        var game =
                new MultiPlayerCfrGame<Node>() {
                    public int playerCount() {
                        return 6;
                    }

                    public Node initialState() {
                        calls[0]++;
                        return snapshot.initialState();
                    }

                    public boolean isTerminal(Node s) {
                        return snapshot.isTerminal(s);
                    }

                    public double[] terminalUtilities(Node s) {
                        calls[1]++;
                        return snapshot.terminalUtilities(s);
                    }

                    public int currentPlayer(Node s) {
                        return snapshot.currentPlayer(s);
                    }

                    public List<String> legalActions(Node s) {
                        return snapshot.legalActions(s);
                    }

                    public String informationSet(Node s) {
                        return snapshot.informationSet(s);
                    }

                    public Node afterAction(Node s, String action) {
                        return snapshot.afterAction(s, action);
                    }

                    public List<ChanceOutcome<Node>> chanceOutcomes(Node s) {
                        return snapshot.chanceOutcomes(s);
                    }
                };
        FiniteTwoPlayerConditionalDecisionLoss.solve(game, question(5));
        assertArrayEquals(new int[] {1, 5}, calls);
    }
}
