package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/** Reproducible research questions from a validated turn-to-river pack. */
public final class TurnRiverResearchTrainer {
    public record Question(
            String packHash,
            String publicationStatus,
            String street,
            List<Card> turnBoard,
            Card river,
            double potBb,
            double remainingStackBb,
            double turnBetBb,
            double riverBetBb,
            int actingPlayer,
            String heroCombo,
            String turnHistory,
            String riverHistory,
            List<String> legalActions,
            double gameGapBb) {
        public Question {
            turnBoard = List.copyOf(turnBoard);
            legalActions = List.copyOf(legalActions);
        }
    }

    public record Feedback(
            String packHash,
            String street,
            String heroCombo,
            String turnHistory,
            Card river,
            String riverHistory,
            String selectedAction,
            double selectedEvBb,
            double bestEvBb,
            double evLossBb,
            Map<String, Double> actionFrequency,
            Map<String, Double> actionEvBb) {
        public Feedback {
            actionFrequency = Map.copyOf(actionFrequency);
            actionEvBb = Map.copyOf(actionEvBb);
        }
    }

    private record Node(
            int player, String turnHistory, Card river, String riverHistory, String heroCombo) {}

    private final TurnRiverSolutionPack pack;
    private final TurnRiverDecisionEvaluator evaluator;
    private final String packHash;
    private final List<Node> turnNodes;
    private final List<Node> riverNodes;

    public TurnRiverResearchTrainer(TurnRiverSolutionPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        evaluator = new TurnRiverDecisionEvaluator(pack);
        packHash = TurnRiverPackJson.contentHash(pack);
        TurnRiverGame game = pack.spot().game();
        Map<String, Node> candidates = new LinkedHashMap<>();
        collect(game, game.initialState(), candidates);
        List<Node> turns = new ArrayList<>();
        List<Node> rivers = new ArrayList<>();
        for (Node node : candidates.values()) {
            try {
                evaluator.evaluate(
                        node.player(),
                        node.turnHistory(),
                        node.river(),
                        node.riverHistory(),
                        node.heroCombo());
                (node.river() == null ? turns : rivers).add(node);
            } catch (IllegalArgumentException exception) {
                if (!"Unknown or unreachable turn-river decision".equals(exception.getMessage()))
                    throw exception;
            }
        }
        if (turns.isEmpty() || rivers.isEmpty())
            throw new IllegalArgumentException(
                    "Turn-river pack has no reachable decisions on a street");
        turnNodes = List.copyOf(turns);
        riverNodes = List.copyOf(rivers);
    }

    public TurnRiverSolutionPack pack() {
        return pack;
    }

    public String packHash() {
        return packHash;
    }

    public int availableTurnQuestions() {
        return turnNodes.size();
    }

    public int availableRiverQuestions() {
        return riverNodes.size();
    }

    public Question question(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Node> choices = random.nextBoolean() ? turnNodes : riverNodes;
        Node node = choices.get(random.nextInt(choices.size()));
        TurnRiverSpot spot = pack.spot();
        boolean calledTurn = node.turnHistory().equals("bc") || node.turnHistory().equals("kbc");
        double contribution = calledTurn ? spot.turnBetBb() : 0;
        String street = node.river() == null ? "TURN" : "RIVER";
        // The action set depends only on the current public history.
        String history = node.river() == null ? node.turnHistory() : node.riverHistory();
        double outstandingBet =
                history.equals("b") || history.equals("kb")
                        ? (node.river() == null ? spot.turnBetBb() : spot.riverBetBb())
                        : 0;
        List<String> actions =
                history.isEmpty() || history.equals("k") ? List.of("k", "b") : List.of("c", "f");
        return new Question(
                packHash,
                pack.publicationStatus(),
                street,
                spot.turnBoard(),
                node.river(),
                spot.potBb() + 2 * contribution + outstandingBet,
                spot.remainingStackBb() - contribution,
                spot.turnBetBb(),
                spot.riverBetBb(),
                node.player(),
                node.heroCombo(),
                node.turnHistory(),
                node.riverHistory(),
                actions,
                pack.gameGapBb());
    }

    public Feedback grade(long seed, String submittedPackHash, String action) {
        if (!packHash.equals(submittedPackHash))
            throw new IllegalArgumentException("Solution pack has changed; start a new question");
        Question question = question(seed);
        if (!question.legalActions().contains(action))
            throw new IllegalArgumentException("Illegal action for this turn-river decision");
        TurnRiverDecisionEvaluator.Decision decision =
                evaluator.evaluate(
                        question.actingPlayer(),
                        question.turnHistory(),
                        question.river(),
                        question.riverHistory(),
                        question.heroCombo());
        double selected = decision.actionEvBb().get(action);
        double best =
                decision.actionEvBb().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElseThrow();
        return new Feedback(
                packHash,
                question.street(),
                question.heroCombo(),
                question.turnHistory(),
                question.river(),
                question.riverHistory(),
                action,
                selected,
                best,
                Math.max(0, best - selected),
                decision.actionFrequency(),
                decision.actionEvBb());
    }

    private static void collect(
            TurnRiverGame game, TurnRiverGame.State state, Map<String, Node> candidates) {
        if (game.isTerminal(state)) return;
        int player = game.currentPlayer(state);
        if (player == -1) {
            for (ChanceOutcome<TurnRiverGame.State> outcome : game.chanceOutcomes(state))
                collect(game, outcome.state(), candidates);
            return;
        }
        String key = player + ":" + game.informationSet(state);
        WeightedCombo hero = player == 0 ? state.first() : state.second();
        candidates.putIfAbsent(
                key,
                new Node(
                        player,
                        state.turnHistory(),
                        state.river(),
                        state.riverHistory(),
                        hero.key()));
        for (String action : game.legalActions(state))
            collect(game, game.afterAction(state, action), candidates);
    }
}
