package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SevensRootStudyTest {
    static final Path DATA = Path.of("../docs/data");
    static final Path REQUEST = DATA.resolve("supplied-sevens-root-study-request.json");
    static final Path SOURCE = DATA.resolve("supplied-flop-size-menu-report.json.gz");
    static final Path REPORT = DATA.resolve("supplied-sevens-root-study-report.json.gz");
    static SixMaxSevensRootStudy.Report evidence;
    @TempDir Path temp;

    @BeforeAll
    static void readUnownedEvidenceWithoutSolving() throws Exception {
        evidence = SixMaxSevensRootStudy.readReport(REPORT);
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
                                .treeToValue(node, SixMaxSevensRootStudy.Report.class));
    }

    @Test
    void savedEvidenceRetainsOriginalParentAndResearchOutcome() throws Exception {
        assertTrue(evidence.completeOuterBounds());
        assertTrue(evidence.feedbackSupported());
        assertEquals(List.of(), evidence.rejectionReasons());
        assertNull(evidence.rejection());
        var old =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(SOURCE, 8 * 1024 * 1024),
                                SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        assertEquals(old, evidence.parent());
    }

    @Test
    void originalSixSeatPublicContextAndCompleteActionCostsAreRetained() {
        var r = evidence;
        assertEquals("7d 7h", r.request().heroCards());
        assertEquals(5, r.request().hero());
        assertEquals(6, r.context().players().size());
        assertEquals(4.5, r.context().potBb());
        assertEquals(2, r.context().toCallBb());
        assertEquals(
                List.of("fold", "call", "raise:9.0"),
                r.moves().stream().map(m -> m.action()).toList());
        assertEquals(
                List.of(0., 2., 8.),
                r.context().actions().stream().map(a -> a.amountBb()).toList());
        assertTrue(r.envelope().question().informationSet().contains("7d 7h"));
        assertFalse(r.envelope().question().informationSet().contains("9d 9h"));
    }

    @Test
    void boundsUseTerminalProfitWithoutTheFrontendCommitmentShift() {
        var r = evidence;
        assertEquals(-1., r.moves().getFirst().lowerEvBb(), 2e-8);
        assertEquals(.6855081544144144, r.moves().get(1).lowerEvBb(), 1e-12);
        assertEquals(
                List.of("OUTSIDE_LIMIT", "WITHIN_LIMIT", "STRATEGY_DEPENDENT"),
                r.moves().stream().map(m -> m.classification().name()).toList());
        assertTrue(r.moves().getLast().upperLossBb() > 7);
        assertEquals(SixMaxSuppliedFlopFeedback.EV_SCOPE, r.evScope());
    }

    @Test
    void bothFreshPoliciesAreInsideTheOriginalWholeGameFace() {
        var r = evidence;
        assertEquals(
                List.of(1, 80000, 160000), r.policies().stream().map(p -> p.iterations()).toList());
        for (var p : r.policies()) {
            assertTrue(p.insideDeclaredFace());
            assertTrue(p.boundsChecked());
            assertEquals(List.of(), p.failures());
            assertEquals(RootEnvelopePolicyChecks.PUBLIC_SCOPE, p.publicRootQualityScope());
            assertEquals(1, p.publicRootReach());
            assertEquals(1. / 3, p.actualQuestionReach(), 1e-8);
            assertEquals(p.actualQuestionReach(), p.counterfactualQuestionReach());
            assertTrue(p.publicRootNashConvBb() <= .001);
            assertTrue(p.decisionRegretBb() <= .01);
            assertEquals(3, p.actionUtilitiesBb().size());
            assertEquals(3, p.actionLossBb().size());
            assertTrue(p.maximumBoundViolationBb() <= 1e-8);
        }
    }

    @Test
    void fiveOriginalCertificatesFitAllUnchangedBudgets() {
        var r = evidence;
        var work = r.envelope().work();
        assertEquals(SixMaxSevensRootStudy.BUDGET, r.maximumWork());
        assertEquals(2, r.maximumWork().maximumParentCalls());
        assertEquals(4, r.maximumWork().maximumParentLps());
        assertEquals(240000, r.maximumWork().freshIterations());
        assertEquals(5, work.lpSolves());
        assertEquals(8000000, work.compilerLimit());
        assertTrue(work.compilerUnits() <= 8000000);
        assertTrue(work.lpPivots() <= 10000);
        assertTrue(work.lpArithmeticWork() <= 4000000000L);
        assertEquals(8, r.envelope().support().size());
    }

    @Test
    void serializedEvidenceCannotConstructOwnershipOrAdmission() {
        assertFalse(evidence.trainerAdmission());
        assertFalse(evidence.envelope().trainerAdmission());
        assertTrue(Modifier.isFinal(SixMaxSevensRootStudy.Result.class.getModifiers()));
        assertTrue(
                Arrays.stream(SixMaxSevensRootStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(UnsupportedOperationException.class, () -> evidence.moves().clear());
        assertThrows(UnsupportedOperationException.class, () -> evidence.policies().clear());
        reject(r -> r.put("trainerAdmission", true));
    }

    @Test
    void requestRejectsOtherHandsActorsPinsThresholdsAndUnknownFields() {
        for (String field : List.of("schemaVersion", "parentStoredSha256", "heroCards"))
            reject(r -> object(r, "/request").put(field, "different"));
        reject(r -> object(r, "/request").put("hero", 3));
        reject(r -> object(r, "/request").put("heroCards", "9d 9h"));
        reject(r -> object(r, "/request").put("maximumLossBb", .02));
        reject(r -> object(r, "/request").put("maximumLossBb", Double.NaN));
        reject(r -> object(r, "/request").put("unknown", 1));
    }

    @Test
    void reportRejectsWrongScopeContextAndWorkClaims() {
        reject(r -> r.put("schemaVersion", "different"));
        reject(r -> r.put("publicationStatus", "TRAINER_READY"));
        reject(r -> r.put("evScope", "CURRENT_DECISION_PROFIT"));
        reject(r -> r.put("reachUnits", "PRIVATE_HAND_NORMALIZED"));
        reject(r -> object(r, "/context").put("potBb", 5));
        reject(r -> object(r, "/maximumWork").put("maximumParentLps", 0));
        reject(r -> object(r, "/maximumWork").put("freshIterations", 1));
        reject(r -> object(r, "/envelope/work").put("lpSolves", 0));
        reject(r -> object(r, "/envelope/work").put("lpPivots", -1));
        reject(r -> object(r, "/envelope/work").put("lpArithmeticWork", 0));
        reject(r -> object(r, "/envelope/work").put("compilerLimit", 9000000));
    }

    @Test
    void reportRejectsWrongQuestionSupportOrEnvelopeOwnership() {
        reject(r -> object(r, "/envelope").put("algorithm", "DTO_ONLY"));
        reject(r -> object(r, "/envelope").put("trainerAdmission", true));
        reject(r -> object(r, "/envelope/question").put("hero", 3));
        reject(r -> object(r, "/envelope").put("questionChanceMass", Double.NaN));
        reject(r -> object(r, "/envelope/support/0").put("chanceWeight", -.1));
        reject(r -> object(r, "/envelope/support/0").put("stateHash", "invented"));
    }

    @Test
    void reportRejectsForgedValuesClassificationsAndOutcomes() {
        reject(r -> object(r, "/moves/1").put("lowerEvBb", 99));
        reject(r -> object(r, "/moves/2").put("classification", "WITHIN_LIMIT"));
        reject(r -> r.put("completeOuterBounds", false));
        reject(r -> r.put("feedbackSupported", false));
        reject(
                r ->
                        ((com.fasterxml.jackson.databind.node.ArrayNode) r.get("rejectionReasons"))
                                .add("invented"));
    }

    @Test
    void reportRejectsForeignPoliciesAndPrivateGameQuality() {
        reject(r -> object(r, "/policies/0").put("solutionHash", "0".repeat(64)));
        reject(r -> object(r, "/policies/1").put("iterations", 4000));
        reject(r -> object(r, "/policies/1").put("publicRootQualityScope", "PRIVATE_HAND_GAME"));
        reject(r -> object(r, "/policies/1").put("publicRootReach", 1. / 3));
        reject(r -> object(r, "/policies/1/fullParentQuality").put("nashConvBb", 0));
        reject(r -> object(r, "/policies/1").put("actualQuestionReach", Double.NaN));
    }

    @Test
    void reportRejectsForgedFaceMembershipLossRegretAndViolations() {
        reject(r -> object(r, "/policies/1").put("insideDeclaredFace", false));
        reject(r -> object(r, "/policies/1").put("globalHeroUpperBb", 99));
        reject(r -> object(r, "/policies/1").put("inclusionMarginBb", 99));
        reject(r -> object(r, "/policies/1/actionLossBb").put("call", 99));
        reject(r -> object(r, "/policies/1").put("decisionRegretBb", 99));
        reject(r -> object(r, "/policies/1").put("maximumBoundViolationBb", 99));
    }

    @Test
    void wrongSourceAndChangedParentAreRejectedBeforeSolving() throws Exception {
        var request = SixMaxSevensRootStudy.readRequest(REQUEST);
        var wrong = temp.resolve("wrong.gz");
        Files.writeString(wrong, "wrong");
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxSevensRootStudy.solve(request, wrong));
        assertThrows(Exception.class, () -> SixMaxSevensRootStudy.solve(request, temp));
        var json = tree();
        object(json, "/request/parent/input/worlds/0").put("weight", 2.0);
        var changed =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(json.get("request"), SixMaxSevensRootStudy.Request.class);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxSevensRootStudy.solve(changed, SOURCE));
    }

    @Test
    void duplicateCoercedAndOversizedRequestsAreRejectedWithoutSolving() throws Exception {
        String text = Files.readString(REQUEST);
        var path = temp.resolve("request.json");
        for (String bad :
                List.of(
                        text.replace("\"hero\": 5", "\"hero\": 5, \"hero\": 5"),
                        text.replace("\"hero\": 5", "\"hero\": 5.5"),
                        text.replace("\"hero\": 5", "\"hero\": \"5\""),
                        text.replace("\"hero\": 5", "\"hero\": null"))) {
            assertNotEquals(text, bad);
            Files.writeString(path, bad);
            assertThrows(Exception.class, () -> SixMaxSevensRootStudy.readRequest(path));
        }
        Files.write(path, new byte[SixMaxSevensRootStudy.MAX_REQUEST_BYTES + 1]);
        assertThrows(Exception.class, () -> SixMaxSevensRootStudy.readRequest(path));
    }

    @Test
    void aliasesAndExistingOutputsAreRejectedWithoutAnotherReplay() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSevensRootMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    SOURCE.toString(),
                                    SOURCE.toString()
                                }));
        var target = temp.resolve("exists.gz");
        Files.writeString(target, "preserve");
        assertThrows(
                FileAlreadyExistsException.class,
                () ->
                        SixMaxSevensRootMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    SOURCE.toString(),
                                    target.toString()
                                }));
        assertEquals("preserve", Files.readString(target));
        assertThrows(Exception.class, () -> SixMaxSevensRootMain.main(new String[] {"unknown"}));
        var copied = temp.resolve("parent.gz");
        Files.copy(SOURCE, copied);
        var link = temp.resolve("same-parent.gz");
        Files.createLink(link, copied);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSevensRootMain.main(
                                new String[] {
                                    "solve", REQUEST.toString(), copied.toString(), link.toString()
                                }));
    }

    @Test
    void malformedSavedReportIsRejectedBeforeAnyReplay() throws Exception {
        var path = temp.resolve("bad.gz");
        Files.writeString(path, "not gzip");
        assertThrows(Exception.class, () -> SixMaxSevensRootStudy.readReport(path));
        assertThrows(Exception.class, () -> SixMaxSevensRootStudy.readReport(temp));
        Files.write(path, new byte[SixMaxSevensRootStudy.MAX_BYTES + 1]);
        assertThrows(Exception.class, () -> SixMaxSevensRootStudy.readReport(path));
    }

    @Test
    void boundedFailureEvidenceStaysUnqualifiedAndCannotCreateOwnedResult() throws Exception {
        var node = tree();
        node.putNull("envelope");
        node.put("completeOuterBounds", false);
        node.put("feedbackSupported", false);
        node.set("moves", SixMaxTexturePayoffTable.mapper().createArrayNode());
        node.set("policies", SixMaxTexturePayoffTable.mapper().createArrayNode());
        node.set(
                "rejectionReasons",
                SixMaxTexturePayoffTable.mapper()
                        .createArrayNode()
                        .add("INCOMPLETE_ROOT_ENVELOPE"));
        node.set(
                "rejection",
                SixMaxTexturePayoffTable.mapper()
                        .valueToTree(
                                new SixMaxSevensRootStudy.Failure(
                                        "LP",
                                        "WORK_LIMIT",
                                        "Synthetic parsing control, not a new solve")));
        var evidence =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(node, SixMaxSevensRootStudy.Report.class);
        assertFalse(evidence.feedbackSupported());
        assertFalse(evidence.trainerAdmission());
        node.put("feedbackSupported", true);
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(node, SixMaxSevensRootStudy.Report.class));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSevensRootStudy.Failure("LP", "INFEASIBLE", "Unproved"));
    }

    @Test
    void originalNinesRequestContractStillRejectsSevens() {
        var p = evidence.parent().request();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRootEnvelopeStudy.Request(
                                SixMaxRootEnvelopeStudy.REQUEST_SCHEMA, p, 5, "7d 7h"));
        assertDoesNotThrow(
                () ->
                        new SixMaxRootEnvelopeStudy.Request(
                                SixMaxRootEnvelopeStudy.REQUEST_SCHEMA, p, 5, "9d 9h"));
    }
}
