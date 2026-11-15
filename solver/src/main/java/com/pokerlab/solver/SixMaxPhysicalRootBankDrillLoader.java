package com.pokerlab.solver;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Capture and validate every input, reproduce once, then expose only immutable cached choices. */
public final class SixMaxPhysicalRootBankDrillLoader {
    public static final String SCHEMA = "pokerlab-physical-root-bank-drill-manifest/v1";
    public static final String STUDY_HASH =
            "e333144323609afd2d5fd47daf7b2636b56647d3f080c579d1491c10afc8e84d";
    public static final String PHYSICAL_STUDY_HASH =
            "feb331638b447a30be52d8fd9bfe6c267120afe29ba0b51c94b416f7d068771e";
    public static final String REPORT_SHA =
            "bb65da5c69e8689bdd758a6f80ece2b9c2fbbaedb903782d1be046ebef24ee5d";
    public static final int MAX_MANIFEST_BYTES = 16 * 1024;
    public static final String REQUEST_SHA =
            "36119e55e1b9eef745b1b7fcad2a2401b5ade450bf84bbdabdd9298155ea2975";

    public record StartupBudget(
            int physicalGames,
            int maximumParentSolveCalls,
            int maximumParentLpSolves,
            int maximumEnvelopeLpSolves,
            int freshReferenceRuns,
            int freshIterations) {
        public StartupBudget {
            if (physicalGames != 1
                    || maximumParentSolveCalls != 1
                    || maximumParentLpSolves != 2
                    || maximumEnvelopeLpSolves != 15
                    || freshReferenceRuns != 2
                    || freshIterations != 240000)
                throw new IllegalArgumentException(
                        "Complete root hand bank startup reservation required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(1, 1, 2, 15, 2, 240000);
        }
    }

    public record Manifest(
            String schemaVersion,
            String studyHash,
            String physicalStudyHash,
            String requestFile,
            String requestSha256,
            String reportFile,
            String reportStoredSha256,
            String bankFile,
            String bankStoredSha256,
            StartupBudget startupBudget) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || !STUDY_HASH.equals(studyHash)
                    || !PHYSICAL_STUDY_HASH.equals(physicalStudyHash)
                    || !file(requestFile)
                    || !file(reportFile)
                    || !file(bankFile)
                    || !requestFile.endsWith(".json")
                    || !reportFile.endsWith(".json.gz")
                    || !bankFile.endsWith(".json.gz")
                    || !REQUEST_SHA.equals(requestSha256)
                    || !REPORT_SHA.equals(reportStoredSha256)
                    || !SixMaxPhysicalRootBankStudy.BANK_SHA.equals(bankStoredSha256)
                    || startupBudget == null)
                throw new IllegalArgumentException(
                        "Pinned root hand bank files and complete budget required");
        }
    }

    private record Captured(
            Path path,
            Path realPath,
            Object fileKey,
            long size,
            String modified,
            String sha256,
            byte[] bytes) {}

    private SixMaxPhysicalRootBankDrillLoader() {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    private static Captured capture(Path path, Path root, int cap) throws Exception {
        if (Files.isSymbolicLink(path))
            throw new IllegalArgumentException("Symbolic startup sources are not accepted");
        Path real = path.toRealPath();
        if (!root.equals(real.getParent()))
            throw new IllegalArgumentException(
                    "Root hand bank dependency escapes manifest directory");
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > cap)
            throw new IllegalArgumentException("Bounded regular root hand bank source required");
        byte[] bytes;
        try (var stream = Files.newInputStream(real)) {
            bytes = stream.readNBytes(cap + 1);
        }
        var after = Files.readAttributes(real, BasicFileAttributes.class);
        if (bytes.length > cap
                || bytes.length != before.size()
                || before.size() != after.size()
                || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IllegalArgumentException("Root hand bank source changed while reading");
        return new Captured(
                path,
                real,
                after.fileKey(),
                after.size(),
                after.lastModifiedTime().toString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                bytes);
    }

    private static byte[] expanded(Captured source) throws Exception {
        if (!source.path().toString().endsWith(".gz")) return source.bytes();
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(source.bytes()))) {
            byte[] expanded = gzip.readNBytes(SixMaxPhysicalRootBankStudy.MAX_BYTES + 1);
            if (expanded.length > SixMaxPhysicalRootBankStudy.MAX_BYTES)
                throw new IllegalArgumentException("Expanded root hand bank source cap exceeded");
            return expanded;
        }
    }

    public static Manifest readManifest(Path path) throws Exception {
        path = path.toAbsolutePath().normalize();
        var captured = capture(path, path.getParent().toRealPath(), MAX_MANIFEST_BYTES);
        return SixMaxTexturePayoffTable.mapper().readValue(captured.bytes(), Manifest.class);
    }

    public static SixMaxRootHandBankDrill.Result load(Path path, String expectedStudyHash)
            throws Exception {
        if (path == null || !STUDY_HASH.equals(expectedStudyHash))
            throw new IllegalArgumentException(
                    "Manifest and exact published root hand bank pin required");
        path = path.toAbsolutePath().normalize();
        Path root = path.getParent().toRealPath();
        var manifestBytes = capture(path, root, MAX_MANIFEST_BYTES);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = mapper.readValue(manifestBytes.bytes(), Manifest.class);
        var requestBytes =
                capture(
                        root.resolve(manifest.requestFile()),
                        root,
                        SixMaxPhysicalRootBankStudy.MAX_REQUEST_BYTES);
        var reportBytes =
                capture(
                        root.resolve(manifest.reportFile()),
                        root,
                        SixMaxPhysicalRootBankStudy.MAX_BYTES);
        var bankBytes =
                capture(
                        root.resolve(manifest.bankFile()),
                        root,
                        SixMaxPhysicalRootBankStudy.MAX_BYTES);
        var sources = List.of(manifestBytes, requestBytes, reportBytes, bankBytes);
        for (int i = 0; i < sources.size(); i++)
            for (int j = i + 1; j < sources.size(); j++)
                if (Files.isSameFile(sources.get(i).path(), sources.get(j).path()))
                    throw new IllegalArgumentException("Aliased root hand bank startup sources");
        if (!manifest.studyHash().equals(expectedStudyHash)
                || !manifest.requestSha256().equals(requestBytes.sha256())
                || !manifest.reportStoredSha256().equals(reportBytes.sha256())
                || !manifest.bankStoredSha256().equals(bankBytes.sha256()))
            throw new IllegalArgumentException("Root hand bank stored source byte pin differs");
        var request =
                mapper.readValue(expanded(requestBytes), SixMaxPhysicalRootBankStudy.Request.class);
        var saved =
                mapper.readValue(expanded(reportBytes), SixMaxPhysicalRootBankStudy.Report.class);
        var bank = mapper.readValue(expanded(bankBytes), SixMaxRootHandBankStudy.Report.class);
        if (!request.equals(saved.request())
                || !PHYSICAL_STUDY_HASH.equals(SixMaxHeadsUpPreflopGame.hash(saved))
                || !expectedStudyHash.equals(SixMaxHeadsUpPreflopGame.hash(bank))
                || !saved.historicalSource().equals(bank))
            throw new IllegalArgumentException(
                    "Complete captured physical bank lineage differs before startup");
        // Replay a private view of the exact captured bytes. No parsed DTO can mint an owned
        // Result.
        Path view = Files.createTempDirectory("pokerlab-physical-root-cache-");
        SixMaxRootHandBankDrill.Result result;
        Path capturedRequest = view.resolve("request.json");
        Path capturedBank = view.resolve("bank.json.gz");
        Path capturedReport = view.resolve("report.json.gz");
        try {
            Files.write(capturedRequest, requestBytes.bytes(), StandardOpenOption.CREATE_NEW);
            Files.write(capturedBank, bankBytes.bytes(), StandardOpenOption.CREATE_NEW);
            Files.write(capturedReport, reportBytes.bytes(), StandardOpenOption.CREATE_NEW);
            var owned =
                    SixMaxPhysicalRootBankStudy.replay(
                            capturedRequest, capturedBank, capturedReport);
            if (!saved.equals(owned.report()))
                throw new IllegalArgumentException(
                        "Complete captured physical bank replay differs");
            result = SixMaxRootHandBankDrill.createFromPhysical(owned);
        } finally {
            // Delete only the three fixed children and the exact directory we created, never
            // recurse.
            Files.deleteIfExists(capturedRequest);
            Files.deleteIfExists(capturedBank);
            Files.deleteIfExists(capturedReport);
            Files.deleteIfExists(view);
        }
        if (!expectedStudyHash.equals(result.snapshot().studyHash())
                || result.snapshot().questions().size() != 3)
            throw new IllegalArgumentException("Owned root hand bank cache inventory differs");
        for (var before : sources) {
            var after =
                    capture(
                            before.path(),
                            root,
                            before == manifestBytes
                                    ? MAX_MANIFEST_BYTES
                                    : SixMaxPhysicalRootBankStudy.MAX_BYTES);
            if (!before.realPath().equals(after.realPath())
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !before.modified().equals(after.modified())
                    || !before.sha256().equals(after.sha256())
                    || !Arrays.equals(before.bytes(), after.bytes()))
                throw new IllegalArgumentException(
                        "Root hand bank sources changed during complete startup");
        }
        return result;
    }
}
