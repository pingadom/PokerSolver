package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Parsing checks only: this class performs no solve or physical replay. */
class RootMinimumFeedbackParsingTest {
    @Test
    void neitherReportJsonNorWrappedReportCanMintOwnedQualification() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var report =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                Path.of(
                                        "../docs/data/supplied-root-minimum-feedback-report.json.gz"),
                                8 * 1024 * 1024),
                        SixMaxRootMinimumFeedback.Report.class);
        String text = SixMaxTextureStudy.json(report);
        for (String input : new String[] {text, "{\"report\":" + text + "}"}) {
            assertThrows(
                    Exception.class,
                    () -> mapper.readValue(input, SixMaxRootMinimumFeedback.Result.class));
            assertThrows(
                    Exception.class,
                    () ->
                            mapper.readValue(
                                    input, SixMaxRootMinimumFeedback.QualifiedFeedback.class));
        }
    }
}
