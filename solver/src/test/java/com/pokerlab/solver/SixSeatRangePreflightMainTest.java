package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixSeatRangePreflightMainTest {
    @TempDir Path temp;

    String[] args(Path out, List<String> ranges) {
        var args = new ArrayList<String>();
        args.add(out.toString());
        args.addAll(ranges);
        return args.toArray(String[]::new);
    }

    @Test
    void exportsCompleteOfflineSupportWithSafeIntegerIdentityAndNoGradingAuthority()
            throws Exception {
        Path out = temp.resolve("plan.json");
        SixSeatRangePreflightMain.main(args(out, SixSeatRangePreflightTest.CORRELATED));
        var j = SixMaxTexturePayoffTable.mapper().readTree(out.toFile());
        assertEquals("pokerlab-six-seat-range-preflight/v1", j.get("schemaVersion").asText());
        assertEquals("16", j.get("nominalProducts").asText());
        assertTrue(j.get("nominalProducts").isTextual());
        assertEquals(12, j.get("legalDeals").asInt());
        assertEquals(12, j.get("deals").size());
        assertEquals(6, j.get("seatSupport").size());
        assertEquals(6, j.get("ranges").size());
        assertEquals(2, j.get("ranges").get(1).size());
        assertFalse(j.get("trainerAdmission").asBoolean());
        assertFalse(j.get("gradingAuthority").asBoolean());
        assertEquals(31, j.get("work").get("candidateChecks").asInt());
    }

    @Test
    void impossibleOrOversizedProductsDoNotExportPartialFiles() {
        Path out = temp.resolve("bad.json");
        for (var ranges :
                List.of(
                        Collections.nCopies(6, "AA"),
                        List.of("22", "33", "44", "55", "66", "77"))) {
            assertThrows(
                    SixSeatRangePreflight.Rejection.class,
                    () -> SixSeatRangePreflightMain.main(args(out, ranges)));
            assertFalse(Files.exists(out));
        }
    }

    @Test
    void existingOutputsAndHardLinksRemainUnchanged() throws Exception {
        Path out = temp.resolve("owner.json"), alias = temp.resolve("alias.json");
        Files.writeString(out, "owner");
        Files.createLink(alias, out);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixSeatRangePreflightMain.main(args(out, SixSeatRangePreflightTest.SINGLE)));
        assertThrows(
                FileAlreadyExistsException.class,
                () ->
                        SixSeatRangePreflightMain.main(
                                args(alias, SixSeatRangePreflightTest.SINGLE)));
        assertEquals("owner", Files.readString(out));
        assertEquals("owner", Files.readString(alias));
    }

    @Test
    void malformedArgumentsNeverCreateFiles() {
        Path out = temp.resolve("bad.json");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixSeatRangePreflightMain.main(new String[] {out.toString(), "AA"}));
        assertThrows(IllegalArgumentException.class, () -> SixSeatRangePreflightMain.main(null));
        var bad = new ArrayList<>(SixSeatRangePreflightTest.SINGLE);
        bad.set(0, "AK");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixSeatRangePreflightMain.main(args(out, bad)));
        assertFalse(Files.exists(out));
    }

    @Test
    void equivalentInputOrderHasDeterministicExports() throws Exception {
        Path a = temp.resolve("a.json"), b = temp.resolve("b.json");
        var ranges = new ArrayList<>(SixSeatRangePreflightTest.CORRELATED);
        ranges.set(1, "AhJh,Kd9d@1");
        SixSeatRangePreflightMain.main(args(a, SixSeatRangePreflightTest.CORRELATED));
        SixSeatRangePreflightMain.main(args(b, ranges));
        assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(b));
    }

    @Test
    void exportsUnsupportedDeclaredCombosWithoutPretendingTheyArePossible() throws Exception {
        var ranges = new ArrayList<>(SixSeatRangePreflightTest.SINGLE);
        ranges.set(1, "KcKd,AcKh@0.5");
        Path out = temp.resolve("unsupported.json");
        SixSeatRangePreflightMain.main(args(out, ranges));
        var j = SixMaxTexturePayoffTable.mapper().readTree(out.toFile());
        assertEquals(2, j.get("ranges").get(1).size());
        assertEquals(1, j.get("legalDeals").asInt());
        assertFalse(j.get("allInputCombosSupported").asBoolean());
        assertEquals(1, j.get("seatSupport").get(1).get("unsupportedCombos").size());
    }
}
