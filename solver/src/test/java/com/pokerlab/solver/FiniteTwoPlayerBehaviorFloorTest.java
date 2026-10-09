package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerBehaviorFloorTest {
    static class Dominance implements MultiPlayerCfrGame<List<String>> {
        int roots, terminals;

        public int playerCount() {
            return 6;
        }

        public List<String> initialState() {
            roots++;
            return List.of();
        }

        public boolean isTerminal(List<String> s) {
            return s.size() == 2;
        }

        public double[] terminalUtilities(List<String> s) {
            terminals++;
            double v = (s.getFirst().equals("a") ? 2 : 0) + (s.getLast().equals("a") ? 1 : 0);
            return new double[] {-1, -1, v, -1, -1, 4 - v};
        }

        public int currentPlayer(List<String> s) {
            return s.isEmpty() ? 2 : 5;
        }

        public List<String> legalActions(List<String> s) {
            return List.of("a", "b");
        }

        public String informationSet(List<String> s) {
            return "hidden";
        }

        public List<String> afterAction(List<String> s, String a) {
            var next = new ArrayList<>(s);
            next.add(a);
            return List.copyOf(next);
        }

        public List<ChanceOutcome<List<String>>> chanceOutcomes(List<String> s) {
            throw new AssertionError();
        }
    }

    static class Recall extends Dominance {
        final boolean recall;

        Recall(boolean recall) {
            this.recall = recall;
        }

        public boolean isTerminal(List<String> s) {
            return s.size() == 3;
        }

        public int currentPlayer(List<String> s) {
            return s.size() == 1 ? 5 : 2;
        }

        public String informationSet(List<String> s) {
            return s.isEmpty()
                    ? "root"
                    : s.size() == 1 ? "villain" : "again" + (recall ? s.getFirst() : "");
        }

        public double[] terminalUtilities(List<String> s) {
            double v =
                    s.getFirst().equals(s.getLast())
                            ? (s.getFirst().equals(s.get(1)) ? 1 : -1)
                            : -2;
            return new double[] {0, 0, v, 0, 0, -v};
        }
    }

    @Test
    void analyticDominanceSeparatesConstrainedAndOriginalQuality() throws Exception {
        var game = new Dominance();
        var r = FiniteTwoPlayerBehaviorFloor.solve(game, .01);
        assertEquals(1, game.roots);
        assertEquals(4, game.terminals);
        assertEquals(1.99, r.audit().constrainedLowerValue(), 1e-10);
        assertEquals(1.99, r.audit().constrainedUpperValue(), 1e-10);
        assertEquals(.99, r.strategy().get("2:hidden").get("a"), 1e-12);
        assertEquals(.01, r.strategy().get("5:hidden").get("a"), 1e-12);
        assertEquals(0, r.audit().constrainedQuality().nashConvBb(), 1e-10);
        assertEquals(.03, r.audit().originalGameQuality().nashConvBb(), 1e-10);
        assertArrayEquals(
                new double[] {-1, -1, 1.99, -1, -1, 2.01},
                r.audit().constrainedQuality().profileUtilitiesBb().stream()
                        .mapToDouble(Double::doubleValue)
                        .toArray(),
                1e-12);
        audit(r);
        literalCertificate(
                new Dominance(),
                new CfrSolution(1, r.strategy()),
                .01,
                r.audit().constrainedQuality());
    }

    @Test
    void nestedOwnActionsUsePositiveRelativeFloorsAndLiteralPlanCertificate() throws Exception {
        for (double floor : List.of(.01, .001, .0001)) {
            var game = new Recall(true);
            var r = FiniteTwoPlayerBehaviorFloor.solve(game, floor);
            assertEquals(0, r.audit().constrainedQuality().nashConvBb(), 1e-9);
            audit(r);
            literalCertificate(
                    game, new CfrSolution(1, r.strategy()), floor, r.audit().constrainedQuality());
        }
    }

    @Test
    void hiddenChanceWorldsHaveOneResponsePerOwnInformationSet() throws Exception {
        for (int types : List.of(2, 4)) {
            var game = new FiniteTwoPlayerAffineSequenceFormTest.Hidden(types);
            var r = FiniteTwoPlayerBehaviorFloor.solve(game, .001);
            audit(r);
            literalCertificate(
                    game, new CfrSolution(1, r.strategy()), .001, r.audit().constrainedQuality());
        }
    }

    @Test
    void floorCfrConvergesIndependentlyWithExactVisitAccounting() throws Exception {
        var game = new Dominance();
        var ref = FiniteTwoPlayerFloorCfr.solve(game, .01, 500);
        assertEquals(1, game.roots);
        assertEquals(4, game.terminals);
        assertEquals(7L * 2 * 500, ref.visitedNodes());
        assertTrue(ref.constrainedQuality().nashConvBb() < .0001);
        assertEquals(1.99, ref.constrainedQuality().profileUtilitiesBb().get(2), .0001);
        assertTrue(ref.originalGameQuality().nashConvBb() > .029);
        literalCertificate(new Dominance(), ref.solution(), .01, ref.constrainedQuality());
        assertEquals(ref, FiniteTwoPlayerFloorCfr.solve(new Dominance(), .01, 500));
    }

    @Test
    void floorCfrUsesConstrainedIntentRegretsAfterRepeatedOwnActions() throws Exception {
        var game = new Recall(true);
        var ref = FiniteTwoPlayerFloorCfr.solve(game, .01, 2000);
        var exact = FiniteTwoPlayerBehaviorFloor.solve(game, .01);
        assertTrue(ref.constrainedQuality().nashConvBb() < .001);
        assertEquals(
                exact.audit().constrainedLowerValue(),
                ref.constrainedQuality().profileUtilitiesBb().get(2),
                .001);
        literalCertificate(game, ref.solution(), .01, ref.constrainedQuality());
    }

    @Test
    void invalidFloorsAndBudgetsFailBeforeReadingTheInput() {
        for (double floor :
                new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 1e-7, .011}) {
            var game = new Dominance();
            assertEquals(
                    FiniteTwoPlayerBehaviorFloor.Failure.INVALID_INPUT,
                    assertThrows(
                                    FiniteTwoPlayerBehaviorFloor.Rejected.class,
                                    () -> FiniteTwoPlayerBehaviorFloor.solve(game, floor))
                            .reason());
            assertThrows(
                    FiniteTwoPlayerBehaviorFloor.Rejected.class,
                    () -> FiniteTwoPlayerFloorCfr.solve(game, floor, 1));
            assertEquals(0, game.roots);
        }
        for (int iterations : new int[] {0, 10_001}) {
            var game = new Dominance();
            assertThrows(
                    FiniteTwoPlayerBehaviorFloor.Rejected.class,
                    () -> FiniteTwoPlayerFloorCfr.solve(game, .01, iterations));
            assertEquals(0, game.roots);
        }
    }

    @Test
    void compilationAndTraversalCapsFailClosed() {
        assertEquals(
                FiniteTwoPlayerBehaviorFloor.Failure.WORK_LIMIT,
                assertThrows(
                                FiniteTwoPlayerBehaviorFloor.Rejected.class,
                                () -> FiniteTwoPlayerBehaviorFloor.solve(new Dominance(), .01, 1))
                        .reason());
        assertEquals(
                FiniteTwoPlayerBehaviorFloor.Failure.WORK_LIMIT,
                assertThrows(
                                FiniteTwoPlayerBehaviorFloor.Rejected.class,
                                () -> FiniteTwoPlayerFloorCfr.solve(new Dominance(), .01, 2, 1))
                        .reason());
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerBehaviorFloor.solve(new Recall(false), .01));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerFloorCfr.solve(new Recall(false), .01, 1));
    }

    @Test
    void certificatesAndPoliciesAreDeeplyImmutableAndResultIsOpaque() throws Exception {
        var r = FiniteTwoPlayerBehaviorFloor.solve(new Dominance(), .01);
        assertThrows(UnsupportedOperationException.class, () -> r.strategy().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> r.strategy().get("2:hidden").clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> r.audit().firstFloorFlow().inequalities().getFirst().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> r.audit().constrainedQuality().maximizingIntents().get(2).clear());
        assertTrue(
                Arrays.stream(FiniteTwoPlayerBehaviorFloor.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void constrainedResponseRejectsUnflooredMissingAndForeignRows() throws Exception {
        var checked = FiniteTwoPlayerSequenceForm.checked(new Dominance());
        for (var strategy :
                List.of(
                        Map.<String, Map<String, Double>>of(),
                        Map.of(
                                "2:hidden",
                                Map.of("a", 1., "b", 0.),
                                "5:hidden",
                                Map.of("a", .01, "b", .99)),
                        Map.of(
                                "2:hidden",
                                Map.of("a", .99, "b", .01),
                                "5:hidden",
                                Map.of("a", .01, "b", .99),
                                "foreign",
                                Map.of("x", 1.))))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SequenceFormFloorResponse.assess(
                                    checked, new CfrSolution(1, strategy), .01));
    }

    static void audit(FiniteTwoPlayerBehaviorFloor.Result result) {
        var a = result.audit();
        assertEquals(FiniteTwoPlayerBehaviorFloor.ALGORITHM, a.algorithm());
        assertNotEquals(FiniteTwoPlayerAffineSequenceForm.ALGORITHM, a.algorithm());
        assertTrue(a.compilationWork().chargedUnits() <= a.compilationWork().limit());
        for (var pair :
                List.of(
                        Map.entry(a.firstFlow(), a.firstFloorFlow()),
                        Map.entry(a.secondFlow(), a.secondFloorFlow()))) {
            var flow = pair.getKey();
            var floor = pair.getValue();
            assertTrue(floor.maximumRelativeFloorViolation() <= 1e-6);
            assertTrue(floor.maximumRelativeConservationResidual() <= 1e-8);
            assertEquals(flow.sequences().size() - 1, floor.floorConstraints().size());
            assertEquals(1, flow.realization().getFirst(), 1e-12);
            for (var c : flow.conservation()) {
                double parent = flow.realization().get(c.parentSequence()), sum = 0;
                assertTrue(parent > 0);
                for (int child : c.childSequences()) {
                    double p = flow.realization().get(child) / parent;
                    assertTrue(p >= a.minimumActionProbability() * (1 - 1e-6));
                    sum += p;
                }
                assertEquals(1, sum, 1e-8);
            }
        }
    }

    /** Enumerate all extreme floored intent plans using only the literal game's public methods. */
    static <S> void literalCertificate(
            MultiPlayerCfrGame<S> game,
            CfrSolution policy,
            double floor,
            FiniteTwoPlayerBehaviorFloor.ConstrainedQuality quality) {
        var infos = new TreeMap<String, List<String>>();
        collect(game, game.initialState(), infos);
        for (int actor = 0; actor < game.playerCount(); actor++) {
            int seat = actor;
            var own =
                    infos.entrySet().stream()
                            .filter(e -> e.getKey().startsWith(seat + ":"))
                            .toList();
            double profile = literal(game, game.initialState(), actor, policy.strategy());
            double best = plans(game, actor, own, 0, new TreeMap<>(policy.strategy()), floor);
            assertEquals(profile, quality.profileUtilitiesBb().get(actor), 1e-9);
            assertEquals(best, quality.bestResponseUtilitiesBb().get(actor), 1e-9);
        }
    }

    static <S> void collect(MultiPlayerCfrGame<S> game, S s, Map<String, List<String>> infos) {
        if (game.isTerminal(s)) return;
        if (game.currentPlayer(s) == -1) {
            for (var c : game.chanceOutcomes(s)) collect(game, c.state(), infos);
            return;
        }
        infos.put(game.currentPlayer(s) + ":" + game.informationSet(s), game.legalActions(s));
        for (String a : game.legalActions(s)) collect(game, game.afterAction(s, a), infos);
    }

    static <S> double literal(
            MultiPlayerCfrGame<S> game, S s, int actor, Map<String, Map<String, Double>> policy) {
        if (game.isTerminal(s)) return game.terminalUtilities(s)[actor];
        double v = 0;
        if (game.currentPlayer(s) == -1) {
            for (var c : game.chanceOutcomes(s))
                v += c.probability() * literal(game, c.state(), actor, policy);
            return v;
        }
        var row = policy.get(game.currentPlayer(s) + ":" + game.informationSet(s));
        for (String a : game.legalActions(s))
            v += row.get(a) * literal(game, game.afterAction(s, a), actor, policy);
        return v;
    }

    static <S> double plans(
            MultiPlayerCfrGame<S> game,
            int actor,
            List<Map.Entry<String, List<String>>> infos,
            int index,
            Map<String, Map<String, Double>> policy,
            double floor) {
        if (index == infos.size()) return literal(game, game.initialState(), actor, policy);
        var entry = infos.get(index);
        var old = policy.get(entry.getKey());
        double best = Double.NEGATIVE_INFINITY;
        for (String selected : entry.getValue()) {
            var row = new TreeMap<String, Double>();
            for (String a : entry.getValue())
                row.put(a, a.equals(selected) ? 1 - (entry.getValue().size() - 1) * floor : floor);
            policy.put(entry.getKey(), row);
            best = Math.max(best, plans(game, actor, infos, index + 1, policy, floor));
        }
        policy.put(entry.getKey(), old);
        return best;
    }
}
