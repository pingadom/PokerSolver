package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicit synthetic likelihoods for actions observed on flop and turn. The probabilities depend on
 * a candidate opponent combo and the public board, never on the actual hidden opponent hand.
 */
public record PostflopActionBelief(
        double checkWithHighCard,
        double checkWithPairOrBetter,
        double callWithHighCard,
        double callWithPairOrBetter) {
    public PostflopActionBelief {
        for (double probability :
                new double[] {
                    checkWithHighCard, checkWithPairOrBetter, callWithHighCard, callWithPairOrBetter
                })
            if (!Double.isFinite(probability) || probability <= 0 || probability >= 1)
                throw new IllegalArgumentException("Postflop action probability must be in (0,1)");
    }

    /** One candidate's probability of producing its own observed actions on both prior streets. */
    public double likelihood(
            WeightedCombo candidate,
            ButtonBigBlindPhysicalDeckGame.State riverState,
            boolean candidateIsBigBlind) {
        if (riverState == null) throw new IllegalArgumentException("Expected a dealt river state");
        return likelihood(candidate, PublicRiverHistory.from(riverState), candidateIsBigBlind);
    }

    public double likelihood(
            WeightedCombo candidate, PublicRiverHistory riverState, boolean candidateIsBigBlind) {
        if (candidate == null
                || riverState == null
                || riverState.flop() == null
                || riverState.turn() == null
                || riverState.river() == null
                || !riverState.preflopHistory().equals("oc"))
            throw new IllegalArgumentException("Expected a dealt river state after open and call");
        List<Card> flop = riverState.flop();
        List<Card> turnBoard = new ArrayList<>(flop);
        turnBoard.add(riverState.turn());
        return streetLikelihood(candidate, flop, riverState.flopHistory(), candidateIsBigBlind)
                * streetLikelihood(
                        candidate, turnBoard, riverState.turnHistory(), candidateIsBigBlind);
    }

    public List<WeightedCombo> posteriorWeights(
            List<WeightedCombo> opponentPrior,
            ButtonBigBlindPhysicalDeckGame.State riverState,
            boolean opponentIsBigBlind) {
        if (riverState == null) throw new IllegalArgumentException("Expected a dealt river state");
        return posteriorWeights(
                opponentPrior, PublicRiverHistory.from(riverState), opponentIsBigBlind);
    }

    public List<WeightedCombo> posteriorWeights(
            List<WeightedCombo> opponentPrior,
            PublicRiverHistory riverState,
            boolean opponentIsBigBlind) {
        if (riverState == null
                || riverState.flop() == null
                || riverState.turn() == null
                || riverState.river() == null)
            throw new IllegalArgumentException("Expected a dealt river state");
        List<WeightedCombo> posterior = new ArrayList<>(opponentPrior.size());
        List<Card> board = new ArrayList<>(riverState.flop());
        board.add(riverState.turn());
        board.add(riverState.river());
        for (WeightedCombo candidate : opponentPrior) {
            if (board.contains(candidate.first()) || board.contains(candidate.second())) continue;
            posterior.add(
                    new WeightedCombo(
                            candidate.first(),
                            candidate.second(),
                            candidate.weight()
                                    * likelihood(candidate, riverState, opponentIsBigBlind)));
        }
        return List.copyOf(posterior);
    }

    /** Probability of an observed action sequence on one public street. */
    public double streetLikelihood(
            WeightedCombo candidate,
            List<Card> board,
            String history,
            boolean candidateIsBigBlind) {
        double check = checkProbability(candidate, board);
        double call = callProbability(candidate, board);
        return switch (history) {
            case "kk" -> check;
            case "bc" -> candidateIsBigBlind ? 1 - check : call;
            case "kbc" -> candidateIsBigBlind ? check * call : 1 - check;
            default -> throw new IllegalArgumentException("Expected a completed public street");
        };
    }

    public double checkProbability(WeightedCombo candidate, List<Card> board) {
        return pairOrBetter(candidate, board) ? checkWithPairOrBetter : checkWithHighCard;
    }

    private double callProbability(WeightedCombo candidate, List<Card> board) {
        return pairOrBetter(candidate, board) ? callWithPairOrBetter : callWithHighCard;
    }

    private static boolean pairOrBetter(WeightedCombo candidate, List<Card> board) {
        if (candidate == null || board == null || board.size() < 3 || board.size() > 4)
            throw new IllegalArgumentException("Expected a candidate and public flop or turn");
        List<Card> cards = new ArrayList<>(board.size() + 2);
        cards.add(candidate.first());
        cards.add(candidate.second());
        cards.addAll(board);
        return HandEvaluator.evaluateBest(cards).rank().category().strength() >= 1;
    }

    String contentDefinition() {
        return "postflop-pair-action/v1|"
                + Double.toHexString(checkWithHighCard)
                + '|'
                + Double.toHexString(checkWithPairOrBetter)
                + '|'
                + Double.toHexString(callWithHighCard)
                + '|'
                + Double.toHexString(callWithPairOrBetter);
    }
}
