package de.appfabrik.pdfbatch.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.CsvInspection;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.PdfFieldInfo;
import de.appfabrik.pdfbatch.core.PdfInspection;
import de.appfabrik.pdfbatch.core.UploadInspection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchJobEntityTest {
    @Test
    void doesNotOverwriteACommittedCancellationWithStaleWorkerProgress() {
        BatchJob cancelling = readyJob();
        cancelling.queue(now());
        cancelling.startProcessing(now());
        cancelling.requestCancellation(now());
        BatchJobEntity entity = BatchJobEntity.fromDomain(cancelling);

        BatchJob staleWorker = readyJob(cancelling.id());
        staleWorker.queue(now());
        staleWorker.startProcessing(now());
        staleWorker.updateProgress(1, 1, 0, now());
        entity.updateFrom(staleWorker);

        assertThat(entity.toDomain().status()).isEqualTo(JobStatus.CANCELLING);
        assertThat(entity.toDomain().processedRows()).isZero();
    }

    @Test
    void allowsTheWorkerToFinalizeCancellation() {
        BatchJob cancelling = readyJob();
        cancelling.queue(now());
        cancelling.requestCancellation(now());
        BatchJobEntity entity = BatchJobEntity.fromDomain(cancelling);
        cancelling.cancel(now());

        entity.updateFrom(cancelling);

        assertThat(entity.toDomain().status()).isEqualTo(JobStatus.CANCELLED);
    }

    private static BatchJob readyJob() {
        return readyJob(UUID.randomUUID());
    }

    private static BatchJob readyJob(UUID id) {
        BatchJob job = BatchJob.draft(
                id,
                new UploadInspection(
                        new PdfInspection(
                                1, List.of(new PdfFieldInfo("name", "PDTextField", true))),
                        new CsvInspection(',', List.of("name"), 2)),
                now(),
                now().plusSeconds(3600));
        job.configure(
                new JobConfiguration(List.of(new FieldMapping("name", "name")), "{name}.pdf"),
                now());
        return job;
    }

    private static Instant now() {
        return Instant.parse("2026-01-01T10:00:00Z");
    }
}
