package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BankRaiseFeedbackReplayTest {
    @Test
    void completeOwnedReplayIssuesOnlyTheMatchingOfflineCapability() throws Exception {
        Path data = Path.of("../docs/data");
        var request = data.resolve("supplied-bank-raise-feedback-request.json");
        var report = data.resolve("supplied-bank-raise-feedback-report.json.gz");
        var result =
                SixMaxRootHandBankRaiseFeedback.replay(
                        request,
                        data.resolve("supplied-root-hand-bank-report.json.gz"),
                        data.resolve("supplied-bank-raise-sequence-report.json.gz"),
                        report);
        assertEquals(SixMaxRootHandBankRaiseFeedback.readReport(report), result.report());
        assertTrue(result.report().qualifiedForOfflineDrill());
        assertFalse(result.report().trainerAdmission());
        var qualified = result.qualifiedFeedback().orElseThrow();
        assertEquals(SixMaxHeadsUpPreflopGame.hash(result.report()), qualified.reportHash());
        assertEquals(SixMaxRootHandBankRaiseFeedback.SOURCE_HASH, qualified.sourceMinimumHash());
        assertEquals(
                List.of(0, 2),
                result.report().assessments().stream()
                        .map(SixMaxRootHandBankRaiseFeedback.Assessment::sourceQuestionIndex)
                        .toList());
    }
}
