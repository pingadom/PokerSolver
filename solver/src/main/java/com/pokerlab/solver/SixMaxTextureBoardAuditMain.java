package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Offline bounded named-board comparison, never a public trainer payload. */
public final class SixMaxTextureBoardAuditMain {
    private SixMaxTextureBoardAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException(
                    "Usage: SixMaxTextureBoardAuditMain <source> <table> <checkpoint> <report> <flops e.g. 2c3d4h;AcKdQs>");
        var boards =
                SixMaxTextureBoardAudit.canonicalBoards(
                        Arrays.stream(args[4].split(";", -1))
                                .map(
                                        text -> {
                                            if (text.length() != 6)
                                                throw new IllegalArgumentException(
                                                        "Flops use six-character card notation");
                                            return List.of(
                                                    Card.parse(text.substring(0, 2)),
                                                    Card.parse(text.substring(2, 4)),
                                                    Card.parse(text.substring(4, 6)));
                                        })
                                .toList());
        var paths =
                Arrays.stream(args)
                        .limit(4)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var table = SixMaxTexturePayoffTable.read(paths.get(1), source);
        var checkpoint = SixMaxTextureStudy.read(paths.get(2), source, table);
        var report = SixMaxTextureBoardAudit.assess(source, table, checkpoint, boards);
        SixMaxTexturePayoffTableMain.atomicWrite(paths.get(3), SixMaxTextureStudy.json(report));
        System.out.println(
                "VALIDATION_ONLY named-board maximum witness share difference "
                        + report.maximumWitnessShareDifference()
                        + " runouts "
                        + report.enumeratedRunouts());
    }
}
