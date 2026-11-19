package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixSeatRangeSourceMainTest {
    @TempDir Path temp;

    Path input(String name, long seed) throws Exception {
        Path p = temp.resolve(name);
        var r =
                SixSeatRangeSourceTest.request(
                        SixSeatRangeSourceTest.SINGLE,
                        SixSeatPayoffWork.Configuration.shared(4, seed));
        Files.write(p, SixMaxTexturePayoffTable.mapper().writeValueAsBytes(r));
        return p;
    }

    @Test
    void capturedPlanHasFullWidthSeedIdentityNoPayoffsAndNoAdmission() throws Exception {
        Path in = input("input.json", Long.MIN_VALUE), out = temp.resolve("plan.json");
        byte[] before = Files.readAllBytes(in);
        SixSeatRangeSourceMain.main(new String[] {"plan", in.toString(), out.toString()});
        var j = SixMaxTexturePayoffTable.mapper().readTree(out.toFile());
        assertEquals("pokerlab-six-seat-range-source/v1", j.get("schemaVersion").asText());
        assertFalse(j.get("solved").asBoolean());
        assertFalse(j.has("sourcePackJson"));
        assertFalse(j.get("trainerAdmission").asBoolean());
        assertFalse(j.get("gradingAuthority").asBoolean());
        assertEquals(
                Long.toString(Long.MIN_VALUE), j.get("request").get("payoff").get("seed").asText());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(before)),
                j.get("requestSha256").asText());
        assertArrayEquals(before, Files.readAllBytes(in));
    }

    @Test
    void freshSolveExportsValidatedOpaquePackWithoutRoundingItsLegacySeed() throws Exception {
        Path in = input("input.json", Long.MIN_VALUE), out = temp.resolve("source.json");
        SixSeatRangeSourceMain.main(new String[] {"solve", in.toString(), out.toString()});
        var j = SixMaxTexturePayoffTable.mapper().readTree(out.toFile());
        assertTrue(j.get("solved").asBoolean());
        assertTrue(j.get("sourcePackJson").isTextual());
        var pack = MultiwayPackJson.readFullRound(j.get("sourcePackJson").asText());
        assertEquals(Long.MIN_VALUE, pack.payoffSeed());
        assertEquals(MultiwaySolutionPack.VALIDATION_ONLY, pack.publicationStatus());
        assertEquals(MultiwayPackJson.fullRoundContentHash(pack), j.get("sourcePackHash").asText());
        assertEquals(
                j.get("plannedCfrWork").get("cfrNodeVisits").asLong(),
                j.get("cfrStatistics").get("visitedNodes").asLong());
        assertEquals(4, j.get("observedSharedBoards").asLong());
        assertFalse(j.get("trainerAdmission").asBoolean());
        assertFalse(j.get("gradingAuthority").asBoolean());
    }

    @Test
    void strictDecoderRejectsMissingUnknownFractionalDuplicateAndNumericSeedInputs()
            throws Exception {
        Path good = input("good.json", 0),
                bad = temp.resolve("bad.json"),
                out = temp.resolve("out.json");
        var mapper = SixMaxTexturePayoffTable.mapper();
        String raw = Files.readString(good);
        for (String text :
                List.of(
                        raw.replace("\"iterations\":2", "\"iterations\":2.0"),
                        raw.replace("\"iterations\":2", "\"iterations\":2,\"iterations\":2"),
                        raw + " {}")) {
            Files.writeString(bad, text);
            assertThrows(
                    JsonProcessingException.class,
                    () ->
                            SixSeatRangeSourceMain.main(
                                    new String[] {"solve", bad.toString(), out.toString()}));
            assertFalse(Files.exists(out));
        }
        for (String field :
                List.of(
                        "missing",
                        "unknown",
                        "seed",
                        "float_seed",
                        "boolean_seed",
                        "variant",
                        "method",
                        "range")) {
            ObjectNode n = (ObjectNode) mapper.readTree(raw);
            if (field.equals("missing")) n.remove("generatedAt");
            else if (field.equals("unknown")) n.put("invented", true);
            else if (field.equals("float_seed")) ((ObjectNode) n.get("payoff")).put("seed", 0.0);
            else if (field.equals("boolean_seed")) ((ObjectNode) n.get("payoff")).put("seed", true);
            else if (field.equals("variant")) n.put("variant", 0);
            else if (field.equals("method")) ((ObjectNode) n.get("payoff")).put("method", 1);
            else if (field.equals("range"))
                ((com.fasterxml.jackson.databind.node.ArrayNode) n.get("ranges"))
                        .set(0, mapper.getNodeFactory().numberNode(22));
            else ((ObjectNode) n.get("payoff")).put("seed", 0);
            Files.writeString(bad, mapper.writeValueAsString(n));
            assertThrows(
                    JsonProcessingException.class,
                    () ->
                            SixSeatRangeSourceMain.main(
                                    new String[] {"solve", bad.toString(), out.toString()}));
            assertFalse(Files.exists(out));
        }
    }

    @Test
    void requestByteCapRejectsWithoutReadingAnUnboundedFileOrPublishing() throws Exception {
        Path in = temp.resolve("large.json"), out = temp.resolve("out.json");
        Files.write(in, new byte[SixSeatRangeSourceMain.MAX_REQUEST_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixSeatRangeSourceMain.main(
                                new String[] {"solve", in.toString(), out.toString()}));
        assertFalse(Files.exists(out));
    }

    @Test
    void overBudgetSolveDoesNotCreateAReportOrRunPartialSourceWork() throws Exception {
        Path in = input("input.json", 0), out = temp.resolve("out.json");
        var mapper = SixMaxTexturePayoffTable.mapper();
        ObjectNode n = (ObjectNode) mapper.readTree(in.toFile());
        n.put("maximumCfrNodeVisits", 1);
        Files.writeString(in, mapper.writeValueAsString(n));
        assertThrows(
                SixSeatRangeSource.BudgetExceeded.class,
                () ->
                        SixSeatRangeSourceMain.main(
                                new String[] {"solve", in.toString(), out.toString()}));
        assertFalse(Files.exists(out));
    }

    @Test
    void existingOutputsInputPathsAndHardLinksArePreservedBeforeSolving() throws Exception {
        Path in = input("input.json", 0), alias = temp.resolve("alias.json");
        Files.createLink(alias, in);
        byte[] before = Files.readAllBytes(in);
        for (Path out : List.of(in, alias))
            assertThrows(
                    FileAlreadyExistsException.class,
                    () ->
                            SixSeatRangeSourceMain.main(
                                    new String[] {"solve", in.toString(), out.toString()}));
        assertArrayEquals(before, Files.readAllBytes(in));
        assertArrayEquals(before, Files.readAllBytes(alias));
    }

    @Test
    void whitespaceChangesCaptureHashButNotSemanticSourcePlan() throws Exception {
        Path a = input("a.json", 0),
                b = temp.resolve("b.json"),
                x = temp.resolve("x.json"),
                y = temp.resolve("y.json");
        Files.writeString(b, "  " + Files.readString(a) + "\n");
        SixSeatRangeSourceMain.main(new String[] {"plan", a.toString(), x.toString()});
        SixSeatRangeSourceMain.main(new String[] {"plan", b.toString(), y.toString()});
        var mapper = SixMaxTexturePayoffTable.mapper();
        var p = mapper.readTree(x.toFile());
        var q = mapper.readTree(y.toFile());
        assertEquals(p.get("sourcePlanHash"), q.get("sourcePlanHash"));
        assertNotEquals(p.get("requestSha256"), q.get("requestSha256"));
    }
}
