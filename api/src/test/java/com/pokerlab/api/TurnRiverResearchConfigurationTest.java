package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.TurnRiverPackBuilder;
import com.pokerlab.solver.TurnRiverPackJson;
import com.pokerlab.solver.TurnRiverValidationSpot;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TurnRiverResearchConfigurationTest {
    @TempDir Path tempDir;

    private final TurnRiverResearchConfiguration configuration =
            new TurnRiverResearchConfiguration();

    @Test
    void loadsValidatedPackAndRejectsMissingPath() throws Exception {
        var trainer = configuration.turnRiverResearchTrainer(fixture().toString());
        assertEquals("VALIDATION_ONLY", trainer.question(42).publicationStatus());
        assertThrows(IllegalStateException.class, () -> configuration.turnRiverResearchTrainer(""));
    }

    @Test
    void rejectsWellFormedPackWithLargeBestResponseGap() throws Exception {
        var pack =
                TurnRiverPackBuilder.generate(
                        TurnRiverValidationSpot.create(), 1, "2026-09-25T12:00:00Z");
        assertTrue(pack.gameGapBb() > 0.05);
        Path path = tempDir.resolve("unsolved.json");
        Files.writeString(path, TurnRiverPackJson.write(pack));
        var error =
                assertThrows(
                        IllegalStateException.class,
                        () -> configuration.turnRiverResearchTrainer(path.toString()));
        assertTrue(error.getMessage().contains("gap gate"));
    }

    private static Path fixture() {
        Path modulePath =
                Path.of(
                        "..",
                        "solver",
                        "src",
                        "test",
                        "resources",
                        "turn-river-validation-pack.json");
        if (Files.isRegularFile(modulePath)) return modulePath.toAbsolutePath();
        return Path.of("solver", "src", "test", "resources", "turn-river-validation-pack.json")
                .toAbsolutePath();
    }
}
