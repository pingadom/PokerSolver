package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerConditionalActionIntervalsTest.*;
import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervalsTest.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerReachBoundsTest {
    static FiniteTwoPlayerReachBounds.Audit solve(Snapshot game, int hero, double floor)
            throws Exception {
        return FiniteTwoPlayerReachBounds.solve(game, question(hero), 1e-8, floor).audit();
    }

    static Snapshot constantHalf(int hero) {
        int opponent = hero == 3 ? 5 : 3;
        var q =
                decision(
                        hero,
                        "question",
                        List.of("safe", "risk"),
                        terminal(hero, 0),
                        terminal(hero, -2));
        var show = decision(opponent, "signal:0", List.of("show"), q);
        var hide = decision(opponent, "signal:1", List.of("hide"), terminal(hero, 0));
        return new Snapshot(
                6, new Node(-1, "", List.of(), List.of(.5, .5), List.of(show, hide), List.of()));
    }

    @Test
    void independentLiteralSignalPlansGiveTheSameMinimumAndMaximumForBothActors() throws Exception {
        for (int hero : List.of(3, 5)) {
            for (boolean mandatory : List.of(false, true)) {
                var a = solve(posteriorGame(hero, mandatory, false), hero, .01);
                // Every policy has hero BR0 (safe/exit), so all literal signal plans are secure.
                var literal = new ArrayList<Double>();
                for (int first = mandatory ? 1 : 0; first <= 1; first++)
                    for (int second = 0; second <= 1; second++) literal.add(.5 * (first + second));
                assertEquals(
                        Collections.min(literal), a.minimumReachWitness().questionReach(), 1e-12);
                assertEquals(
                        Collections.max(literal), a.maximumReachWitness().questionReach(), 1e-12);
                assertEquals(
                        mandatory
                                ? FiniteTwoPlayerReachBounds.Status
                                        .CERTIFIED_ALL_AT_OR_ABOVE_REQUIRED_FLOOR
                                : FiniteTwoPlayerReachBounds.Status
                                        .VERIFIED_WITNESSES_ON_BOTH_SIDES,
                        a.status());
                assertFalse(a.trainerAdmission());
                assertEquals(2, a.work().intervalLpSolves());
                assertEquals(List.of(new OwnAction(hero + ":root", "enter")), a.fixedHeroPast());
                for (var w : List.of(a.minimumReachWitness(), a.maximumReachWitness())) {
                    assertEquals(
                            w.questionReach(),
                            w.masses().stream()
                                    .mapToDouble(
                                            FiniteTwoPlayerConditionalActionIntervals.ReachMass
                                                    ::behavioralWeight)
                                    .sum(),
                            1e-12);
                    assertEquals(0, w.globalHeroBestResponse(), 1e-10);
                    for (var mass : w.masses())
                        assertEquals(
                                mass.originalRealizationWeight(), mass.behavioralWeight(), 1e-12);
                    assertTrue(w.certificate().maximumPrimalViolation() <= 1e-8);
                    assertTrue(w.certificate().maximumDualViolation() <= 1e-8);
                }
                assertEquals(
                        a.denominator().constant()
                                - a.minimumReachWitness().certificate().dualValue()
                                - 1e-8,
                        a.certifiedReachLowerBound(),
                        1e-12);
                assertEquals(
                        a.denominator().constant()
                                + a.maximumReachWitness().certificate().dualValue()
                                + 1e-8,
                        a.certifiedReachUpperBound(),
                        1e-12);
            }
        }
    }

    @Test
    void exactBoundaryIsUnresolvedRatherThanFalselyCertifiedOrCalledImpossible() throws Exception {
        for (int hero : List.of(3, 5)) {
            var a = solve(constantHalf(hero), hero, .5);
            assertEquals(.5, a.minimumReachWitness().questionReach(), 1e-12);
            assertEquals(.5, a.maximumReachWitness().questionReach(), 1e-12);
            assertEquals(
                    FiniteTwoPlayerReachBounds.Status.UNRESOLVED_AT_CERTIFICATE_TOLERANCE,
                    a.status());
            assertEquals(
                    FiniteTwoPlayerReachBounds.Status.CERTIFIED_ALL_BELOW_REQUIRED_FLOOR,
                    solve(constantHalf(hero), hero, .5001).status());
            assertEquals(
                    FiniteTwoPlayerReachBounds.Status.CERTIFIED_ALL_AT_OR_ABOVE_REQUIRED_FLOOR,
                    solve(constantHalf(hero), hero, .4999).status());
        }
    }

    @Test
    void independentSecurityConstraintCanForceEveryOpponentBelowTheFloor() throws Exception {
        for (int hero : List.of(3, 5)) {
            var q =
                    decision(
                            hero,
                            "question",
                            List.of("safe", "risk"),
                            terminal(hero, 1),
                            terminal(hero, 2));
            var game =
                    new Snapshot(
                            6,
                            decision(
                                    hero == 3 ? 5 : 3,
                                    "signal",
                                    List.of("show", "hide"),
                                    q,
                                    terminal(hero, 0)));
            var a = solve(game, hero, .0001);
            // Hero best response is2*p(show), hence the full1e-8 security face requires p<=5e-9.
            assertEquals(0, a.minimumReachWitness().questionReach(), 1e-12);
            assertEquals(5e-9, a.maximumReachWitness().questionReach(), 1e-10);
            assertTrue(a.certifiedReachUpperBound() < .0001);
            assertEquals(
                    FiniteTwoPlayerReachBounds.Status.CERTIFIED_ALL_BELOW_REQUIRED_FLOOR,
                    a.status());
        }
    }

    @Test
    void reachPrecedesTheChosenLegalActionAndDoesNotEnumerateContinuationPlans() throws Exception {
        var later = new ArrayList<Node>();
        for (int i = 0; i < 7; i++)
            later.add(
                    decision(5, "later" + i, List.of("a", "b"), terminal(5, -2), terminal(5, -1)));
        var chance = new Node(-1, "", List.of(), Collections.nCopies(7, 1.0 / 7), later, List.of());
        var q = decision(5, "question", List.of("safe", "risk"), terminal(5, 0), chance);
        var game = new Snapshot(6, decision(3, "past", List.of("go"), q));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerConditionalActionIntervals.solve(game, question(5)));
        var risk = solve(game, 5, .01);
        var safe =
                FiniteTwoPlayerReachBounds.solve(
                                game,
                                new FiniteTwoPlayerConditionalActionIntervals.Question(
                                        5, "5:question", "safe"),
                                1e-8,
                                .01)
                        .audit();
        assertEquals(1, risk.minimumReachWitness().questionReach(), 1e-12);
        assertEquals(risk.denominator(), safe.denominator());
        assertEquals(risk.minimumReachWitness(), safe.minimumReachWitness());
        assertEquals(risk.maximumReachWitness(), safe.maximumReachWitness());
    }

    @Test
    void bothLpEndpointsAndWitnessesShareTheDeclaredWorkCaps() throws Exception {
        var game = posteriorGame(5, false, false);
        var a = solve(game, 5, .01);
        var one = a.minimumReachWitness().certificate().work();
        for (long arithmetic : List.of(0L, one.arithmeticWork()))
            assertThrows(
                    BoundedLinearProgram.Rejected.class,
                    () ->
                            FiniteTwoPlayerReachBounds.solve(
                                    game,
                                    question(5),
                                    1e-8,
                                    .01,
                                    FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                                    BoundedLinearProgram.MAX_PIVOTS,
                                    arithmetic));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerReachBounds.solve(
                                game,
                                question(5),
                                1e-8,
                                .01,
                                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                                one.pivots(),
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                () ->
                        FiniteTwoPlayerReachBounds.solve(
                                game,
                                question(5),
                                1e-8,
                                .01,
                                a.work().compilerUnits() - 1,
                                BoundedLinearProgram.MAX_PIVOTS,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
    }

    @Test
    void immutableOwnedResultHasNoPosteriorOrEvAndMisclassificationCannotParse() throws Exception {
        var a = solve(posteriorGame(5, false, false), 5, .01);
        assertTrue(
                Arrays.stream(FiniteTwoPlayerReachBounds.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(
                UnsupportedOperationException.class, () -> a.maximumReachWitness().point().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.fixedHeroPast().clear());
        var json = SixMaxTextureStudy.json(a);
        assertFalse(json.contains("posterior"));
        assertFalse(json.contains("conditionalHeroBestResponse"));
        var mapper = SixMaxTexturePayoffTable.mapper();
        assertEquals(a, mapper.readValue(json, FiniteTwoPlayerReachBounds.Audit.class));
        ObjectNode tree = mapper.valueToTree(a);
        tree.put("status", "CERTIFIED_ALL_BELOW_REQUIRED_FLOOR");
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(tree, FiniteTwoPlayerReachBounds.Audit.class));
    }

    @Test
    void invalidQuestionAndLimitsReturnNoResult() {
        var game = posteriorGame(5, false, false);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerReachBounds.solve(
                                game,
                                new FiniteTwoPlayerConditionalActionIntervals.Question(
                                        5, "5:question", "alien"),
                                1e-8,
                                .01));
        for (double bad : List.of(Double.NaN, Double.POSITIVE_INFINITY, 0.0, 1.01))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FiniteTwoPlayerReachBounds.solve(game, question(5), 1e-8, bad));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerReachBounds.solve(game, question(5), .001, .01));
    }
}
