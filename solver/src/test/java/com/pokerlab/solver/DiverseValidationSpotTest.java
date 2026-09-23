package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiverseValidationSpotTest {
    @TempDir Path temporaryDirectory;

    @Test
    void widerSyntheticSpotProducesDifferentHeroDecisions() throws Exception {
        Path output = temporaryDirectory.resolve("diverse-pack.json");
        GenerateValidationPack.main(
                new String[] {
                    "mc-plus",
                    output.toString(),
                    "3000",
                    "10000",
                    "17",
                    "2026-09-23T12:00:00Z",
                    "diverse"
                });
        PreflopSolutionPack pack =
                PreflopPackJson.read(Files.readString(output, StandardCharsets.UTF_8));
        assertEquals(47, pack.matchups().size());
        assertEquals(PreflopSolutionPack.CFR_PLUS_SOLVER_VERSION, pack.solverVersion());
        assertEquals(PreflopSolutionPack.VALIDATION_ONLY, pack.publicationStatus());
        assertEquals(pack, PreflopPackJson.read(PreflopPackJson.write(pack)));
        assertTrue(pack.estimatedGameGapBb() < 0.001);
        assertTrue(pack.heroDecisions().stream().anyMatch(d -> d.shoveEvBb() > d.foldEvBb() + 1));
        assertTrue(pack.heroDecisions().stream().anyMatch(d -> d.foldEvBb() > d.shoveEvBb() + 1));
        assertTrue(
                pack.heroDecisions().stream()
                        .anyMatch(d -> d.shoveFrequency() > 0.1 && d.foldFrequency() > 0.1));

        PreflopPackScreening.Report screening = PreflopPackScreening.assess(pack);
        assertEquals(8, screening.heroCombos());
        assertEquals(7, screening.opponentCombos());
        assertTrue(screening.clearlyShoveCombos() > 0);
        assertTrue(screening.clearlyFoldCombos() > 0);
        assertFalse(screening.passesAutomatedChecks());
        assertEquals(
                java.util.List.of(PreflopPackScreening.Finding.PAYOFF_UNCERTAINTY_TOO_LARGE),
                screening.findings());

        PreflopTrainer trainer = new PreflopTrainer(pack);
        Map<String, PreflopTrainer.Question> questions =
                LongStream.range(0, 500)
                        .mapToObj(trainer::question)
                        .collect(
                                Collectors.toMap(
                                        PreflopTrainer.Question::heroCombo,
                                        question -> question,
                                        (first, ignored) -> first));
        assertEquals(8, questions.size());
        PreflopTrainer.Question weakHand = questions.get("6h 7h");
        assertNotNull(weakHand);
        assertEquals(0, trainer.grade(weakHand, PreflopTrainer.Action.FOLD).evLossBb());
        assertTrue(trainer.grade(weakHand, PreflopTrainer.Action.SHOVE).evLossBb() > 1);
        PreflopTrainer.Question bluffHand = questions.get("5s As");
        assertNotNull(bluffHand);
        assertEquals(0, trainer.grade(bluffHand, PreflopTrainer.Action.SHOVE).evLossBb());
        assertTrue(trainer.grade(bluffHand, PreflopTrainer.Action.FOLD).evLossBb() > 1);
    }

    @Test
    void narrowExactFixtureFailsCoverageAndDecisionDiversityScreen() throws Exception {
        PreflopSolutionPack pack;
        try (var resource = getClass().getResourceAsStream("/validation-pack.json")) {
            assertNotNull(resource);
            pack =
                    PreflopPackJson.read(
                            new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
        PreflopPackScreening.Report screening = PreflopPackScreening.assess(pack);
        assertFalse(screening.passesAutomatedChecks());
        assertEquals(
                java.util.List.of(
                        PreflopPackScreening.Finding.HERO_RANGE_TOO_SMALL,
                        PreflopPackScreening.Finding.OPPONENT_RANGE_TOO_SMALL,
                        PreflopPackScreening.Finding.NO_CLEAR_FOLD),
                screening.findings());
    }

    @Test
    void exactDiverseFixturePassesNumericScreenButRemainsValidationOnly() throws Exception {
        PreflopSolutionPack pack;
        String fixture;
        try (var resource = getClass().getResourceAsStream("/diverse-validation-pack.json")) {
            assertNotNull(resource);
            fixture = new String(resource.readAllBytes(), StandardCharsets.UTF_8).trim();
            pack = PreflopPackJson.read(fixture);
        }
        assertEquals(fixture, PreflopPackJson.write(pack));
        assertEquals(DiverseValidationSpot.create().contentHash(), pack.spotHash());
        assertEquals(PreflopSolutionPack.EXACT_ENUMERATION, pack.payoffMethod());
        assertEquals(PreflopSolutionPack.CFR_PLUS_SOLVER_VERSION, pack.solverVersion());
        assertEquals(PreflopSolutionPack.VALIDATION_ONLY, pack.publicationStatus());
        assertEquals(47, pack.matchups().size());
        assertEquals(0, pack.maximumCalledPayoffStandardErrorBb());
        assertTrue(pack.estimatedGameGapBb() < 0.001);
        assertTrue(
                pack.heroDecisions().stream()
                        .anyMatch(d -> d.shoveFrequency() > 0.1 && d.foldFrequency() > 0.1));
        PreflopPackScreening.Report screening = PreflopPackScreening.assess(pack);
        assertTrue(screening.passesAutomatedChecks());
        assertEquals(java.util.List.of(), screening.findings());
    }
}
