package com.pokerlab.core.batch;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BatchResult(
        UUID simulationId, int batchId, long trials, List<PlayerResult> players, long elapsedMs) {
    public BatchResult {
        Objects.requireNonNull(simulationId);
        players = List.copyOf(players);
        if (batchId < 0 || trials < 1 || elapsedMs < 0 || players.size() < 2 || players.size() > 9)
            throw new IllegalArgumentException("invalid batch result");
        if (players.stream().map(PlayerResult::name).distinct().count() != players.size())
            throw new IllegalArgumentException("duplicate result player");
        for (var player : players) {
            if (player.wins() > trials
                    || player.ties() > trials
                    || player.losses() > trials
                    || player.wins() + player.ties() + player.losses() != trials)
                throw new IllegalArgumentException("player counts do not match trials");
        }
        if (Math.abs(players.stream().mapToDouble(PlayerResult::equityShares).sum() - trials)
                > Math.max(1e-8, trials * 1e-10))
            throw new IllegalArgumentException("equity shares do not conserve pots");
    }
}
