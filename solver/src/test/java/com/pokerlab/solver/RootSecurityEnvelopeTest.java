package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.FiniteTwoPlayerSequenceForm.Node;
import com.pokerlab.solver.FiniteTwoPlayerSequenceForm.Snapshot;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class RootSecurityEnvelopeTest {
    static Node leaf(int hero, double value, double constant) {
        return new Node(
                -2,
                "",
                List.of(),
                List.of(),
                List.of(),
                hero == 0 ? List.of(value, constant - value) : List.of(constant - value, value));
    }

    static Node node(int actor, String key, List<String> actions, Node... children) {
        return new Node(actor, actor + ":" + key, actions, List.of(), List.of(children), List.of());
    }

    static Node chance(List<Double> weights, Node... children) {
        return new Node(-1, "", List.of(), weights, List.of(children), List.of());
    }

    static Snapshot model(int hero, double constant, double p) {
        int opponent = 1 - hero;
        var safe0 =
                node(
                        opponent,
                        "opponent",
                        List.of("x", "y"),
                        leaf(hero, 1, constant),
                        leaf(hero, 1, constant));
        var risk0 =
                node(
                        opponent,
                        "opponent",
                        List.of("x", "y"),
                        leaf(hero, 10, constant),
                        leaf(hero, 0, constant));
        var safe1 =
                node(
                        opponent,
                        "opponent",
                        List.of("x", "y"),
                        leaf(hero, 1, constant),
                        leaf(hero, 1, constant));
        var risk1 =
                node(
                        opponent,
                        "opponent",
                        List.of("x", "y"),
                        leaf(hero, -10, constant),
                        leaf(hero, 0, constant));
        return new Snapshot(
                2,
                chance(
                        List.of(p, 1 - p),
                        node(hero, "type0", List.of("safe", "risk"), safe0, risk0),
                        node(hero, "type1", List.of("safe", "risk"), safe1, risk1)));
    }

    static double value(Node n, int hero, Map<String, String> choices, double x) {
        if (n.actor() == -2) return n.utilities().get(hero);
        if (n.actor() == hero)
            return value(
                    n.children().get(n.actions().indexOf(choices.get(n.key()))), hero, choices, x);
        double sum = 0;
        for (int i = 0; i < n.children().size(); i++)
            sum +=
                    (n.actor() == -1 ? n.probabilities().get(i) : i == 0 ? x : 1 - x)
                            * value(n.children().get(i), hero, choices, x);
        return sum;
    }

    static List<Map<String, String>> pure(Node root, int hero) {
        var menus = new TreeMap<String, List<String>>();
        collect(root, hero, menus);
        var result = new ArrayList<Map<String, String>>();
        result.add(Map.of());
        for (var row : menus.entrySet()) {
            var next = new ArrayList<Map<String, String>>();
            for (var old : result)
                for (String action : row.getValue()) {
                    var p = new TreeMap<>(old);
                    p.put(row.getKey(), action);
                    next.add(p);
                }
            result = next;
        }
        return result;
    }

    static void collect(Node n, int hero, Map<String, List<String>> rows) {
        if (n.actor() == hero) rows.put(n.key(), n.actions());
        n.children().forEach(c -> collect(c, hero, rows));
    }

    static void oracle(Snapshot game, int hero, Map<String, String> fixed) throws Exception {
        var q = new FiniteTwoPlayerRootSecurityEnvelope.Question(hero, hero + ":type0");
        var owned =
                FiniteTwoPlayerRootSecurityEnvelope.solve(
                        game, q, fixed, 8_000_000, 10000, 4_000_000_000L);
        var a = owned.audit();
        assertEquals(4, a.work().lpSolves());
        assertFalse(a.trainerAdmission());
        var strategies = pure(game.initialState(), hero);
        Node question = game.initialState().children().getFirst();
        double guarantee =
                Math.min(
                        value(game.initialState(), hero, a.fixedHeroChoices(), 0),
                        value(game.initialState(), hero, a.fixedHeroChoices(), 1));
        assertTrue(a.guaranteedGlobalLower() <= guarantee + 1e-8);
        assertEquals(guarantee, a.guaranteedGlobalLower(), 2e-8);
        for (int i = 0; i <= 100; i++) {
            double x = i / 100.;
            double global =
                    strategies.stream()
                            .mapToDouble(s -> value(game.initialState(), hero, s, x))
                            .max()
                            .orElseThrow();
            if (global > a.globalHeroUpperValue() + a.securitySlack() + 1e-10) continue;
            for (var bound : a.actions()) {
                Node child = question.children().get(question.actions().indexOf(bound.action()));
                double best =
                        strategies.stream()
                                .mapToDouble(s -> value(child, hero, s, x))
                                .max()
                                .orElseThrow();
                assertTrue(
                        bound.lowerUtility() <= best + 1e-8,
                        "Lower must bound every optimal conditional continuation");
                assertTrue(
                        bound.upperUtility() >= best - 1e-8,
                        "Upper must bound every optimal conditional continuation");
            }
        }
    }

    @Test
    void exhaustivePurePlanAndOpponentMixtureOraclesCheckEitherHeroAndConstantOffsets()
            throws Exception {
        for (int hero : List.of(0, 1))
            for (double c : List.of(0., 3.)) oracle(model(hero, c, .5), hero, null);
    }

    @Test
    void poorCompleteHeroPolicyWidensTheGuaranteeInsteadOfPretendingToBeNash() throws Exception {
        var game = model(0, 0, .5);
        var fixed = Map.of("0:type0", "safe", "0:type1", "risk");
        oracle(game, 0, fixed);
        var a =
                FiniteTwoPlayerRootSecurityEnvelope.solve(
                                game,
                                new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:type0"),
                                fixed,
                                8_000_000,
                                10000,
                                4_000_000_000L)
                        .audit();
        assertEquals(-4.5, a.guaranteedGlobalLower(), 2e-8);
        assertTrue(a.securityAllowance() > 10);
        assertTrue(
                a.actions().get(1).upperUtility() > 9,
                "Conservative upper is not an attained endpoint");
        assertTrue(
                a.guaranteeWitness().globalHeroBestResponse() > a.globalHeroUpperValue() + 1,
                "Guarantee domain must include opponents outside the security face");
    }

    @Test
    void hiddenWorldsShareOneHeroRootChoiceAndRetainTheirOriginalChanceWeights() throws Exception {
        var game = model(0, 0, .5);
        var first = game.initialState().children().getFirst();
        var second = game.initialState().children().getLast();
        var shared = new Snapshot(2, chance(List.of(.1, .4, .5), first, first, second));
        var a =
                FiniteTwoPlayerRootSecurityEnvelope.solve(
                                shared,
                                new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:type0"))
                        .audit();
        assertEquals(.5, a.questionChanceMass());
        assertEquals(List.of(.1, .4), a.support().stream().map(s -> s.chanceWeight()).toList());
        assertEquals(2, a.fixedHeroChoices().size());
    }

    @Test
    void fixedContinuationIsOnlyALowerBoundOnOptimalHeroPlay() throws Exception {
        var response = node(0, "response", List.of("bad", "good"), leaf(0, -3, 0), leaf(0, 2, 0));
        var opponent = node(1, "opponent", List.of("x", "y"), response, response);
        var first =
                node(
                        0,
                        "type0",
                        List.of("stop", "go"),
                        node(1, "stopper", List.of("x", "y"), leaf(0, 0, 0), leaf(0, 0, 0)),
                        opponent);
        var last =
                node(
                        0,
                        "type1",
                        List.of("stop", "go"),
                        node(1, "other", List.of("x", "y"), leaf(0, 0, 0), leaf(0, 0, 0)),
                        node(1, "other", List.of("x", "y"), leaf(0, 0, 0), leaf(0, 0, 0)));
        var game = new Snapshot(2, chance(List.of(.5, .5), first, last));
        var fixed = Map.of("0:type0", "stop", "0:type1", "stop", "0:response", "bad");
        var a =
                FiniteTwoPlayerRootSecurityEnvelope.solve(
                                game,
                                new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:type0"),
                                fixed,
                                8_000_000,
                                10000,
                                4_000_000_000L)
                        .audit();
        var bound = a.actions().get(1);
        assertEquals(-3, bound.lowerUtility(), 2e-8);
        assertEquals(2, bound.lowerWitness().optimalContinuationUtility(), 1e-8);
        assertTrue(bound.upperUtility() >= 2 - 1e-8);
    }

    @Test
    void rootChanceMassOneIsAllowedWithoutUsingAPaddedProbabilityAsAnActualReach()
            throws Exception {
        var g = model(0, 0, .5);
        var game = new Snapshot(2, g.initialState().children().getFirst());
        var a =
                FiniteTwoPlayerRootSecurityEnvelope.solve(
                                game,
                                new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:type0"))
                        .audit();
        assertEquals(1, a.questionChanceMass());
    }

    @Test
    void laterUnknownAndTooRareQuestionsRejectBeforeEnvelopeOptimization() {
        var g = model(0, 0, .5);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solve(
                                g,
                                new FiniteTwoPlayerRootSecurityEnvelope.Question(1, "1:opponent")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solve(
                                g,
                                new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:absent")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solve(
                                model(0, 0, .00001),
                                new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:type0")));
    }

    @Test
    void malformedPurePoliciesAndImperfectRecallReject() {
        var g = model(0, 0, .5);
        var q = new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:type0");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solve(
                                g, q, Map.of("0:type0", "safe"), 8_000_000, 10000, 4_000_000_000L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solve(
                                g,
                                q,
                                Map.of("0:type0", "unknown", "0:type1", "safe"),
                                8_000_000,
                                10000,
                                4_000_000_000L));
        var repeated = node(0, "response", List.of("a", "b"), leaf(0, 0, 0), leaf(0, 1, 0));
        var bad = node(0, "type0", List.of("left", "right"), repeated, repeated);
        var opponent = node(1, "other", List.of("x", "y"), bad, bad);
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSecurityEnvelope.solve(new Snapshot(2, opponent), q));
    }

    @Test
    void originalCompilerAndLpCapsCanOnlyBeLowered() {
        var g = model(0, 0, .5);
        var q = new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:type0");
        assertThrows(
                FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solve(
                                g, q, null, 0, 10000, 4_000_000_000L));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () -> FiniteTwoPlayerRootSecurityEnvelope.solve(g, q, null, 8_000_000, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solve(
                                g, q, null, 8_000_000, 10001, 4_000_000_000L));
    }

    @Test
    void ownedResultAndItsPurePolicyAreImmutableAndDistinctFromExactIntervals() throws Exception {
        var a =
                FiniteTwoPlayerRootSecurityEnvelope.solve(
                        model(0, 0, .5),
                        new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:type0"));
        assertTrue(
                Arrays.stream(
                                FiniteTwoPlayerRootSecurityEnvelope.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(
                UnsupportedOperationException.class, () -> a.audit().fixedHeroChoices().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.audit().actions().clear());
        assertEquals(FiniteTwoPlayerRootSecurityEnvelope.SCOPE, a.audit().scope());
        assertEquals(FiniteTwoPlayerRootSecurityEnvelope.POLICY, a.audit().fixedPolicyAlgorithm());
    }
}
