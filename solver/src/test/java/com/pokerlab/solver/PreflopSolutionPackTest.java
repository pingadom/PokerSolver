package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreflopSolutionPackTest {
    private static final String GENERATED_AT = "2026-09-23T12:00:00Z";

    @TempDir Path temporaryDirectory;

    @Test
    void generatesReproducibleVersionedJsonAndValidatesOnRead() {
        PreflopAllInSpot spot = ValidationSpot.create();
        PreflopSolutionPack pack =
                PreflopPackBuilder.generate(spot, 3_000, 5_000, 42, GENERATED_AT);
        String json = PreflopPackJson.write(pack);
        PreflopSolutionPack loaded = PreflopPackJson.read(json);

        assertEquals(pack, loaded);
        assertEquals(json, PreflopPackJson.write(loaded));
        assertEquals(
                json,
                PreflopPackJson.write(
                        PreflopPackBuilder.generate(
                                ValidationSpot.create(), 3_000, 5_000, 42, GENERATED_AT)));
        assertEquals(8, pack.matchups().size());
        assertEquals(3, pack.heroDecisions().size());
        assertTrue(pack.estimatedGameGapBb() >= 0);
        assertTrue(pack.maximumCalledPayoffStandardErrorBb() > 0);
        assertEquals(spot.contentHash(), pack.spotHash());
        for (PreflopSolutionPack.HeroDecision decision : pack.heroDecisions()) {
            assertEquals(1, decision.shoveFrequency() + decision.foldFrequency(), 1e-9);
            assertEquals(-22, decision.foldEvBb(), 1e-9);
        }
    }

    @Test
    void rejectsChangedSpotHashMissingMatchupAndForgedActionEv() {
        PreflopSolutionPack valid =
                PreflopPackBuilder.generate(ValidationSpot.create(), 100, 1_000, 42, GENERATED_AT);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PreflopPackJson.read(
                                PreflopPackJson.write(valid)
                                        .replace(valid.spotHash(), "0".repeat(64))));

        var incomplete = new ArrayList<>(valid.matchups());
        incomplete.remove(0);
        assertThrows(
                IllegalArgumentException.class,
                () -> with(valid, incomplete, valid.heroDecisions()).validate());

        var forged = new ArrayList<>(valid.heroDecisions());
        var first = forged.get(0);
        forged.set(
                0,
                new PreflopSolutionPack.HeroDecision(
                        first.combo(),
                        first.shoveFrequency(),
                        first.foldFrequency(),
                        first.shoveEvBb() + 1,
                        first.foldEvBb()));
        assertThrows(
                IllegalArgumentException.class,
                () -> with(valid, valid.matchups(), forged).validate());
    }

    @Test
    void offlineGeneratorWritesPackWithoutOverwritingExistingFile() throws Exception {
        Path output = temporaryDirectory.resolve("validation-pack.json");
        String[] arguments = {"mc", output.toString(), "100", "1000", "42", GENERATED_AT};
        GenerateValidationPack.main(arguments);
        PreflopSolutionPack pack = PreflopPackJson.read(Files.readString(output));
        assertEquals(PreflopSolutionPack.VALIDATION_ONLY, pack.publicationStatus());
        assertThrows(
                java.nio.file.FileAlreadyExistsException.class,
                () -> GenerateValidationPack.main(arguments));
    }

    @Test
    void exactPackHasNoPayoffSamplingErrorAndMatchesCommittedFixture() throws Exception {
        PreflopSolutionPack exact =
                PreflopPackBuilder.generateExact(ValidationSpot.create(), 3_000, GENERATED_AT);
        assertEquals(PreflopSolutionPack.EXACT_ENUMERATION, exact.payoffMethod());
        assertEquals(1_712_304, exact.payoffTrialsPerMatchup());
        assertEquals(0, exact.maximumCalledPayoffStandardErrorBb());
        assertTrue(
                exact.matchups().stream()
                        .allMatch(matchup -> matchup.estimate().standardError() == 0));
        assertEquals(exact, PreflopPackJson.read(PreflopPackJson.write(exact)));
        try (var resource = getClass().getResourceAsStream("/validation-pack.json")) {
            assertNotNull(resource);
            String fixture = new String(resource.readAllBytes(), StandardCharsets.UTF_8).trim();
            assertEquals(fixture, PreflopPackJson.write(exact));
        }
    }

    private static PreflopSolutionPack with(
            PreflopSolutionPack original,
            java.util.List<PreflopSolutionPack.MatchupEquity> matchups,
            java.util.List<PreflopSolutionPack.HeroDecision> decisions) {
        return new PreflopSolutionPack(
                original.schemaVersion(),
                original.solverVersion(),
                original.publicationStatus(),
                original.generatedAt(),
                original.spot(),
                original.spotHash(),
                original.iterations(),
                original.payoffMethod(),
                original.payoffTrialsPerMatchup(),
                original.payoffSeed(),
                original.estimatedGameGapBb(),
                original.maximumCalledPayoffStandardErrorBb(),
                matchups,
                original.solution(),
                decisions);
    }
}
