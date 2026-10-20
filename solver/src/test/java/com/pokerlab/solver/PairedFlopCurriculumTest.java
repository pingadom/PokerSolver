package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PairedFlopCurriculumTest {
    static SixMaxPairedFlopCurriculum.Result owned;
    static SixMaxPairedFlopTrainer trainer;

    @BeforeAll
    static void completeOwnedReplay() throws Exception {
        owned = PairedFlopOwnedFixture.owned();
        trainer = new SixMaxPairedFlopTrainer(owned);
    }

    @Test
    void fourCompletePhysicalStudiesAndAllThirtyTwoEndpointsReproduce() throws Exception {
        var report = owned.report();
        assertEquals(report, SixMaxPairedFlopCurriculum.readReport(PairedFlopOwnedFixture.REPORT));
        assertTrue(report.qualifiedForOfflineDrill(), report.rejectionReasons().toString());
        assertFalse(report.trainerAdmission());
        assertEquals(
                PairedFlopOwnedFixture.PIN, owned.qualifiedFeedback().orElseThrow().reportHash());
        assertEquals(PairedFlopOwnedFixture.PIN, SixMaxHeadsUpPreflopGame.hash(report));
        var labels = List.of("js9s", "ahkh", "asqs", "qhth");
        for (int i = 0; i < 4; i++) {
            var d = report.decisions().get(i);
            var c = d.conditional();
            assertEquals(SixMaxPairedFlopCurriculum.HANDS.get(i), c.request().heroCards());
            assertEquals(
                    SixMaxRefinedConditionalStudy.readReport(
                            Path.of(
                                    "../docs/data/supplied-paired-basis-btn-"
                                            + labels.get(i)
                                            + "-conditional-report.json.gz")),
                    c);
            assertEquals(List.of("8d", "Kc", "Kd"), c.context().board());
            assertEquals(9.75, c.context().potBb());
            assertEquals(3.25, c.context().toCallBb());
            assertEquals(List.of("fold", "call"), c.context().legalActions());
            assertEquals("BTN", c.context().actingSeat().name());
            assertEquals(
                    List.of(
                            SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                            SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT),
                    d.moves().stream().map(m -> m.classification()).toList());
            assertTrue(d.qualified());
            for (var a : c.actions()) {
                var audit = a.diagnostic();
                assertEquals(4, audit.work().intervalLpSolves());
                assertEquals(
                        BoundedLinearProgram.ASCENDING_REFINED_BASIS_ALGORITHM,
                        audit.lowerWitness().certificate().algorithm());
                assertEquals(c.parent().solve(), audit.baseline());
                assertTrue(audit.certifiedReachLowerBound() > .249);
            }
        }
    }

    @Test
    void exactlyTwoFreshRunsAreSharedAcrossAllTwelvePolicyChecks() throws Exception {
        var report = owned.report();
        assertEquals(
                List.of(80000, 160000),
                report.references().stream().map(r -> r.policy().iterations()).toList());
        assertEquals(3, report.uniquePoliciesChecked());
        assertEquals(12, report.policyContextChecks());
        for (var reference : report.references()) {
            assertEquals(1321, reference.work().validatedTreeNodes());
            assertEquals(
                    4L * reference.policy().iterations(), reference.work().prunedSuffixRoots());
            assertEquals(
                    2646L * reference.policy().iterations(),
                    reference.work().statistics().visitedNodes());
        }
        for (var decision : report.decisions()) {
            var checks = new ArrayList<SixMaxSuppliedFlopFeedback.Check>();
            checks.add(decision.primary());
            checks.addAll(decision.references());
            for (var check : checks) {
                assertTrue(check.failures().isEmpty());
                assertTrue(check.insideDeclaredFace());
                assertTrue(check.boundsChecked());
                assertEquals(0, check.maximumBoundViolationBb());
                assertTrue(check.decisionRegretBb() < 1e-8);
                assertTrue(check.counterfactualQuestionReachGivenPhysicalFlop() > .249);
            }
            for (int i = 0; i < 2; i++)
                assertEquals(
                        SixMaxConnectedPostflopAudit.solutionHash(
                                report.references().get(i).policy()),
                        decision.references().get(i).solutionHash());
        }
    }

    @Test
    void aggregateWorkCountsEveryRepeatedParentAndEndpoint() {
        var report = owned.report();
        var work = report.work();
        assertEquals(12, work.parentSolves());
        assertEquals(24, work.parentLpSolves());
        assertEquals(8, work.endpointActions());
        assertEquals(32, work.endpointLpSolves());
        assertEquals(240000, work.freshIterations());
        assertEquals(635040000L, work.referenceVisitedNodes());
        assertEquals(
                work, SixMaxPairedFlopCurriculum.measure(report.decisions(), report.references()));
        long compilation = 0, arithmetic = 0;
        for (var decision : report.decisions()) {
            var parent = decision.conditional().parent().solve();
            compilation += 3 * parent.reductionWork().chargedUnits();
            arithmetic +=
                    3
                            * (parent.firstLp().work().arithmeticWork()
                                    + parent.secondLp().work().arithmeticWork());
            for (var action : decision.conditional().actions()) {
                compilation += action.diagnostic().work().compilerUnits();
                arithmetic += action.diagnostic().work().intervalLpArithmeticWork();
            }
        }
        assertEquals(compilation, work.compilerUnits());
        assertEquals(arithmetic, work.lpArithmeticWork());
        SixMaxPairedFlopCurriculum.withinBudget(work, report.request().workBudget());
        var incomplete =
                new SixMaxPairedFlopCurriculum.Work(
                        4,
                        4,
                        8,
                        8,
                        32,
                        compilation,
                        work.lpPivots(),
                        arithmetic,
                        2,
                        240000,
                        work.referenceVisitedNodes());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPairedFlopCurriculum.withinBudget(
                                incomplete, report.request().workBudget()));
    }

    @Test
    void requestRejectsOmittedReorderedForeignSourcesAndIncreasedBudgetsBeforeSolving() {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String kind :
                List.of(
                        "missing",
                        "duplicate",
                        "order",
                        "parent",
                        "path",
                        "mode",
                        "budget",
                        "reference",
                        "loss",
                        "selection")) {
            var node = (ObjectNode) mapper.valueToTree(owned.report().request());
            var questions = (ArrayNode) node.get("questions");
            switch (kind) {
                case "missing" -> questions.remove(3);
                case "duplicate" -> questions.set(2, questions.get(0).deepCopy());
                case "order" -> {
                    var first = questions.get(0);
                    questions.set(0, questions.get(1));
                    questions.set(1, first);
                }
                case "parent" ->
                        ((ObjectNode) questions.get(1).at("/parent/connected"))
                                .put("betPotFraction", .25);
                case "path" ->
                        ((ObjectNode) questions.get(0))
                                .set("flopActions", mapper.valueToTree(List.of("check")));
                case "mode" ->
                        ((ObjectNode) questions.get(0))
                                .put(
                                        "intervalAlgorithm",
                                        FiniteTwoPlayerConditionalActionIntervals
                                                .PHYSICAL_FLOP_ASCENDING_REFINED_ALGORITHM);
                case "budget" ->
                        ((ObjectNode) node.get("workBudget"))
                                .put("lpArithmeticWork", 128000000001L);
                case "reference" ->
                        ((ObjectNode) node.get("referenceBudget")).put("totalIterations", 960000);
                case "loss" -> node.put("maximumLossBb", .005);
                case "selection" -> node.put("selection", SixMaxBtnFlopCurriculum.SELECTION);
            }
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(node, SixMaxPairedFlopCurriculum.Request.class),
                    kind);
        }
    }

    @Test
    void parsedOrRehashedEditsCannotManufactureOwnedQualification() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String kind :
                List.of(
                        "admission",
                        "value",
                        "policy",
                        "reach",
                        "regret",
                        "context",
                        "work",
                        "reference-work",
                        "order",
                        "classification")) {
            var node = (ObjectNode) mapper.valueToTree(owned.report());
            switch (kind) {
                case "admission" -> node.put("trainerAdmission", true);
                case "value" -> ((ObjectNode) node.at("/decisions/1/moves/0")).put("lowerEvBb", 0);
                case "policy" ->
                        ((ObjectNode) node.at("/references/0/policy/strategy").elements().next())
                                .put("call", .1);
                case "reach" ->
                        ((ObjectNode) node.at("/decisions/2/references/0"))
                                .put("counterfactualQuestionReachGivenPhysicalFlop", .1);
                case "regret" ->
                        ((ObjectNode) node.at("/decisions/3/primary")).put("decisionRegretBb", .5);
                case "context" ->
                        ((ObjectNode) node.at("/decisions/2/conditional/context")).put("potBb", 10);
                case "work" -> ((ObjectNode) node.get("work")).put("parentSolves", 4);
                case "reference-work" ->
                        ((ObjectNode) node.at("/references/0/work/statistics"))
                                .put("visitedNodes", 1);
                case "order" -> ((ArrayNode) node.get("decisions")).remove(0);
                case "classification" ->
                        ((ObjectNode) node.at("/decisions/0/moves/0"))
                                .put("classification", "WITHIN_LIMIT");
            }
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxPairedFlopCurriculum.verifyReport(
                                    mapper.treeToValue(
                                            node, SixMaxPairedFlopCurriculum.Report.class),
                                    owned),
                    kind);
        }
        SixMaxPairedFlopCurriculum.verifyReport(owned.report(), owned);
        for (var type :
                List.of(
                        SixMaxPairedFlopCurriculum.Result.class,
                        SixMaxPairedFlopCurriculum.QualifiedFeedback.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    static List<SixMaxPairedFlopTrainer.Choice> allChoices() {
        var choices = new ArrayList<SixMaxPairedFlopTrainer.Choice>();
        for (String action : List.of("fold", "call"))
            for (int i = 0; i < 4; i++) choices.add(new SixMaxPairedFlopTrainer.Choice(i, action));
        return List.copyOf(choices);
    }

    @Test
    void cachedEightGradesCanonicalReviewAndDistinctCoverageMatchOwnedEvidence() throws Exception {
        var review = trainer.review(allChoices());
        assertEquals(SixMaxPairedFlopTrainer.read(PairedFlopOwnedFixture.REVIEW), review);
        assertEquals(new SixMaxPairedFlopTrainer.Counts(4, 4, 4, 0), review.counts());
        assertEquals(review, trainer.replay(review));
        assertArrayEquals(
                Files.readAllBytes(PairedFlopOwnedFixture.REVIEW),
                trainer.reviewJson(allChoices()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (int i = 0; i < 4; i++) {
            var q = trainer.question(i);
            assertEquals(PairedFlopOwnedFixture.PIN, q.reportHash());
            assertEquals(List.of("fold", "call"), q.context().legalActions());
            assertTrue(trainer.grade(q, "fold").selected().lowerLossBb() > .01);
            assertTrue(trainer.grade(q, "call").selected().upperLossBb() < .01);
        }
        assertThrows(UnsupportedOperationException.class, () -> trainer.questions().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> trainer.grade(trainer.question(0), "call").alternatives().clear());
    }

    @Test
    void globalHundredAttemptBoundAndStrictInventoryDoNotTurnRetriesIntoCoverage() {
        var choices = Collections.nCopies(100, new SixMaxPairedFlopTrainer.Choice(3, "call"));
        assertEquals(
                new SixMaxPairedFlopTrainer.Counts(1, 100, 0, 0), trainer.review(choices).counts());
        assertEquals(
                new SixMaxPairedFlopTrainer.Counts(0, 0, 0, 0), trainer.review(List.of()).counts());
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(Collections.nCopies(101, choices.getFirst())));
        assertThrows(IllegalArgumentException.class, () -> trainer.review(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(Collections.singletonList(null)));
        for (int index : List.of(-1, 4))
            assertThrows(IllegalArgumentException.class, () -> trainer.question(index));
        for (String action : List.of("check", "bet", "CALL", ""))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> trainer.grade(trainer.question(0), action));
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(null, "call"));
    }

    @Test
    void reviewReplayRejectsClientGradesCountsOrderingAndForeignPins() {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String kind :
                List.of(
                        "pin",
                        "question",
                        "grade",
                        "alternative",
                        "index",
                        "count",
                        "actor",
                        "selection")) {
            var node = (ObjectNode) mapper.valueToTree(trainer.review(allChoices()));
            switch (kind) {
                case "pin" -> node.put("reportHash", "0".repeat(64));
                case "question" -> ((ObjectNode) node.at("/questions/0")).put("heroCards", "2c 2d");
                case "grade" ->
                        ((ObjectNode) node.at("/attempts/0/feedback/selected"))
                                .put("upperEvBb", 50);
                case "alternative" ->
                        ((ObjectNode) node.at("/attempts/0/feedback/alternatives/1"))
                                .put("upperLossBb", 50);
                case "index" -> ((ObjectNode) node.at("/attempts/0")).put("index", 10);
                case "count" ->
                        ((ObjectNode) node.get("counts")).put("distinctQuestionsAttempted", 100);
                case "actor" -> ((ObjectNode) node.at("/questions/0")).put("hero", "BB");
                case "selection" -> node.put("selection", SixMaxBtnFlopTrainer.SELECTION);
            }
            assertThrows(
                    Exception.class,
                    () ->
                            trainer.replay(
                                    mapper.treeToValue(node, SixMaxPairedFlopTrainer.Review.class)),
                    kind);
        }
    }

    @Test
    void persistedInputsAreStrictBoundedAndOutputsExclusive(@TempDir Path dir) throws Exception {
        for (String name : List.of("report.json", "report.json.gz")) {
            var file = dir.resolve(name);
            SixMaxPairedFlopCurriculum.write(file, owned);
            assertEquals(owned.report(), SixMaxPairedFlopCurriculum.readReport(file));
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxPairedFlopCurriculum.write(file, owned));
        }
        var review = dir.resolve("review.json.gz");
        trainer.write(review, trainer.review(allChoices()));
        assertEquals(trainer.review(allChoices()), trainer.replay(review));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> trainer.write(review, trainer.review(List.of())));
        var bad = dir.resolve("bad.json");
        for (String value :
                List.of(
                        Files.readString(PairedFlopOwnedFixture.INPUT) + "{}",
                        "{\"selection\":1,\"selection\":2}",
                        "{\"extra\":1}")) {
            Files.writeString(bad, value);
            assertThrows(Exception.class, () -> SixMaxPairedFlopCurriculum.readRequest(bad));
        }
        Files.write(bad, new byte[SixMaxPairedFlopCurriculum.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxPairedFlopCurriculum.readReport(bad));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPairedFlopCurriculum.replay(review, review));
    }

    @Test
    void loaderPinsAliasesAndCliChoicesRejectBeforeExpensiveReplay(@TempDir Path dir)
            throws Exception {
        var input = PairedFlopOwnedFixture.INPUT;
        var report = PairedFlopOwnedFixture.REPORT;
        for (String pin : Arrays.asList(null, "bad", "0".repeat(64)))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxPairedFlopTrainerLoader.load(input, report, pin));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPairedFlopTrainerLoader.load(input, input, PairedFlopOwnedFixture.PIN));
        var alias = dir.resolve("alias.json");
        Files.createLink(alias, input.toAbsolutePath());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPairedFlopTrainerLoader.load(input, alias, PairedFlopOwnedFixture.PIN));
        assertEquals(
                List.of(new SixMaxPairedFlopTrainer.Choice(2, "call")),
                SixMaxPairedFlopTrainerMain.choices(new String[] {"2:call"}));
        for (String invalid : List.of("4:call", "-1:fold", "1:bet", "1:CALL", "01:call"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxPairedFlopTrainerMain.choices(new String[] {invalid}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPairedFlopTrainerMain.main(
                                new String[] {
                                    "create",
                                    input.toString(),
                                    report.toString(),
                                    PairedFlopOwnedFixture.PIN,
                                    input.toString()
                                }));
    }
}
