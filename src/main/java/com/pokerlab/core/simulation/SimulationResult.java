package com.pokerlab.core.simulation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

public class SimulationResult {
        private final int simulations;
        private final Map<String, Integer> winsByPlayer;
        private final Map<String, Double> equitySharesByPlayer;
        private final List<SimulationTrial> verboseTrials;
        private int tiedPots;
        private long runtimeMillis;

        public SimulationResult(List<PlayerHand> players, int simulations) {
            this.simulations = simulations;
            this.winsByPlayer = new LinkedHashMap<>();
            this.equitySharesByPlayer = new LinkedHashMap<>();
            this.verboseTrials = new ArrayList<>();

            for (PlayerHand player : players) {
                winsByPlayer.put(player.playerName(), 0);
                equitySharesByPlayer.put(player.playerName(), 0.0);
            }
        }

        public void recordWin(String playerName) {
            winsByPlayer.merge(playerName, 1, Integer::sum);
            equitySharesByPlayer.merge(playerName, 1.0, Double::sum);
        }

        public void recordTie(List<String> tiedPlayers) {
            tiedPots++;
            double share = 1.0 / tiedPlayers.size();

            for (String playerName : tiedPlayers) {
                equitySharesByPlayer.merge(playerName, share, Double::sum);
            }
        }

        public void addVerboseTrial(SimulationTrial trial) {
            verboseTrials.add(trial);
        }

        public void setRuntimeMillis(long runtimeMillis) {
            this.runtimeMillis = runtimeMillis;
        }

        public int simulations() {
            return simulations;
        }

        public int tiedPots() {
            return tiedPots;
        }

        public long runtimeMillis() {
            return runtimeMillis;
        }

        public int wins(String playerName) {
            return winsByPlayer.getOrDefault(playerName, 0);
        }

        public double equityShare(String playerName) {
            return equitySharesByPlayer.getOrDefault(playerName, 0.0);
        }

        public double equity(String playerName) {
            return equityShare(playerName) / simulations;
        }

        public Map<String, Integer> winsByPlayer() {
            return Map.copyOf(winsByPlayer);
        }

        public Map<String, Double> equitySharesByPlayer() {
            return Map.copyOf(equitySharesByPlayer);
        }

        public List<SimulationTrial> verboseTrials() {
            return List.copyOf(verboseTrials);
        }
    }