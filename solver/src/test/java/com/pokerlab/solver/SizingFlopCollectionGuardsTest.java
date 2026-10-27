package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SizingFlopCollectionGuardsTest {
    @Test
    void typedManifestRejectsChangedBudgetSourcePinsAndEscapingFiles() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var valid =
                SixMaxSizingFlopCollectionLoader.readManifest(
                        SizingFlopCollectionIntegrationTest.MANIFEST);
        assertEquals(
                21,
                SixMaxSizingFlopCollectionLoader.dependencies(
                                SizingFlopCollectionIntegrationTest.MANIFEST, valid)
                        .size());
        for (String key :
                List.of(
                        "schemaVersion",
                        "potReportHash",
                        "legacyCollectionHash",
                        "potRequestFile",
                        "legacyManifestFile",
                        "startupBudget")) {
            var n = (ObjectNode) mapper.valueToTree(valid);
            if (key.equals("startupBudget"))
                ((ObjectNode) n.path(key)).put("freshIterations", 1212000);
            else n.put(key, key.endsWith("File") ? "../escape.json" : "0".repeat(64));
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(n, SixMaxSizingFlopCollectionLoader.Manifest.class),
                    key);
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizingFlopCollectionLoader.load(
                                SizingFlopCollectionIntegrationTest.MANIFEST, "0".repeat(64)));
    }

    @Test
    void strictReviewsRejectDuplicateUnknownNonIntegralAndOversizeInput(@TempDir Path temp)
            throws Exception {
        var original = Files.readString(SizingFlopCollectionIntegrationTest.REVIEW);
        for (String text :
                List.of(
                        original + "{}",
                        original.replaceFirst("\"schemaVersion\"", "\"extra\""),
                        original.replaceFirst("\"index\" : 0", "\"index\" : 0.5"),
                        original.replaceFirst(
                                "\"collectionHash\"",
                                "\"collectionHash\":\"0\",\"collectionHash\""))) {
            var p = temp.resolve(UUID.randomUUID() + ".json");
            Files.writeString(p, text);
            assertThrows(Exception.class, () -> SixMaxSizingFlopCollection.read(p));
        }
        var huge = temp.resolve("huge.json");
        Files.write(huge, new byte[SixMaxSizingFlopCollection.MAX_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> SixMaxSizingFlopCollection.read(huge));
    }

    @Test
    void cliRejectsExistingOutputsAliasesAndBadChoicesBeforeSourceReplay(@TempDir Path temp)
            throws Exception {
        var output = temp.resolve("keep.json");
        Files.writeString(output, "keep");
        var manifest = SizingFlopCollectionIntegrationTest.MANIFEST.toString();
        var pin = SizingFlopCollectionIntegrationTest.PIN;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizingFlopCollectionMain.main(
                                new String[] {"create", manifest, pin, output.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizingFlopCollectionMain.main(
                                new String[] {"create", manifest, pin, manifest}));
        for (String choice : List.of("20:call", "-1:call", "18:bet", "19:check", "x:call"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxSizingFlopCollectionMain.main(
                                    new String[] {
                                        "create",
                                        manifest,
                                        pin,
                                        temp.resolve("fresh.json").toString(),
                                        choice
                                    }));
        assertEquals("keep", Files.readString(output));
        assertFalse(Files.exists(temp.resolve("fresh.json")));
    }
}
