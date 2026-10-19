package com.pokerlab.solver;

import java.nio.file.*;

final class RefinedFlopOwnedFixture {
    static final Path INPUT =
            Path.of("../docs/data/supplied-paired-btn-facing-bet-feedback-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-paired-btn-facing-bet-feedback-report.json.gz");
    private static SixMaxRefinedFlopFeedback.Result owned;

    static synchronized SixMaxRefinedFlopFeedback.Result owned() throws Exception {
        if (owned == null)
            owned = SixMaxRefinedFlopFeedback.solve(SixMaxRefinedFlopFeedback.readRequest(INPUT));
        return owned;
    }

    static SixMaxSuppliedFlopBettingGame game() throws Exception {
        var input = SixMaxRefinedFlopFeedback.readRequest(INPUT).conditional().parent().connected();
        return new SixMaxSuppliedFlopBettingGame(
                input.input(), input.flop(), input.betPotFraction());
    }
}
