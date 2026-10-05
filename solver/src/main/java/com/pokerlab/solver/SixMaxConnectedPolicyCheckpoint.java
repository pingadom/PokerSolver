package com.pokerlab.solver;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/** Explicit average-policy checkpoint, not a regret-table restart or a trainer pack. */
public final class SixMaxConnectedPolicyCheckpoint {
    public static final String SCHEMA_VERSION = "six-max-connected-policy-checkpoint/v1";
    public static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build();

    public record Snapshot(
            String schemaVersion,
            String publicationStatus,
            String sourcePackHash,
            String sourceSpotHash,
            SixMaxContinuationStudyBudget budget,
            List<SixMaxConnectedPreflopGame.Selection> selections,
            String solutionHash,
            CfrSolution policy) {
        public Snapshot {
            if (!SCHEMA_VERSION.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus))
                throw new IllegalArgumentException(
                        "Unsupported connected checkpoint schema or status");
            for (var hash : new String[] {sourcePackHash, sourceSpotHash, solutionHash})
                if (hash == null || !hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Canonical SHA-256 hashes are required");
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(policy, "policy");
            selections = List.copyOf(selections);
        }
    }

    public record Loaded(Snapshot snapshot, SixMaxConnectedPreflopGame game) {}

    private SixMaxConnectedPolicyCheckpoint() {}

    public static Snapshot capture(
            SixMaxPreflopSolutionPack source,
            SixMaxConnectedPreflopGame game,
            CfrSolution policy,
            SixMaxContinuationStudyBudget budget) {
        var snapshot =
                new Snapshot(
                        SCHEMA_VERSION,
                        "VALIDATION_ONLY",
                        MultiwayPackJson.fullRoundContentHash(source),
                        source.spotHash(),
                        budget,
                        game.selections(),
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        policy);
        // Reconstruct from the declared source, rather than trusting the caller's game identity.
        validate(snapshot, source);
        return snapshot;
    }

    public static Loaded read(Path path, SixMaxPreflopSolutionPack source) throws IOException {
        if (Files.size(path) > MAX_BYTES)
            throw new IllegalArgumentException("Connected checkpoint exceeds 128 MiB limit");
        var snapshot = MAPPER.readValue(path.toFile(), Snapshot.class);
        return new Loaded(snapshot, validate(snapshot, source));
    }

    private static SixMaxConnectedPreflopGame validate(
            Snapshot snapshot, SixMaxPreflopSolutionPack source) {
        if (!snapshot.sourcePackHash().equals(MultiwayPackJson.fullRoundContentHash(source))
                || !snapshot.sourceSpotHash().equals(source.spotHash()))
            throw new IllegalArgumentException("Checkpoint belongs to a different source pack");
        var game = new SixMaxConnectedPreflopGame(source.rebuildGame(), snapshot.selections());
        snapshot.budget().validate(game);
        var completion =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, snapshot.policy(), snapshot.budget().maximumCompleteTreeStates());
        if (completion.addedInformationSets() != 0)
            throw new IllegalArgumentException("Checkpoint is missing explicit policy rows");
        if (!snapshot.solutionHash()
                .equals(SixMaxConnectedPostflopAudit.solutionHash(snapshot.policy())))
            throw new IllegalArgumentException("Checkpoint policy hash does not match");
        return game;
    }

    /** Validates before mutation, then atomically replaces only this checkpoint file. */
    public static void write(Path path, Snapshot snapshot, SixMaxPreflopSolutionPack source)
            throws IOException {
        validate(snapshot, source);
        var destination = path.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        var temporary = Files.createTempFile(destination.getParent(), ".connected-policy-", ".tmp");
        try {
            MAPPER.writeValue(temporary.toFile(), snapshot);
            if (Files.size(temporary) > MAX_BYTES)
                throw new IllegalArgumentException("Connected checkpoint exceeds 128 MiB limit");
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException(
                        "Checkpoint filesystem must support atomic replacement", unsupported);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
