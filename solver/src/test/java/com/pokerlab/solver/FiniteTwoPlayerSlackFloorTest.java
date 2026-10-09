package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;

class FiniteTwoPlayerSlackFloorTest {
    @Test
    void analyticValuesAndOriginalQualityKeepTheirSeparateMeanings() throws Exception {
        for (double floor : List.of(.01, .001, .0001, .00001, .000001)) {
            var game = new FiniteTwoPlayerBehaviorFloorTest.Dominance();
            var r = FiniteTwoPlayerSlackFloor.solve(game, floor);
            assertEquals(1, game.roots);
            assertEquals(4, game.terminals);
            assertEquals(2 - floor, r.audit().constrainedLowerValue(), 1e-10);
            assertEquals(3 * floor, r.audit().originalGameQuality().nashConvBb(), 1e-10);
            audit(r);
            FiniteTwoPlayerBehaviorFloorTest.literalCertificate(
                    new FiniteTwoPlayerBehaviorFloorTest.Dominance(),
                    new CfrSolution(1, r.strategy()),
                    floor,
                    r.audit().constrainedQuality());
        }
    }

    @Test
    void repeatedOwnActionsHavePositiveMassAndIndependentLiteralResponse() throws Exception {
        for (double floor : List.of(.01, .0001, .000001)) {
            var game = new FiniteTwoPlayerBehaviorFloorTest.Recall(true);
            var r = FiniteTwoPlayerSlackFloor.solve(game, floor);
            audit(r);
            FiniteTwoPlayerBehaviorFloorTest.literalCertificate(
                    game, new CfrSolution(1, r.strategy()), floor, r.audit().constrainedQuality());
            assertEquals(
                    FiniteTwoPlayerBehaviorFloor.solve(game, floor).audit().constrainedLowerValue(),
                    r.audit().constrainedLowerValue(),
                    1e-9);
        }
    }

    @Test
    void hiddenChanceInformationHasOneIntentPerPrivateType() throws Exception {
        for (int types : List.of(2, 4)) {
            var game = new FiniteTwoPlayerAffineSequenceFormTest.Hidden(types);
            var r = FiniteTwoPlayerSlackFloor.solve(game, .000001);
            audit(r);
            FiniteTwoPlayerBehaviorFloorTest.literalCertificate(
                    game,
                    new CfrSolution(1, r.strategy()),
                    .000001,
                    r.audit().constrainedQuality());
        }
    }

    @Test
    void largeHiddenSupportFitsUnchangedDimensionsWithoutPurePlanEnumeration() throws Exception {
        var r =
                FiniteTwoPlayerSlackFloor.solve(
                        new FiniteTwoPlayerAffineSequenceFormTest.Hidden(32), .001);
        assertEquals(64, r.strategy().size());
        assertEquals(32, r.audit().firstProjection().freeSequences().size());
        assertEquals(32, r.audit().firstProjection().inequalities().size());
        audit(r);
    }

    @Test
    void actualCardsAllFiveFloorsMatchIndependentOriginalFullFlowOracle() throws Exception {
        var source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        var game =
                new SixMaxHeadsUpPreflopGame(
                        source, SixMaxHeadsUpPreflopStudyTest.specification("five-target"));
        try (var raw = getClass().getResourceAsStream("/behavior-floor-literal-control.json.gz");
                var zip = new java.util.zip.GZIPInputStream(Objects.requireNonNull(raw))) {
            var fixture = SixMaxTexturePayoffTable.mapper().readTree(zip);
            assertEquals(5, fixture.path("slackControls").size());
            for (var control : fixture.path("slackControls")) {
                double floor = control.path("minimumActionProbability").asDouble();
                var r = FiniteTwoPlayerSlackFloor.solve(game, floor);
                assertEquals(
                        control.path("constrainedLowerValue").asDouble(),
                        r.audit().constrainedLowerValue(),
                        1e-10);
                assertEquals(
                        control.path("constrainedUpperValue").asDouble(),
                        r.audit().constrainedUpperValue(),
                        1e-10);
                audit(r);
                FiniteTwoPlayerBehaviorFloorTest.literalCertificate(
                        game,
                        new CfrSolution(1, r.strategy()),
                        floor,
                        r.audit().constrainedQuality());
            }
        }
        var smallest = FiniteTwoPlayerSlackFloor.solve(game, .000001);
        assertEquals(.0000040994571161, smallest.audit().originalGameQuality().nashConvBb(), 1e-9);
    }

    @Test
    void reorderedActionsHaveTheirOwnIdentityAndTheSameConstrainedValue() throws Exception {
        var game = new FiniteTwoPlayerBehaviorFloorTest.Dominance();
        var original = FiniteTwoPlayerSlackFloor.solve(game, .001);
        var reverse =
                FiniteTwoPlayerSlackFloor.solve(
                        new FiniteTwoPlayerBehaviorFloorTest.Dominance() {
                            public List<String> legalActions(List<String> s) {
                                return List.of("b", "a");
                            }
                        },
                        .001);
        assertNotEquals(original.audit().snapshotHash(), reverse.audit().snapshotHash());
        assertNotEquals(original.audit().reductionHash(), reverse.audit().reductionHash());
        assertEquals(
                original.audit().constrainedLowerValue(),
                reverse.audit().constrainedLowerValue(),
                1e-10);
        for (String key : original.strategy().keySet())
            for (String action : original.strategy().get(key).keySet())
                assertEquals(
                        original.strategy().get(key).get(action),
                        reverse.strategy().get(key).get(action),
                        1e-10);
    }

    @Test
    void capsInvalidFloorsAndImperfectRecallStillFailClosed() {
        for (double floor : new double[] {0, .011, 1e-7, Double.NaN, Double.POSITIVE_INFINITY}) {
            var game = new FiniteTwoPlayerBehaviorFloorTest.Dominance();
            assertThrows(
                    FiniteTwoPlayerBehaviorFloor.Rejected.class,
                    () -> FiniteTwoPlayerSlackFloor.solve(game, floor));
            assertEquals(0, game.roots);
        }
        assertEquals(
                FiniteTwoPlayerBehaviorFloor.Failure.WORK_LIMIT,
                assertThrows(
                                FiniteTwoPlayerBehaviorFloor.Rejected.class,
                                () ->
                                        FiniteTwoPlayerSlackFloor.solve(
                                                new FiniteTwoPlayerBehaviorFloorTest.Dominance(),
                                                .01,
                                                1))
                        .reason());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerSlackFloor.solve(
                                new FiniteTwoPlayerBehaviorFloorTest.Recall(false), .001));
    }

    @Test
    void auditsAreImmutableAndCannotManufactureASolvedHandle() throws Exception {
        var r =
                FiniteTwoPlayerSlackFloor.solve(
                        new FiniteTwoPlayerBehaviorFloorTest.Dominance(), .001);
        assertThrows(UnsupportedOperationException.class, () -> r.strategy().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> r.audit().firstProjection().transform().getFirst().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> r.audit().firstBehavior().original().realization().clear());
        assertTrue(
                Arrays.stream(FiniteTwoPlayerSlackFloor.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        assertNotEquals(FiniteTwoPlayerBehaviorFloor.ALGORITHM, r.audit().algorithm());
    }

    static void audit(FiniteTwoPlayerSlackFloor.Result r) {
        var a = r.audit();
        assertEquals(FiniteTwoPlayerSlackFloor.ALGORITHM, a.algorithm());
        assertTrue(a.constrainedQuality().nashConvBb() <= 1e-8);
        assertTrue(a.compilationWork().chargedUnits() <= a.compilationWork().limit());
        for (var pair :
                List.of(
                        Map.entry(a.firstProjection(), a.firstBehavior()),
                        Map.entry(a.secondProjection(), a.secondBehavior()))) {
            var p = pair.getKey();
            var b = pair.getValue();
            var flow = b.original();
            assertTrue(p.maximumConservationCoefficientResidual() <= 1e-12);
            assertTrue(b.maximumRelativeFloorViolation() <= 1e-6);
            assertTrue(b.maximumRelativeConservationResidual() <= 1e-8);
            assertTrue(b.maximumAffineReconstructionError() <= 1e-8);
            assertEquals(flow.conservation().size(), p.inequalities().size());
            assertEquals(1, p.offset().getFirst());
            for (var c : flow.conservation()) {
                double parent = flow.realization().get(c.parentSequence()), sum = 0;
                assertTrue(parent > 0);
                double constant = -p.offset().get(c.parentSequence());
                for (int child : c.childSequences()) {
                    constant += p.offset().get(child);
                    double probability = flow.realization().get(child) / parent;
                    assertTrue(probability >= a.minimumActionProbability() * (1 - 1e-6));
                    sum += probability;
                }
                assertEquals(1, sum, 1e-8);
                assertEquals(0, constant, 1e-12);
                for (int j = 0; j < p.freeSequences().size(); j++) {
                    double v = -p.transform().get(c.parentSequence()).get(j);
                    for (int child : c.childSequences()) v += p.transform().get(child).get(j);
                    assertEquals(0, v, 1e-12);
                }
            }
        }
    }
}
