package de.appfabrik.pdfbatch.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.util.UUID;

public final class JobWorker {
    private final JobRepository repository;
    private final JobWorkspace workspace;
    private final DocumentEngine documentEngine;
    private final DocumentLimits limits;
    private final Clock clock;

    public JobWorker(
            JobRepository repository,
            JobWorkspace workspace,
            DocumentEngine documentEngine,
            DocumentLimits limits,
            Clock clock) {
        this.repository = repository;
        this.workspace = workspace;
        this.documentEngine = documentEngine;
        this.limits = limits;
        this.clock = clock;
    }

    public void process(UUID id) {
        BatchJob job = repository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (job.status() == JobStatus.CANCELLING) {
            workspace.deleteJob(id);
            job.cancel(clock.instant());
            repository.save(job);
            return;
        }

        try {
            job.startProcessing(clock.instant());
            repository.save(job);
            try (InputStream pdfInput = workspace.openTemplate(id);
                    InputStream csvInput = workspace.openCsv(id);
                    OutputStream zipOutput = workspace.createResult(id)) {
                BatchJob workingJob = job;
                ProcessingSummary summary = documentEngine.process(
                        pdfInput,
                        csvInput,
                        job.csvDelimiter(),
                        job.configuration(),
                        limits,
                        zipOutput,
                        () -> repository.isCancellationRequested(id),
                        new ProcessingListener() {
                            @Override
                            public void rowProcessed(int processed, int successful, int failed) {
                                if (repository.isCancellationRequested(id)) {
                                    throw new JobCancelledException();
                                }
                                workingJob.updateProgress(
                                        processed, successful, failed, clock.instant());
                                repository.save(workingJob);
                            }

                            @Override
                            public void packagingStarted() {
                                if (repository.isCancellationRequested(id)) {
                                    throw new JobCancelledException();
                                }
                                workingJob.startPackaging(clock.instant());
                                repository.save(workingJob);
                            }
                        });
                if (repository.isCancellationRequested(id)) {
                    throw new JobCancelledException();
                }
                job.complete(summary, clock.instant());
                repository.save(job);
            } catch (java.io.IOException exception) {
                throw new PdfBatchException(
                        "STORAGE_IO_FAILED", "Could not process local job files", exception);
            }
        } catch (JobCancelledException exception) {
            workspace.deleteJob(id);
            BatchJob latest = repository.findById(id).orElse(job);
            if (latest.status() != JobStatus.CANCELLING) {
                latest.requestCancellation(clock.instant());
            }
            latest.cancel(clock.instant());
            repository.save(latest);
        } catch (RuntimeException exception) {
            workspace.deleteJob(id);
            BatchJob latest = repository.findById(id).orElse(job);
            if (latest.status() == JobStatus.CANCELLING) {
                latest.cancel(clock.instant());
                repository.save(latest);
            } else if (!latest.status().isTerminal()) {
                latest.fail(
                        exception instanceof PdfBatchException pdfBatchException
                                ? pdfBatchException.code()
                                : "PROCESSING_FAILED",
                        "Document processing failed",
                        clock.instant());
                repository.save(latest);
            }
        }
    }
}
