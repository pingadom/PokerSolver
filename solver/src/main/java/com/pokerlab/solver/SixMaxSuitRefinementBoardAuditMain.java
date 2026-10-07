package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Bounded offline physical witness generation and read-only replay. */
public final class SixMaxSuitRefinementBoardAuditMain {
    private SixMaxSuitRefinementBoardAuditMain() {}

    public static void main(String[] args) throws Exception {
        boolean audit = args.length == 7 && args[0].equals("audit");
        boolean replay = args.length == 6 && args[0].equals("replay");
        if (!audit && !replay)
            throw new IllegalArgumentException(
                    "Usage: SixMaxSuitRefinementBoardAuditMain audit <source> <rank-table> <suit-table> <checkpoint> <report> <flops> | replay <source> <rank-table> <suit-table> <checkpoint> <report>");
        var boards =
                audit
                        ? SixMaxSuitRefinementPayoffTableMain.parseBoards(args[6])
                        : List.<List<Card>>of();
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .limit(5)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var table = SixMaxSuitRefinementPayoffTable.read(paths.get(2), source, parent);
        var cp = SixMaxSuitRefinementStudy.read(paths.get(3), source, parent, table);
        var report =
                replay
                        ? SixMaxSuitRefinementBoardAudit.replay(
                                paths.get(4), source, parent, table, cp)
                        : SixMaxSuitRefinementBoardAudit.assess(source, parent, table, cp, boards);
        if (audit) SixMaxSuitRefinementBoardAudit.write(paths.get(4), report);
        System.out.println(
                "VALIDATION_ONLY refined-board maximum share difference "
                        + report.maximumWitnessShareDifference()
                        + " runouts "
                        + report.enumeratedRunouts());
    }
}
