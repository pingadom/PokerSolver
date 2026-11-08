package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Parser/control tests never construct a physical game or run a numerical solver. */
class RootHandBankStudyTest {
    static final Path DATA = Path.of("../docs/data");
    static final Path REQUEST = DATA.resolve("supplied-root-hand-bank-request.json");
    static final Path SOURCE = DATA.resolve("supplied-flop-size-menu-report.json.gz");
    static final Path REPORT = DATA.resolve("supplied-root-hand-bank-report.json.gz");
    static SixMaxRootHandBankStudy.Report evidence;
    @TempDir Path temp;

    @BeforeAll
    static void readEvidenceWithoutSolving() throws Exception {
        evidence = SixMaxRootHandBankStudy.readReport(REPORT);
    }

    static ObjectNode tree() {
        return SixMaxTexturePayoffTable.mapper().valueToTree(evidence);
    }

    static ObjectNode object(ObjectNode root, String path) {
        return (ObjectNode) root.at(path);
    }

    void reject(Consumer<ObjectNode> change) {
        var node = tree();
        change.accept(node);
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(node, SixMaxRootHandBankStudy.Report.class));
    }

    @Test
    void everyDeclaredPrivateHandRetainsAnOrderedSixSeatOutcome() {
        assertEquals(List.of("7d 7h", "9d 9h", "Ac Jc"), evidence.request().heroCards());
        assertEquals(
                List.of(0, 1, 2),
                evidence.assessments().stream()
                        .map(SixMaxRootHandBankStudy.Assessment::questionIndex)
                        .toList());
        for (var a : evidence.assessments()) {
            assertEquals(6, a.context().players().size());
            assertEquals(4.5, a.context().potBb());
            assertEquals(2., a.context().toCallBb());
            assertEquals(
                    List.of(0., 2., 8.),
                    a.context().actions().stream().map(x -> x.amountBb()).toList());
            assertEquals(8, a.envelope().support().size());
            assertEquals(1. / 3, a.envelope().questionChanceMass(), 1e-8);
        }
    }

    @Test
    void singleParentAndReferencesMatchThePreviouslyPublishedPhysicalSource() throws Exception {
        var source =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(SOURCE, 8 * 1024 * 1024),
                                SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        assertEquals(source, evidence.parent());
        assertEquals(
                List.of(80000, 160000),
                evidence.parent().references().stream().map(r -> r.policy().iterations()).toList());
        for (var a : evidence.assessments()) assertEquals(source.solve(), a.envelope().baseline());
    }

    @Test
    void sevensOutcomeExactlyMatchesItsStandaloneOwnedEvidence() throws Exception {
        var previous =
                SixMaxSevensRootStudy.readReport(
                        DATA.resolve("supplied-sevens-root-study-report.json.gz"));
        var a = evidence.assessments().getFirst();
        assertEquals(previous.context(), a.context());
        assertEquals(previous.envelope(), a.envelope());
        assertEquals(previous.moves(), a.moves());
        assertEquals(previous.policies(), a.policies());
        assertEquals(previous.rejectionReasons(), a.rejectionReasons());
    }

    @Test
    void ninesOutcomeExactlyMatchesItsStandaloneEnvelopeAndQualification() throws Exception {
        var previous =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        DATA.resolve(
                                                "supplied-root-envelope-feedback-report.json.gz"),
                                        8 * 1024 * 1024),
                                SixMaxRootEnvelopeFeedback.Report.class);
        var a = evidence.assessments().get(1);
        assertEquals(previous.envelope().context(), a.context());
        assertEquals(previous.envelope().envelope(), a.envelope());
        assertEquals(previous.moves(), a.moves());
        var policies = new ArrayList<RootEnvelopePolicyChecks.Check>();
        policies.add(previous.primary());
        policies.addAll(previous.references());
        assertEquals(policies, a.policies());
        assertEquals(previous.rejectionReasons(), a.rejectionReasons());
    }

    @Test
    void allBoundsUseTerminalProfitAndKeepAmbiguousActionsVisible() {
        assertEquals(SixMaxSuppliedFlopFeedback.EV_SCOPE, evidence.evScope());
        for (var a : evidence.assessments()) {
            assertEquals(-1., a.moves().getFirst().lowerEvBb(), 2e-8);
            assertEquals(
                    List.of("fold", "call", "raise:9.0"),
                    a.moves().stream().map(m -> m.action()).toList());
            assertTrue(
                    a.moves().stream()
                            .anyMatch(m -> m.classification().name().equals("STRATEGY_DEPENDENT")));
            assertTrue(a.completeOuterBounds());
            assertTrue(a.feedbackSupported());
        }
        assertEquals(List.of(0, 1, 2), evidence.supportedQuestionIndices());
    }

    @Test
    void allNinePolicyChecksUseTheWholePublicGameAndOriginalChanceMass() {
        for (var a : evidence.assessments()) {
            assertEquals(
                    List.of(1, 80000, 160000),
                    a.policies().stream().map(p -> p.iterations()).toList());
            for (var p : a.policies()) {
                assertEquals(RootEnvelopePolicyChecks.PUBLIC_SCOPE, p.publicRootQualityScope());
                assertEquals(1., p.publicRootReach());
                assertEquals(1. / 3, p.actualQuestionReach(), 1e-8);
                assertTrue(p.insideDeclaredFace());
                assertTrue(p.boundsChecked());
                assertTrue(p.failures().isEmpty());
            }
        }
    }

    @Test
    void cumulativeReservationKeepsEveryUnchangedPerHandKernelCap() {
        var b = evidence.maximumWork();
        var w = evidence.completedWork();
        assertEquals(SixMaxRootHandBankStudy.BUDGET, b);
        assertEquals(1, b.physicalGames());
        assertEquals(4, b.maximumParentCalls());
        assertEquals(8, b.maximumParentLps());
        assertEquals(240000, b.freshIterations());
        assertEquals(3, w.completedEnvelopes());
        assertEquals(15, w.envelopeLps());
        assertEquals(SixMaxRootHandBankStudy.WORK_SCOPE, w.scope());
        assertTrue(w.compilerUnits() <= 24000000);
        assertTrue(w.pivots() <= 30000);
        assertTrue(w.arithmetic() <= 12000000000L);
        for (var a : evidence.assessments()) {
            assertEquals(8000000, a.envelope().work().compilerLimit());
            assertTrue(a.envelope().work().lpPivots() <= 10000);
            assertTrue(a.envelope().work().lpArithmeticWork() <= 4000000000L);
        }
    }

    @Test
    void reportDtoCannotCreateOwnedResultOrTrainerAdmission() {
        assertFalse(evidence.trainerAdmission());
        assertTrue(Modifier.isFinal(SixMaxRootHandBankStudy.Result.class.getModifiers()));
        assertTrue(
                Arrays.stream(SixMaxRootHandBankStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(UnsupportedOperationException.class, () -> evidence.assessments().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> evidence.request().heroCards().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> evidence.supportedQuestionIndices().clear());
        reject(r -> r.put("trainerAdmission", true));
    }

    @Test
    void requestCannotDropReorderDuplicateOrInventHands() {
        reject(r -> ((ArrayNode) r.at("/request/heroCards")).remove(2));
        reject(r -> ((ArrayNode) r.at("/request/heroCards")).set(0, r.at("/request/heroCards/1")));
        reject(r -> ((ArrayNode) r.at("/request/heroCards")).add("As Ah"));
        reject(r -> object(r, "/request").put("hero", 3));
        reject(r -> object(r, "/request").put("maximumLossBb", .02));
        reject(r -> object(r, "/request").put("parentStoredSha256", "0".repeat(64)));
    }

    @Test
    void assessmentsCannotDropSwapOrRelabelOutcomes() {
        reject(r -> ((ArrayNode) r.get("assessments")).remove(2));
        reject(r -> ((ArrayNode) r.get("assessments")).set(0, r.at("/assessments/1")));
        reject(r -> object(r, "/assessments/2").put("heroCards", "7d 7h"));
        reject(r -> object(r, "/assessments/2").put("questionIndex", 0));
        reject(r -> object(r, "/assessments/2/context").put("potBb", 99));
    }

    @Test
    void globalReservationsAndCompletedTotalsCannotBeForged() {
        reject(r -> object(r, "/maximumWork").put("maximumQuestions", 2));
        reject(r -> object(r, "/maximumWork").put("freshIterations", 720000));
        reject(r -> object(r, "/completedWork").put("compilerUnits", 0));
        reject(r -> object(r, "/completedWork").put("pivots", 0));
        reject(r -> object(r, "/completedWork").put("arithmetic", 0));
        reject(r -> object(r, "/completedWork").put("scope", "ALL_ATTEMPTED_WORK"));
        reject(r -> ((ArrayNode) r.get("supportedQuestionIndices")).remove(2));
    }

    @Test
    void crossHandEnvelopesAndPolicyIdentityCannotBeSubstituted() {
        reject(r -> object(r, "/assessments/2").set("envelope", r.at("/assessments/0/envelope")));
        reject(r -> object(r, "/assessments/1/envelope/question").put("hero", 3));
        reject(r -> object(r, "/assessments/2/policies/1").put("solutionHash", "0".repeat(64)));
        reject(
                r ->
                        object(r, "/assessments/2/policies/1")
                                .put("publicRootQualityScope", "PRIVATE_HAND_GAME"));
        reject(r -> object(r, "/assessments/2/policies/1").put("publicRootReach", 1. / 3));
    }

    @Test
    void perHandValuesLossesClassificationsAndOutcomesCannotBeForged() {
        reject(r -> object(r, "/assessments/2/moves/1").put("lowerEvBb", 99));
        reject(r -> object(r, "/assessments/2/policies/1/actionLossBb").put("call", 99));
        reject(r -> object(r, "/assessments/1").put("feedbackSupported", false));
        reject(r -> object(r, "/assessments/2/policies/1").put("decisionRegretBb", 99));
        reject(r -> r.put("completeOuterBounds", false));
    }

    @Test
    void aTypedFailureRetainsItsSlotAndDoesNotClaimSpentWorkOrInfeasibility() throws Exception {
        var r = tree();
        var a = object(r, "/assessments/2");
        a.putNull("envelope");
        a.set(
                "rejection",
                SixMaxTexturePayoffTable.mapper()
                        .valueToTree(
                                new SixMaxSevensRootStudy.Failure(
                                        "LP", "WORK_LIMIT", "Synthetic parser control only")));
        a.putArray("moves");
        a.putArray("policies");
        a.put("completeOuterBounds", false);
        a.put("feedbackSupported", false);
        a.putArray("rejectionReasons").add("INCOMPLETE_ROOT_ENVELOPE");
        r.put("completeOuterBounds", false);
        r.putArray("supportedQuestionIndices").add(0).add(1);
        var w = object(r, "/completedWork");
        w.put("completedEnvelopes", 2);
        w.put("envelopeLps", 10);
        var removed = evidence.assessments().get(2).envelope().work();
        w.put("compilerUnits", evidence.completedWork().compilerUnits() - removed.compilerUnits());
        w.put("pivots", evidence.completedWork().pivots() - removed.lpPivots());
        w.put("arithmetic", evidence.completedWork().arithmetic() - removed.lpArithmeticWork());
        var parsed =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(r, SixMaxRootHandBankStudy.Report.class);
        assertEquals(3, parsed.assessments().size());
        assertFalse(parsed.completeOuterBounds());
        assertEquals("WORK_LIMIT", parsed.assessments().get(2).rejection().reason());
        a.put("feedbackSupported", true);
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(r, SixMaxRootHandBankStudy.Report.class));
        a.put("feedbackSupported", false);
        object(r, "/assessments/2/rejection").put("reason", "INFEASIBLE");
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(r, SixMaxRootHandBankStudy.Report.class));
    }

    @Test
    void wrongSourceAndChangedParentFailBeforeAnyPhysicalWork() throws Exception {
        var request = SixMaxRootHandBankStudy.readRequest(REQUEST);
        var wrong = temp.resolve("wrong.gz");
        Files.writeString(wrong, "wrong");
        assertThrows(Exception.class, () -> SixMaxRootHandBankStudy.solve(request, wrong));
        assertThrows(Exception.class, () -> SixMaxRootHandBankStudy.solve(request, temp));
        var r = tree();
        object(r, "/request/parent/input/worlds/0").put("weight", 2.);
        var changed =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(r.get("request"), SixMaxRootHandBankStudy.Request.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootHandBankStudy.solve(changed, SOURCE));
    }

    @Test
    void duplicateCoercedAndOversizedRequestsFailWithoutSolving() throws Exception {
        String text = Files.readString(REQUEST);
        var p = temp.resolve("request.json");
        for (String bad :
                List.of(
                        text.replace("\"hero\": 5", "\"hero\": 5,\"hero\": 5"),
                        text.replace("\"hero\": 5", "\"hero\": \"5\""),
                        text.replace("\"hero\": 5", "\"hero\": 5.0"),
                        text.replace("\"maximumLossBb\": 0.01", "\"maximumLossBb\": NaN"),
                        text.replace("\"hero\": 5", "\"hero\": 5,\"unknown\": 0"))) {
            assertNotEquals(text, bad);
            Files.writeString(p, bad);
            assertThrows(Exception.class, () -> SixMaxRootHandBankStudy.readRequest(p));
        }
        Files.write(p, new byte[SixMaxRootHandBankStudy.MAX_REQUEST_BYTES + 1]);
        assertThrows(Exception.class, () -> SixMaxRootHandBankStudy.readRequest(p));
    }

    @Test
    void storedExpandedAndTruncatedReportCapsFailWithoutSolving() throws Exception {
        var file = temp.resolve("report.gz");
        Files.write(file, new byte[SixMaxRootHandBankStudy.MAX_BYTES + 1]);
        assertThrows(Exception.class, () -> SixMaxRootHandBankStudy.readReport(file));
        try (var zip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(file))) {
            zip.write(new byte[SixMaxRootHandBankStudy.MAX_BYTES + 1]);
        }
        assertTrue(Files.size(file) < SixMaxRootHandBankStudy.MAX_BYTES);
        assertThrows(Exception.class, () -> SixMaxRootHandBankStudy.readReport(file));
        Files.write(file, Arrays.copyOf(Files.readAllBytes(REPORT), 40));
        assertThrows(Exception.class, () -> SixMaxRootHandBankStudy.readReport(file));
    }

    @Test
    void cliRejectsExistingOutputAndAliasedInputsBeforeSolving() throws Exception {
        var output = temp.resolve("existing.gz");
        Files.writeString(output, "preserve");
        assertThrows(
                Exception.class,
                () ->
                        SixMaxRootHandBankMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    SOURCE.toString(),
                                    output.toString()
                                }));
        assertEquals("preserve", Files.readString(output));
        assertThrows(
                Exception.class,
                () ->
                        SixMaxRootHandBankMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    SOURCE.toString(),
                                    SOURCE.toString()
                                }));
        var alias = temp.resolve("alias.gz");
        Files.createLink(alias, SOURCE.toAbsolutePath());
        assertThrows(
                Exception.class,
                () ->
                        SixMaxRootHandBankMain.main(
                                new String[] {
                                    "solve", REQUEST.toString(), SOURCE.toString(), alias.toString()
                                }));
    }
}
