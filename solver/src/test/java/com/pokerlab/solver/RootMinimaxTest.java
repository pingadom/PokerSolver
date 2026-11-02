package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervalsTest.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class RootMinimaxTest {
    record Control(
            String name, Snapshot game, double expected, FiniteTwoPlayerRootMinimax.Audit audit) {}

    private static final double EPS = 1e-8;

    private static FiniteTwoPlayerRootActionIntervals.Question question(int hero) {
        return new FiniteTwoPlayerRootActionIntervals.Question(hero, hero + ":root", "risk");
    }

    private static void encloses(double expected, FiniteTwoPlayerRootMinimax.Audit a) {
        assertTrue(a.minimum().lowerBound() <= expected + EPS);
        assertTrue(a.minimum().upperBound() >= expected - EPS);
        assertEquals(expected, a.minimum().witnessUtility(), 2e-7);
        assertTrue(a.minimum().upperBound() - a.minimum().lowerBound() < 5e-7);
        assertEquals(1, a.work().endpointLpSolves());
        assertFalse(a.trainerAdmission());
        certificate(a);
    }

    /**
     * Independent dot products on the original unscaled constraints, not solver residual fields.
     */
    private static void certificate(FiniteTwoPlayerRootMinimax.Audit a) {
        var p = a.problem();
        var x = a.witness().point();
        var y = a.witness().dual();
        assertEquals(p.cost().size(), x.size());
        assertEquals(p.rhs().size(), y.size());
        assertTrue(x.stream().allMatch(v -> Double.isFinite(v) && v >= -EPS));
        assertTrue(y.stream().allMatch(v -> Double.isFinite(v) && v >= -EPS));
        for (int i = 0; i < y.size(); i++) {
            double total = 0;
            for (int j = 0; j < x.size(); j++) total += p.matrix().get(i).get(j) * x.get(j);
            assertTrue(total <= p.rhs().get(i) + EPS);
        }
        for (int j = 0; j < x.size(); j++) {
            double total = 0;
            for (int i = 0; i < y.size(); i++) total += p.matrix().get(i).get(j) * y.get(i);
            assertTrue(total >= p.cost().get(j) - EPS);
        }
        double primal = 0, dual = 0;
        for (int j = 0; j < x.size(); j++) primal += p.cost().get(j) * x.get(j);
        for (int i = 0; i < y.size(); i++) dual += p.rhs().get(i) * y.get(i);
        assertEquals(primal, a.witness().certificate().primalValue(), EPS);
        assertEquals(dual, a.witness().certificate().dualValue(), EPS);
        assertEquals(primal, dual, EPS);
        assertEquals(
                BoundedLinearProgram.PHASE_ONE_REFINED_BASIS_ALGORITHM,
                a.witness().certificate().algorithm());
        assertEquals(
                a.opponentProjection().independentSequences().size()
                        + a.heroProjection().rhs().size()
                        + 1,
                x.size());
        assertTrue(
                a.witness().globalHeroBestResponse()
                        <= a.globalHeroUpperValue() + a.securitySlack() + EPS);
    }

    @Test
    void dominatedActionsMatchBothExistingEndpointCompilersForEitherHero() throws Exception {
        for (int h : List.of(3, 5)) {
            var a = FiniteTwoPlayerRootMinimax.solve(dominated(h), question(h)).audit();
            encloses(-2, a);
            var old = FiniteTwoPlayerRootActionIntervals.solve(dominated(h), question(h)).audit();
            var conditional =
                    FiniteTwoPlayerConditionalActionIntervals.solve(
                                    dominated(h),
                                    new FiniteTwoPlayerConditionalActionIntervals.Question(
                                            h, h + ":root", "risk"))
                            .audit();
            assertEquals(old.lowerUtility(), a.minimum().witnessUtility(), EPS);
            assertEquals(conditional.lowerUtility(), a.minimum().witnessUtility(), EPS);
            assertEquals(1, a.originalQuestionReach());
        }
    }

    private static Snapshot penny(int h, double safe) {
        int o = h == 3 ? 5 : 3;
        var left = decision(o, "reply", List.of("x", "y"), terminal(h, 1), terminal(h, -1));
        var right = decision(o, "reply", List.of("x", "y"), terminal(h, -1), terminal(h, 1));
        return new Snapshot(
                6,
                decision(
                        h,
                        "root",
                        List.of("safe", "risk"),
                        terminal(h, safe),
                        decision(h, "continue", List.of("left", "right"), left, right)));
    }

    @Test
    void theMinimaxUsesOneSharedMixedOpponentRatherThanMinimizingEachPlanSeparately()
            throws Exception {
        for (int h : List.of(3, 5)) {
            var a = FiniteTwoPlayerRootMinimax.solve(penny(h, 10), question(h)).audit();
            encloses(0, a);
            assertEquals(2, a.plans().size());
            // Both pure plans individually have minimum -1, but min_p max_plan is zero at p=1/2.
            assertTrue(a.minimum().lowerBound() > -.01);
        }
    }

    @Test
    void anUpperBracketOnTheMinimumIsNotAnUpperBoundOnTheWholeActionRange() throws Exception {
        var game = penny(5, 10);
        var a = FiniteTwoPlayerRootMinimax.solve(game, question(5)).audit();
        var all = FiniteTwoPlayerRootActionIntervals.solve(game, question(5)).audit();
        encloses(0, a);
        assertEquals(1, all.upperUtility(), EPS);
        assertTrue(all.upperUtility() > a.minimum().upperBound() + .9);
        assertTrue(a.scope().contains("NOT_FULL_ACTION_INTERVAL"));
    }

    @Test
    void aUniqueSecurityFaceOpponentRetainsTheOriginalSlack() throws Exception {
        for (int h : List.of(3, 5)) {
            int o = h == 3 ? 5 : 3;
            var game =
                    new Snapshot(
                            6,
                            decision(
                                    h,
                                    "root",
                                    List.of("risk", "other"),
                                    decision(
                                            o,
                                            "reply",
                                            List.of("x", "y"),
                                            terminal(h, 1),
                                            terminal(h, -1)),
                                    decision(
                                            o,
                                            "reply",
                                            List.of("x", "y"),
                                            terminal(h, -1),
                                            terminal(h, 1))));
            var a = FiniteTwoPlayerRootMinimax.solve(game, question(h), 1e-6, .0001).audit();
            encloses(-1e-6, a);
            assertEquals(1e-6, a.securitySlack());
        }
    }

    @Test
    void hiddenWorldsDoNotGiveTheHeroPrivateInformation() throws Exception {
        for (int h : List.of(3, 5)) {
            int o = h == 3 ? 5 : 3;
            var worlds = new ArrayList<Node>();
            for (int hidden = 0; hidden < 2; hidden++) {
                var replies = new ArrayList<Node>();
                for (int r = 0; r < 2; r++)
                    replies.add(
                            decision(
                                    h,
                                    "guess",
                                    List.of("heads", "tails"),
                                    terminal(h, (hidden == 0 ? -1 : -3) - r),
                                    terminal(h, (hidden == 1 ? -1 : -3) - r)));
                worlds.add(
                        decision(
                                h,
                                "root",
                                List.of("safe", "risk"),
                                terminal(h, 0),
                                decision(
                                        o,
                                        "hidden-reply",
                                        List.of("x", "y"),
                                        replies.toArray(Node[]::new))));
            }
            var game =
                    new Snapshot(
                            6, new Node(-1, "", List.of(), List.of(.5, .5), worlds, List.of()));
            var a = FiniteTwoPlayerRootMinimax.solve(game, question(h)).audit();
            encloses(-3, a);
            assertEquals(2, a.plans().size());
            assertEquals(2, a.support().size());
            assertEquals(
                    List.of(.5, .5),
                    a.support().stream()
                            .map(FiniteTwoPlayerRootMinimax.Support::posteriorProbability)
                            .toList());
        }
    }

    @Test
    void originalChanceReachIsNormalizedExactlyOnce() throws Exception {
        var selected = dominated(5).initialState();
        var outside = decision(5, "outside", List.of("a", "b"), terminal(5, 0), terminal(5, 0));
        var game =
                new Snapshot(
                        6,
                        new Node(
                                -1,
                                "",
                                List.of(),
                                List.of(.25, .75),
                                List.of(selected, outside),
                                List.of()));
        var a = FiniteTwoPlayerRootMinimax.solve(game, question(5)).audit();
        encloses(-2, a);
        assertEquals(.25, a.originalQuestionReach());
        assertEquals(1, a.support().getFirst().posteriorProbability());
        assertEquals(.25, a.support().getFirst().originalChanceWeight());
        assertEquals(
                FiniteTwoPlayerRootMinimax.Failure.INSUFFICIENT_REACH,
                assertThrows(
                                FiniteTwoPlayerRootMinimax.Rejected.class,
                                () -> FiniteTwoPlayerRootMinimax.solve(game, question(5), 1e-8, .5))
                        .reason());
    }

    @Test
    void unreachableOwnChoicesAreReducedWithoutDeletingOpponentActions() throws Exception {
        int h = 5, o = 3;
        var stop = decision(o, "reply", List.of("x", "y"), terminal(h, -4), terminal(h, -3));
        var left = decision(o, "reply", List.of("x", "y"), terminal(h, -2), terminal(h, -4));
        var right = decision(o, "reply", List.of("x", "y"), terminal(h, -4), terminal(h, 0));
        var game =
                new Snapshot(
                        6,
                        decision(
                                h,
                                "root",
                                List.of("safe", "risk"),
                                terminal(h, 0),
                                decision(
                                        h,
                                        "after",
                                        List.of("stop", "go"),
                                        new Node(
                                                -1,
                                                "",
                                                List.of(),
                                                List.of(1.0),
                                                List.of(stop),
                                                List.of()),
                                        decision(
                                                h,
                                                "last",
                                                List.of("left", "right"),
                                                left,
                                                right))));
        var a = FiniteTwoPlayerRootMinimax.solve(game, question(h)).audit();
        encloses(-8.0 / 3, a);
        assertEquals(4, a.cartesianPlans().intValueExact());
        assertEquals(3, a.reducedPlans().intValueExact());
        assertTrue(a.plans().stream().anyMatch(p -> !p.continuation().containsKey("5:last")));
        var old = FiniteTwoPlayerRootActionIntervals.solve(game, question(h)).audit();
        assertEquals(old.lowerUtility(), a.minimum().witnessUtility(), EPS);
    }

    @Test
    void independentHeroBranchesMultiplyTheirPlanChoices() throws Exception {
        var game =
                new Snapshot(
                        6,
                        decision(
                                5,
                                "root",
                                List.of("safe", "risk"),
                                terminal(5, 10),
                                decision(
                                        3,
                                        "branch",
                                        List.of("x", "y"),
                                        decision(
                                                5,
                                                "after-x",
                                                List.of("a", "b"),
                                                terminal(5, 2),
                                                terminal(5, 4)),
                                        decision(
                                                5,
                                                "after-y",
                                                List.of("a", "b"),
                                                terminal(5, 1),
                                                terminal(5, 3)))));
        var a = FiniteTwoPlayerRootMinimax.solve(game, question(5)).audit();
        encloses(3, a);
        assertEquals(4, a.reducedPlans().intValueExact());
        assertTrue(a.plans().stream().allMatch(p -> p.continuation().size() == 2));
    }

    @Test
    void laterOrUnknownQuestionsCannotUseConstantReach() {
        var later =
                new Snapshot(
                        6,
                        decision(
                                3,
                                "signal",
                                List.of("show", "hide"),
                                dominated(5).initialState(),
                                terminal(5, 0)));
        assertEquals(
                FiniteTwoPlayerRootMinimax.Failure.NON_ROOT_QUESTION,
                assertThrows(
                                FiniteTwoPlayerRootMinimax.Rejected.class,
                                () -> FiniteTwoPlayerRootMinimax.solve(later, question(5)))
                        .reason());
        assertEquals(
                FiniteTwoPlayerRootMinimax.Failure.NON_ROOT_QUESTION,
                assertThrows(
                                FiniteTwoPlayerRootMinimax.Rejected.class,
                                () ->
                                        FiniteTwoPlayerRootMinimax.solve(
                                                dominated(5),
                                                new FiniteTwoPlayerRootActionIntervals.Question(
                                                        5, "5:missing", "risk")))
                        .reason());
    }

    private static Snapshot many(boolean extra) {
        var choices = new ArrayList<String>();
        var children = new ArrayList<Node>();
        for (int i = 0; i < 6; i++) {
            choices.add("a" + i);
            children.add(
                    decision(
                            5, "choose-" + i, List.of("x", "y"), terminal(5, -1), terminal(5, -1)));
        }
        Node continuation = decision(3, "branch", choices, children.toArray(Node[]::new));
        if (extra)
            continuation =
                    decision(
                            5,
                            "optional",
                            List.of("quit", "continue"),
                            terminal(5, -1),
                            continuation);
        return new Snapshot(
                6, decision(5, "root", List.of("safe", "risk"), terminal(5, 0), continuation));
    }

    @Test
    void theOriginal64PlanBoundaryIsPreserved() throws Exception {
        var a = FiniteTwoPlayerRootMinimax.solve(many(false), question(5)).audit();
        encloses(-1, a);
        assertEquals(64, a.reducedPlans().intValueExact());
        var failure =
                assertThrows(
                        ReducedHeroContinuations.PlanLimit.class,
                        () -> FiniteTwoPlayerRootMinimax.solve(many(true), question(5)));
        assertEquals(65, failure.reducedPlans().intValueExact());
    }

    @Test
    void reducedCompilerAndLpBudgetsRejectWithoutReturningAResult() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootMinimax.solve(
                                dominated(5), question(5), 1e-8, .0001, 0, 10000, 4000000000L));
        for (int pivots : List.of(0, 10000)) {
            var failure =
                    assertThrows(
                            BoundedLinearProgram.Rejected.class,
                            () ->
                                    FiniteTwoPlayerRootMinimax.solve(
                                            dominated(5),
                                            question(5),
                                            1e-8,
                                            .0001,
                                            8000000,
                                            pivots,
                                            pivots == 0 ? 4000000000L : 0));
            assertEquals(BoundedLinearProgram.Failure.WORK_LIMIT, failure.reason());
        }
    }

    @Test
    void callersCannotExpandLimitsOrForgeAnOwnedResult() {
        for (double slack : List.of(Double.NaN, 0.0, .001))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerRootMinimax.solve(
                                    dominated(5), question(5), slack, .0001));
        for (double reach : List.of(Double.NaN, 0.0, 1.1))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FiniteTwoPlayerRootMinimax.solve(dominated(5), question(5), 1e-8, reach));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootMinimax.solve(
                                dominated(5),
                                question(5),
                                1e-8,
                                .0001,
                                8000000,
                                10001,
                                4000000000L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootMinimax.solve(
                                dominated(5),
                                question(5),
                                1e-8,
                                .0001,
                                8000000,
                                10000,
                                4000000001L));
        assertTrue(
                Arrays.stream(FiniteTwoPlayerRootMinimax.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void theOwnedAuditIsDeeplyImmutableAndRepeatable() throws Exception {
        var first = FiniteTwoPlayerRootMinimax.solve(dominated(5), question(5)).audit();
        var second = FiniteTwoPlayerRootMinimax.solve(dominated(5), question(5)).audit();
        assertEquals(first, second);
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.problem().matrix().getFirst().set(0, 100.0));
        assertThrows(UnsupportedOperationException.class, () -> first.problem().rhs().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.support().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.plans().getFirst().continuation().put("5:fake", "a"));
        assertThrows(UnsupportedOperationException.class, () -> first.witness().point().clear());
    }

    @Test
    void allFiveIndependentlyCheckedControlAuditsReplayExactly() throws Exception {
        try (var stream =
                getClass().getResourceAsStream("/root-minimax-analytic-controls.json.gz")) {
            assertNotNull(stream);
            var stored = stream.readNBytes(8 * 1024 * 1024 + 1);
            assertEquals(
                    "8c8df3801c1347db6a5e32f322030abd30f24a9bacd389cf170f8982288c25e1",
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(stored)));
            try (var gzip =
                    new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(stored))) {
                var bytes = gzip.readNBytes(8 * 1024 * 1024 + 1);
                assertTrue(bytes.length <= 8 * 1024 * 1024);
                var controls = SixMaxTexturePayoffTable.mapper().readValue(bytes, Control[].class);
                assertEquals(
                        List.of("dominated-3", "shared-3", "dominated-5", "shared-5", "reduced-5"),
                        Arrays.stream(controls).map(Control::name).toList());
                for (var control : controls) {
                    var result =
                            FiniteTwoPlayerRootMinimax.solve(
                                    control.game(), control.audit().question());
                    assertEquals(control.audit(), result.audit());
                    encloses(control.expected(), result.audit());
                }
            }
        }
    }

    @Test
    void aForcedOpponentWithNoIndependentFlowVariablesStillHasAnEndpoint() throws Exception {
        var game =
                new Snapshot(
                        6,
                        decision(
                                5,
                                "root",
                                List.of("safe", "risk"),
                                terminal(5, 0),
                                decision(3, "forced", List.of("only"), terminal(5, -3))));
        var a = FiniteTwoPlayerRootMinimax.solve(game, question(5)).audit();
        encloses(-3, a);
        assertTrue(a.opponentProjection().independentSequences().isEmpty());
    }

    @Test
    void lowChanceMassInactiveActorsAndIllegalActionsRejectBeforeEndpointWork() {
        var game =
                new Snapshot(
                        6,
                        new Node(
                                -1,
                                "",
                                List.of(),
                                List.of(.00005, .99995),
                                List.of(dominated(5).initialState(), terminal(5, 0)),
                                List.of()));
        assertEquals(
                FiniteTwoPlayerRootMinimax.Failure.INSUFFICIENT_REACH,
                assertThrows(
                                FiniteTwoPlayerRootMinimax.Rejected.class,
                                () -> FiniteTwoPlayerRootMinimax.solve(game, question(5)))
                        .reason());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootMinimax.solve(
                                dominated(5),
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        0, "0:root", "risk")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootMinimax.solve(
                                dominated(5),
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        5, "5:root", "missing")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootMinimax.solve(
                                dominated(5),
                                question(5),
                                1e-8,
                                .0001,
                                8000001,
                                10000,
                                4000000000L));
    }
}
