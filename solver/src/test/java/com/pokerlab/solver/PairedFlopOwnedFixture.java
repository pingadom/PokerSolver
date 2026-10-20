package com.pokerlab.solver;

import java.nio.file.*;

final class PairedFlopOwnedFixture {
    static final Path INPUT =
            Path.of("../docs/data/supplied-paired-four-hand-curriculum-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-paired-four-hand-curriculum-report.json.gz");
    static final Path REVIEW = Path.of("../docs/data/supplied-paired-four-hand-flop-drill.json");
    static final String PIN = "c0e778afe5530740ee8d4dad3b9f9716a05fc45d8c1d141880d99212af99f61e";
    private static SixMaxPairedFlopCurriculum.Result owned;

    static synchronized SixMaxPairedFlopCurriculum.Result owned() throws Exception {
        if (owned == null) owned = SixMaxPairedFlopCurriculum.replay(INPUT, REPORT);
        return owned;
    }

    private PairedFlopOwnedFixture() {}
}
