package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhysicalRootBankControlsTest {
    static Path data = Path.of("../docs/data");
    static SixMaxPhysicalRootBankStudy.Report saved;
    static SixMaxPhysicalRootBankStudy.Request request;

    @BeforeAll
    static void parseOnlyNoNumericalReplay() throws Exception {
        saved =
                SixMaxPhysicalRootBankStudy.readReport(
                        data.resolve("supplied-physical-root-bank-reuse-report.json.gz"));
        request =
                SixMaxPhysicalRootBankStudy.readRequest(
                        data.resolve("supplied-physical-root-bank-reuse-request.json"));
    }

    static ObjectNode tree() {
        return SixMaxTexturePayoffTable.mapper().valueToTree(saved);
    }

    static ObjectNode obj(ObjectNode n, String pointer) {
        return (ObjectNode) n.at(pointer);
    }

    static void reject(ObjectNode n) {
        assertThrows(
                JsonProcessingException.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(n, SixMaxPhysicalRootBankStudy.Report.class));
    }

    @Test
    void completeReadOnlyReportKeepsAllMovesAndOriginalPolicyChecks() {
        assertEquals(
                List.of("7d 7h", "9d 9h", "Ac Jc"),
                saved.assessments().stream()
                        .map(SixMaxPhysicalRootBankStudy.Assessment::heroCards)
                        .toList());
        assertEquals(9, saved.assessments().stream().mapToInt(a -> a.moves().size()).sum());
        assertEquals(9, saved.assessments().stream().mapToInt(a -> a.policies().size()).sum());
        assertEquals(3366363, saved.batch().work().compilerUnits());
        assertEquals(468, saved.batch().work().endpointPivots());
        assertEquals(1440054300, saved.batch().work().endpointArithmeticWork());
    }

    @Test
    void scopeAndTrainerAdmissionCannotBeChanged() {
        var n = tree();
        n.put("trainerAdmission", true);
        reject(n);
        n = tree();
        n.put("originalMenusReproduced", false);
        reject(n);
        n = tree();
        obj(n, "/batch").put("algorithm", FiniteTwoPlayerRootSecurityBatch.ALGORITHM);
        reject(n);
    }

    @Test
    void fullPhysicalParentAndCertificateCannotBeSubstituted() {
        var n = tree();
        obj(n, "/physicalParent/solve").put("snapshotHash", "0".repeat(64));
        reject(n);
        n = tree();
        obj(n, "/batch/firstParentWitness/certificate").put("primalValue", 99.);
        reject(n);
    }

    @Test
    void originalEnvelopeBoundsAndWitnessesCannotChange() {
        var n = tree();
        obj(n, "/batch/envelopes/0/actions/0").put("lowerUtility", 99.);
        reject(n);
        n = tree();
        obj(n, "/batch/envelopes/2/questionUpperWitness")
                .put("behavioralPolicyHash", "0".repeat(64));
        reject(n);
    }

    @Test
    void comparisonHashesAndOrderedHandInventoryAreDerived() {
        var n = tree();
        obj(n, "/comparisons/0").put("originalEnvelopeHash", "a".repeat(64));
        reject(n);
        n = tree();
        ((ArrayNode) n.get("assessments")).remove(1);
        reject(n);
        n = tree();
        obj(n, "/assessments/0").put("heroCards", "9d 9h");
        reject(n);
    }

    @Test
    void referenceSupportAndPublicMovesAreExact() {
        var n = tree();
        obj(n, "/assessments/1/moves/0").put("classification", "WITHIN_LIMIT");
        reject(n);
        n = tree();
        obj(n, "/assessments/2/policies/0").put("insideDeclaredFace", false);
        reject(n);
    }

    @Test
    void totalAndRemainingWorkCannotResetOrClaimExtraParents() {
        for (var field :
                List.of(
                        "parentSolveCalls",
                        "parentLpSolves",
                        "parentCompilerUnits",
                        "parentLpArithmeticWork")) {
            var n = tree();
            obj(n, "/batch/work").put(field, 1);
            reject(n);
        }
        var n = tree();
        obj(n, "/batch/envelopes/1/work").put("compilerLimit", 8000000);
        reject(n);
        n = tree();
        obj(n, "/batch/work").put("endpointLpSolves", 14);
        reject(n);
        n = tree();
        obj(n, "/maximumWork").put("endpointArithmetic", 5000000000L);
        reject(n);
    }

    @Test
    void requestUnknownDuplicateNullAndCoercedFieldsReject() throws Exception {
        String json = SixMaxTextureStudy.json(request);
        assertThrows(
                JsonProcessingException.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .readValue(
                                        json.replaceFirst("\\{", "{\"unknown\":true,"),
                                        SixMaxPhysicalRootBankStudy.Request.class));
        assertThrows(
                JsonProcessingException.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .readValue(
                                        json.replaceFirst("\\{", "{\"schemaVersion\":\"bad\","),
                                        SixMaxPhysicalRootBankStudy.Request.class));
        var n = (ObjectNode) SixMaxTexturePayoffTable.mapper().valueToTree(request);
        n.putNull("maximumLossBb");
        assertThrows(
                JsonProcessingException.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(n, SixMaxPhysicalRootBankStudy.Request.class));
        n.put("maximumLossBb", "0.01");
        assertThrows(
                JsonProcessingException.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(n, SixMaxPhysicalRootBankStudy.Request.class));
    }

    @Test
    void trueDtoFlagsCannotCreateAnOwnedResult() throws Exception {
        assertTrue(
                Arrays.stream(SixMaxPhysicalRootBankStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(
                JsonProcessingException.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .readValue(
                                        SixMaxTextureStudy.json(saved),
                                        SixMaxPhysicalRootBankStudy.Result.class));
    }

    @Test
    void returnedCollectionsRemainImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> saved.assessments().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> saved.assessments().getFirst().policies().clear());
        assertThrows(UnsupportedOperationException.class, () -> saved.batch().envelopes().clear());
    }

    @Test
    void changedOrMissingSourceRejectsBeforePhysicalWork(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("bank.gz");
        Files.write(source, new byte[] {1, 2, 3});
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPhysicalRootBankStudy.solve(request, source));
        assertThrows(
                NoSuchFileException.class,
                () -> SixMaxPhysicalRootBankStudy.solve(request, temp.resolve("missing")));
    }

    @Test
    void sourceIdentityAndTimestampChangesAreDetectedWithoutSolving(@TempDir Path temp)
            throws Exception {
        Path source = temp.resolve("bank.gz");
        Files.copy(data.resolve("supplied-root-hand-bank-report.json.gz"), source);
        var capture = SixMaxPhysicalRootBankStudy.class.getDeclaredMethod("capture", Path.class);
        capture.setAccessible(true);
        Object captured = capture.invoke(null, source);
        var verify =
                SixMaxPhysicalRootBankStudy.class.getDeclaredMethod("verify", captured.getClass());
        verify.setAccessible(true);
        Files.setLastModifiedTime(
                source, FileTime.fromMillis(Files.getLastModifiedTime(source).toMillis() + 5000));
        assertThrows(InvocationTargetException.class, () -> verify.invoke(null, captured));
    }

    @Test
    void existingOutputsAndHardLinkAliasesRejectBeforeSolving(@TempDir Path temp) throws Exception {
        Path req = temp.resolve("request.json"),
                alias = temp.resolve("alias.json"),
                output = temp.resolve("output.gz");
        Files.copy(data.resolve("supplied-physical-root-bank-reuse-request.json"), req);
        Files.createLink(alias, req);
        Files.writeString(output, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPhysicalRootBankMain.main(
                                new String[] {
                                    "solve",
                                    req.toString(),
                                    data.resolve("supplied-root-hand-bank-report.json.gz")
                                            .toString(),
                                    alias.toString()
                                }));
        assertThrows(
                FileAlreadyExistsException.class,
                () ->
                        SixMaxPhysicalRootBankMain.main(
                                new String[] {
                                    "solve",
                                    req.toString(),
                                    data.resolve("supplied-root-hand-bank-report.json.gz")
                                            .toString(),
                                    output.toString()
                                }));
        assertEquals("keep", Files.readString(output));
    }

    @Test
    void storedAndExpandedReportCapsRejectBeforeParsing(@TempDir Path temp) throws Exception {
        Path stored = temp.resolve("large.gz"), expanded = temp.resolve("expanded.gz");
        Files.write(stored, new byte[SixMaxPhysicalRootBankStudy.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPhysicalRootBankStudy.readReport(stored));
        try (var out = new GZIPOutputStream(Files.newOutputStream(expanded))) {
            out.write(new byte[SixMaxPhysicalRootBankStudy.MAX_BYTES + 1]);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPhysicalRootBankStudy.readReport(expanded));
    }
}
