package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxHeadsUpFloorStudyTest {
    static SixMaxPreflopSolutionPack source;
    static SixMaxHeadsUpFloorStudy.Result study;
    static SixMaxHeadsUpPreflopGame game;
    static final Path SAVED =
            Path.of("../docs/data/heads-up-preflop-five-target-floor-report.json");
    @TempDir Path temporary;

    @BeforeAll
    static void solve() throws Exception {
        source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        var spec = SixMaxHeadsUpPreflopStudyTest.specification("five-target");
        game = new SixMaxHeadsUpPreflopGame(source, spec);
        study = SixMaxHeadsUpFloorStudy.solve(source, spec, .0001);
    }

    @AfterAll
    static void release() {
        source = null;
        study = null;
        game = null;
    }

    @Test
    void savedDiagnosticReplaysBothSolversAndCompleteFeedbackScreen() throws Exception {
        var r = SixMaxHeadsUpFloorStudy.replay(SAVED, source).report();
        assertEquals(study.report(), r);
        assertFalse(r.trainerAdmission());
        assertEquals(SixMaxHeadsUpFloorStudy.STATUS, r.publicationStatus());
        assertEquals(5, r.materialDecisions());
        assertEquals(0, r.stableDecisions());
        assertTrue(r.failures().contains("UNSTABLE_MATERIAL_DECISIONS"));
        assertTrue(r.failures().contains("REFERENCE_ORIGINAL_GAME_GAP:500"));
        assertEquals(
                List.of(500, 1000),
                r.references().stream().map(FiniteTwoPlayerFloorCfr.Result::iterations).toList());
        assertEquals(10, r.decisions().size());
        assertTrue(r.decisions().stream().allMatch(d -> d.primary().values() != null));
        assertTrue(
                r.decisions().stream()
                        .filter(d -> !d.material())
                        .allMatch(d -> !d.stable() && d.comparisons().isEmpty()));
        assertTrue(
                r.decisions().stream()
                        .filter(SixMaxHeadsUpPreflopStudy.Decision::material)
                        .allMatch(d -> d.comparisons().size() == 2));
    }

    @Test
    void actualCardValuesMatchIndependentFullFlowHighsAndLiteralIntentPlans() throws Exception {
        var a = study.report().solve();
        // Original full-flow/slack HiGHS oracle, not the owned affine compiler.
        assertEquals(.7891170763244302, a.constrainedLowerValue(), 1e-10);
        assertEquals(0, a.constrainedQuality().nashConvBb(), 1e-10);
        assertEquals(.000409946612888, a.originalGameQuality().nashConvBb(), 1e-10);
        assertTrue(a.originalGameQuality().nashConvBb() < SixMaxSuitDecisionStability.LOCAL_GAP_BB);
        FiniteTwoPlayerBehaviorFloorTest.literalCertificate(
                game, study.report().candidate(), .0001, a.constrainedQuality());
        for (var ref : study.report().references()) {
            assertEquals(a.snapshotHash(), ref.snapshotHash());
            assertEquals(181L * 2 * ref.iterations(), ref.visitedNodes());
            FiniteTwoPlayerBehaviorFloorTest.literalCertificate(
                    game, ref.solution(), .0001, ref.constrainedQuality());
        }
    }

    @Test
    void relativeFloorAndOriginalFlowCertificatesCoverEverySequence() throws Exception {
        var r = FiniteTwoPlayerBehaviorFloor.solve(game, .0001);
        FiniteTwoPlayerBehaviorFloorTest.audit(r);
        assertTrue(
                r.strategy().values().stream()
                        .flatMap(m -> m.values().stream())
                        .allMatch(p -> p >= .0001 * (1 - 1e-6)));
        assertEquals(
                SixMaxConnectedPostflopAudit.solutionHash(study.report().candidate()),
                r.audit().behavioralPolicyHash());
        var changed = FiniteTwoPlayerBehaviorFloor.solve(game, .001);
        assertEquals(r.audit().snapshotHash(), changed.audit().snapshotHash());
        assertNotEquals(r.audit().reductionHash(), changed.audit().reductionHash());
        assertTrue(changed.audit().originalGameQuality().nashConvBb() > .001);
    }

    @Test
    void numericalFailuresAtSmallerFloorsDoNotReturnPolicies() {
        for (double floor : List.of(.00001, .000001)) {
            var failure =
                    assertThrows(
                            BoundedLinearProgram.Rejected.class,
                            () -> FiniteTwoPlayerBehaviorFloor.solve(game, floor));
            assertEquals(BoundedLinearProgram.Failure.NUMERICAL_FAILURE, failure.reason());
        }
    }

    @Test
    void candidateAllActionFeedbackUsesSameQuestionPosteriorForBothReferences() {
        var primary = SixMaxHeadsUpPreflopDecisionValues.assess(game, study.report().candidate());
        for (var d : study.report().decisions())
            if (d.material()) {
                var roots =
                        primary.stream()
                                .filter(
                                        p ->
                                                p.row()
                                                        .informationSet()
                                                        .equals(d.primary().informationSet()))
                                .findFirst()
                                .orElseThrow()
                                .roots();
                for (var c : d.comparisons()) {
                    var ref =
                            study.report().references().stream()
                                    .filter(r -> r.iterations() == c.iterations())
                                    .findFirst()
                                    .orElseThrow();
                    var expected =
                            SixMaxHeadsUpPreflopDecisionValues.values(game, roots, ref.solution());
                    assertEquals(expected, c.fixedQuestionReferenceValues());
                    assertEquals(
                            d.primary().values().actionEvBb().keySet(),
                            expected.actionEvBb().keySet());
                }
            }
    }

    @Test
    void plainAndGzipOutputsRoundTripWithoutOverwriting() throws Exception {
        for (String name : List.of("report.json", "report.json.gz")) {
            var output = temporary.resolve(name);
            SixMaxHeadsUpFloorStudy.write(output, study);
            assertEquals(study.report(), SixMaxHeadsUpFloorStudy.replay(output, source).report());
            var before = Files.readAllBytes(output);
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxHeadsUpFloorStudy.write(output, study));
            assertArrayEquals(before, Files.readAllBytes(output));
        }
    }

    @Test
    void replayRejectsSelfConsistentPolicyCertificateAndFeedbackTampering() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field :
                List.of(
                        "candidate",
                        "solve",
                        "references",
                        "decisions",
                        "binding",
                        "publicationStatus",
                        "trainerAdmission")) {
            var tree = mapper.valueToTree(study.report());
            switch (field) {
                case "candidate" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path(field))
                                .put("iterations", 2);
                case "solve" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path(field))
                                .put("constrainedLowerValue", 99);
                case "references" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path(field).get(0))
                                .put("visitedNodes", 1);
                case "decisions" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path(field).get(0))
                                .put("stable", true);
                case "binding" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path(field))
                                .put("sourcePackHash", "0".repeat(64));
                case "publicationStatus" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree)
                                .put(field, "VALIDATION_ONLY");
                case "trainerAdmission" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put(field, true);
                default -> throw new AssertionError();
            }
            var output = temporary.resolve(field + ".json");
            mapper.writeValue(output.toFile(), tree);
            assertThrows(
                    Exception.class, () -> SixMaxHeadsUpFloorStudy.replay(output, source), field);
        }
    }

    @Test
    void unsupportedUnknownFieldsAndOversizedDiagnosticsFailClosed() throws Exception {
        var tree = SixMaxTexturePayoffTable.mapper().valueToTree(study.report());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("extra", true);
        var unknown = temporary.resolve("unknown.json");
        SixMaxTexturePayoffTable.mapper().writeValue(unknown.toFile(), tree);
        assertThrows(Exception.class, () -> SixMaxHeadsUpFloorStudy.replay(unknown, source));
        var oversized = temporary.resolve("large.json.gz");
        try (var zip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(oversized))) {
            zip.write(new byte[SixMaxHeadsUpPreflopStudy.MAX_BYTES + 1]);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHeadsUpFloorStudy.replay(oversized, source));
    }

    @Test
    void cliSolvesAndReplaysAndPreflightsAliasesBeforeLoadingSources() throws Exception {
        var output = temporary.resolve("cli.json");
        var sourcePath = Path.of("../docs/data/sixmax-staged-three-nine-source-pack.json");
        SixMaxHeadsUpFloorStudyMain.main(
                new String[] {
                    "solve",
                    sourcePath.toString(),
                    SixMaxHeadsUpPreflopStudyTest.data("five-target", "specification").toString(),
                    ".0001",
                    output.toString()
                });
        SixMaxHeadsUpFloorStudyMain.main(
                new String[] {"replay", sourcePath.toString(), output.toString()});
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpFloorStudyMain.main(
                                new String[] {
                                    "solve", "missing", "missing-spec", ".0001", output.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpFloorStudyMain.main(
                                new String[] {"solve", "same", "spec", ".0001", "./same"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpFloorStudyMain.main(
                                new String[] {
                                    "solve",
                                    "missing",
                                    "spec",
                                    "NaN",
                                    temporary.resolve("new.json").toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHeadsUpFloorStudyMain.main(new String[] {}));
    }

    @Test
    void researchReportCannotManufactureExistingTrainerHandle() {
        assertTrue(
                Arrays.stream(SixMaxHeadsUpFloorStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        assertFalse(
                SixMaxHeadsUpPreflopStudy.Result.class.isAssignableFrom(
                        SixMaxHeadsUpFloorStudy.Result.class));
        assertThrows(
                UnsupportedOperationException.class, () -> study.report().references().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> study.report().decisions().getFirst().comparisons().clear());
    }

    @Test
    void independentOracleFixtureExactlyMatchesEveryLiveChanceActionAndTerminal() throws Exception {
        try (var raw = getClass().getResourceAsStream("/behavior-floor-literal-control.json.gz");
                var zip = new java.util.zip.GZIPInputStream(Objects.requireNonNull(raw))) {
            var fixture = SixMaxTexturePayoffTable.mapper().readTree(zip);
            assertEquals(
                    SixMaxTexturePayoffTable.mapper().valueToTree(game.binding()),
                    fixture.path("binding"));
            var root = fixture.path("root");
            var outcomes = game.chanceOutcomes(game.initialState());
            assertEquals(-1, root.path("actor").asInt());
            assertEquals(outcomes.size(), root.path("children").size());
            for (int i = 0; i < outcomes.size(); i++) {
                var outcome = outcomes.get(i);
                assertEquals(
                        outcome.probability(), root.path("probabilities").get(i).asDouble(), 1e-15);
                assertEquals(
                        game.dealtHands(outcome.state()).stream().map(WeightedCombo::key).toList(),
                        SixMaxTexturePayoffTable.mapper()
                                .convertValue(fixture.path("deals").get(i), List.class));
                literalFixture(outcome.state(), root.path("children").get(i));
            }
            for (var control : fixture.path("ownedControls")) {
                var result =
                        FiniteTwoPlayerBehaviorFloor.solve(
                                game, control.path("minimumActionProbability").asDouble());
                assertEquals(
                        control.path("constrainedLowerValue").asDouble(),
                        result.audit().constrainedLowerValue(),
                        1e-12);
                assertEquals(
                        control.path("constrainedUpperValue").asDouble(),
                        result.audit().constrainedUpperValue(),
                        1e-12);
            }
        }
    }

    private static void literalFixture(
            SixMaxHeadsUpPreflopGame.State state, com.fasterxml.jackson.databind.JsonNode node) {
        if (game.isTerminal(state)) {
            assertEquals(-2, node.path("actor").asInt());
            var utilities = game.terminalUtilities(state);
            for (int i = 0; i < 6; i++) {
                assertEquals(utilities[i], node.path("utilities").get(i).asDouble(), 1e-12);
                assertEquals(
                        game.publicBettingState(state)
                                .committedBb(PreflopAllInSpot.Seat.values()[i]),
                        node.path("committed").get(i).asDouble(),
                        1e-12);
            }
            return;
        }
        assertEquals(game.currentPlayer(state), node.path("actor").asInt());
        assertEquals(game.informationSet(state), node.path("key").asText());
        var actions = game.legalActions(state);
        assertEquals(actions.size(), node.path("children").size());
        for (int i = 0; i < actions.size(); i++) {
            assertEquals(actions.get(i), node.path("actions").get(i).asText());
            literalFixture(game.afterAction(state, actions.get(i)), node.path("children").get(i));
        }
    }
}
