package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RootSequenceMinimumStudyTest {
    static final Path REQUEST = Path.of("../docs/data/supplied-root-sequence-minimum-request.json");
    static final Path SOURCE =
            Path.of("../docs/data/supplied-root-envelope-feedback-report.json.gz");
    static final Path REPORT =
            Path.of("../docs/data/supplied-root-sequence-minimum-report.json.gz");
    static final Path LEGACY = Path.of("../docs/data/supplied-root-minimum-report.json.gz");
    static SixMaxRootSequenceMinimumStudy.Result owned;
    static SixMaxRootEnvelopeFeedback.Report source;

    @BeforeAll
    static void oneReservedCompletePhysicalReplay() throws Exception {
        source = SixMaxRootMinimumStudy.readSource(SOURCE);
        owned = SixMaxRootSequenceMinimumStudy.replay(REQUEST, SOURCE, LEGACY, REPORT);
    }

    @Test
    void completeOwnedReportReproducesSavedPhysicalStudyAndOriginalParent() throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        REPORT, SixMaxRootSequenceMinimumStudy.MAX_BYTES),
                                SixMaxRootSequenceMinimumStudy.Report.class);
        assertEquals(saved, owned.report());
        assertEquals(source.envelope().parent(), owned.report().parent());
        assertEquals(source.request(), owned.report().request().sourceRequest());
        assertEquals(source.envelope().context(), owned.report().context());
        assertEquals(SixMaxRootMinimumFeedback.readMinimum(LEGACY), owned.report().legacyMinimum());
    }

    @Test
    void physicalMinimumAgreesWithLegacyWithoutEnumeratingContinuations() {
        var r = owned.report();
        assertTrue(r.completeMinimum());
        assertNull(r.rejection());
        assertTrue(r.referenceSupportedMinimum());
        assertTrue(r.rejectionReasons().isEmpty());
        var a = r.minimum();
        assertEquals(0, a.work().enumeratedContinuationPlans());
        assertEquals(BigInteger.valueOf(52), r.legacyMinimum().minimum().reducedPlans());
        assertEquals(BigInteger.valueOf(1296), r.legacyMinimum().minimum().cartesianPlans());
        assertTrue(r.legacyMinimumBracketsOverlap());
        assertEquals(-1.6375789613221059e-15, r.legacyMinimumWitnessDifferenceBb(), 1e-18);
        assertEquals(8, a.support().size());
        assertEquals(1. / 3, a.originalQuestionReach(), 1e-15);
        assertEquals(.211141818822754, a.minimum().lowerBound(), 1e-12);
        assertTrue(a.minimum().upperBound() - a.minimum().lowerBound() < 3e-8);
        assertEquals(-6.359434212217635, r.previousConservativeLowerUtilityBb(), 1e-12);
        assertEquals(6.570576031040389, r.lowerBoundDifferenceBb(), 1e-12);
    }

    @Test
    void unchangedParentCertificatesFaceAndOriginalWorkLimitsAreBound() throws Exception {
        var r = owned.report();
        var a = r.minimum();
        assertEquals(r.parent().solve(), a.baseline());
        assertEquals(r.parent().firstLpWitness(), a.firstParentWitness());
        assertEquals(r.parent().secondLpWitness(), a.secondParentWitness());
        assertEquals(1e-8, a.securitySlack());
        assertEquals(.0001, a.requiredMinimumReach());
        assertEquals(8000000, a.work().compilerLimit());
        assertEquals(2040770, a.work().compilerUnits());
        assertEquals(1, a.work().endpointLpSolves());
        assertEquals(73, a.work().endpointPivots());
        assertEquals(572344163L, a.work().endpointArithmeticWork());
        assertEquals(159, a.problem().matrix().size());
        assertEquals(132, a.problem().cost().size());
        assertTrue(
                a.work().endpointArithmeticWork()
                        < r.legacyMinimum().minimum().work().endpointArithmeticWork());
        assertTrue(a.work().compilerUnits() > r.legacyMinimum().minimum().work().compilerUnits());
        var face =
                FiniteTwoPlayerRootActionIntervals.face(
                        a.orientedPayoff(),
                        a.heroProjection(),
                        a.opponentProjection(),
                        a.globalHeroUpperValue() + a.securitySlack(),
                        new SequenceFormAffineProjection.Budget(8000000));
        assertEquals(SixMaxHistoryPhysicalConditionalRefinement.hash(face), a.securityFaceHash());
    }

    @Test
    void primaryAndBothFreshReferencesRetainWholeParentAndPublicRootChecks() {
        var expected = new ArrayList<>(List.of(source.primary()));
        expected.addAll(source.references());
        assertEquals(
                List.of(1, 80000, 160000),
                owned.report().policies().stream().map(p -> p.original().iterations()).toList());
        for (int i = 0; i < 3; i++) {
            var p = owned.report().policies().get(i);
            assertEquals(expected.get(i), p.original());
            assertTrue(p.minimumLowerSupported());
            assertEquals(0., p.minimumLowerViolationBb());
            assertTrue(p.failures().isEmpty());
            assertTrue(p.original().fullParentQuality().nashConvBb() < .001);
            assertTrue(p.original().publicRootNashConvBb() < .001);
            assertTrue(p.original().insideDeclaredFace());
            assertTrue(p.original().decisionRegretBb() < .01);
        }
    }

    @Test
    void minimumUpperBracketIsNeverAnAllStrategyActionValueCeiling() {
        var r = owned.report();
        var primary = r.policies().getFirst();
        assertTrue(primary.forcedActionUtilityBb() > r.minimum().minimum().upperBound() + .5);
        assertTrue(primary.minimumLowerSupported());
        assertTrue(primary.failures().isEmpty());
        assertEquals(FiniteTwoPlayerRootSequenceMinimax.SCOPE, r.minimumScope());
    }

    @Test
    void oldFullIntervalWorkFailureAndTrainerFeedbackRemainUnchanged() throws Exception {
        var old =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-reduced-preflop-report.json.gz"),
                                        8 * 1024 * 1024));
        assertTrue(old.toString().contains("WORK_LIMIT"));
        assertEquals(
                SixMaxRootSequenceMinimumStudy.SOURCE_HASH, SixMaxHeadsUpPreflopGame.hash(source));
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.STRATEGY_DEPENDENT,
                source.moves().getLast().classification());
    }

    @Test
    void researchEvidenceCannotGrantAdmissionAndOwnedResultConstructorIsPrivate() throws Exception {
        assertFalse(owned.report().trainerAdmission());
        assertFalse(owned.report().minimum().trainerAdmission());
        var m = SixMaxTexturePayoffTable.mapper();
        var json = m.valueToTree(owned.report());
        assertThrows(
                Exception.class,
                () -> m.treeToValue(json, SixMaxRootSequenceMinimumStudy.Result.class));
        assertTrue(
                Arrays.stream(SixMaxRootSequenceMinimumStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(UnsupportedOperationException.class, () -> owned.report().policies().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().rejectionReasons().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().policies().getFirst().failures().clear());
    }

    @Test
    void strictRequestSchemaPinActionAndJsonRejectBeforePhysicalWork() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        String text = Files.readString(REQUEST);
        for (String invalid :
                List.of(
                        text + " {}",
                        text.replaceFirst("\\{", "{\"extra\":0,"),
                        text.replaceFirst("\\{", "{\"schemaVersion\":\"duplicate\","))) {
            assertThrows(
                    Exception.class,
                    () -> m.readValue(invalid, SixMaxRootSequenceMinimumStudy.Request.class));
        }
        for (String key :
                List.of(
                        "schemaVersion",
                        "sourceReportHash",
                        "action",
                        "legacyMinimumStoredSha256")) {
            var n = (ObjectNode) m.valueToTree(owned.report().request());
            n.put(key, "wrong");
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(n, SixMaxRootSequenceMinimumStudy.Request.class));
        }
    }

    @Test
    void changedOrOversizedSourceBytesRejectBeforeAnyPhysicalReplay(@TempDir Path temp)
            throws Exception {
        Path changed = temp.resolve("changed.gz");
        byte[] bytes = Files.readAllBytes(SOURCE);
        bytes[bytes.length - 1] ^= 1;
        Files.write(changed, bytes);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRootMinimumStudy.readSource(changed));
        Files.write(changed, new byte[SixMaxRootSequenceMinimumStudy.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRootMinimumStudy.readSource(changed));
        assertThrows(IllegalArgumentException.class, () -> SixMaxRootMinimumStudy.readSource(temp));
        Files.write(changed, Files.readAllBytes(LEGACY));
        byte[] legacyBytes = Files.readAllBytes(changed);
        legacyBytes[legacyBytes.length - 1] ^= 1;
        Files.write(changed, legacyBytes);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceMinimumStudy.solve(
                                owned.report().request(), SOURCE, changed));
        var sourceCopy = Files.copy(SOURCE, temp.resolve("source-copy.gz"));
        var alias = temp.resolve("source-alias.gz");
        Files.createLink(alias, sourceCopy);
        assertTrue(Files.isSameFile(sourceCopy, alias));
        assertEquals(
                "Inputs and outputs must be distinct files",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        SixMaxRootSequenceMinimumStudy.solve(
                                                owned.report().request(), sourceCopy, alias))
                        .getMessage());
    }

    @Test
    void forgedReportScopeAdmissionLineageAndPolicyOutcomesReject() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        for (String field :
                List.of("trainerAdmission", "completeMinimum", "referenceSupportedMinimum")) {
            var n = (ObjectNode) m.valueToTree(owned.report());
            n.put(field, !n.get(field).booleanValue());
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(n, SixMaxRootSequenceMinimumStudy.Report.class));
        }
        for (String field : List.of("schemaVersion", "publicationStatus", "minimumScope")) {
            var n = (ObjectNode) m.valueToTree(owned.report());
            n.put(field, "wrong");
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(n, SixMaxRootSequenceMinimumStudy.Report.class));
        }
        for (String field : List.of("legacyMinimumWitnessDifferenceBb", "lowerBoundDifferenceBb")) {
            var edited = (ObjectNode) m.valueToTree(owned.report());
            edited.put(field, 42.);
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(edited, SixMaxRootSequenceMinimumStudy.Report.class));
        }
        var changedOverlap = (ObjectNode) m.valueToTree(owned.report());
        changedOverlap.put("legacyMinimumBracketsOverlap", false);
        assertThrows(
                Exception.class,
                () -> m.treeToValue(changedOverlap, SixMaxRootSequenceMinimumStudy.Report.class));
        var n = (ObjectNode) m.valueToTree(owned.report());
        n.withArray("policies").remove(1);
        assertThrows(
                Exception.class,
                () -> m.treeToValue(n, SixMaxRootSequenceMinimumStudy.Report.class));
        var p = (ObjectNode) m.valueToTree(owned.report());
        ((ObjectNode) p.withArray("policies").get(0)).put("minimumLowerViolationBb", 10.);
        assertThrows(
                Exception.class,
                () -> m.treeToValue(p, SixMaxRootSequenceMinimumStudy.Report.class));
    }

    @Test
    void typedFailureKeepsPolicyEvidenceWithoutPublishingAValueOrSupport() throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        var n = (ObjectNode) m.valueToTree(owned.report());
        n.putNull("minimum");
        n.putNull("previousConservativeLowerUtilityBb");
        n.putNull("lowerBoundDifferenceBb");
        n.putNull("legacyMinimumWitnessDifferenceBb");
        n.putNull("legacyMinimumBracketsOverlap");
        n.put("completeMinimum", false);
        n.put("referenceSupportedMinimum", false);
        var failure = n.putObject("rejection");
        failure.put("kind", "LP");
        failure.put("reason", "WORK_LIMIT");
        failure.put("detail", "protocol control only");
        n.putArray("rejectionReasons").add("MINIMUM:LP:WORK_LIMIT");
        for (var p : n.withArray("policies")) {
            ((ObjectNode) p).putNull("minimumLowerViolationBb");
            ((ObjectNode) p).put("minimumLowerSupported", false);
        }
        var failed = m.treeToValue(n, SixMaxRootSequenceMinimumStudy.Report.class);
        assertFalse(failed.completeMinimum());
        assertFalse(failed.referenceSupportedMinimum());
        assertNull(failed.minimum());
        assertEquals(3, failed.policies().size());
    }

    @Test
    void commandsCannotOverwriteAliasOrStartForInvalidArguments(@TempDir Path temp)
            throws Exception {
        Path keep = temp.resolve("keep.json");
        Files.writeString(keep, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceMinimumMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    SOURCE.toString(),
                                    LEGACY.toString(),
                                    keep.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootSequenceMinimumMain.main(
                                new String[] {
                                    "solve",
                                    REQUEST.toString(),
                                    SOURCE.toString(),
                                    LEGACY.toString(),
                                    REQUEST.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRootSequenceMinimumMain.main(new String[] {"anything"}));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxRootSequenceMinimumStudy.write(keep, owned));
        assertEquals("keep", Files.readString(keep));
        var copy = temp.resolve("copy.gz");
        SixMaxRootSequenceMinimumStudy.write(copy, owned);
        assertEquals(
                owned.report(),
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        copy, SixMaxRootSequenceMinimumStudy.MAX_BYTES),
                                SixMaxRootSequenceMinimumStudy.Report.class));
    }
}
