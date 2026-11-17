package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeightedRangeNotationMainTest {
    @TempDir Path temp;

    @Test
    void weightedPhysicalExportHasNoGradingAuthorityAndRetainsBlockerAccounting() throws Exception {
        Path out = temp.resolve("range.json");
        WeightedRangeNotationMain.main(
                new String[] {"AA@0.5,AKs,AKo@0.25", out.toString(), "Ac,Kd"});
        var j = SixMaxTexturePayoffTable.mapper().readTree(out.toFile());
        assertEquals("pokerlab-weighted-range-input/v1", j.get("schemaVersion").asText());
        assertFalse(j.get("trainerAdmission").asBoolean());
        assertFalse(j.get("gradingAuthority").asBoolean());
        assertEquals(22, j.get("inputCombos").asInt());
        assertEquals(10, j.get("blockedCombos").asInt());
        assertEquals(12, j.get("retainedCombos").asInt());
        assertEquals(5.25, j.get("retainedWeightMass").asDouble());
        assertEquals(12, j.get("combos").size());
        assertEquals(
                WeightedRangeNotation.compile("AA@0.5,AKs,AKo@0.25").contentHash(),
                j.get("sourceRangeHash").asText());
    }

    @Test
    void exportsAreDeterministicForEquivalentExpressionsAndKnownCardOrder() throws Exception {
        Path a = temp.resolve("a.json"), b = temp.resolve("b.json");
        WeightedRangeNotationMain.main(new String[] {"AA,AKs", a.toString(), "Ac,Kd"});
        WeightedRangeNotationMain.main(new String[] {"AKs,AA@1", b.toString(), "Kd,Ac"});
        assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(b));
    }

    @Test
    void allBlockedReportIsExplicitInsteadOfInventingPlayableSupport() throws Exception {
        Path out = temp.resolve("empty.json");
        WeightedRangeNotationMain.main(new String[] {"AA", out.toString(), "Ac,Ad,Ah,As"});
        var j = SixMaxTexturePayoffTable.mapper().readTree(out.toFile());
        assertFalse(j.get("hasSupport").asBoolean());
        assertEquals(0, j.get("combos").size());
        assertEquals(0, j.get("retainedWeightMass").asDouble());
    }

    @Test
    void invalidInputsAndArgumentInventoriesDoNotCreateOutput() throws Exception {
        Path out = temp.resolve("bad.json");
        for (String[] args :
                new String[][] {
                    {"AK", out.toString()},
                    {"AA", out.toString(), "Ac,Ac"},
                    {"AA", out.toString(), "Ac,"},
                    {"AA", out.toString(), ""},
                    {"AA", out.toString(), "Ac", "extra"}
                }) {
            assertThrows(
                    IllegalArgumentException.class, () -> WeightedRangeNotationMain.main(args));
            assertFalse(Files.exists(out));
        }
        assertThrows(IllegalArgumentException.class, () -> WeightedRangeNotationMain.main(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> WeightedRangeNotationMain.main(new String[] {}));
    }

    @Test
    void existingFilesAndHardLinkAliasesAreNeverOverwritten() throws Exception {
        Path out = temp.resolve("existing.json"), alias = temp.resolve("alias.json");
        Files.writeString(out, "owner data");
        Files.createLink(alias, out);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> WeightedRangeNotationMain.main(new String[] {"AA", out.toString()}));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> WeightedRangeNotationMain.main(new String[] {"AA", alias.toString()}));
        assertEquals("owner data", Files.readString(out));
        assertEquals("owner data", Files.readString(alias));
    }
}
