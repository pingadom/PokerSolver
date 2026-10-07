package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exact named-board witnesses for information hidden by the rank/texture abstraction. This compares
 * checkdown shares before postflop actions, not strategy EVs or full-poker exploitability.
 */
public final class SixMaxRankTextureBoardAudit {
    public static final int MAX_BOARDS = 24;
    public static final String SCHEMA = "six-max-rank-texture-board-witnesses/v1";

    public record DealWitness(
            List<String> hands,
            double probabilityGivenBoard,
            double exactFirstShare,
            double rankTextureFirstShare) {
        public DealWitness {
            hands = List.copyOf(hands);
        }
    }

    public record BoardWitness(
            List<String> board,
            SixMaxRankTexturePayoffTable.Signal signal,
            double boardProbabilityGivenHistory,
            long enumeratedRunouts,
            double exactFirstShareGivenBoard,
            double rankTextureFirstShareUsingBoardPosterior,
            double rankTextureFirstShareGivenSignal,
            double runoutAbstractionShareDifference,
            double posteriorInformationShareDifference,
            double totalShareDifference,
            double checkdownFirstUtilityDifferenceBb,
            double calledPotFirstUtilityDifferenceBb,
            double maximumPrivateWorldShareDifference,
            List<DealWitness> deals) {
        public BoardWitness {
            board = List.copyOf(board);
            deals = List.copyOf(deals);
        }
    }

    public record HistoryAudit(
            List<PublicAction> history,
            String status,
            Seat firstToAct,
            Seat secondToAct,
            double historyProbability,
            double potBb,
            double betBb,
            List<List<String>> blockedBoards,
            List<BoardWitness> witnesses) {
        public HistoryAudit {
            history = List.copyOf(history);
            blockedBoards = blockedBoards.stream().map(List::copyOf).toList();
            witnesses = List.copyOf(witnesses);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            String comparison,
            String model,
            String sourcePackHash,
            String sourceSpotHash,
            String payoffTableHash,
            String gameHash,
            String solutionHash,
            String algorithm,
            List<List<String>> requestedBoards,
            List<HistoryAudit> histories,
            long enumeratedRunouts,
            double maximumWitnessShareDifference) {
        public Report {
            requestedBoards = requestedBoards.stream().map(List::copyOf).toList();
            histories = List.copyOf(histories);
        }
    }

    private SixMaxRankTextureBoardAudit() {}

    static List<List<Card>> canonicalBoards(List<List<Card>> boards) {
        if (boards.isEmpty() || boards.size() > MAX_BOARDS)
            throw new IllegalArgumentException("Require one to twenty-four distinct named flops");
        var result =
                boards.stream()
                        .map(
                                board -> {
                                    if (board.size() != 3 || board.stream().distinct().count() != 3)
                                        throw new IllegalArgumentException(
                                                "Each flop requires three distinct cards");
                                    return board.stream()
                                            .sorted(Comparator.comparing(Card::compact))
                                            .toList();
                                })
                        .toList();
        if (result.stream().distinct().count() != result.size())
            throw new IllegalArgumentException("Duplicate named flop, including reordered cards");
        return result;
    }

    public static Report assess(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            SixMaxRankTextureStudy.Checkpoint checkpoint,
            List<List<Card>> requested)
            throws Exception {
        var boards = canonicalBoards(requested);
        var game = SixMaxRankTextureStudy.rebuild(source, table, checkpoint);
        var preflop = SixMaxPreflopContinuationFeedback.preflopPolicy(checkpoint.solution());
        var histories = new ArrayList<HistoryAudit>();
        long runouts = 0;
        double maxDifference = 0;
        for (var selection : game.selections()) {
            if (!SixMaxTextureConditionalAudit.hasReach(
                    game.sourceGame(), preflop, selection.history())) {
                histories.add(
                        new HistoryAudit(
                                selection.history(),
                                "ZERO_POLICY_REACH",
                                null,
                                null,
                                0,
                                0,
                                0,
                                List.of(),
                                List.of()));
                continue;
            }
            var transition =
                    new SixMaxPolicyFlopTransition(game.sourceGame(), preflop, selection.history());
            // A log-space reached world can underflow when converted back to a probability.
            // Reject before a named board could incorrectly be labelled completely blocked.
            for (var deal : transition.deals())
                if (deal.probability() < Double.MIN_NORMAL)
                    throw new IllegalArgumentException(
                            "Reached private-world underflow requires log-space audit");
            double bet =
                    Math.min(
                            transition.potBb() * selection.potFraction(),
                            transition.remainingStackBb());
            var witnesses = new ArrayList<BoardWitness>();
            var blocked = new ArrayList<List<String>>();
            for (var board : boards) {
                double probability = transition.flopProbability(board);
                if (probability == 0) {
                    blocked.add(names(board));
                    continue;
                }
                if (probability < Double.MIN_NORMAL)
                    throw new IllegalArgumentException(
                            "Named board reach underflow requires log-space audit");
                var flop = transition.conditionOnFlop(board);
                var signal =
                        SixMaxRankTexturePayoffTable.Signal.from(
                                board.get(0), board.get(1), board.get(2));
                int texture = table.signals().indexOf(signal);
                double classMass = 0, classShareSum = 0;
                for (var deal : transition.deals()) {
                    var row = tableDeal(table, deal);
                    double mass = deal.probability() * row.flopCounts().get(texture) / 9880.0;
                    if (mass == 0 && row.flopCounts().get(texture) == 0) continue;
                    if (mass < Double.MIN_NORMAL)
                        throw new IllegalArgumentException(
                                "Texture posterior underflow requires log-space audit");
                    classMass += mass;
                    classShareSum += mass * share(row, transition, texture);
                }
                if (!(classMass > 0))
                    throw new IllegalStateException("Legal board must have texture support");
                double exactShare = 0, coarseShareAtBoard = 0, maxWorldDifference = 0;
                long boardRunouts = 0;
                var deals = new ArrayList<DealWitness>();
                for (int index = 0; index < flop.deals().size(); index++) {
                    var deal = flop.deals().get(index);
                    var exact = flop.exactCheckdown(index);
                    if (exact.runouts() != SixMaxRankTexturePayoffTable.RUNOUTS_PER_FLOP)
                        throw new IllegalStateException(
                                "All six private hands must block the runout deck");
                    double actual = exact.shares().get(transition.firstToAct());
                    double coarse = share(tableDeal(table, deal), transition, texture);
                    exactShare += deal.probability() * actual;
                    coarseShareAtBoard += deal.probability() * coarse;
                    maxWorldDifference = Math.max(maxWorldDifference, Math.abs(actual - coarse));
                    boardRunouts += exact.runouts();
                    deals.add(
                            new DealWitness(
                                    deal.hands().stream().map(WeightedCombo::key).toList(),
                                    deal.probability(),
                                    actual,
                                    coarse));
                }
                double classShare = classShareSum / classMass;
                double difference = exactShare - classShare;
                witnesses.add(
                        new BoardWitness(
                                names(board),
                                signal,
                                probability,
                                boardRunouts,
                                exactShare,
                                coarseShareAtBoard,
                                classShare,
                                exactShare - coarseShareAtBoard,
                                coarseShareAtBoard - classShare,
                                difference,
                                transition.potBb() * difference,
                                (transition.potBb() + 2 * bet) * difference,
                                maxWorldDifference,
                                deals));
                runouts += boardRunouts;
                maxDifference = Math.max(maxDifference, Math.abs(difference));
            }
            histories.add(
                    new HistoryAudit(
                            selection.history(),
                            "AUDITED",
                            transition.firstToAct(),
                            transition.secondToAct(),
                            transition.reachProbability(),
                            transition.potBb(),
                            bet,
                            blocked,
                            witnesses));
        }
        return new Report(
                SCHEMA,
                "VALIDATION_ONLY",
                "NAMED_BOARDS_CHECKDOWN_SHARES_BEFORE_POSTFLOP_ACTIONS",
                checkpoint.model(),
                checkpoint.sourcePackHash(),
                checkpoint.sourceSpotHash(),
                checkpoint.payoffTableHash(),
                checkpoint.gameHash(),
                checkpoint.solutionHash(),
                checkpoint.algorithm(),
                boards.stream().map(SixMaxRankTextureBoardAudit::names).toList(),
                histories,
                runouts,
                maxDifference);
    }

    private static List<String> names(List<Card> board) {
        return board.stream().map(Card::compact).toList();
    }

    private static SixMaxRankTexturePayoffTable.Deal tableDeal(
            SixMaxRankTexturePayoffTable.Artifact table,
            SixMaxPolicyFlopTransition.JointDeal deal) {
        var keys = deal.hands().stream().map(WeightedCombo::key).toList();
        return table.deals().stream()
                .filter(row -> row.hands().equals(keys))
                .findFirst()
                .orElseThrow();
    }

    private static double share(
            SixMaxRankTexturePayoffTable.Deal row,
            SixMaxPolicyFlopTransition transition,
            int texture) {
        int first = transition.firstToAct().ordinal(), second = transition.secondToAct().ordinal();
        return row.pair((1 << first) | (1 << second))
                .share(
                        first,
                        texture,
                        row.flopCounts().get(texture)
                                * SixMaxRankTexturePayoffTable.RUNOUTS_PER_FLOP);
    }
}
