package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerConditionalDecisionLossTest.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerConditionedDecisionLossTest {
    @Test
    void conditionedControlsStillConsultTheOriginalGameOnlyForOneImmutableCopy() throws Exception {
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

                    public Node afterAction(Node s, String a) {
                        return snapshot.afterAction(s, a);
                    }

                    public List<ChanceOutcome<Node>> chanceOutcomes(Node s) {
                        return snapshot.chanceOutcomes(s);
                    }
                };
        FiniteTwoPlayerConditionedDecisionLoss.solve(game, question(5), 1e-8, .01);
        assertArrayEquals(new int[] {1, 5}, calls);
    }

    @Test
    void bothActorOrientationsPreserveOriginalExtremaAndExposeRecoverableRawObjectives()
            throws Exception {
        for (int hero : List.of(3, 5))
            for (int mode : List.of(0, 1, 2)) {
                var game = correlated(hero, mode);
                var old =
                        FiniteTwoPlayerConditionalDecisionLoss.solve(game, question(hero)).audit();
                var conditioned =
                        FiniteTwoPlayerConditionedDecisionLoss.solve(
                                        game, question(hero), 1e-8, .01)
                                .audit();
                var joint = conditioned.joint();
                assertEquals(FiniteTwoPlayerConditionalDecisionLoss.ALGORITHM, old.algorithm());
                assertEquals(
                        FiniteTwoPlayerConditionalDecisionLoss.CONDITIONED_ALGORITHM,
                        joint.algorithm());
                assertFalse(joint.trainerAdmission());
                for (int i = 0; i < old.moves().size(); i++) {
                    assertEquals(
                            old.moves().get(i).lowerLoss(), joint.moves().get(i).lowerLoss(), 1e-9);
                    assertEquals(
                            old.moves().get(i).upperLoss(), joint.moves().get(i).upperLoss(), 1e-9);
                    var interval = joint.actions().get(i).interval();
                    assertEquals(
                            FiniteTwoPlayerConditionalActionIntervals.CONDITIONED_ALGORITHM,
                            interval.algorithm());
                    assertEquals(
                            old.actions().get(i).interval().lowerUtility(),
                            interval.lowerUtility(),
                            1e-9);
                    assertEquals(
                            old.actions().get(i).interval().upperUtility(),
                            interval.upperUtility(),
                            1e-9);
                    for (int j = 0; j < interval.upperWitnesses().size(); j++) {
                        var w = interval.upperWitnesses().get(j);
                        var plan = interval.numeratorPlans().get(j);
                        double original = plan.constant() * w.scaling();
                        for (int k = 0; k < plan.cost().size(); k++)
                            original += plan.cost().get(k) * w.point().get(k);
                        assertEquals(
                                original,
                                w.certificate().primalValue() - conditioned.upperObjectiveShift(),
                                1e-9);
                        assertTrue(w.posteriorTotalVariation() <= 1e-8);
                        assertTrue(
                                w.globalHeroBestResponse()
                                        <= interval.globalHeroUpperValue()
                                                + interval.securitySlack()
                                                + 1e-8);
                    }
                }
            }
    }

    static Node translated(Node node, int hero, double amount) {
        if (node.actor() == -2) {
            var values = new ArrayList<>(node.utilities());
            int opponent = hero == 3 ? 5 : 3;
            values.set(hero, values.get(hero) + amount);
            values.set(opponent, values.get(opponent) - amount);
            return new Node(
                    node.actor(),
                    node.key(),
                    node.actions(),
                    node.probabilities(),
                    node.children(),
                    values);
        }
        return new Node(
                node.actor(),
                node.key(),
                node.actions(),
                node.probabilities(),
                node.children().stream().map(n -> translated(n, hero, amount)).toList(),
                node.utilities());
    }

    @Test
    void translatingTerminalUtilitiesChangesShiftAndEvsButPreservesDecisionLoss() throws Exception {
        var game = correlated(5, 2);
        var a = FiniteTwoPlayerConditionedDecisionLoss.solve(game, question(5), 1e-8, .01).audit();
        var b =
                FiniteTwoPlayerConditionedDecisionLoss.solve(
                                new Snapshot(6, translated(game.initialState(), 5, 37)),
                                question(5),
                                1e-8,
                                .01)
                        .audit();
        assertNotEquals(a.upperObjectiveShift(), b.upperObjectiveShift());
        for (int i = 0; i < a.joint().moves().size(); i++) {
            assertEquals(
                    a.joint().moves().get(i).lowerLoss(),
                    b.joint().moves().get(i).lowerLoss(),
                    1e-8);
            assertEquals(
                    a.joint().moves().get(i).upperLoss(),
                    b.joint().moves().get(i).upperLoss(),
                    1e-8);
            assertEquals(
                    a.joint().actions().get(i).interval().upperUtility() + 37,
                    b.joint().actions().get(i).interval().upperUtility(),
                    1e-8);
        }
    }

    @Test
    void conditioningStillSharesAllThreeAggregateCaps() throws Exception {
        var game = correlated(5, 1);
        var q = question(5);
        var work =
                FiniteTwoPlayerConditionedDecisionLoss.solve(game, q, 1e-8, .01)
                        .audit()
                        .joint()
                        .work();
        assertEquals(18, work.intervalLpSolves());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionedDecisionLoss.solve(
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
                        FiniteTwoPlayerConditionedDecisionLoss.solve(
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
                        FiniteTwoPlayerConditionedDecisionLoss.solve(
                                game,
                                q,
                                1e-8,
                                .01,
                                8_000_000,
                                BoundedLinearProgram.MAX_PIVOTS,
                                work.intervalLpArithmeticWork() - 1));
    }

    @Test
    void zeroReachStillRejectsWithoutRestrictingTheSecurityFace() {
        var error =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () ->
                                FiniteTwoPlayerConditionedDecisionLoss.solve(
                                        FiniteTwoPlayerConditionalActionIntervalsTest.posteriorGame(
                                                5, false, false),
                                        question(5),
                                        1e-8,
                                        .01));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                error.reason());
    }

    @Test
    void oldAuditCannotBeRelabeledAndOwnedResultIsImmutable() throws Exception {
        var old =
                FiniteTwoPlayerConditionalDecisionLoss.solve(correlated(5, 0), question(5)).audit();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FiniteTwoPlayerConditionedDecisionLoss.Audit(
                                FiniteTwoPlayerConditionedDecisionLoss.REPRESENTATION, -4, old));
        var audit =
                FiniteTwoPlayerConditionedDecisionLoss.solve(
                                correlated(5, 0), question(5), 1e-8, .01)
                        .audit();
        assertThrows(UnsupportedOperationException.class, () -> audit.joint().moves().clear());
        assertTrue(
                Arrays.stream(
                                FiniteTwoPlayerConditionedDecisionLoss.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }
}
