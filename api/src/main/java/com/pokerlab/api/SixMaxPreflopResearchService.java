package com.pokerlab.api;

import com.pokerlab.solver.CashRakeRule;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopBetting;
import com.pokerlab.solver.SixMaxPreflopDrillSession;
import com.pokerlab.solver.SixMaxPreflopPublicTable;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer;
import com.pokerlab.solver.SixMaxPreflopSolutionPack;
import java.util.List;

/** Loads one full-round saved research policy once; requests never solve or sample boards. */
public final class SixMaxPreflopResearchService {
    public record Metadata(
            String spotId,
            String spotHash,
            String packHash,
            String packSchema,
            String publicationStatus,
            String solverVersion,
            String generatedAt,
            String payoffMethod,
            String continuationModel,
            String chanceModel,
            List<Seat> seats,
            List<Integer> rangeComboCounts,
            double stackBb,
            double smallBlindBb,
            List<Double> raiseToBb,
            CashRakeRule rake,
            double nashConvBb,
            double maximumPayoffStandardErrorBb,
            int sessionLength) {}

    /** Session seeds cross HTTP as strings to retain all 64 bits in JavaScript clients. */
    public record QuestionView(
            String sessionSeed,
            int index,
            String packHash,
            Seat actingSeat,
            String heroCombo,
            List<SixMaxPreflopResearchTrainer.PublicAction> priorActions,
            List<String> legalActions,
            List<SixMaxPreflopPublicTable.Player> players,
            double potBb,
            double toCallBb,
            double stackBb,
            double smallBlindBb,
            String publicationStatus) {}

    public record GradedQuestion(
            QuestionView question, SixMaxPreflopResearchTrainer.Feedback feedback) {}

    public record SessionReview(
            String packHash,
            List<GradedQuestion> attempts,
            double totalEvLossBb,
            double averageEvLossBb) {}

    private final SixMaxPreflopDrillSession session;
    private final Metadata metadata;
    private final SixMaxPreflopBetting.Rules rules;

    public SixMaxPreflopResearchService(SixMaxPreflopSolutionPack pack) {
        session = new SixMaxPreflopDrillSession(pack);
        var spot = pack.spot();
        rules = spot.rules();
        metadata =
                new Metadata(
                        spot.id(),
                        pack.spotHash(),
                        session.packHash(),
                        pack.schemaVersion(),
                        pack.publicationStatus(),
                        pack.solverVersion(),
                        pack.generatedAt(),
                        pack.payoffMethod(),
                        spot.continuationModel(),
                        "EXACT_RANGE_PRODUCT",
                        List.of(Seat.values()),
                        spot.ranges().stream().map(List::size).toList(),
                        spot.rules().stackBb(),
                        spot.rules().smallBlindBb(),
                        spot.rules().raiseToBb(),
                        spot.rake(),
                        pack.nashConvBb(),
                        pack.maxTerminalPayoffSEBb(),
                        SixMaxPreflopDrillSession.LENGTH);
    }

    public Metadata metadata() {
        return metadata;
    }

    public QuestionView question(long seed, int index) {
        return view(session.question(seed, index));
    }

    public GradedQuestion grade(long seed, int index, String packHash, String action) {
        requirePack(packHash);
        var attempt = session.grade(session.question(seed, index), action);
        return new GradedQuestion(view(attempt.question()), attempt.feedback());
    }

    public SessionReview review(long seed, String packHash, List<String> actions) {
        var review = session.review(seed, packHash, actions);
        return new SessionReview(
                review.packHash(),
                review.attempts().stream()
                        .map(
                                attempt ->
                                        new GradedQuestion(
                                                view(attempt.question()), attempt.feedback()))
                        .toList(),
                review.totalEvLossBb(),
                review.averageEvLossBb());
    }

    private void requirePack(String hash) {
        if (!metadata.packHash().equals(hash))
            throw new IllegalArgumentException("Pack hash does not match this session");
    }

    private QuestionView view(SixMaxPreflopDrillSession.Question question) {
        var decision = question.decision();
        var table = SixMaxPreflopPublicTable.replay(rules, decision.priorActions());
        if (table.actingSeat() != decision.actingSeat()
                || Math.abs(table.potBb() - decision.potBb()) > 1e-9
                || Math.abs(table.toCallBb() - decision.toCallBb()) > 1e-9)
            throw new IllegalStateException("Public table does not match solver question");
        return new QuestionView(
                Long.toString(question.sessionSeed()),
                question.index(),
                question.packHash(),
                decision.actingSeat(),
                decision.heroCombo(),
                decision.priorActions(),
                decision.legalActions(),
                table.players(),
                decision.potBb(),
                decision.toCallBb(),
                decision.stackBb(),
                decision.smallBlindBb(),
                decision.publicationStatus());
    }
}
