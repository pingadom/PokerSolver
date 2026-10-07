package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Named flops select whole public rank/texture groups for exact physical-card observation. */
public final class SixMaxSuitRefinementPayoffTableMain {
    private SixMaxSuitRefinementPayoffTableMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException(
                    "Usage: SixMaxSuitRefinementPayoffTableMain <source> <rank-table> <new-table> <group-selector flops e.g. 2c3d4h;AsKsQs>");
        var boards = parseBoards(args[3]);
        var refined =
                boards.stream()
                        .map(
                                b ->
                                        SixMaxRankTexturePayoffTable.Signal.from(
                                                b.get(0), b.get(1), b.get(2)))
                        .distinct()
                        .sorted()
                        .toList();
        SixMaxSuitRefinementPayoffTable.refinement(refined);
        var paths =
                Arrays.stream(args)
                        .limit(3)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var table =
                SixMaxSuitRefinementPayoffTable.generate(
                        source,
                        parent,
                        refined,
                        n -> System.out.println("Exact physical-flop worlds " + n));
        SixMaxSuitRefinementPayoffTable.write(paths.get(2), table, source, parent);
        System.out.println(
                "VALIDATION_ONLY observations "
                        + table.observations().size()
                        + " refined groups "
                        + refined.size()
                        + " hash "
                        + SixMaxSuitRefinementPayoffTable.hash(table));
    }

    static List<List<Card>> parseBoards(String text) {
        return SixMaxRankTextureBoardAudit.canonicalBoards(
                Arrays.stream(text.split(";", -1))
                        .map(
                                s -> {
                                    if (s.length() != 6)
                                        throw new IllegalArgumentException(
                                                "Flops require six-character card notation");
                                    return List.of(
                                            Card.parse(s.substring(0, 2)),
                                            Card.parse(s.substring(2, 4)),
                                            Card.parse(s.substring(4, 6)));
                                })
                        .toList());
    }
}
