package com.pokerlab.api;

import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame;
import com.pokerlab.solver.SixMaxSuppliedFlopTrainer;
import com.pokerlab.solver.SixMaxSuppliedRangePreflopGame;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Separate one-question interval protocol. Only canonical cached evidence reaches requests. */
@RestController
@RequestMapping("/api/v1/trainer/research/flop-interval")
@ConditionalOnProperty(
        name = "pokerlab.trainer.flop-interval-research-enabled",
        havingValue = "true")
public class FlopIntervalResearchController {
    public record Metadata(
            String reportHash,
            String publicationStatus,
            boolean trainerAdmission,
            String selection,
            String model,
            String chanceModel,
            String evScope,
            String reachUnits,
            String beliefs,
            String sourceHistoryReachStatus,
            int availableQuestions,
            int activePlayers,
            int maximumAttempts,
            double maximumLossBb) {}

    public record GradeRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotBlank @Size(max = 32) String action) {}

    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotNull @Size(max = SixMaxSuppliedFlopTrainer.MAX_ATTEMPTS)
                    List<@NotBlank @Size(max = 32) String> actions) {}

    private final SixMaxSuppliedFlopTrainer trainer;

    public FlopIntervalResearchController(SixMaxSuppliedFlopTrainer trainer) {
        this.trainer = trainer;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        var q = trainer.question();
        return response(
                new Metadata(
                        q.reportHash(),
                        q.publicationStatus(),
                        false,
                        q.selection(),
                        SixMaxSuppliedFlopBettingGame.MODEL,
                        SixMaxSuppliedFlopBettingGame.CHANCE,
                        q.evScope(),
                        q.reachUnits(),
                        SixMaxSuppliedRangePreflopGame.BELIEFS,
                        SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                        1,
                        2,
                        SixMaxSuppliedFlopTrainer.MAX_ATTEMPTS,
                        q.maximumLossBb()));
    }

    @GetMapping("/question")
    public ResponseEntity<SixMaxSuppliedFlopTrainer.Question> question() {
        return response(trainer.question());
    }

    @PostMapping("/grade")
    public ResponseEntity<SixMaxSuppliedFlopTrainer.Feedback> grade(
            @Valid @RequestBody GradeRequest request) {
        requireReport(request.reportHash());
        return response(trainer.grade(trainer.question(), request.action()));
    }

    @PostMapping("/review")
    public ResponseEntity<SixMaxSuppliedFlopTrainer.Review> review(
            @Valid @RequestBody ReviewRequest request) {
        requireReport(request.reportHash());
        return response(trainer.review(trainer.question(), request.actions()));
    }

    private void requireReport(String hash) {
        if (!trainer.question().reportHash().equals(hash))
            throw new IllegalArgumentException(
                    "Qualification report changed; load the question again");
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
