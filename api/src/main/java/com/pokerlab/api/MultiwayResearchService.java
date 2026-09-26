package com.pokerlab.api;

import com.pokerlab.solver.CfrSolution;
import com.pokerlab.solver.MultiwayCallTrainer;
import com.pokerlab.solver.MultiwayDrillSession;
import com.pokerlab.solver.MultiwayPackJson;
import com.pokerlab.solver.MultiwayPreflopCallGame;
import com.pokerlab.solver.MultiwaySidePotPack;
import com.pokerlab.solver.MultiwaySolutionPack;
import com.pokerlab.solver.PreflopAllInSpot;
import java.util.List;

/** Loads one immutable research pack once. Requests only draw or grade saved strategies. */
public final class MultiwayResearchService {
    private static final double MAX_NASH_CONV_BB = 0.05;

    public record Metadata(
            String spotId,
            String spotHash,
            String packHash,
            String packSchema,
            String publicationStatus,
            String solverVersion,
            String generatedAt,
            String payoffMethod,
            List<PreflopAllInSpot.Seat> seats,
            List<Double> committedBb,
            double stackBb,
            List<Double> stacksBb,
            double deadMoneyBb,
            String rakeModel,
            double nashConvBb,
            double maximumPayoffStandardErrorBb,
            int sessionLength) {}

    /** Seeds are strings at the HTTP boundary to retain every bit in JavaScript clients. */
    public record QuestionView(
            String sessionSeed,
            int index,
            int playerFilter,
            String packHash,
            PreflopAllInSpot.Seat aggressorSeat,
            int actingPlayer,
            PreflopAllInSpot.Seat actingSeat,
            String heroCombo,
            List<MultiwayCallTrainer.PublicAction> priorResponses,
            double potBb,
            double callCostBb,
            double stackBb,
            String publicationStatus,
            List<MultiwayCallTrainer.Action> legalActions) {}

    public record GradedQuestion(QuestionView question, MultiwayCallTrainer.Feedback feedback) {}

    public record SessionReview(
            String packHash,
            List<GradedQuestion> attempts,
            double totalEvLossBb,
            double averageEvLossBb) {}

    private final Metadata metadata;
    private final MultiwayDrillSession session;

    public MultiwayResearchService(MultiwaySolutionPack pack) {
        this(
                pack.rebuildGame(),
                pack.solution(),
                new Metadata(
                        pack.spot().id(),
                        pack.spotHash(),
                        MultiwayPackJson.contentHash(pack),
                        pack.schemaVersion(),
                        pack.publicationStatus(),
                        pack.solverVersion(),
                        pack.generatedAt(),
                        pack.payoffMethod(),
                        pack.spot().seats(),
                        pack.spot().committedBb(),
                        pack.spot().stackBb(),
                        java.util.Collections.nCopies(
                                pack.spot().seats().size(), pack.spot().stackBb()),
                        pack.spot().deadMoneyBb(),
                        "NO_RAKE",
                        pack.nashConvBb(),
                        pack.maxTerminalPayoffSEBb(),
                        MultiwayDrillSession.LENGTH));
    }

    public MultiwayResearchService(MultiwaySidePotPack pack) {
        this(
                pack.rebuildGame(),
                pack.solution(),
                new Metadata(
                        pack.spot().id(),
                        pack.spotHash(),
                        MultiwayPackJson.sidePotContentHash(pack),
                        pack.schemaVersion(),
                        pack.publicationStatus(),
                        pack.solverVersion(),
                        pack.generatedAt(),
                        pack.payoffMethod(),
                        pack.spot().seats(),
                        pack.spot().committedBb(),
                        pack.spot().stacksBb().get(0),
                        pack.spot().stacksBb(),
                        pack.spot().deadMoneyBb(),
                        "NO_RAKE",
                        pack.nashConvBb(),
                        pack.maxTerminalPayoffSEBb(),
                        MultiwayDrillSession.LENGTH));
    }

    private MultiwayResearchService(
            MultiwayPreflopCallGame game, CfrSolution solution, Metadata metadata) {
        if (!"EXACT_ENUMERATION".equals(metadata.payoffMethod()))
            throw new IllegalStateException("Multiway research trainer requires exact payoffs");
        if (metadata.nashConvBb() > MAX_NASH_CONV_BB)
            throw new IllegalStateException(
                    "Multiway research pack exceeds the deviation threshold");
        this.metadata = metadata;
        session = new MultiwayDrillSession(game, solution);
    }

    public Metadata metadata() {
        return metadata;
    }

    public QuestionView question(long seed, int index, int player) {
        return view(seed, index, player, session.question(seed, index, player));
    }

    public GradedQuestion grade(
            long seed, int index, int player, String packHash, MultiwayCallTrainer.Action action) {
        requirePack(packHash);
        var attempt = session.grade(seed, index, player, action);
        return new GradedQuestion(
                view(seed, index, player, attempt.question()), attempt.feedback());
    }

    public SessionReview review(
            long seed, int player, String packHash, List<MultiwayCallTrainer.Action> actions) {
        requirePack(packHash);
        var review = session.review(seed, player, actions);
        return new SessionReview(
                metadata.packHash(),
                review.attempts().stream()
                        .map(
                                attempt ->
                                        new GradedQuestion(
                                                view(
                                                        seed,
                                                        attempt.index(),
                                                        player,
                                                        attempt.question()),
                                                attempt.feedback()))
                        .toList(),
                review.totalEvLossBb(),
                review.averageEvLossBb());
    }

    private void requirePack(String candidate) {
        if (!metadata.packHash().equals(candidate))
            throw new IllegalArgumentException("Solution pack has changed; start a new session");
    }

    private QuestionView view(
            long seed, int index, int player, MultiwayCallTrainer.Question question) {
        return new QuestionView(
                Long.toString(seed),
                index,
                player,
                metadata.packHash(),
                metadata.seats().get(0),
                question.actingPlayer(),
                question.actingSeat(),
                question.heroCombo(),
                question.priorResponses(),
                question.potBb(),
                question.callCostBb(),
                question.stackBb(),
                question.publicationStatus(),
                question.legalActions());
    }
}
