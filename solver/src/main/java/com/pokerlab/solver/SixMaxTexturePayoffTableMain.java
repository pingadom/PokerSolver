package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Offline exact texture-table generation; expensive enumeration never runs in a trainer request.
 */
public final class SixMaxTexturePayoffTableMain {
    private SixMaxTexturePayoffTableMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Usage: SixMaxTexturePayoffTableMain <source-pack.json> <table.json>");
        var sourcePath = Path.of(args[0]).toAbsolutePath().normalize();
        var output = Path.of(args[1]).toAbsolutePath().normalize();
        distinct(List.of(sourcePath, output));
        var source = source(sourcePath);
        var table =
                SixMaxTexturePayoffTable.generate(
                        source, deal -> System.out.println("EXACT_TEXTURE_DEAL " + deal));
        atomicWrite(output, SixMaxTexturePayoffTable.json(table));
        System.out.println("Texture table " + SixMaxTexturePayoffTable.hash(table) + " " + output);
    }

    static SixMaxPreflopSolutionPack source(Path input) throws Exception {
        if (Files.size(input) > 16L * 1024 * 1024)
            throw new IllegalArgumentException("Source exceeds 16 MiB");
        return MultiwayPackJson.readFullRound(Files.readString(input));
    }

    static void distinct(List<Path> paths) throws Exception {
        for (int i = 0; i < paths.size(); i++)
            for (int j = i + 1; j < paths.size(); j++)
                if (paths.get(i).equals(paths.get(j))
                        || (Files.exists(paths.get(i))
                                && Files.exists(paths.get(j))
                                && Files.isSameFile(paths.get(i), paths.get(j))))
                    throw new IllegalArgumentException("Inputs and outputs must be distinct files");
    }

    static void atomicWrite(Path output, String json) throws Exception {
        atomicWrite(output, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static void atomicWrite(Path output, byte[] bytes) throws Exception {
        Files.createDirectories(output.getParent());
        var temporary = Files.createTempFile(output.getParent(), ".texture-study-", ".json");
        try {
            Files.write(temporary, bytes);
            Files.move(
                    temporary,
                    output,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
