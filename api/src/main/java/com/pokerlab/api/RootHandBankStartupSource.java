package com.pokerlab.api;

import com.pokerlab.solver.SixMaxPhysicalRootBankDrillLoader;
import com.pokerlab.solver.SixMaxRootHandBankDrill;
import com.pokerlab.solver.SixMaxRootHandBankDrillLoader;
import java.nio.file.Path;

/** An operator-selected startup capability, created only after a complete owned loader replay. */
final class RootHandBankStartupSource {
    enum Mode {
        ORIGINAL,
        PHYSICAL_PARENT_REUSE
    }

    record Work(
            int physicalGames,
            int maximumParentSolveCalls,
            int maximumParentLpSolves,
            int maximumEnvelopeLpSolves,
            int freshReferenceRuns,
            int freshIterations) {}

    record Provenance(
            String schemaVersion,
            Mode startupSource,
            String studyHash,
            String sourceProofHash,
            boolean trainerAdmission,
            Work startupBudget,
            int availableQuestions,
            int cachedLegalGrades,
            String publicMetadataMeaning) {}

    private final SixMaxRootHandBankDrill.Result drill;
    private final Provenance provenance;

    private RootHandBankStartupSource(Mode mode, SixMaxRootHandBankDrill.Result drill) {
        if (!SixMaxRootHandBankDrillLoader.STUDY_HASH.equals(drill.snapshot().studyHash())
                || drill.snapshot().trainerAdmission()
                || drill.snapshot().questions().size() != 3
                || drill.snapshot().questions().stream().mapToInt(q -> q.moves().size()).sum() != 9)
            throw new IllegalArgumentException("Complete original public cache required");
        this.drill = drill;
        boolean reused = mode == Mode.PHYSICAL_PARENT_REUSE;
        this.provenance =
                new Provenance(
                        "pokerlab-root-hand-bank-startup/v1",
                        mode,
                        drill.snapshot().studyHash(),
                        reused
                                ? SixMaxPhysicalRootBankDrillLoader.PHYSICAL_STUDY_HASH
                                : SixMaxRootHandBankDrillLoader.STUDY_HASH,
                        false,
                        new Work(1, reused ? 1 : 4, reused ? 2 : 8, 15, 2, 240000),
                        3,
                        9,
                        "UNCHANGED_HISTORICAL_PUBLIC_PROTOCOL_RESERVATION");
    }

    static Mode mode(String value) {
        try {
            return Mode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException(
                    "root hand bank startup source must be ORIGINAL or PHYSICAL_PARENT_REUSE", e);
        }
    }

    static RootHandBankStartupSource load(String manifest, String hash, String selection)
            throws Exception {
        Mode mode = mode(selection);
        if (manifest == null || hash == null || manifest.isBlank() || hash.isBlank())
            throw new IllegalStateException(
                    "root hand bank drill needs a manifest path and exact study hash");
        if (!SixMaxRootHandBankDrillLoader.STUDY_HASH.equals(hash))
            throw new IllegalArgumentException("Exact published root hand bank pin required");
        var log = org.slf4j.LoggerFactory.getLogger(RootHandBankStartupSource.class);
        log.warn(
                "Reserving root hand bank source {} startup: one physical game, {}/15 parent/envelope LPs, two fresh references/240000 iterations",
                mode,
                mode == Mode.ORIGINAL ? 8 : 2);
        var drill =
                mode == Mode.ORIGINAL
                        ? SixMaxRootHandBankDrillLoader.load(Path.of(manifest), hash)
                        : SixMaxPhysicalRootBankDrillLoader.load(Path.of(manifest), hash);
        var source = new RootHandBankStartupSource(mode, drill);
        log.warn(
                "Research-only root hand bank startup complete: source={}, publicStudy={}, proof={}, questions=3, trainerAdmission=false",
                mode,
                hash,
                source.provenance.sourceProofHash());
        return source;
    }

    SixMaxRootHandBankDrill.Result drill() {
        return drill;
    }

    Provenance provenance() {
        return provenance;
    }
}
