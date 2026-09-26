package com.pokerlab.api;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.FlopTurnRiverHandSession;
import com.pokerlab.solver.FlopTurnRiverSolutionPack;
import com.pokerlab.solver.WeightedCombo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Opt-in HTTP boundary for validation-only connected flop hand replay. */
@RestController
@RequestMapping("/api/v1/trainer/research/flop")
@ConditionalOnProperty(name = "pokerlab.trainer.flop-research-enabled", havingValue = "true")
public class FlopResearchController {
    public record Metadata(
            String spotHash,
            String packHash,
            String publicationStatus,
            List<Card> flop,
            double potBb,
            double remainingStackBb,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb,
            List<String> firstRange,
            List<String> secondRange,
            double gameGapBb,
            String rakeModel,
            String chanceModel,
            String bettingTree) {}

    public record HandReplayRequest(
            @NotBlank String seed,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotNull @Min(0) @Max(1) Integer heroPlayer,
            @NotNull List<String> actions) {}

    private final FlopTurnRiverHandSession session;

    public FlopResearchController(FlopTurnRiverHandSession session) {
        this.session = session;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        FlopTurnRiverSolutionPack pack = session.pack();
        var spot = pack.spot();
        return response(
                new Metadata(
                        pack.spotHash(),
                        session.packHash(),
                        pack.publicationStatus(),
                        spot.flop(),
                        spot.potBb(),
                        spot.remainingStackBb(),
                        spot.flopBetBb(),
                        spot.turnBetBb(),
                        spot.riverBetBb(),
                        spot.firstRange().stream().map(WeightedCombo::key).toList(),
                        spot.secondRange().stream().map(WeightedCombo::key).toList(),
                        pack.gameGapBb(),
                        "NO_RAKE",
                        "FULL_PHYSICAL_DECK",
                        "SINGLE_BET_EACH_STREET_NO_RAISES"));
    }

    @PostMapping("/hands/replay")
    public ResponseEntity<FlopTurnRiverHandSession.Snapshot> replayHand(
            @Valid @RequestBody HandReplayRequest request) {
        return response(
                session.replay(
                        parseSeed(request.seed()),
                        request.packHash(),
                        request.heroPlayer(),
                        request.actions()));
    }

    private static long parseSeed(String seed) {
        try {
            return Long.parseLong(seed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Seed must be a signed 64-bit integer", exception);
        }
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
