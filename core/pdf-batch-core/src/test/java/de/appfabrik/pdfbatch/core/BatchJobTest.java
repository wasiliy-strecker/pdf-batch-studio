package de.appfabrik.pdfbatch.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchJobTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void followsTheHappyPathStateTransitions() {
        BatchJob job = newDraft();

        job.configure(configuration(), NOW.plusSeconds(1));
        job.queue(NOW.plusSeconds(2));
        job.startProcessing(NOW.plusSeconds(3));
        job.updateProgress(1, 1, 0, NOW.plusSeconds(4));
        job.startPackaging(NOW.plusSeconds(5));
        job.complete(new ProcessingSummary(2, 2, 0), NOW.plusSeconds(6));

        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.processedRows()).isEqualTo(2);
        assertThat(job.successfulRows()).isEqualTo(2);
        assertThat(job.failedRows()).isZero();
    }

    @Test
    void completesWithErrorsWhenAtLeastOneRowFailed() {
        BatchJob job = newDraft();
        job.configure(configuration(), NOW);
        job.queue(NOW);
        job.startProcessing(NOW);
        job.startPackaging(NOW);

        job.complete(new ProcessingSummary(2, 1, 1), NOW);

        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED_WITH_ERRORS);
    }

    @Test
    void rejectsInvalidTransitionsAndNonMonotonicProgress() {
        BatchJob job = newDraft();

        assertThatThrownBy(() -> job.queue(NOW))
                .isInstanceOf(InvalidJobStateException.class);

        job.configure(configuration(), NOW);
        job.queue(NOW);
        job.startProcessing(NOW);
        job.updateProgress(1, 1, 0, NOW);

        assertThatThrownBy(() -> job.updateProgress(0, 0, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monotonic");
    }

    @Test
    void supportsCancellationFromAnActiveState() {
        BatchJob job = newDraft();
        job.configure(configuration(), NOW);
        job.queue(NOW);

        job.requestCancellation(NOW.plusSeconds(1));
        job.cancel(NOW.plusSeconds(2));

        assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
    }

    private static BatchJob newDraft() {
        UploadInspection inspection = new UploadInspection(
                new PdfInspection(1, List.of(new PdfFieldInfo("name", "PDTextField", true))),
                new CsvInspection(',', List.of("name"), 2));
        return BatchJob.draft(UUID.randomUUID(), inspection, NOW, NOW.plusSeconds(3600));
    }

    private static JobConfiguration configuration() {
        return new JobConfiguration(List.of(new FieldMapping("name", "name")), "{name}.pdf");
    }
}
