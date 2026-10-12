package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedReachBoundsTest {
    static Path requestPath() {
        return SixMaxSuppliedRangePreflopGameTest.data(
                "supplied-four-button-hand-reach-bounds-request.json");
    }

    static Path reportPath() {
        return requestPath()
                .resolveSibling("supplied-four-button-hand-reach-bounds-report.json.gz");
    }

    static SixMaxSuppliedReachBounds.Result result;

    @BeforeAll
    static void physicalReplay() throws Exception {
        result = SixMaxSuppliedReachBounds.replay(requestPath(), reportPath());
    }

    @Test
    void maximumReachRulesOutEvenTheSmallestExistingFloor() throws Exception {
        var r = result.report();
        var a = r.diagnostic();
        assertFalse(r.trainerAdmission());
        assertEquals(24, r.prior().size());
        assertEquals(15792192, r.binding().budget().enumeratedBoards());
        assertEquals(31584384, r.binding().budget().handEvaluations());
        assertEquals("UNAVAILABLE_NO_SOURCE_POLICY", r.binding().sourceHistoryReachStatus());
        assertEquals(
                FiniteTwoPlayerReachBounds.Status.CERTIFIED_ALL_BELOW_REQUIRED_FLOOR, a.status());
        assertEquals(0, a.minimumReachWitness().questionReach(), 1e-12);
        assertEquals(3.94280367682e-5, a.maximumReachWitness().questionReach(), 1e-12);
        assertEquals(3.94380367693e-5, a.certifiedReachUpperBound(), 1e-12);
        assertTrue(a.certifiedReachUpperBound() < .0001);
        assertEquals(.01, a.requiredMinimumReach());
        assertEquals(2, a.work().intervalLpSolves());
        assertEquals(315337.0 / 607392, a.globalHeroUpperValue(), 1e-12);
        var old =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        requestPath()
                                                .resolveSibling(
                                                        "supplied-four-button-hand-zero-reach-report.json.gz"),
                                        SixMaxSuppliedReachBounds.MAX_BYTES),
                                SixMaxSuppliedReachRejection.Report.class);
        assertEquals(old.prior(), r.prior());
        assertEquals(old.payoffs(), r.payoffs());
        assertEquals(old.diagnostic().baseline(), a.baseline());
        assertEquals(old.diagnostic().minimumReachWitness(), a.minimumReachWitness());
        for (var w : List.of(a.minimumReachWitness(), a.maximumReachWitness())) {
            assertEquals(6, w.masses().size());
            assertTrue(
                    w.globalHeroBestResponse()
                            <= a.globalHeroUpperValue() + a.securitySlack() + 1e-8);
        }
    }

    @Test
    void exclusivePlainAndGzipReportsPhysicallyReplay(@TempDir Path temp) throws Exception {
        for (String suffix : List.of("json", "json.gz")) {
            var out = temp.resolve("bounds." + suffix);
            SixMaxSuppliedReachBounds.write(out, result);
            assertEquals(
                    result.report(), SixMaxSuppliedReachBounds.replay(requestPath(), out).report());
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxSuppliedReachBounds.write(out, result));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedReachBoundsMain.main(
                                new String[] {
                                    "assess", requestPath().toString(), reportPath().toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedReachBoundsMain.main(
                                new String[] {
                                    "assess", requestPath().toString(), requestPath().toString()
                                }));
    }

    @Test
    void alteredMaximumCertificateAndRehashedCountsCannotReplay(@TempDir Path temp)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        ObjectNode tree = mapper.valueToTree(result.report());
        ((ObjectNode) tree.get("diagnostic").get("maximumReachWitness").get("certificate"))
                .put("dualValue", .5);
        var out = temp.resolve("dual.json");
        mapper.writeValue(out.toFile(), tree);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedReachBounds.replay(requestPath(), out));
        ObjectNode physical = mapper.valueToTree(result.report());
        var counts = (ObjectNode) physical.get("payoffs").get(0).get("counts");
        counts.put("firstWins", counts.get("firstWins").longValue() + 1);
        counts.put("secondWins", counts.get("secondWins").longValue() - 1);
        var payoffs =
                mapper.readerForListOf(SixMaxSuppliedRangePreflopGame.Payoff.class)
                        .readValue(physical.get("payoffs"));
        ((ObjectNode) physical.get("binding"))
                .put("payoffHash", SixMaxHeadsUpPreflopGame.hash(payoffs));
        var changed = temp.resolve("counts.json");
        mapper.writeValue(changed.toFile(), physical);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedReachBounds.replay(requestPath(), changed));
    }

    @Test
    void positiveQuestionIsDiagnosticOnlyAndLegacyFormatsStayDistinct(@TempDir Path temp)
            throws Exception {
        var q = result.report().request();
        var root =
                new SixMaxSuppliedReachBounds.Request(
                        q.schemaVersion(),
                        q.input(),
                        5,
                        "5:supplied-conditional-preflop:7d 7h:",
                        "fold",
                        q.securitySlack(),
                        q.minimumReach());
        var positive = SixMaxSuppliedReachBounds.assess(root).report();
        assertEquals(
                FiniteTwoPlayerReachBounds.Status.CERTIFIED_ALL_AT_OR_ABOVE_REQUIRED_FLOOR,
                positive.diagnostic().status());
        assertEquals(1.0 / 3, positive.diagnostic().minimumReachWitness().questionReach(), 1e-12);
        assertFalse(positive.trainerAdmission());
        assertTrue(
                Arrays.stream(SixMaxSuppliedReachBounds.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var alien = temp.resolve("alien.json");
        SixMaxTexturePayoffTable.mapper().writeValue(alien.toFile(), root);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedReachBounds.replay(alien, reportPath()));
        assertThrows(
                Exception.class, () -> SixMaxSuppliedReachRejection.readRequest(requestPath()));
        assertThrows(
                Exception.class, () -> SixMaxSuppliedIntervalFeedback.readRequest(requestPath()));
        assertThrows(
                Exception.class,
                () ->
                        SixMaxSuppliedReachBounds.readRequest(
                                requestPath()
                                        .resolveSibling(
                                                "supplied-four-button-hand-zero-reach-request.json")));
    }

    @Test
    void independentReceiptBindsBothEndpointsAndExactLiteralPlanProof() throws Exception {
        var directory = requestPath().getParent();
        var receipt =
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        directory.resolve(
                                                "supplied-four-button-hand-reach-bounds-independent-check.json")));
        assertFalse(receipt.get("trainerAdmission").booleanValue());
        assertEquals(0, receipt.get("freshLpSolves").intValue());
        assertEquals(2, receipt.get("originalLpCertificatesChecked").intValue());
        assertEquals(54, receipt.get("literalOpponentPlansChecked").intValue());
        assertEquals("315337/607392", receipt.get("exactGameValueRational").textValue());
        assertTrue(receipt.get("independentlyCheckedReachUpper").doubleValue() < .0001);
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
                                        .resolve("scripts/check-supplied-reach-bounds.py"))
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
                                                SixMaxSuppliedReachBounds.MAX_BYTES))));
    }
}
