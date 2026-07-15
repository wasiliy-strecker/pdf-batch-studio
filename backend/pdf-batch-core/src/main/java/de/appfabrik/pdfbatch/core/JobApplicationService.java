package de.appfabrik.pdfbatch.core;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JobApplicationService {
    private final JobRepository repository;
    private final JobWorkspace workspace;
    private final DocumentEngine documentEngine;
    private final JobDispatchPort dispatcher;
    private final DocumentLimits limits;
    private final Clock clock;
    private final Duration retention;

    public JobApplicationService(
            JobRepository repository,
            JobWorkspace workspace,
            DocumentEngine documentEngine,
            JobDispatchPort dispatcher,
            DocumentLimits limits,
            Clock clock,
            Duration retention) {
        this.repository = repository;
        this.workspace = workspace;
        this.documentEngine = documentEngine;
        this.dispatcher = dispatcher;
        this.limits = limits;
        this.clock = clock;
        this.retention = retention;
    }

    public BatchJob create(InputStream template, InputStream csv) {
        UUID id = UUID.randomUUID();
        try {
            workspace.storeTemplate(id, template, limits.maxPdfBytes());
            workspace.storeCsv(id, csv, limits.maxCsvBytes());
            UploadInspection inspection;
            try (InputStream pdfInput = workspace.openTemplate(id);
                    InputStream csvInput = workspace.openCsv(id)) {
                inspection = documentEngine.inspect(pdfInput, csvInput, limits);
            } catch (java.io.IOException exception) {
                throw new PdfBatchException(
                        "STORAGE_READ_FAILED", "Could not read the uploaded documents", exception);
            }
            Instant now = clock.instant();
            return repository.save(BatchJob.draft(id, inspection, now, now.plus(retention)));
        } catch (RuntimeException exception) {
            workspace.deleteJob(id);
            throw exception;
        }
    }

    public BatchJob configure(UUID id, JobConfiguration configuration) {
        BatchJob job = get(id);
        JobConfigurationValidator.validate(job, configuration);
        job.configure(configuration, clock.instant());
        return repository.save(job);
    }

    public byte[] preview(UUID id) {
        BatchJob job = get(id);
        if (job.status() != JobStatus.READY) {
            throw new InvalidJobStateException(job.status(), "generate a preview");
        }
        try (InputStream pdfInput = workspace.openTemplate(id);
                InputStream csvInput = workspace.openCsv(id)) {
            return documentEngine.preview(
                    pdfInput,
                    csvInput,
                    job.csvDelimiter(),
                    job.configuration(),
                    limits);
        } catch (java.io.IOException exception) {
            throw new PdfBatchException(
                    "STORAGE_READ_FAILED", "Could not read the uploaded documents", exception);
        }
    }

    public synchronized BatchJob start(UUID id) {
        BatchJob job = get(id);
        if (job.status() != JobStatus.READY) {
            throw new InvalidJobStateException(job.status(), "start processing");
        }
        if (repository.countActiveJobs() > 0) {
            throw new JobCapacityException();
        }
        job.queue(clock.instant());
        BatchJob queued = repository.save(job);
        try {
            dispatcher.dispatch(id);
        } catch (RuntimeException exception) {
            queued.fail("QUEUE_REJECTED", "The local processing queue is full", clock.instant());
            repository.save(queued);
            workspace.deleteJob(id);
            throw exception;
        }
        return queued;
    }

    public BatchJob cancel(UUID id) {
        BatchJob job = get(id);
        job.requestCancellation(clock.instant());
        return repository.save(job);
    }

    public BatchJob get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    public InputStream result(UUID id) {
        BatchJob job = get(id);
        if (job.status() != JobStatus.COMPLETED
                && job.status() != JobStatus.COMPLETED_WITH_ERRORS) {
            throw new InvalidJobStateException(job.status(), "download the result");
        }
        return workspace.openResult(id);
    }

    public void delete(UUID id) {
        BatchJob job = get(id);
        if (job.status().isActive()) {
            throw new InvalidJobStateException(job.status(), "delete; cancel the job first");
        }
        workspace.deleteJob(id);
        repository.deleteById(id);
    }

    public List<UUID> cleanupExpired() {
        Instant now = clock.instant();
        List<BatchJob> jobs = repository.findExpiredBefore(now);
        return jobs.stream()
                .filter(job -> !job.status().isActive())
                .map(job -> {
                    job.expire(now);
                    repository.save(job);
                    workspace.deleteJob(job.id());
                    repository.deleteById(job.id());
                    return job.id();
                })
                .toList();
    }
}
