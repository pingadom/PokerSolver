package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxBtnFlopCurriculumTest {
    static final Path INPUT =
            Path.of("../docs/data/supplied-four-button-hand-btn-curriculum-request.json");
    static final Path SAVED =
            Path.of("../docs/data/supplied-four-button-hand-btn-curriculum-report.json.gz");
    static SixMaxBtnFlopCurriculum.Request request;
    static SixMaxBtnFlopCurriculum.Result owned;

    @BeforeAll
    static void physicalReplay() throws Exception {
        request = SixMaxBtnFlopCurriculum.readRequest(INPUT);
        // Rebuild all eight endpoints and both complete fresh runs. No imported policy grants
        // freshness.
        owned = SixMaxBtnFlopCurriculum.replay(INPUT, SAVED);
    }

    @Test
    void allFourOwnedContextsQualifyWithTheUnchangedPhysicalParent() throws Exception {
        var report = owned.report();
        assertTrue(report.qualifiedForOfflineDrill(), report.rejectionReasons().toString());
        assertFalse(report.trainerAdmission());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(report),
                owned.qualifiedFeedback().orElseThrow().reportHash());
        var parent =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-connected-flop-report.json.gz"),
                                        SixMaxBtnFlopCurriculum.MAX_BYTES),
                                SixMaxSuppliedFlopBettingStudy.Report.class);
        assertEquals(parent, report.parent());
        assertEquals(
                SixMaxBtnFlopCurriculum.HANDS,
                report.decisions().stream().map(d -> d.heroCards()).toList());
        for (var d : report.decisions()) {
            assertTrue(d.qualified());
            assertTrue(d.rejectionReasons().isEmpty());
            assertEquals(3, d.context().actingSeat().ordinal());
            assertEquals(6.5, d.context().potBb());
            assertEquals(0, d.context().toCallBb());
            assertEquals(List.of("check", "bet"), d.context().legalActions());
            assertEquals(
                    "3:supplied-flop-one-bet:" + d.heroCards() + ":|BB:call:2s 8s Ts:check",
                    d.informationSet());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                    d.moves().getFirst().classification());
            assertEquals(
                    SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                    d.moves().getLast().classification());
            for (var a : d.actions()) {
                assertEquals(request.intervalAlgorithm(), a.diagnostic().algorithm());
                assertEquals(
                        BoundedLinearProgram.REFINED_DIRECTION_ALGORITHM,
                        a.diagnostic().lowerWitness().certificate().algorithm());
                assertEquals(List.of(), a.diagnostic().fixedHeroPast());
                assertTrue(a.diagnostic().certifiedReachLowerBound() > .249);
            }
        }
    }

    @Test
    void sharedFreshBudgetMeansThreePoliciesAndTwelveContextChecks() throws Exception {
        var report = owned.report();
        assertEquals(
                List.of(80_000, 160_000),
                report.references().stream().map(CfrSolution::iterations).toList());
        assertEquals(240_000, request.referenceBudget().totalIterations());
        assertEquals(3, report.uniquePoliciesChecked());
        assertEquals(12, report.policyContextChecks());
        var unique = new HashSet<String>();
        unique.add(report.parent().solutionHash());
        for (var policy : report.references())
            unique.add(SixMaxConnectedPostflopAudit.solutionHash(policy));
        assertEquals(3, unique.size());
        for (var d : report.decisions()) {
            var checks = new ArrayList<SixMaxSuppliedFlopFeedback.Check>();
            checks.add(d.primary());
            checks.addAll(d.references());
            for (var c : checks) {
                assertTrue(c.failures().isEmpty());
                assertTrue(c.insideDeclaredFace());
                assertTrue(c.boundsChecked());
                assertEquals(0, c.maximumBoundViolationBb());
                assertEquals(
                        c.actualQuestionReachGivenPhysicalFlop(),
                        c.counterfactualQuestionReachGivenPhysicalFlop(),
                        1e-12);
                assertEquals(
                        c.actualQuestionReachGivenPhysicalFlop() / 9880,
                        c.actualQuestionRootReach(),
                        1e-20);
                assertTrue(c.actualQuestionReachGivenPhysicalFlop() > .249);
            }
            for (int i = 0; i < 2; i++)
                assertEquals(
                        SixMaxConnectedPostflopAudit.solutionHash(report.references().get(i)),
                        d.references().get(i).solutionHash());
        }
    }

    @Test
    void lowIterationReferencesStillFailTheOriginalFaceForEveryNewContext() throws Exception {
        var connected = request.connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        var weak = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(2000);
        for (var d : owned.report().decisions()) {
            var check =
                    SixMaxBtnFlopFeedback.assess(
                            game, d.heroCards(), request.publicPreflopHistory(), d.actions(), weak);
            assertTrue(check.fullParentQuality().nashConvBb() < .001);
            assertFalse(check.insideDeclaredFace());
            assertFalse(check.boundsChecked());
            assertNull(check.maximumBoundViolationBb());
            assertTrue(check.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
        }
    }

    @Test
    void unsupportedSelectionOrderAlgorithmsAndBudgetsFailBeforeSolving() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String kind : List.of("algorithm", "hands", "order", "selection", "budget", "path")) {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(request);
            switch (kind) {
                case "algorithm" ->
                        node.put(
                                "intervalAlgorithm",
                                FiniteTwoPlayerConditionalActionIntervals
                                        .PHYSICAL_FLOP_EXACT_POWER_TWO_ALGORITHM);
                case "hands" -> node.set("heroHands", mapper.valueToTree(List.of("9s Js")));
                case "order" ->
                        node.set(
                                "heroHands",
                                mapper.valueToTree(List.of("Ah Kh", "9s Js", "As Qs", "Qh Th")));
                case "selection" -> node.put("selection", "automatic");
                case "budget" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.get("referenceBudget"))
                                .put("totalIterations", 960_000);
                case "path" -> node.set("flopActions", mapper.valueToTree(List.of("bet")));
            }
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(node, SixMaxBtnFlopCurriculum.Request.class));
        }
        assertThrows(
                Exception.class,
                () ->
                        SixMaxBtnFlopCurriculum.readRequest(
                                Path.of(
                                        "../docs/data/supplied-four-button-hand-btn-flop-feedback-request.json")));
        assertThrows(Exception.class, () -> SixMaxBtnFlopFeedback.readRequest(INPUT));
    }

    @Test
    void exactReplayGateRejectsEditedValuesPoliciesReachContextAndCounters() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String kind : List.of("value", "policy", "reach", "context", "counter", "order")) {
            var node = mapper.valueToTree(owned.report());
            switch (kind) {
                case "value" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.at("/decisions/1/moves/0"))
                                .put("lowerEvBb", 0);
                case "policy" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.at("/references/0/strategy").elements().next())
                                .put("call", .1);
                case "reach" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.at("/decisions/2/references/0"))
                                .put("counterfactualQuestionReachGivenPhysicalFlop", .1);
                case "context" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        node.at("/decisions/3/context"))
                                .put("potBb", 7);
                case "counter" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                                .put("uniquePoliciesChecked", 12);
                case "order" ->
                        ((com.fasterxml.jackson.databind.node.ArrayNode) node.get("decisions"))
                                .remove(0);
            }
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxBtnFlopCurriculum.verifyReport(
                                    mapper.treeToValue(node, SixMaxBtnFlopCurriculum.Report.class),
                                    owned));
        }
        SixMaxBtnFlopCurriculum.verifyReport(owned.report(), owned);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopCurriculum.verifyReport(owned.report(), null));
        for (var type :
                List.of(
                        SixMaxBtnFlopCurriculum.Result.class,
                        SixMaxBtnFlopCurriculum.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void everyContextMustHaveBothRobustClassesAndAllPolicyGates() {
        var d = owned.report().decisions().getFirst();
        assertEquals(
                List.of(),
                SixMaxBtnFlopCurriculum.decisionFailures(d.moves(), d.primary(), d.references()));
        var tight =
                d.moves().stream()
                        .map(
                                m ->
                                        new SixMaxSuppliedIntervalFeedback.Move(
                                                m.action(),
                                                m.lowerEvBb(),
                                                m.upperEvBb(),
                                                m.lowerLossBb(),
                                                m.upperLossBb(),
                                                SixMaxSuppliedIntervalFeedback.classify(
                                                        m.lowerLossBb(), m.upperLossBb(), 1e-9)))
                        .toList();
        assertTrue(
                SixMaxBtnFlopCurriculum.decisionFailures(tight, d.primary(), d.references())
                        .contains("NO_ROBUST_MOVE_WITHIN_LIMIT"));
        assertTrue(
                SixMaxBtnFlopCurriculum.decisionFailures(
                                List.of(d.moves().getLast()), d.primary(), d.references())
                        .contains("NO_ROBUST_MOVE_OUTSIDE_LIMIT"));
    }

    @Test
    void filesRemainBoundedExclusiveAndRequestBound(@TempDir Path dir) throws Exception {
        var output = dir.resolve("report.json.gz");
        SixMaxBtnFlopCurriculum.write(output, owned);
        byte[] bytes = Files.readAllBytes(output);
        assertEquals(owned.report(), SixMaxBtnFlopCurriculum.readReport(output));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxBtnFlopCurriculum.write(output, owned));
        assertArrayEquals(bytes, Files.readAllBytes(output));
        var altered =
                new SixMaxBtnFlopCurriculum.Request(
                        request.schemaVersion(),
                        request.selection(),
                        request.connected(),
                        request.intervalAlgorithm(),
                        request.referenceBudget(),
                        request.heroHands(),
                        request.publicPreflopHistory(),
                        request.flopActions(),
                        .005);
        var input = dir.resolve("input.json");
        Files.writeString(input, SixMaxTextureStudy.json(altered));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopCurriculum.replay(input, output));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopCurriculum.replay(output, output));
        Files.write(input, new byte[SixMaxBtnFlopCurriculum.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxBtnFlopCurriculum.readRequest(input));
    }

    @Test
    void receiptBindsSourceBytesAndTwelveChecksDoNotClaimTwelveFreshPolicies() throws Exception {
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        Path.of(
                                                "../docs/data/supplied-four-button-hand-btn-curriculum-independent-check.json")));
        assertEquals(32, receipt.get("originalEndpointCertificatesChecked").asInt());
        assertEquals(3, receipt.get("uniquePoliciesChecked").asInt());
        assertEquals(12, receipt.get("policyContextChecks").asInt());
        assertEquals(240000, receipt.get("declaredSharedFreshReferenceIterations").asInt());
        assertEquals(0, receipt.get("freshCfrRuns").asInt());
        assertEquals(0, receipt.get("freshLpSolves").asInt());
        assertFalse(receipt.get("trainerAdmission").asBoolean());
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(owned.report()), receipt.get("reportHash").asText());
        assertEquals(sha(INPUT), receipt.get("requestStoredBytesSha256").asText());
        assertEquals(sha(SAVED), receipt.get("reportStoredBytesSha256").asText());
        for (var entry :
                Map.of(
                                "checkerSourceSha256",
                                "check-btn-flop-curriculum.py",
                                "boundsHelperSourceSha256",
                                "check-supplied-flop-action-bounds.py",
                                "physicalHelperSourceSha256",
                                "check-supplied-connected-flop.py")
                        .entrySet())
            assertEquals(
                    sha(Path.of("../scripts/" + entry.getValue())),
                    receipt.get(entry.getKey()).asText());
        assertThrows(UnsupportedOperationException.class, () -> request.heroHands().clear());
        assertThrows(UnsupportedOperationException.class, () -> owned.report().decisions().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().references().getFirst().strategy().clear());
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(path)));
    }

    static SixMaxBtnFlopTrainer trainer() {
        // Reuse THIS test class's genuinely owned complete replay, never parsed report authority.
        return new SixMaxBtnFlopTrainer(owned);
    }

    static List<SixMaxBtnFlopTrainer.Choice> mixedChoices() {
        var choices = new ArrayList<SixMaxBtnFlopTrainer.Choice>();
        for (String action : List.of("check", "bet"))
            for (int i = 0; i < 4; i++) choices.add(new SixMaxBtnFlopTrainer.Choice(i, action));
        return choices;
    }

    @Test
    void cachedTrainerInventoryGradesAndPublicPrivacyFollowAllFourOwnedContexts() throws Exception {
        var trainer = trainer();
        assertEquals(4, trainer.questions().size());
        assertEquals(
                SixMaxBtnFlopCurriculum.HANDS,
                trainer.questions().stream()
                        .map(SixMaxBtnFlopTrainer.Question::heroCards)
                        .toList());
        for (int i = 0; i < 4; i++) {
            var q = trainer.question(i);
            var source = owned.report().decisions().get(i);
            assertEquals(source.context(), q.context());
            assertEquals("BTN", q.hero());
            assertEquals(i, q.questionIndex());
            assertEquals(trainer.reportHash(), q.reportHash());
            assertFalse(q.trainerAdmission());
            for (String action : List.of("check", "bet")) {
                var grade = trainer.grade(q, action);
                assertEquals(source.moves(), grade.alternatives());
                assertEquals(
                        source.moves().stream()
                                .filter(m -> m.action().equals(action))
                                .findFirst()
                                .orElseThrow(),
                        grade.selected());
                assertEquals(i, grade.questionIndex());
            }
            var json = SixMaxTextureStudy.json(q);
            for (String hidden :
                    List.of(
                            "opponentCards",
                            "worlds",
                            "strategy",
                            "candidate",
                            "lowerWitness",
                            "projectedPayoff")) assertFalse(json.contains("\"" + hidden + "\""));
        }
        assertThrows(UnsupportedOperationException.class, () -> trainer.questions().clear());
        assertEquals(
                List.of(SixMaxBtnFlopCurriculum.Result.class),
                Arrays.asList(SixMaxBtnFlopTrainer.class.getConstructors()[0].getParameterTypes()));
        assertThrows(IllegalArgumentException.class, () -> trainer.question(-1));
        assertThrows(IllegalArgumentException.class, () -> trainer.question(4));
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(null, "bet"));
        for (String action : Arrays.asList(null, "fold", "BET", "call"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> trainer.grade(trainer.question(0), action));
    }

    @Test
    void mixedTrainerReviewMatchesOwnedFixtureAndHundredAttemptsFitProtocol(@TempDir Path dir)
            throws Exception {
        var trainer = trainer();
        var review = trainer.review(mixedChoices());
        assertEquals(
                Files.readString(
                        Path.of("../docs/data/supplied-four-button-hand-btn-flop-drill.json")),
                trainer.reviewJson(mixedChoices()));
        assertTrue(
                trainer.reviewJson(
                                        Collections.nCopies(
                                                100, new SixMaxBtnFlopTrainer.Choice(3, "bet")))
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                .length
                        <= SixMaxBtnFlopTrainer.MAX_BYTES);
        assertEquals(new SixMaxBtnFlopTrainer.Counts(4, 4, 4, 0), review.counts());
        assertEquals(
                review,
                trainer.replay(
                        Path.of("../docs/data/supplied-four-button-hand-btn-flop-drill.json")));
        assertEquals(
                new SixMaxBtnFlopTrainer.Counts(0, 0, 0, 0), trainer.review(List.of()).counts());
        var hundred =
                trainer.review(Collections.nCopies(100, new SixMaxBtnFlopTrainer.Choice(3, "bet")));
        assertEquals(new SixMaxBtnFlopTrainer.Counts(1, 100, 0, 0), hundred.counts());
        for (String name : List.of("hundred.json", "hundred.json.gz")) {
            var path = dir.resolve(name);
            trainer.write(path, hundred);
            assertEquals(hundred, trainer.replay(path));
            assertTrue(Files.size(path) <= SixMaxBtnFlopTrainer.MAX_BYTES);
            assertThrows(FileAlreadyExistsException.class, () -> trainer.write(path, hundred));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        trainer.review(
                                Collections.nCopies(
                                        101, new SixMaxBtnFlopTrainer.Choice(0, "bet"))));
        assertThrows(IllegalArgumentException.class, () -> trainer.review(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(Arrays.asList((SixMaxBtnFlopTrainer.Choice) null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(List.of(new SixMaxBtnFlopTrainer.Choice(4, "bet"))));
    }

    @Test
    void exactTrainerReplayRejectsEveryQuestionAndGradeMutation() throws Exception {
        var trainer = trainer();
        var mapper = SixMaxTexturePayoffTable.mapper();
        var original = mapper.valueToTree(trainer.review(mixedChoices()));
        for (int i = 0; i < 4; i++) {
            for (String field :
                    List.of(
                            "reportHash",
                            "heroCards",
                            "informationSet",
                            "model",
                            "chanceModel",
                            "beliefs",
                            "sourceHistoryReachStatus",
                            "evScope",
                            "reachUnits",
                            "selection")) {
                var tree = original.deepCopy();
                var questionPointer = "/questions/" + i;
                ((ObjectNode) tree.at(questionPointer)).put(field, "edited");
                assertThrows(
                        Exception.class,
                        () ->
                                trainer.replay(
                                        mapper.treeToValue(
                                                tree, SixMaxBtnFlopTrainer.Review.class)),
                        field);
                assertThrows(
                        Exception.class,
                        () ->
                                trainer.grade(
                                        mapper.treeToValue(
                                                tree.at(questionPointer),
                                                SixMaxBtnFlopTrainer.Question.class),
                                        "bet"));
            }
            for (String pointer :
                    List.of(
                            "/questions/" + i + "/context",
                            "/attempts/" + i + "/feedback/selected",
                            "/attempts/" + i + "/feedback/alternatives/1")) {
                var tree = original.deepCopy();
                ((ObjectNode) tree.at(pointer))
                        .put(pointer.contains("context") ? "potBb" : "upperEvBb", 100);
                assertThrows(
                        Exception.class,
                        () ->
                                trainer.replay(
                                        mapper.treeToValue(
                                                tree, SixMaxBtnFlopTrainer.Review.class)));
            }
        }
        for (String kind :
                List.of(
                        "pin",
                        "index",
                        "question-index",
                        "feedback-index",
                        "grade-pin",
                        "counts",
                        "order",
                        "admission")) {
            var tree = original.deepCopy();
            switch (kind) {
                case "pin" -> ((ObjectNode) tree).put("reportHash", "0".repeat(64));
                case "index" -> ((ObjectNode) tree.at("/attempts/0")).put("index", 1);
                case "question-index" ->
                        ((ObjectNode) tree.at("/questions/0")).put("questionIndex", 1);
                case "feedback-index" ->
                        ((ObjectNode) tree.at("/attempts/0/feedback")).put("questionIndex", 1);
                case "grade-pin" ->
                        ((ObjectNode) tree.at("/attempts/0/feedback"))
                                .put("reportHash", "0".repeat(64));
                case "counts" ->
                        ((ObjectNode) tree.get("counts")).put("distinctQuestionsAttempted", 8);
                case "admission" ->
                        ((ObjectNode) tree.at("/questions/0")).put("trainerAdmission", true);
                case "order" -> {
                    var a = (com.fasterxml.jackson.databind.node.ArrayNode) tree.get("attempts");
                    var first = a.get(0);
                    a.set(0, a.get(1));
                    a.set(1, first);
                }
            }
            assertThrows(
                    Exception.class,
                    () ->
                            trainer.replay(
                                    mapper.treeToValue(tree, SixMaxBtnFlopTrainer.Review.class)),
                    kind);
        }
    }

    @Test
    void trainerReviewReaderRejectsDuplicatesUnknownFieldsCoercionAndCompressedOverflow(
            @TempDir Path dir) throws Exception {
        var json = SixMaxTextureStudy.json(trainer().review(List.of()));
        var path = dir.resolve("review.json");
        for (String bad :
                List.of(
                        json + " {}",
                        json.replace("\"questionIndex\" : 0", "\"questionIndex\" : 0.5"),
                        json.replaceFirst("\\{", "{\"hidden\":0,"),
                        json.replaceFirst("\\{", "{\"reportHash\":\"duplicate\","))) {
            Files.writeString(path, bad);
            assertThrows(Exception.class, () -> SixMaxBtnFlopTrainer.read(path));
        }
        Files.writeString(path, " ".repeat(SixMaxBtnFlopTrainer.MAX_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> SixMaxBtnFlopTrainer.read(path));
        var zipPath = dir.resolve("large.json.gz");
        try (var zip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(zipPath))) {
            zip.write(Files.readAllBytes(path));
        }
        assertThrows(IllegalArgumentException.class, () -> SixMaxBtnFlopTrainer.read(zipPath));
    }
}
