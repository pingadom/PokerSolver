package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixSeatPayoffWorkMainTest {
    @TempDir Path temp;

    static String[] args(
            String mode,
            Path out,
            String method,
            String boards,
            String hands,
            List<String> ranges) {
        var v = new ArrayList<>(List.of(mode, out.toString(), method, boards, hands));
        v.addAll(ranges);
        return v.toArray(String[]::new);
    }

    @Test
    void planOnlyExactExportDoesNotEvaluateOrGrantAdmission() throws Exception {
        Path p = temp.resolve("exact-plan.json");
        SixSeatPayoffWorkMain.main(
                args("plan", p, "exact", "658008", "3948048", SixSeatPayoffWorkTest.SINGLE));
        var j = SixMaxTexturePayoffTable.mapper().readTree(p.toFile());
        assertEquals("pokerlab-six-seat-payoff-work/v1", j.get("schemaVersion").asText());
        assertFalse(j.get("executed").asBoolean());
        assertFalse(j.has("payoffs"));
        assertFalse(j.has("observedSharedBoards"));
        assertFalse(j.get("trainerAdmission").asBoolean());
        assertFalse(j.get("gradingAuthority").asBoolean());
        assertEquals(658008, j.get("plannedWork").get("boardEvaluations").asLong());
        assertTrue(j.get("configuration").get("seed").isTextual());
        assertEquals("0", j.get("configuration").get("seed").asText());
        assertEquals(6, j.get("ranges").size());
    }

    @Test
    void explicitSharedEvaluationExportsCompletePrivateRowsAndObservedWork() throws Exception {
        Path p = temp.resolve("sampled.json");
        SixSeatPayoffWorkMain.main(
                args(
                        "evaluate",
                        p,
                        "shared:4:" + Long.MIN_VALUE,
                        "4",
                        "24",
                        SixSeatPayoffWorkTest.SINGLE));
        var j = SixMaxTexturePayoffTable.mapper().readTree(p.toFile());
        assertTrue(j.get("executed").asBoolean());
        assertEquals(57, j.get("payoffs").size());
        assertEquals(4, j.get("observedSharedBoards").asLong());
        assertEquals(4, j.get("boardsFromPerDealEstimates").asLong());
        assertEquals(Long.toString(Long.MIN_VALUE), j.get("configuration").get("seed").asText());
        assertEquals("SHARED_BOARD_MONTE_CARLO", j.get("configuration").get("method").asText());
        assertFalse(j.get("gradingAuthority").asBoolean());
        assertFalse(j.get("trainerAdmission").asBoolean());
    }

    @Test
    void budgetAndRangeRejectionsNeverCreatePartialReports() {
        Path p = temp.resolve("rejected.json");
        assertThrows(
                SixSeatPayoffWork.BudgetExceeded.class,
                () ->
                        SixSeatPayoffWorkMain.main(
                                args(
                                        "evaluate",
                                        p,
                                        "shared:8:711",
                                        "95",
                                        "576",
                                        SixSeatPayoffWorkTest.CORRELATED)));
        assertFalse(Files.exists(p));
        assertThrows(
                SixSeatRangePreflight.Rejection.class,
                () ->
                        SixSeatPayoffWorkMain.main(
                                args(
                                        "plan",
                                        p,
                                        "exact",
                                        "10000000",
                                        "60000000",
                                        List.of("22", "33", "44", "55", "66", "77"))));
        assertFalse(Files.exists(p));
    }

    @Test
    void existingFilesAndHardLinksRejectBeforeAnyEvaluation() throws Exception {
        Path p = temp.resolve("owner.json"), alias = temp.resolve("alias.json");
        Files.writeString(p, "owner");
        Files.createLink(alias, p);
        for (Path path : List.of(p, alias))
            assertThrows(
                    FileAlreadyExistsException.class,
                    () ->
                            SixSeatPayoffWorkMain.main(
                                    args(
                                            "evaluate",
                                            path,
                                            "exact",
                                            "658008",
                                            "3948048",
                                            SixSeatPayoffWorkTest.SINGLE)));
        assertEquals("owner", Files.readString(p));
        assertEquals("owner", Files.readString(alias));
    }

    @Test
    void invalidArgumentsMethodsAndNumericBudgetsDoNotCreateOutput() {
        Path p = temp.resolve("bad.json");
        assertThrows(IllegalArgumentException.class, () -> SixSeatPayoffWorkMain.main(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixSeatPayoffWorkMain.main(new String[] {"plan"}));
        for (String method :
                List.of(
                        "auto",
                        "exact:1:0",
                        "shared:0:0",
                        "shared:1:9223372036854775808",
                        "shared:1",
                        "shared:x:0"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixSeatPayoffWorkMain.main(
                                    args(
                                            "plan",
                                            p,
                                            method,
                                            "4",
                                            "24",
                                            SixSeatPayoffWorkTest.SINGLE)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixSeatPayoffWorkMain.main(
                                args(
                                        "solve",
                                        p,
                                        "exact",
                                        "658008",
                                        "3948048",
                                        SixSeatPayoffWorkTest.SINGLE)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixSeatPayoffWorkMain.main(
                                args(
                                        "plan",
                                        p,
                                        "exact",
                                        "10000001",
                                        "60000000",
                                        SixSeatPayoffWorkTest.SINGLE)));
        assertFalse(Files.exists(p));
    }

    @Test
    void equivalentInputsAndSamplingConfigurationsHaveDeterministicReports() throws Exception {
        Path a = temp.resolve("a.json"), b = temp.resolve("b.json");
        var ranges = new ArrayList<>(SixSeatPayoffWorkTest.CORRELATED);
        ranges.set(1, "AhJh@1,Kd9d");
        SixSeatPayoffWorkMain.main(
                args("plan", a, "shared:8:711", "96", "576", SixSeatPayoffWorkTest.CORRELATED));
        SixSeatPayoffWorkMain.main(args("plan", b, "shared:8:711", "96", "576", ranges));
        assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(b));
    }
}
