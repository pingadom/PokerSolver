package com.pokerlab.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pokerlab.solver.CfrSolver;
import com.pokerlab.solver.DiverseValidationSpot;
import com.pokerlab.solver.PreflopPackBuilder;
import com.pokerlab.solver.PreflopPackJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResearchTrainerConfigurationTest {
    @TempDir Path tempDir;

    private final ResearchTrainerConfiguration configuration = new ResearchTrainerConfiguration();

    @Test
    void loadsScreenedExactPack() throws IOException {
        var trainer =
                configuration.researchTrainer(fixture("diverse-validation-pack.json").toString());
        assertEquals("VALIDATION_ONLY", trainer.question(42).publicationStatus());
    }

    @Test
    void rejectsMissingAndNarrowPacks() {
        assertThrows(IllegalStateException.class, () -> configuration.researchTrainer(""));
        var exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                configuration.researchTrainer(
                                        fixture("validation-pack.json").toString()));
        assertTrue(exception.getMessage().contains("fails numeric screening"));
    }

    @Test
    void rejectsSampledPayoffsEvenWhenThePackIsWellFormed() throws IOException {
        var sampled =
                PreflopPackBuilder.generate(
                        DiverseValidationSpot.create(),
                        10,
                        100,
                        42,
                        "2026-09-23T12:00:00Z",
                        CfrSolver.Variant.CFR_PLUS);
        Path path = tempDir.resolve("sampled-pack.json");
        Files.writeString(path, PreflopPackJson.write(sampled));
        var exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> configuration.researchTrainer(path.toString()));
        assertTrue(exception.getMessage().contains("requires exact payoffs"));
    }

    private static Path fixture(String name) {
        Path modulePath = Path.of("..", "solver", "src", "test", "resources", name);
        if (Files.isRegularFile(modulePath)) return modulePath.toAbsolutePath();
        return Path.of("solver", "src", "test", "resources", name).toAbsolutePath();
    }
}
