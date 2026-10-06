package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxPrivateRangeCorrelationAuditMainTest {
    @Test
    void savedSourceAuditIsHashBoundAndRequiresNoConnectedMenu(@TempDir Path temp)
            throws Exception {
        var source = Path.of("../docs/data/sixmax-correlated-source-pack.json");
        var output = temp.resolve("nested/correlation.json");
        var original = Files.readAllBytes(source);
        SixMaxPrivateRangeCorrelationAuditMain.main(
                new String[] {source.toString(), output.toString()});
        var artifact =
                new ObjectMapper()
                        .readValue(
                                output.toFile(),
                                SixMaxPrivateRangeCorrelationAuditMain.Artifact.class);
        assertEquals(
                artifact,
                new ObjectMapper()
                        .readValue(
                                Path.of("../docs/data/sixmax-correlated-private-range-audit.json")
                                        .toFile(),
                                SixMaxPrivateRangeCorrelationAuditMain.Artifact.class));
        var pack = SixMaxCorrelatedSourcePackTest.source();
        assertEquals("six-max-private-range-correlation/v2", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals(MultiwayPackJson.fullRoundContentHash(pack), artifact.sourcePackHash());
        assertEquals(pack.spotHash(), artifact.sourceSpotHash());
        assertEquals(
                SixMaxPrivateRangeCorrelationAudit.assess(pack.rebuildGame()),
                artifact.correlation());
        assertEquals(
                SixMaxJointRangeDependenceAudit.assess(pack.rebuildGame()),
                artifact.jointDependence());
        var legacy = new ObjectMapper().valueToTree(artifact);
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).remove("jointDependence");
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy)
                .put("schemaVersion", "six-max-private-range-correlation/v1");
        var historical =
                new ObjectMapper()
                        .treeToValue(legacy, SixMaxPrivateRangeCorrelationAuditMain.Artifact.class);
        assertNull(historical.jointDependence());
        assertEquals(artifact.correlation(), historical.correlation());
        assertArrayEquals(original, Files.readAllBytes(source));
        try (var children = Files.list(output.getParent())) {
            assertEquals(1, children.count());
        }
    }

    @Test
    void invalidSourceAndAliasedOutputsPreserveExistingFiles(@TempDir Path temp) throws Exception {
        var source = temp.resolve("source.json");
        var original =
                Files.readAllBytes(Path.of("../docs/data/sixmax-correlated-source-pack.json"));
        Files.write(source, original);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPrivateRangeCorrelationAuditMain.main(
                                new String[] {
                                    source.toString(),
                                    temp.resolve("nested/../source.json").toString()
                                }));
        assertArrayEquals(original, Files.readAllBytes(source));
        var alias = temp.resolve("hard-link.json");
        Files.createLink(alias, source);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPrivateRangeCorrelationAuditMain.main(
                                new String[] {source.toString(), alias.toString()}));
        assertArrayEquals(original, Files.readAllBytes(source));
        var output = temp.resolve("report.json");
        Files.writeString(output, "keep report");
        Files.writeString(source, "invalid JSON");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPrivateRangeCorrelationAuditMain.main(
                                new String[] {source.toString(), output.toString()}));
        assertEquals("keep report", Files.readString(output));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPrivateRangeCorrelationAuditMain.main(
                                new String[] {source.toString(), output.toString(), "extra"}));
        assertEquals("keep report", Files.readString(output));
    }
}
