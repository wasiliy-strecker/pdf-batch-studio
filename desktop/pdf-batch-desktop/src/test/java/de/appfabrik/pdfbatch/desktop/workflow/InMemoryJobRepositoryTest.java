package de.appfabrik.pdfbatch.desktop.workflow;

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

class InMemoryJobRepositoryTest {
    private final InMemoryJobRepository repository = new InMemoryJobRepository();

    @Test
    void storesDefensiveCopiesOfMutableAggregates() {
        BatchJob job = draft();
        repository.save(job);

        job.configure(configuration(), Instant.parse("2026-01-01T00:00:01Z"));

        assertThat(repository.findById(job.id()).orElseThrow().status()).isEqualTo(JobStatus.DRAFT);
    }

    @Test
    void doesNotOverwriteCancellationWithAStaleWorkerCopy() {
        BatchJob job = draft();
        job.configure(configuration(), Instant.parse("2026-01-01T00:00:01Z"));
        job.queue(Instant.parse("2026-01-01T00:00:02Z"));
        repository.save(job);

        BatchJob staleWorker = repository.findById(job.id()).orElseThrow();
        BatchJob cancelling = repository.findById(job.id()).orElseThrow();
        cancelling.requestCancellation(Instant.parse("2026-01-01T00:00:03Z"));
        repository.save(cancelling);

        staleWorker.startProcessing(Instant.parse("2026-01-01T00:00:04Z"));
        repository.save(staleWorker);

        assertThat(repository.findById(job.id()).orElseThrow().status())
                .isEqualTo(JobStatus.CANCELLING);
    }

    private static BatchJob draft() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return BatchJob.draft(
                UUID.randomUUID(),
                new UploadInspection(
                        new PdfInspection(1, List.of(new PdfFieldInfo("fullName", "text", true))),
                        new CsvInspection(',', List.of("name"), 1)),
                now,
                now.plusSeconds(3_600));
    }

    private static JobConfiguration configuration() {
        return new JobConfiguration(List.of(new FieldMapping("fullName", "name")), "{name}.pdf");
    }
}
