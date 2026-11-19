package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixSeatTrainerActionAuditTest {
    @TempDir Path temp;

    @Test
    void cliScoresTheSamePromptsAndConservesDrawAndSeatCounts() throws Exception {
        var input = temp.resolve("input.json");
        var output = temp.resolve("result.json");
        var mapper = SixMaxTexturePayoffTable.mapper();
        var request =
                SixSeatRangeSourceTest.request(
                        SixSeatRangeSourceTest.SINGLE,
                        SixSeatPayoffWork.Configuration.shared(4, 711));
        Files.write(input, mapper.writeValueAsBytes(request));
        var args =
                new String[] {
                    "compare-trainer",
                    input.toString(),
                    output.toString(),
                    "658007",
                    "3948048",
                    "exact-suit-reuse",
                    "128",
                    "-9223372036854775808",
                    "0.01",
                    "0.02"
                };
        assertThrows(
                SixSeatPayoffWork.BudgetExceeded.class, () -> SixSeatRangeSourceMain.main(args));
        assertFalse(Files.exists(output));
        args[3] = "658008";
        SixSeatRangeSourceMain.main(args);
        var report = mapper.readTree(Files.readAllBytes(output));
        var audit = report.get("trainerActionAudit");
        assertEquals(
                "pokerlab-six-seat-trainer-action-audit/v1", report.get("schemaVersion").asText());
        assertFalse(report.get("trainerAdmission").asBoolean());
        assertFalse(report.get("gradingAuthority").asBoolean());
        assertEquals("EXACT_SUIT_REUSE", report.get("exactReferenceMethod").asText());
        assertEquals(1, report.get("plannedExactEvaluatedDeals").asInt());
        assertEquals("-9223372036854775808", audit.get("firstSeed").asText());
        assertEquals(128, audit.get("draws").asInt());
        assertEquals(6, audit.get("seats").size());
        int draws = 0;
        int stable = 0;
        for (var row : audit.get("prompts")) {
            draws += row.get("draws").asInt();
            assertEquals(row.at("/prompt/legalActions").size(), row.get("actions").size());
            assertFalse(row.get("sampledAcceptableActions").isEmpty());
            assertFalse(row.get("exactAcceptableActions").isEmpty());
            double max = 0;
            for (var action : row.get("actions")) {
                double sampled = action.get("sampledLossBb").asDouble();
                double exact = action.get("exactLossBb").asDouble();
                assertTrue(sampled >= 0 && exact >= 0);
                max = Math.max(max, Math.abs(sampled - exact));
            }
            assertEquals(max, row.get("maximumLossDriftBb").asDouble());
            boolean passes =
                    row.get("sampledAcceptableActions").equals(row.get("exactAcceptableActions"))
                            && max <= .02;
            assertEquals(passes, row.get("stable").asBoolean());
            if (passes) stable++;
        }
        assertEquals(128, draws);
        assertEquals(audit.get("prompts").size(), audit.get("distinctPrompts").asInt());
        assertEquals(stable, audit.get("stablePrompts").asInt());
        assertTrue(audit.get("distinctPrompts").asInt() < 128);
        assertTrue(stable < audit.get("distinctPrompts").asInt());
        int seatDraws = 0;
        for (var seat : audit.get("seats")) seatDraws += seat.get("draws").asInt();
        assertEquals(128, seatDraws);
        assertThrows(
                java.nio.file.FileAlreadyExistsException.class,
                () -> SixSeatRangeSourceMain.main(args));
    }

    @Test
    void optionBoundsIncludeTheLastSeedAndRejectOverflowOrNonFiniteLimits() {
        assertDoesNotThrow(() -> new SixSeatTrainerActionAudit.Options(1, Long.MAX_VALUE, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixSeatTrainerActionAudit.Options(2, Long.MAX_VALUE, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixSeatTrainerActionAudit.Options(513, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixSeatTrainerActionAudit.Options(1, 0, Double.NaN, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixSeatTrainerActionAudit.Options(1, 0, 0, Double.POSITIVE_INFINITY));
    }
}
