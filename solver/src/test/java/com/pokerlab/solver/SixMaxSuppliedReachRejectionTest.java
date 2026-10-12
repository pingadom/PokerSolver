package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedReachRejectionTest {
    static Path requestPath() {
        return SixMaxSuppliedRangePreflopGameTest.data(
                "supplied-four-button-hand-zero-reach-request.json");
    }

    static Path reportPath() {
        return requestPath().resolveSibling("supplied-four-button-hand-zero-reach-report.json.gz");
    }

    static SixMaxSuppliedReachRejection.Result result;

    @BeforeAll
    static void physicallyReplaySavedCounterexample() throws Exception {
        result = SixMaxSuppliedReachRejection.replay(requestPath(), reportPath());
    }

    @Test
    void broaderPhysicalHandModelPreservesCertifiedZeroReachAndDoesNotAdmitTraining() {
        var r = result.report();
        var a = r.diagnostic();
        assertFalse(r.trainerAdmission());
        assertEquals(24, r.prior().size());
        assertEquals(4, r.prior().stream().map(w -> w.dealtCombos().get(3)).distinct().count());
        assertEquals(3, r.prior().stream().map(w -> w.dealtCombos().get(5)).distinct().count());
        assertEquals(2, r.prior().stream().map(w -> w.dealtCombos().get(1)).distinct().count());
        assertEquals(15792192, r.binding().budget().enumeratedBoards());
        assertEquals(31584384, r.binding().budget().handEvaluations());
        assertEquals(
                SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                r.binding().sourceHistoryReachStatus());
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.ReachFailureStatus
                        .WITNESS_BELOW_REQUIRED_FLOOR,
                a.status());
        assertEquals(0, a.minimumReachWitness().questionReach(), 1e-12);
        assertEquals(-1e-8, a.certifiedReachLowerBound(), 1e-12);
        assertEquals(.01, a.requiredMinimumReach());
        assertEquals(.0001, a.securitySlack());
        assertEquals(6, a.minimumReachWitness().masses().size());
        assertEquals(1, a.work().intervalLpSolves());
        assertEquals(315337.0 / 607392, a.globalHeroUpperValue(), 1e-12);
        assertEquals(
                a.globalHeroUpperValue(), a.minimumReachWitness().globalHeroBestResponse(), 1e-12);
    }

    @Test
    void savedFailureWritesPlainAndGzipWithoutOverwritingAndReplays(@TempDir Path temp)
            throws Exception {
        for (String suffix : List.of("json", "json.gz")) {
            var output = temp.resolve("failure." + suffix);
            SixMaxSuppliedReachRejection.write(output, result);
            assertEquals(
                    result.report(),
                    SixMaxSuppliedReachRejection.replay(requestPath(), output).report());
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxSuppliedReachRejection.write(output, result));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedReachRejectionMain.main(
                                new String[] {
                                    "diagnose", requestPath().toString(), requestPath().toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedReachRejectionMain.main(
                                new String[] {
                                    "diagnose", requestPath().toString(), reportPath().toString()
                                }));
        assertTrue(
                Arrays.stream(SixMaxSuppliedReachRejection.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(
                Exception.class, () -> SixMaxSuppliedIntervalFeedback.readRequest(requestPath()));
    }

    @Test
    void tamperedDualAndRehashedPhysicalCountsFailFullReplay(@TempDir Path temp) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        ObjectNode changed = mapper.valueToTree(result.report());
        ((ObjectNode) changed.get("diagnostic").get("minimumReachWitness").get("certificate"))
                .put("dualValue", .5);
        var output = temp.resolve("changed-dual.json");
        mapper.writeValue(output.toFile(), changed);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedReachRejection.replay(requestPath(), output));
        ObjectNode physical = mapper.valueToTree(result.report());
        var counts = (ObjectNode) physical.get("payoffs").get(0).get("counts");
        counts.put("firstWins", counts.get("firstWins").longValue() + 1);
        counts.put("secondWins", counts.get("secondWins").longValue() - 1);
        var payoffs =
                mapper.readerForListOf(SixMaxSuppliedRangePreflopGame.Payoff.class)
                        .readValue(physical.get("payoffs"));
        ((ObjectNode) physical.get("binding"))
                .put("payoffHash", SixMaxHeadsUpPreflopGame.hash(payoffs));
        var physicalOutput = temp.resolve("changed-counts.json");
        mapper.writeValue(physicalOutput.toFile(), physical);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedReachRejection.replay(requestPath(), physicalOutput));
    }

    @Test
    void positiveReachAndInvalidLineageDoNotProduceSavedFailure(@TempDir Path temp)
            throws Exception {
        var r = result.report().request();
        var root =
                new SixMaxSuppliedReachRejection.Request(
                        r.schemaVersion(),
                        r.input(),
                        5,
                        "5:supplied-conditional-preflop:7d 7h:",
                        "fold",
                        r.securitySlack(),
                        r.minimumReach());
        var exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxSuppliedReachRejection.diagnose(root));
        assertEquals(
                "Question did not fail the full-face reach certificate", exception.getMessage());
        var alien = temp.resolve("alien.json");
        SixMaxTexturePayoffTable.mapper().writeValue(alien.toFile(), root);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedReachRejection.replay(alien, reportPath()));
    }

    @Test
    void independentReceiptBindsRequestReportAndStdlibChecker() throws Exception {
        var directory = requestPath().getParent();
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        directory.resolve(
                                                "supplied-four-button-hand-zero-reach-independent-check.json")));
        assertFalse(receipt.get("trainerAdmission").booleanValue());
        assertEquals(0, receipt.get("freshLpSolves").intValue());
        assertEquals(1, receipt.get("originalLpCertificatesChecked").intValue());
        assertEquals(24, receipt.get("checkedPhysicalWorlds").intValue());
        assertEquals(6, receipt.get("checkedSelectedMasses").intValue());
        assertEquals(0, receipt.get("independentMinimumReach").intValue());
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (var pair :
                Map.of(
                                "requestStoredBytesSha256",
                                requestPath(),
                                "reportStoredBytesSha256",
                                reportPath(),
                                "sourceScriptBytesSha256",
                                directory
                                        .getParent()
                                        .getParent()
                                        .resolve("scripts/check-supplied-zero-reach.py"))
                        .entrySet())
            assertEquals(
                    receipt.get(pair.getKey()).textValue(),
                    HexFormat.of().formatHex(digest.digest(Files.readAllBytes(pair.getValue()))));
        assertEquals(
                receipt.get("reportExpandedBytesSha256").textValue(),
                HexFormat.of()
                        .formatHex(
                                digest.digest(
                                        SixMaxRankTexturePayoffTable.readBytes(
                                                reportPath(),
                                                SixMaxSuppliedReachRejection.MAX_BYTES))));
    }
}
