package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervalsTest.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;

class RootSequenceMinimaxTest {
    record Control(
            String name,
            Snapshot game,
            double expected,
            FiniteTwoPlayerRootSequenceMinimax.Audit audit) {}

    private static final double EPS = 1e-8;
    private static final String RESOURCE = "/root-sequence-minimax-analytic-controls.json.gz";

    static FiniteTwoPlayerRootActionIntervals.Question question(int hero) {
        return new FiniteTwoPlayerRootActionIntervals.Question(hero, hero + ":root", "risk");
    }

    static Snapshot privateTypes(int hero, int count) {
        int other = hero == 3 ? 5 : 3;
        var types = new ArrayList<Node>();
        var probabilities = new ArrayList<Double>();
        for (int i = 0; i < count; i++) {
            double sign = i % 2 == 0 ? 1 : -1;
            var left =
                    decision(
                            other,
                            "reply",
                            List.of("x", "y"),
                            terminal(hero, sign),
                            terminal(hero, -sign));
            var right =
                    decision(
                            other,
                            "reply",
                            List.of("x", "y"),
                            terminal(hero, -sign),
                            terminal(hero, sign));
            types.add(decision(hero, "private-" + i, List.of("left", "right"), left, right));
            probabilities.add(1.0 / count);
        }
        return new Snapshot(
                6,
                decision(
                        hero,
                        "root",
                        List.of("safe", "risk"),
                        terminal(hero, 10),
                        new Node(-1, "", List.of(), probabilities, types, List.of())));
    }

    static Snapshot quarterReach() {
        return new Snapshot(
                6,
                new Node(
                        -1,
                        "",
                        List.of(),
                        List.of(.25, .75),
                        List.of(
                                dominated(5).initialState(),
                                decision(
                                        5,
                                        "outside",
                                        List.of("a", "b"),
                                        terminal(5, 0),
                                        terminal(5, 0))),
                        List.of()));
    }

    static List<RootMinimaxTest.Control> oldControls() throws Exception {
        try (var stream =
                RootSequenceMinimaxTest.class.getResourceAsStream(
                        "/root-minimax-analytic-controls.json.gz")) {
            assertNotNull(stream);
            byte[] stored = stream.readAllBytes();
            assertEquals(
                    "8c8df3801c1347db6a5e32f322030abd30f24a9bacd389cf170f8982288c25e1",
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(stored)));
            try (var gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(stored))) {
                return List.of(
                        SixMaxTexturePayoffTable.mapper()
                                .readValue(gzip, RootMinimaxTest.Control[].class));
            }
        }
    }

    private static void valid(double expected, FiniteTwoPlayerRootSequenceMinimax.Audit a) {
        assertEquals(FiniteTwoPlayerRootSequenceMinimax.ALGORITHM, a.algorithm());
        assertFalse(a.trainerAdmission());
        assertTrue(a.minimum().lowerBound() <= expected + EPS);
        assertTrue(a.minimum().upperBound() >= expected - EPS);
        assertEquals(expected, a.minimum().witnessUtility(), 2e-7);
        assertEquals(a.minimum().witnessUtility(), a.continuationUtility(), EPS);
        assertEquals(
                1, a.continuationFlow().realization().get(a.heroDomain().forcedSequence()), EPS);
        assertEquals(1, a.work().endpointLpSolves());
        assertEquals(0, a.work().enumeratedContinuationPlans());
        assertTrue(a.work().compilerUnits() <= 8_000_000);
        assertTrue(a.work().endpointArithmeticWork() <= 4_000_000_000L);
        assertTrue(a.work().endpointPivots() <= 10_000);
        var p = a.problem();
        var x = a.witness().point();
        var y = a.witness().dual();
        assertEquals(p.cost().size(), x.size());
        assertEquals(p.rhs().size(), y.size());
        assertTrue(x.stream().allMatch(v -> Double.isFinite(v) && v >= -EPS));
        assertTrue(y.stream().allMatch(v -> Double.isFinite(v) && v >= -EPS));
        for (int i = 0; i < y.size(); i++) {
            double value = 0;
            for (int j = 0; j < x.size(); j++) value += p.matrix().get(i).get(j) * x.get(j);
            assertTrue(value <= p.rhs().get(i) + EPS);
        }
        for (int j = 0; j < x.size(); j++) {
            double value = 0;
            for (int i = 0; i < y.size(); i++) value += p.matrix().get(i).get(j) * y.get(i);
            assertTrue(value >= p.cost().get(j) - EPS);
        }
        double primal = 0, dual = 0;
        for (int j = 0; j < x.size(); j++) primal += p.cost().get(j) * x.get(j);
        for (int i = 0; i < y.size(); i++) dual += p.rhs().get(i) * y.get(i);
        assertEquals(primal, a.witness().certificate().primalValue(), EPS);
        assertEquals(dual, a.witness().certificate().dualValue(), EPS);
        assertEquals(primal, dual, EPS);
        assertEquals(p.objectiveConstant() - dual - EPS, a.minimum().lowerBound(), EPS);
        assertEquals(p.objectiveConstant() - primal, a.minimum().witnessUtility(), EPS);
        assertTrue(
                a.witness().globalHeroBestResponse()
                        <= a.globalHeroUpperValue() + a.securitySlack() + EPS);
    }

    @Test
    void smallControlsAgreeWithSavedCompletePlanAndOriginalIntervalEvidence() throws Exception {
        for (var control : oldControls()) {
            var q = control.audit().question();
            var a = FiniteTwoPlayerRootSequenceMinimax.solve(control.game(), q).audit();
            valid(control.expected(), a);
            assertEquals(control.audit().baseline(), a.baseline());
            assertEquals(control.audit().orientedPayoff(), a.orientedPayoff());
            assertEquals(control.audit().securityFaceHash(), a.securityFaceHash());
            assertEquals(
                    control.audit().minimum().witnessUtility(), a.minimum().witnessUtility(), EPS);
        }
    }

    @Test
    void thirtyTwoPrivateTypesAvoidBillionsOfCompletePlansForEitherHero() throws Exception {
        for (int hero : List.of(3, 5)) {
            var game = privateTypes(hero, 32);
            var a = FiniteTwoPlayerRootSequenceMinimax.solve(game, question(hero)).audit();
            valid(0, a);
            assertEquals(67, a.continuationFlow().sequences().size());
            assertEquals(1, a.opponentProjection().independentSequences().size());
            var rejected =
                    assertThrows(
                            ReducedHeroContinuations.PlanLimit.class,
                            () -> FiniteTwoPlayerRootMinimax.solve(game, question(hero)));
            assertEquals(BigInteger.ONE.shiftLeft(32), rejected.reducedPlans());
            assertEquals(64, FiniteTwoPlayerRootActionIntervals.MAX_CONTINUATION_PLANS);
        }
    }

    @Test
    void chancePosteriorIsNormalizedOnceAndOtherRootsRemainInTheGlobalFace() throws Exception {
        var a = FiniteTwoPlayerRootSequenceMinimax.solve(quarterReach(), question(5)).audit();
        valid(-2, a);
        assertEquals(.25, a.originalQuestionReach());
        assertEquals(1, a.support().getFirst().posteriorProbability());
        assertEquals(.25, a.support().getFirst().originalChanceWeight());
        assertTrue(
                a.continuationFlow().conservation().stream()
                        .anyMatch(c -> c.key().equals("5:outside")));
    }

    @Test
    void aMinimumUpperBracketCannotBeUsedAsTheFullActionCeiling() throws Exception {
        var game = privateTypes(5, 1);
        var a = FiniteTwoPlayerRootSequenceMinimax.solve(game, question(5)).audit();
        valid(0, a);
        var all = FiniteTwoPlayerRootActionIntervals.solve(game, question(5)).audit();
        assertEquals(1, all.upperUtility(), 1e-7);
        assertTrue(a.minimum().upperBound() < .000001);
        assertTrue(a.scope().contains("NOT_FULL_ACTION_INTERVAL"));
    }

    @Test
    void sharedHiddenRootWorldsCannotChooseSeparateHeroContinuations() throws Exception {
        var worlds = new ArrayList<Node>();
        for (double sign : List.of(1., -1.)) {
            var left = decision(3, "forced", List.of("only"), terminal(5, 1 + 2 * sign));
            var right = decision(3, "forced", List.of("only"), terminal(5, 1 - 2 * sign));
            worlds.add(
                    decision(
                            5,
                            "root",
                            List.of("safe", "risk"),
                            terminal(5, 10),
                            decision(5, "continue", List.of("left", "right"), left, right)));
        }
        worlds.add(decision(5, "outside", List.of("a", "b"), terminal(5, 0), terminal(5, 0)));
        var game =
                new Snapshot(
                        6,
                        new Node(-1, "", List.of(), List.of(.125, .125, .75), worlds, List.of()));
        var a = FiniteTwoPlayerRootSequenceMinimax.solve(game, question(5)).audit();
        valid(1, a); // Per-world choices would leak the hidden deal and incorrectly return 3.
        assertEquals(.25, a.originalQuestionReach());
        assertEquals(
                List.of(.5, .5),
                a.support().stream()
                        .map(FiniteTwoPlayerRootMinimax.Support::posteriorProbability)
                        .toList());
        assertEquals(
                1,
                a.continuationFlow().conservation().stream()
                        .filter(c -> c.key().equals("5:continue"))
                        .count());
        assertEquals(
                FiniteTwoPlayerRootMinimax.solve(game, question(5))
                        .audit()
                        .minimum()
                        .witnessUtility(),
                a.minimum().witnessUtility(),
                EPS);
    }

    @Test
    void aTerminalForcedMoveAndZeroIndependentFlowsAreSupported() throws Exception {
        var game = dominated(5);
        var safe = new FiniteTwoPlayerRootActionIntervals.Question(5, "5:root", "safe");
        valid(0, FiniteTwoPlayerRootSequenceMinimax.solve(game, safe).audit());
        var forcedOpponent =
                new Snapshot(
                        6,
                        decision(
                                5,
                                "root",
                                List.of("safe", "risk"),
                                terminal(5, 0),
                                decision(3, "forced", List.of("only"), terminal(5, -3))));
        var a = FiniteTwoPlayerRootSequenceMinimax.solve(forcedOpponent, question(5)).audit();
        valid(-3, a);
        assertTrue(a.opponentProjection().independentSequences().isEmpty());
        var forcedBoth =
                new Snapshot(
                        6,
                        decision(
                                5,
                                "root",
                                List.of("risk"),
                                decision(3, "forced", List.of("only"), terminal(5, -3))));
        valid(-3, FiniteTwoPlayerRootSequenceMinimax.solve(forcedBoth, question(5)).audit());
    }

    @Test
    void lowOriginalReachAndNonRootQuestionsRejectBeforeEndpointWork() {
        var tiny =
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
                FiniteTwoPlayerRootSequenceMinimax.Failure.INSUFFICIENT_REACH,
                assertThrows(
                                FiniteTwoPlayerRootSequenceMinimax.Rejected.class,
                                () -> FiniteTwoPlayerRootSequenceMinimax.solve(tiny, question(5)))
                        .reason());
        var later =
                new Snapshot(
                        6,
                        decision(
                                3,
                                "before",
                                List.of("x", "y"),
                                dominated(5).initialState(),
                                terminal(5, 0)));
        assertEquals(
                FiniteTwoPlayerRootSequenceMinimax.Failure.NON_ROOT_QUESTION,
                assertThrows(
                                FiniteTwoPlayerRootSequenceMinimax.Rejected.class,
                                () -> FiniteTwoPlayerRootSequenceMinimax.solve(later, question(5)))
                        .reason());
    }

    @Test
    void inactiveActorsIllegalMovesAndReducedWorkLimitsCannotCreateAnAudit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSequenceMinimax.solve(dominated(5), question(0)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceMinimax.solve(
                                dominated(5),
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        5, "5:root", "missing")));
        assertThrows(
                FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                () ->
                        FiniteTwoPlayerRootSequenceMinimax.solve(
                                dominated(5), question(5), 1e-8, .0001, 0, 10000, 4_000_000_000L));
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        FiniteTwoPlayerRootSequenceMinimax.solve(
                                                privateTypes(5, 1),
                                                question(5),
                                                1e-8,
                                                .0001,
                                                8_000_000,
                                                10000,
                                                0))
                        .reason());
    }

    @Test
    void capsNonfiniteInputsAndReachCannotBeRelaxed() {
        for (double slack : List.of(Double.NaN, Double.POSITIVE_INFINITY, 0., .001))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerRootSequenceMinimax.solve(
                                    dominated(5),
                                    question(5),
                                    slack,
                                    .0001,
                                    8_000_000,
                                    10000,
                                    4_000_000_000L));
        for (double reach : List.of(Double.NaN, .00001, 1.1))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerRootSequenceMinimax.solve(
                                    dominated(5),
                                    question(5),
                                    1e-8,
                                    reach,
                                    8_000_000,
                                    10000,
                                    4_000_000_000L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceMinimax.solve(
                                dominated(5),
                                question(5),
                                1e-8,
                                .0001,
                                8_000_001,
                                10000,
                                4_000_000_000L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceMinimax.solve(
                                dominated(5),
                                question(5),
                                1e-8,
                                .0001,
                                8_000_000,
                                10001,
                                4_000_000_000L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceMinimax.solve(
                                dominated(5),
                                question(5),
                                1e-8,
                                .0001,
                                8_000_000,
                                10000,
                                4_000_000_001L));
    }

    @Test
    void auditCollectionsAndOwnedConstructorAreImmutable() throws Exception {
        var a = FiniteTwoPlayerRootSequenceMinimax.solve(dominated(5), question(5)).audit();
        assertThrows(
                UnsupportedOperationException.class,
                () -> a.heroDomain().matrix().getFirst().add(0.));
        assertThrows(UnsupportedOperationException.class, () -> a.problem().matrix().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.witness().point().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> a.continuationFlow().realization().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.support().clear());
        assertTrue(
                Arrays.stream(
                                FiniteTwoPlayerRootSequenceMinimax.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void completeSavedAuditsReplayExactly() throws Exception {
        try (var stream = getClass().getResourceAsStream(RESOURCE)) {
            assertNotNull(stream);
            byte[] stored = stream.readNBytes(8 * 1024 * 1024 + 1);
            assertTrue(stored.length <= 8 * 1024 * 1024);
            assertEquals(
                    "7fde2e928d0a05330b6466264fd057b7d1ffae4f5411eb291046eae019efe51c",
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(stored)));
            try (var gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(stored))) {
                byte[] expanded = gzip.readNBytes(8 * 1024 * 1024 + 1);
                assertTrue(expanded.length <= 8 * 1024 * 1024);
                var controls =
                        SixMaxTexturePayoffTable.mapper().readValue(expanded, Control[].class);
                assertEquals(8, controls.length);
                for (var c : controls) {
                    var audit =
                            FiniteTwoPlayerRootSequenceMinimax.solve(c.game(), c.audit().question())
                                    .audit();
                    assertEquals(c.audit(), audit);
                    valid(c.expected(), audit);
                }
            }
        }
    }

    @Test
    void anIndependentForcedRootSequenceHasTheSameMinimum() throws Exception {
        var root = privateTypes(5, 2).initialState();
        var reversed =
                new Snapshot(
                        6,
                        decision(
                                5,
                                "root",
                                List.of("risk", "safe"),
                                root.children().getLast(),
                                root.children().getFirst()));
        var a = FiniteTwoPlayerRootSequenceMinimax.solve(reversed, question(5)).audit();
        valid(0, a);
        assertEquals(0, a.heroProjection().offset().get(a.heroDomain().forcedSequence()));
        assertEquals(
                FiniteTwoPlayerRootMinimax.solve(reversed, question(5))
                        .audit()
                        .minimum()
                        .witnessUtility(),
                a.minimum().witnessUtility(),
                EPS);
    }

    @Test
    void strongerReachAndTheOriginalSequenceLimitsStillReject() {
        assertEquals(
                FiniteTwoPlayerRootSequenceMinimax.Failure.INSUFFICIENT_REACH,
                assertThrows(
                                FiniteTwoPlayerRootSequenceMinimax.Rejected.class,
                                () ->
                                        FiniteTwoPlayerRootSequenceMinimax.solve(
                                                quarterReach(),
                                                question(5),
                                                1e-8,
                                                .5,
                                                8_000_000,
                                                10000,
                                                4_000_000_000L))
                        .reason());
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSequenceMinimax.solve(privateTypes(5, 64), question(5)));
        assertEquals(129, MAX_SEQUENCES_PER_ACTOR);
        assertEquals(64, MAX_INFORMATION_SETS_PER_ACTOR);
    }

    @Test
    void aZeroPivotBudgetFailsWithoutChangingRepresentationOrRetrying() {
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(
                                BoundedLinearProgram.Rejected.class,
                                () ->
                                        FiniteTwoPlayerRootSequenceMinimax.solve(
                                                privateTypes(5, 1),
                                                question(5),
                                                1e-8,
                                                .0001,
                                                8_000_000,
                                                0,
                                                4_000_000_000L))
                        .reason());
    }

    @Test
    void malformedConstantSumAndThreeActorGamesReject() {
        var nonconstant = new ArrayList<>(terminal(5, 1).utilities());
        nonconstant.set(3, 50.);
        var bad = new Node(-2, "", List.of(), List.of(), List.of(), nonconstant);
        var risk = decision(3, "reply", List.of("x", "y"), bad, terminal(5, 0));
        var game =
                new Snapshot(6, decision(5, "root", List.of("safe", "risk"), terminal(5, 0), risk));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSequenceMinimax.solve(game, question(5)));
        var third = decision(4, "third", List.of("a", "b"), terminal(5, 1), terminal(5, 0));
        var three =
                new Snapshot(
                        6,
                        decision(
                                5,
                                "root",
                                List.of("safe", "risk"),
                                terminal(5, 0),
                                decision(3, "reply", List.of("x", "y"), third, terminal(5, 0))));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSequenceMinimax.solve(three, question(5)));
    }

    @Test
    void parsedAuditJsonCannotManufactureAnOwnedResult() throws Exception {
        var result = FiniteTwoPlayerRootSequenceMinimax.solve(dominated(5), question(5));
        var mapper = SixMaxTexturePayoffTable.mapper();
        byte[] evidence = mapper.writeValueAsBytes(Map.of("audit", result.audit()));
        assertThrows(
                Exception.class,
                () -> mapper.readValue(evidence, FiniteTwoPlayerRootSequenceMinimax.Result.class));
    }

    private static Node shiftHero(Node node, int hero, double delta) {
        if (node.actor() == -2) {
            var utilities = new ArrayList<>(node.utilities());
            utilities.set(hero, utilities.get(hero) + delta);
            return new Node(-2, "", List.of(), List.of(), List.of(), utilities);
        }
        return new Node(
                node.actor(),
                node.key(),
                node.actions(),
                node.probabilities(),
                node.children().stream().map(child -> shiftHero(child, hero, delta)).toList(),
                node.utilities());
    }

    @Test
    void positiveAndNegativeUtilityOffsetsPreserveTheMinimumForBothOrientations() throws Exception {
        for (int hero : List.of(3, 5))
            for (double delta : List.of(-2., 7.)) {
                var game =
                        new Snapshot(
                                6, shiftHero(privateTypes(hero, 32).initialState(), hero, delta));
                var a = FiniteTwoPlayerRootSequenceMinimax.solve(game, question(hero)).audit();
                valid(delta, a);
                assertEquals(4 + delta, a.baseline().constantSum());
            }
    }

    @Test
    void aHeroCannotForgetItsOwnActionAcrossSharedContinuationInformation() {
        var repeated =
                decision(
                        5,
                        "forgotten",
                        List.of("a", "b"),
                        decision(3, "reply", List.of("x", "y"), terminal(5, 1), terminal(5, -1)),
                        terminal(5, 0));
        var game =
                new Snapshot(
                        6,
                        decision(
                                5,
                                "root",
                                List.of("safe", "risk"),
                                terminal(5, 10),
                                decision(
                                        5,
                                        "choice",
                                        List.of("left", "right"),
                                        repeated,
                                        repeated)));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSequenceMinimax.solve(game, question(5)));
    }

    @Test
    void allParentAndEndpointChecksUseTheSingleImmutableInputSnapshot() throws Exception {
        var original = dominated(5);
        var calls = new int[2];
        MultiPlayerCfrGame<Node> once =
                new MultiPlayerCfrGame<>() {
                    public int playerCount() {
                        return original.playerCount();
                    }

                    public Node initialState() {
                        if (++calls[0] > 1) throw new IllegalStateException("Input read again");
                        return original.initialState();
                    }

                    public boolean isTerminal(Node n) {
                        return original.isTerminal(n);
                    }

                    public double[] terminalUtilities(Node n) {
                        calls[1]++;
                        return original.terminalUtilities(n);
                    }

                    public int currentPlayer(Node n) {
                        return original.currentPlayer(n);
                    }

                    public List<String> legalActions(Node n) {
                        return original.legalActions(n);
                    }

                    public String informationSet(Node n) {
                        return original.informationSet(n);
                    }

                    public Node afterAction(Node n, String a) {
                        return original.afterAction(n, a);
                    }

                    public List<ChanceOutcome<Node>> chanceOutcomes(Node n) {
                        return original.chanceOutcomes(n);
                    }
                };
        valid(-2, FiniteTwoPlayerRootSequenceMinimax.solve(once, question(5)).audit());
        assertArrayEquals(new int[] {1, 3}, calls);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("One new analytic capture path required");
        var output = Path.of(args[0]);
        if (Files.exists(output)) throw new FileAlreadyExistsException(output.toString());
        var controls = new ArrayList<Control>();
        for (var c : oldControls())
            controls.add(
                    new Control(
                            c.name(),
                            c.game(),
                            c.expected(),
                            FiniteTwoPlayerRootSequenceMinimax.solve(c.game(), c.audit().question())
                                    .audit()));
        for (int hero : List.of(3, 5)) {
            var game = privateTypes(hero, 32);
            controls.add(
                    new Control(
                            "private32-" + hero,
                            game,
                            0,
                            FiniteTwoPlayerRootSequenceMinimax.solve(game, question(hero))
                                    .audit()));
        }
        var quarter = quarterReach();
        controls.add(
                new Control(
                        "quarter-5",
                        quarter,
                        -2,
                        FiniteTwoPlayerRootSequenceMinimax.solve(quarter, question(5)).audit()));
        for (var c : controls) valid(c.expected(), c.audit());
        try (var stream =
                new GZIPOutputStream(
                        Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
            stream.write(SixMaxTexturePayoffTable.mapper().writeValueAsBytes(controls));
        }
        System.out.println(
                "Saved eight analytic captures; no physical poker game or continuation enumeration");
    }
}
