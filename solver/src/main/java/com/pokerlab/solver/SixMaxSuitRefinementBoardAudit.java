package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Independent physical-flop checkdown witnesses; no action EV or full-poker error certificate. */
public final class SixMaxSuitRefinementBoardAudit {
    public static final String SCHEMA = "six-max-suit-refinement-board-witnesses/v1";
    static final int MAX_BYTES = 2 * 1024 * 1024;

    public record DealWitness(
            List<String> hands,
            double probabilityGivenBoard,
            double exactFirstShare,
            double modelFirstShare) {
        public DealWitness {
            hands = List.copyOf(hands);
        }
    }

    public record BoardWitness(
            List<String> board,
            String observationKey,
            double boardProbabilityGivenHistory,
            double modelObservationProbabilityGivenHistory,
            long enumeratedRunouts,
            double exactFirstShareGivenBoard,
            double modelFirstShareGivenObservation,
            double shareDifference,
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
            String parentRankTableHash,
            String payoffTableHash,
            String gameHash,
            String solutionHash,
            List<List<String>> requestedBoards,
            List<HistoryAudit> histories,
            long enumeratedRunouts,
            double maximumWitnessShareDifference,
            double maximumPrivateWorldShareDifference) {
        public Report {
            requestedBoards = requestedBoards.stream().map(List::copyOf).toList();
            histories = List.copyOf(histories);
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !SixMaxSuitRefinementStudy.MODEL.equals(model)
                    || !"DECLARED_PHYSICAL_FLOPS_CHECKDOWN_SHARES_BEFORE_ACTIONS"
                            .equals(comparison))
                throw new IllegalArgumentException("Unsupported suit board witness identity");
        }
    }

    private SixMaxSuitRefinementBoardAudit() {}

    public static Report assess(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            SixMaxSuitRefinementStudy.Checkpoint cp,
            List<List<Card>> requested)
            throws Exception {
        var boards = SixMaxRankTextureBoardAudit.canonicalBoards(requested);
        for (var board : boards)
            if (!SixMaxSuitRefinementPayoffTable.observe(board, table.refinedSignals()).physical())
                throw new IllegalArgumentException(
                        "Board witnesses require declared physical refinement");
        var game = SixMaxSuitRefinementStudy.rebuild(source, parent, table, cp);
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(cp.solution());
        var histories = new ArrayList<HistoryAudit>();
        long runouts = 0;
        double maximum = 0, maximumWorld = 0;
        for (var selection : game.selections()) {
            if (!SixMaxTextureConditionalAudit.hasReach(
                    game.sourceGame(), pre, selection.history())) {
                histories.add(
                        new HistoryAudit(
                                selection.history(),
                                "ZERO_POLICY_REACH",
                                null,
                                null,
                                0,
                                List.of(),
                                List.of()));
                continue;
            }
            var transition =
                    new SixMaxPolicyFlopTransition(game.sourceGame(), pre, selection.history());
            requireNormal(transition.reachProbability());
            for (var deal : transition.deals()) requireNormal(deal.probability());
            var blocked = new ArrayList<List<String>>();
            var witnesses = new ArrayList<BoardWitness>();
            int first = transition.firstToAct().ordinal(),
                    second = transition.secondToAct().ordinal();
            int mask = (1 << first) | (1 << second);
            for (var board : boards) {
                double probability = transition.flopProbability(board);
                if (probability == 0) {
                    blocked.add(names(board));
                    continue;
                }
                requireNormal(probability);
                var observation =
                        SixMaxSuitRefinementPayoffTable.observe(board, table.refinedSignals());
                int index = table.observations().indexOf(observation);
                if (index < 0)
                    throw new IllegalStateException("Legal physical flop absent from model");
                double modelMass = 0, modelSum = 0;
                for (var deal : transition.deals()) {
                    var row = tableDeal(table, deal);
                    long count = row.flopCounts().get(index);
                    if (count == 0) continue;
                    double mass = deal.probability() * count / 9880;
                    requireNormal(mass);
                    modelMass += mass;
                    modelSum += mass * row.pair(mask).share(first, index, count * 666);
                }
                if (Math.abs(modelMass - probability) > 1e-15)
                    throw new IllegalStateException(
                            "Physical observation and board posterior differ");
                var flop = transition.conditionOnFlop(board);
                double exactSum = 0, worldMaximum = 0;
                long boardRunouts = 0;
                var deals = new ArrayList<DealWitness>();
                for (int world = 0; world < flop.deals().size(); world++) {
                    var deal = flop.deals().get(world);
                    var exact = flop.exactCheckdown(world);
                    if (exact.runouts() != 666)
                        throw new IllegalStateException(
                                "All six private hands must block the runout deck");
                    var row = tableDeal(table, deal);
                    if (row.flopCounts().get(index) != 1)
                        throw new IllegalStateException(
                                "Legal named board must occur once per world");
                    double actual = exact.shares().get(transition.firstToAct());
                    double model = row.pair(mask).share(first, index, 666);
                    double error = Math.abs(actual - model);
                    if (error > 1e-12)
                        throw new IllegalStateException(
                                "Refined payoff disagrees with independent physical runouts");
                    exactSum += deal.probability() * actual;
                    worldMaximum = Math.max(worldMaximum, error);
                    boardRunouts += exact.runouts();
                    deals.add(
                            new DealWitness(
                                    deal.hands().stream().map(WeightedCombo::key).toList(),
                                    deal.probability(),
                                    actual,
                                    model));
                }
                double modelShare = modelSum / modelMass;
                double difference = exactSum - modelShare;
                if (Math.abs(difference) > 1e-12)
                    throw new IllegalStateException(
                            "Refined posterior settlement differs from actual flop");
                witnesses.add(
                        new BoardWitness(
                                names(board),
                                observation.key(),
                                probability,
                                modelMass,
                                boardRunouts,
                                exactSum,
                                modelShare,
                                difference,
                                worldMaximum,
                                deals));
                runouts += boardRunouts;
                maximum = Math.max(maximum, Math.abs(difference));
                maximumWorld = Math.max(maximumWorld, worldMaximum);
            }
            histories.add(
                    new HistoryAudit(
                            selection.history(),
                            "AUDITED",
                            transition.firstToAct(),
                            transition.secondToAct(),
                            transition.reachProbability(),
                            blocked,
                            witnesses));
        }
        return new Report(
                SCHEMA,
                "VALIDATION_ONLY",
                "DECLARED_PHYSICAL_FLOPS_CHECKDOWN_SHARES_BEFORE_ACTIONS",
                cp.model(),
                cp.sourcePackHash(),
                cp.sourceSpotHash(),
                cp.parentRankTableHash(),
                cp.payoffTableHash(),
                cp.gameHash(),
                cp.solutionHash(),
                boards.stream().map(SixMaxSuitRefinementBoardAudit::names).toList(),
                histories,
                runouts,
                maximum,
                maximumWorld);
    }

    static List<String> names(List<Card> cards) {
        return cards.stream().map(Card::compact).toList();
    }

    private static SixMaxSuitRefinementPayoffTable.Deal tableDeal(
            SixMaxSuitRefinementPayoffTable.Artifact table,
            SixMaxPolicyFlopTransition.JointDeal deal) {
        var keys = deal.hands().stream().map(WeightedCombo::key).toList();
        return table.deals().stream()
                .filter(row -> row.hands().equals(keys))
                .findFirst()
                .orElseThrow();
    }

    private static void requireNormal(double value) {
        if (!Double.isFinite(value) || value < Double.MIN_NORMAL)
            throw new IllegalArgumentException(
                    "Board witness underflow requires a log-space audit");
    }

    public static void write(Path output, Report report) throws Exception {
        SixMaxRankTexturePayoffTable.writeBytes(
                output,
                SixMaxTextureStudy.json(report).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                MAX_BYTES);
    }

    public static Report replay(
            Path input,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            SixMaxSuitRefinementStudy.Checkpoint cp)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(input, MAX_BYTES),
                                Report.class);
        var boards =
                saved.requestedBoards().stream()
                        .map(b -> b.stream().map(Card::parse).toList())
                        .toList();
        var expected = assess(source, parent, table, cp, boards);
        if (!expected.equals(saved))
            throw new IllegalArgumentException("Suit board replay differs");
        return expected;
    }
}
