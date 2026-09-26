package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/** Reproducible, validation-only river questions and server-side action grading. */
public final class RiverResearchTrainer {
    public record Question(
            String packHash,
            String spotId,
            String publicationStatus,
            List<Card> board,
            double potBb,
            double remainingStackBb,
            double betBb,
            PreflopAllInSpot.Seat actingSeat,
            String heroCombo,
            String publicHistory,
            List<String> legalActions,
            double gameGapBb) {
        public Question {
            board = List.copyOf(board);
            legalActions = List.copyOf(legalActions);
        }
    }

    public record Feedback(
            String packHash,
            String heroCombo,
            String publicHistory,
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

    private record Node(int player, String history, String combo) {}

    private final RiverSolutionPack pack;
    private final RiverDecisionEvaluator evaluator;
    private final List<Node> nodes;
    private final String packHash;

    public RiverResearchTrainer(RiverSolutionPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        evaluator = new RiverDecisionEvaluator(pack);
        packHash = RiverPackJson.contentHash(pack);
        List<Node> available = new ArrayList<>();
        addReachable(available, 0, "", pack.spot().firstRange());
        addReachable(available, 0, "kb", pack.spot().firstRange());
        addReachable(available, 1, "k", pack.spot().secondRange());
        addReachable(available, 1, "b", pack.spot().secondRange());
        if (available.isEmpty())
            throw new IllegalArgumentException("River pack has no reachable decisions");
        nodes = List.copyOf(available);
    }

    public RiverSolutionPack pack() {
        return pack;
    }

    public String packHash() {
        return packHash;
    }

    public int availableQuestions() {
        return nodes.size();
    }

    public Question question(long seed) {
        Node node = nodes.get(new SplittableRandom(seed).nextInt(nodes.size()));
        RiverBetSpot spot = pack.spot();
        return new Question(
                packHash,
                spot.id(),
                pack.publicationStatus(),
                spot.board(),
                spot.potBb(),
                spot.remainingStackBb(),
                spot.betBb(),
                node.player() == 0 ? spot.firstSeat() : spot.secondSeat(),
                node.combo(),
                node.history(),
                node.history().isEmpty() || node.history().equals("k")
                        ? List.of("k", "b")
                        : List.of("c", "f"),
                pack.gameGapBb());
    }

    public Feedback grade(long seed, String submittedPackHash, String action) {
        if (!packHash.equals(submittedPackHash))
            throw new IllegalArgumentException("Solution pack has changed; start a new question");
        Question question = question(seed);
        if (!question.legalActions().contains(action))
            throw new IllegalArgumentException("Illegal action for this river decision");
        int player = question.actingSeat() == pack.spot().firstSeat() ? 0 : 1;
        RiverDecisionEvaluator.Decision decision =
                evaluator.evaluate(player, question.publicHistory(), question.heroCombo());
        double selected = decision.actionEvBb().get(action);
        double best =
                decision.actionEvBb().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElseThrow();
        return new Feedback(
                packHash,
                question.heroCombo(),
                question.publicHistory(),
                action,
                selected,
                best,
                Math.max(0, best - selected),
                decision.actionFrequency(),
                decision.actionEvBb());
    }

    private void addReachable(
            List<Node> available, int player, String history, List<WeightedCombo> range) {
        for (WeightedCombo combo : range) {
            try {
                evaluator.evaluate(player, history, combo.key());
                available.add(new Node(player, history, combo.key()));
            } catch (IllegalArgumentException exception) {
                if (!"Unknown or unreachable river decision".equals(exception.getMessage()))
                    throw exception;
            }
        }
    }
}
