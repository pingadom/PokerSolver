package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxBtnFlopTrainerLoaderTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-four-button-hand-btn-curriculum-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-four-button-hand-btn-curriculum-report.json.gz");
    static final String PIN = "9686d76c4a9d3b1a0875f7fcaed3edecf4491e0c24698892b516f91a69c89cd1";

    @Test
    void pinPathsAliasesAndByteCapsFailBeforeAnyPhysicalWork(@TempDir Path dir) throws Exception {
        for (String pin : Arrays.asList(null, "bad", "0".repeat(64)))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxBtnFlopTrainerLoader.load(REQUEST, REPORT, pin));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopTrainerLoader.load(null, REPORT, PIN));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopTrainerLoader.load(REQUEST, REQUEST, PIN));
        var alias = dir.resolve("request.json");
        Files.createLink(alias, REQUEST.toAbsolutePath());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopTrainerLoader.load(REQUEST, alias, PIN));
        var oversized = dir.resolve("oversized.json");
        Files.writeString(oversized, " ".repeat(SixMaxBtnFlopCurriculum.MAX_BYTES + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopTrainerLoader.load(oversized, REPORT, PIN));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var changed = mapper.valueToTree(SixMaxBtnFlopCurriculum.readRequest(REQUEST));
        ((ObjectNode) changed).put("maximumLossBb", .005);
        var input = dir.resolve("changed-request.json");
        Files.writeString(input, changed.toString());
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxBtnFlopTrainerLoader.load(input, REPORT, PIN));
        assertEquals("BTN curriculum request differs", failure.getMessage());
    }

    @Test
    void rehashedEditedSourceStillRequiresCompletePhysicalReplay(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var changed = mapper.valueToTree(SixMaxBtnFlopCurriculum.readReport(REPORT));
        // Structurally valid report, edited public table fact, and the attacker's matching pin.
        ((ObjectNode) changed.at("/decisions/2/context")).put("potBb", 7.5);
        var output = dir.resolve("changed.json");
        Files.writeString(output, changed.toString());
        String pin = SixMaxHeadsUpPreflopGame.hash(SixMaxBtnFlopCurriculum.readReport(output));
        assertNotEquals(PIN, pin);
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxBtnFlopTrainerLoader.load(REQUEST, output, pin));
        assertEquals("Physical four-hand BTN replay differs", failure.getMessage());
    }

    @Test
    void cliValidatesExplicitChoicesAndProtectedPathsBeforeReplay() {
        assertEquals(
                List.of(new SixMaxBtnFlopTrainer.Choice(2, "bet")),
                SixMaxBtnFlopTrainerMain.choices(new String[] {"2:bet"}));
        for (String invalid : List.of("4:bet", "-1:check", "1:call", "1:BET", "01:bet"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxBtnFlopTrainerMain.choices(new String[] {invalid}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBtnFlopTrainerMain.choices(
                                Collections.nCopies(101, "0:check").toArray(String[]::new)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxBtnFlopTrainerMain.main(
                                new String[] {
                                    "create",
                                    REQUEST.toString(),
                                    REPORT.toString(),
                                    PIN,
                                    REQUEST.toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxBtnFlopTrainerMain.main(new String[] {"replay"}));
    }
}
