package de.appfabrik.pdfbatch.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void cleanupDeletesExpiredMetadataAndFiles() {
        FakeRepository repository = new FakeRepository();
        FakeWorkspace workspace = new FakeWorkspace();
        BatchJob expired = BatchJob.draft(
                UUID.randomUUID(), inspection(), NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        repository.save(expired);
        JobApplicationService service = service(repository, workspace);

        List<UUID> deleted = service.cleanupExpired();

        assertThat(deleted).containsExactly(expired.id());
        assertThat(repository.findById(expired.id())).isEmpty();
        assertThat(workspace.deletedJobs).containsExactly(expired.id());
    }

    @Test
    void workerFinalizesCancellationBeforeOpeningFiles() {
        FakeRepository repository = new FakeRepository();
        FakeWorkspace workspace = new FakeWorkspace();
        BatchJob job = BatchJob.draft(UUID.randomUUID(), inspection(), NOW, NOW.plusSeconds(3600));
        job.configure(configuration(), NOW);
        job.queue(NOW);
        job.requestCancellation(NOW);
        repository.save(job);
        JobWorker worker = new JobWorker(
                repository,
                workspace,
                new FakeDocumentEngine(),
                limits(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        worker.process(job.id());

        assertThat(repository.findById(job.id()).orElseThrow().status())
                .isEqualTo(JobStatus.CANCELLED);
        assertThat(workspace.openCount).isZero();
        assertThat(workspace.deletedJobs).containsExactly(job.id());
    }

    private static JobApplicationService service(
            FakeRepository repository, FakeWorkspace workspace) {
        return new JobApplicationService(
                repository,
                workspace,
                new FakeDocumentEngine(),
                id -> {},
                limits(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(1));
    }

    private static DocumentLimits limits() {
        return new DocumentLimits(1000, 1000, 25, 10, 10, 100);
    }

    private static UploadInspection inspection() {
        return new UploadInspection(
                new PdfInspection(1, List.of(new PdfFieldInfo("name", "PDTextField", true))),
                new CsvInspection(',', List.of("name"), 1));
    }

    private static JobConfiguration configuration() {
        return new JobConfiguration(List.of(new FieldMapping("name", "name")), "{name}.pdf");
    }

    private static final class FakeRepository implements JobRepository {
        private final Map<UUID, BatchJob> jobs = new LinkedHashMap<>();

        @Override
        public BatchJob save(BatchJob job) {
            jobs.put(job.id(), job);
            return job;
        }

        @Override
        public Optional<BatchJob> findById(UUID id) {
            return Optional.ofNullable(jobs.get(id));
        }

        @Override
        public void deleteById(UUID id) {
            jobs.remove(id);
        }

        @Override
        public long countActiveJobs() {
            return jobs.values().stream().filter(job -> job.status().isActive()).count();
        }

        @Override
        public boolean isCancellationRequested(UUID id) {
            return findById(id).map(job -> job.status() == JobStatus.CANCELLING).orElse(false);
        }

        @Override
        public List<BatchJob> findExpiredBefore(Instant cutoff) {
            return jobs.values().stream()
                    .filter(job -> !job.expiresAt().isAfter(cutoff))
                    .toList();
        }
    }

    private static final class FakeWorkspace implements JobWorkspace {
        private final List<UUID> deletedJobs = new ArrayList<>();
        private int openCount;

        @Override
        public void storeTemplate(UUID jobId, InputStream input, long maxBytes) {}

        @Override
        public void storeCsv(UUID jobId, InputStream input, long maxBytes) {}

        @Override
        public InputStream openTemplate(UUID jobId) {
            openCount++;
            return new ByteArrayInputStream(new byte[] {1});
        }

        @Override
        public InputStream openCsv(UUID jobId) {
            openCount++;
            return new ByteArrayInputStream(new byte[] {1});
        }

        @Override
        public InputStream openResult(UUID jobId) {
            openCount++;
            return new ByteArrayInputStream(new byte[] {1});
        }

        @Override
        public OutputStream createResult(UUID jobId) {
            openCount++;
            return new ByteArrayOutputStream();
        }

        @Override
        public boolean resultExists(UUID jobId) {
            return false;
        }

        @Override
        public void deleteResult(UUID jobId) {}

        @Override
        public void deleteJob(UUID jobId) {
            deletedJobs.add(jobId);
        }
    }

    private static final class FakeDocumentEngine implements DocumentEngine {
        @Override
        public UploadInspection inspect(InputStream pdf, InputStream csv, DocumentLimits limits) {
            return inspection();
        }

        @Override
        public byte[] preview(
                InputStream pdf,
                InputStream csv,
                char delimiter,
                JobConfiguration configuration,
                DocumentLimits limits) {
            return new byte[] {1};
        }

        @Override
        public ProcessingSummary process(
                InputStream pdf,
                InputStream csv,
                char delimiter,
                JobConfiguration configuration,
                DocumentLimits limits,
                OutputStream zipOutput,
                java.util.function.BooleanSupplier cancellationRequested,
                ProcessingListener listener) {
            return new ProcessingSummary(1, 1, 0);
        }
    }
}
