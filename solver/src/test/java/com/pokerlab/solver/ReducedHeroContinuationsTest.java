package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.FiniteTwoPlayerSequenceForm.Node;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReducedHeroContinuationsTest {
    static Node leaf(double value) {
        return new Node(-2, "", List.of(), List.of(), List.of(), List.of(value, -value));
    }

    static Node node(int actor, String key, List<String> actions, Node... children) {
        return new Node(actor, key, actions, List.of(), List.of(children), List.of());
    }

    static ReducedHeroContinuations.Compiled compile(List<Node> roots, int hero) {
        return ReducedHeroContinuations.compile(
                roots, hero, 1609, new SequenceFormAffineProjection.Budget());
    }

    static void rows(Node n, int hero, Map<String, List<String>> rows) {
        if (n.actor() == hero) rows.put(n.key(), n.actions());
        n.children().forEach(c -> rows(c, hero, rows));
    }

    static List<Map<String, String>> full(List<Node> roots, int hero) {
        var rows = new TreeMap<String, List<String>>();
        roots.forEach(n -> rows(n, hero, rows));
        var result = new ArrayList<Map<String, String>>();
        result.add(Map.of());
        for (var row : rows.entrySet()) {
            var next = new ArrayList<Map<String, String>>();
            for (var old : result)
                for (String action : row.getValue()) {
                    var plan = new TreeMap<>(old);
                    plan.put(row.getKey(), action);
                    next.add(plan);
                }
            result = next;
            if (result.size() > 1296) fail("Independent full-plan oracle ceiling exceeded");
        }
        return result;
    }

    static void visited(Node n, int hero, Map<String, String> plan, Map<String, String> reduced) {
        if (n.actor() == hero) {
            String choice = plan.get(n.key());
            assertNotNull(choice);
            reduced.put(n.key(), choice);
            visited(n.children().get(n.actions().indexOf(choice)), hero, plan, reduced);
        } else n.children().forEach(c -> visited(c, hero, plan, reduced));
    }

    static void coefficients(
            Node n,
            int hero,
            Map<String, String> plan,
            String sequence,
            Map<String, Double> result) {
        if (n.actor() == -2) {
            result.merge(sequence, n.utilities().get(hero), Double::sum);
            return;
        }
        if (n.actor() == hero) {
            String choice = plan.get(n.key());
            assertNotNull(choice);
            coefficients(
                    n.children().get(n.actions().indexOf(choice)), hero, plan, sequence, result);
        } else
            for (int i = 0; i < n.children().size(); i++)
                coefficients(
                        n.children().get(i),
                        hero,
                        plan,
                        n.key() + "=" + n.actions().get(i),
                        result);
    }

    static void equivalent(List<Node> roots, int hero, int expectedFull, int expectedReduced) {
        var reduced = compile(roots, hero);
        var full = full(roots, hero);
        assertEquals(expectedFull, full.size());
        assertEquals(BigInteger.valueOf(expectedFull), reduced.cartesianPlans());
        assertEquals(expectedReduced, reduced.choices().size());
        var projected = new HashSet<Map<String, String>>();
        for (var p : full) {
            var active = new TreeMap<String, String>();
            roots.forEach(r -> visited(r, hero, p, active));
            projected.add(active);
            var before = new TreeMap<String, Double>();
            var after = new TreeMap<String, Double>();
            for (var r : roots) {
                coefficients(r, hero, p, "", before);
                coefficients(r, hero, active, "", after);
            }
            assertEquals(
                    before,
                    after,
                    "Every opponent-sequence payoff coefficient must survive erasure");
        }
        assertEquals(projected, new HashSet<>(reduced.choices()));
    }

    @Test
    void oneSharedChoiceAcrossHiddenWorldsAndBothHeroOrientations() {
        for (int hero : List.of(0, 1)) {
            int opponent = 1 - hero;
            var roots = new ArrayList<Node>();
            for (int i = 0; i < 2; i++) {
                var reply =
                        node(
                                hero,
                                "hero-response",
                                List.of("fold", "call"),
                                leaf(-4 - i),
                                leaf(7 + i));
                var villain =
                        node(
                                opponent,
                                "opponent-" + i,
                                List.of("check", "bet"),
                                leaf(i - 2),
                                reply);
                roots.add(node(hero, "hero-first", List.of("bet", "check"), leaf(3 + i), villain));
            }
            equivalent(roots, hero, 4, 3);
        }
    }

    @Test
    void opponentBranchesRemainIndependentAndFullySpecified() {
        var left = node(0, "left", List.of("a", "b"), leaf(-1), leaf(3));
        var right = node(0, "right", List.of("x", "y"), leaf(7), leaf(-5));
        equivalent(List.of(node(1, "villain", List.of("left", "right"), left, right)), 0, 4, 4);
    }

    @Test
    void eightPrivateWorldsDoNotMultiplyOneInformationSetIntoEightDecisions() {
        var roots = new ArrayList<Node>();
        for (int i = 0; i < 8; i++)
            roots.add(node(0, "shared", List.of("fold", "call"), leaf(-i), leaf(i + 1)));
        equivalent(roots, 0, 2, 2);
    }

    static Node flop(String tag) {
        var response = node(0, tag + ":response", List.of("fold", "call"), leaf(-9), leaf(11));
        var checked =
                node(1, tag + ":opponent-after-check", List.of("check", "bet"), leaf(3), response);
        var quarter =
                node(1, tag + ":opponent-quarter", List.of("fold", "call"), leaf(5), leaf(-2));
        var half = node(1, tag + ":opponent-half", List.of("fold", "call"), leaf(7), leaf(-4));
        return node(0, tag + ":hero", List.of("check", "quarter", "half"), checked, quarter, half);
    }

    static Node raisedRoot() {
        var last = node(0, "hero-facing-100", List.of("fold", "call"), leaf(-50), leaf(51));
        var raise50 =
                node(
                        1,
                        "opponent-facing-50",
                        List.of("fold", "call", "raise100"),
                        leaf(22),
                        flop("flop50"),
                        last);
        var hero22 =
                node(
                        0,
                        "hero-facing-22",
                        List.of("fold", "call", "raise50"),
                        leaf(-9),
                        flop("flop22"),
                        raise50);
        return node(
                1,
                "opponent-facing-9",
                List.of("fold", "call", "raise22"),
                leaf(3),
                flop("flop9"),
                hero22);
    }

    @Test
    void jointSizingStructureDropsFrom1296To52WithoutLosingAnOriginalPlan() {
        equivalent(List.of(raisedRoot()), 0, 1296, 52);
        equivalent(List.of(flop("called")), 0, 6, 4);
    }

    @Test
    void perfectRecallAndConsistentHiddenWorldMenusAreMandatory() {
        var repeated = node(0, "same", List.of("fold", "call"), leaf(1), leaf(-1));
        var wrong = node(0, "first", List.of("left", "right"), repeated, repeated);
        assertThrows(IllegalArgumentException.class, () -> compile(List.of(wrong), 0));
        var different = node(0, "same", List.of("check", "raise"), leaf(1), leaf(-1));
        assertThrows(
                IllegalArgumentException.class, () -> compile(List.of(repeated, different), 0));
    }

    static Node independent(int rows) {
        var actions = new ArrayList<String>();
        var children = new ArrayList<Node>();
        for (int i = 0; i < rows; i++) {
            actions.add("branch" + i);
            children.add(node(0, "hero" + i, List.of("a", "b"), leaf(i), leaf(-i)));
        }
        return new Node(1, "opponent", actions, List.of(), children, List.of());
    }

    @Test
    void unchanged64PlanLimitRejectsBeforeEnumeratingAnOverBudgetForest() {
        assertEquals(64, compile(List.of(independent(6)), 0).choices().size());
        var failure =
                assertThrows(
                        ReducedHeroContinuations.PlanLimit.class,
                        () -> compile(List.of(independent(7)), 0));
        assertEquals(BigInteger.valueOf(128), failure.reducedPlans());
        assertEquals(BigInteger.valueOf(128), failure.cartesianPlans());
        assertEquals(64, FiniteTwoPlayerRootActionIntervals.MAX_CONTINUATION_PLANS);
    }

    @Test
    void originalCompilerWorkBudgetStillRejectsBeforeAllocation() {
        assertThrows(
                FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                () ->
                        ReducedHeroContinuations.compile(
                                List.of(raisedRoot()),
                                0,
                                1609,
                                new SequenceFormAffineProjection.Budget(0)));
    }

    @Test
    void choicesAreImmutableCanonicalAndIndependentOfWorldOrder() {
        var a = node(0, "hero", List.of("fold", "call"), leaf(-1), leaf(2));
        var b = node(0, "hero", List.of("fold", "call"), leaf(4), leaf(9));
        var forward = compile(List.of(a, b), 0);
        assertEquals(forward, compile(List.of(b, a), 0));
        assertThrows(UnsupportedOperationException.class, () -> forward.choices().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> forward.choices().getFirst().put("hero", "illegal"));
    }
}
