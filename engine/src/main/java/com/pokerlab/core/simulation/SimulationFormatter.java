package com.pokerlab.core.simulation;

import com.pokerlab.core.hand.EvaluatedHand;
import java.util.Map;

public final class SimulationFormatter {

    private SimulationFormatter() {}

    public static String formatTrial(SimulationTrial trial) {
        StringBuilder sb = new StringBuilder();

        sb.append("Trial ").append(trial.trialNumber()).append(System.lineSeparator());

        sb.append("Players:").append(System.lineSeparator());
        for (PlayerHand player : trial.players()) {
            sb.append("- ").append(player).append(System.lineSeparator());
        }

        sb.append("Board: ").append(trial.board()).append(System.lineSeparator());

        sb.append("Best hands:").append(System.lineSeparator());
        for (Map.Entry<String, EvaluatedHand> entry : trial.evaluatedHands().entrySet()) {
            sb.append("- ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append(System.lineSeparator());
        }

        sb.append("Winner");
        if (trial.winners().size() > 1) {
            sb.append("s");
        }

        sb.append(": ").append(String.join(", ", trial.winners())).append(System.lineSeparator());

        return sb.toString();
    }

    public static String formatSummary(SimulationResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append("Simulation complete").append(System.lineSeparator());
        sb.append("Simulations: ").append(result.simulations()).append(System.lineSeparator());
        sb.append("Tied pots: ").append(result.tiedPots()).append(System.lineSeparator());
        sb.append("Runtime: ")
                .append(result.runtimeMillis())
                .append(" ms")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        sb.append("Results:").append(System.lineSeparator());

        for (String playerName : result.winsByPlayer().keySet()) {
            double equityPercent = result.equity(playerName) * 100.0;

            sb.append("- ")
                    .append(playerName)
                    .append(": wins=")
                    .append(result.wins(playerName))
                    .append(", equity=")
                    .append(String.format("%.2f%%", equityPercent))
                    .append(System.lineSeparator());
        }

        return sb.toString();
    }
}
