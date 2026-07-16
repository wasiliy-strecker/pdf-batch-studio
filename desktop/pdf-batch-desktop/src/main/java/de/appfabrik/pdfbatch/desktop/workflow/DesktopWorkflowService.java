package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.DocumentEngine;
import de.appfabrik.pdfbatch.core.DocumentLimits;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.InvalidJobStateException;
import de.appfabrik.pdfbatch.core.JobApplicationService;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.JobWorker;
import de.appfabrik.pdfbatch.core.PdfBatchException;
import de.appfabrik.pdfbatch.document.LocalJobWorkspace;
import de.appfabrik.pdfbatch.document.PdfCsvDocumentEngine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public final class DesktopWorkflowService implements DesktopWorkflow {
    public static final DocumentLimits COMMUNITY_LIMITS =
            new DocumentLimits(20_000_000, 5_000_000, 25, 100, 500, 2_000);

    private final Path workspaceRoot;
    private final JobApplicationService jobs;
    private final DesktopJobDispatcher dispatcher;
    private UUID currentJobId;
    private boolean closed;

    public static DesktopWorkflowService create(Path workspaceRoot) {
        InMemoryJobRepository repository = new InMemoryJobRepository();
        LocalJobWorkspace workspace = new LocalJobWorkspace(workspaceRoot);
        DocumentEngine documentEngine = new PdfCsvDocumentEngine();
        Clock clock = Clock.systemUTC();
        JobWorker worker = new JobWorker(
                repository, workspace, documentEngine, COMMUNITY_LIMITS, clock);
        DesktopJobDispatcher dispatcher = new DesktopJobDispatcher(worker);
        JobApplicationService jobs = new JobApplicationService(
                repository,
                workspace,
                documentEngine,
                dispatcher,
                COMMUNITY_LIMITS,
                clock,
                Duration.ofHours(1));
        return new DesktopWorkflowService(workspaceRoot, jobs, dispatcher);
    }

    DesktopWorkflowService(
            Path workspaceRoot, JobApplicationService jobs, DesktopJobDispatcher dispatcher) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.jobs = jobs;
        this.dispatcher = dispatcher;
    }

    @Override
    public synchronized BatchJob inspect(Path pdf, Path csv) {
        requireOpen();
        discardCurrentJob();
        try (InputStream pdfInput = Files.newInputStream(pdf);
                InputStream csvInput = Files.newInputStream(csv)) {
            BatchJob job = jobs.create(pdfInput, csvInput);
            currentJobId = job.id();
            return job;
        } catch (IOException exception) {
            throw new PdfBatchException(
                    "DESKTOP_FILE_READ_FAILED", "Could not read the selected PDF or CSV", exception);
        }
    }

    @Override
    public synchronized BatchJob configure(
            List<FieldMapping> mappings, String filenamePattern) {
        requireOpen();
        return jobs.configure(requireCurrentJob(), new JobConfiguration(mappings, filenamePattern));
    }

    @Override
    public synchronized byte[] preview() {
        requireOpen();
        return jobs.preview(requireCurrentJob());
    }

    @Override
    public synchronized BatchJob start() {
        requireOpen();
        return jobs.start(requireCurrentJob());
    }

    @Override
    public synchronized BatchJob status() {
        requireOpen();
        return jobs.get(requireCurrentJob());
    }

    @Override
    public synchronized BatchJob cancel() {
        requireOpen();
        return jobs.cancel(requireCurrentJob());
    }

    @Override
    public synchronized BatchJob exportResult(Path destination) {
        requireOpen();
        UUID jobId = requireCurrentJob();
        BatchJob completed = jobs.get(jobId);
        if (completed.status() != JobStatus.COMPLETED
                && completed.status() != JobStatus.COMPLETED_WITH_ERRORS) {
            throw new InvalidJobStateException(completed.status(), "export the result");
        }

        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new PdfBatchException(
                    "INVALID_EXPORT_PATH", "The selected ZIP destination has no parent directory");
        }

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".pdf-batch-", ".zip");
            try (InputStream result = jobs.result(jobId)) {
                Files.copy(result, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            jobs.delete(jobId);
            currentJobId = null;
            return completed;
        } catch (IOException exception) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Keep the original export error.
                }
            }
            throw new PdfBatchException(
                    "RESULT_EXPORT_FAILED", "Could not save the result ZIP", exception);
        }
    }

    @Override
    public synchronized void reset() {
        requireOpen();
        discardCurrentJob();
    }

    @Override
    public void close() {
        UUID jobId;
        synchronized (this) {
            if (closed) {
                return;
            }
            jobId = currentJobId;
            if (jobId != null) {
                BatchJob job = jobs.get(jobId);
                if (job.status().isActive()) {
                    jobs.cancel(jobId);
                }
            }
            closed = true;
        }

        dispatcher.close();

        if (jobId != null) {
            try {
                BatchJob job = jobs.get(jobId);
                if (!job.status().isActive()) {
                    jobs.delete(jobId);
                }
            } catch (RuntimeException ignored) {
                // Application shutdown must continue even if temporary cleanup fails.
            }
        }
        try {
            Files.deleteIfExists(workspaceRoot);
        } catch (IOException ignored) {
            // The operating system can clean a remaining empty temporary directory.
        }
    }

    private void discardCurrentJob() {
        if (currentJobId == null) {
            return;
        }
        BatchJob current = jobs.get(currentJobId);
        if (current.status().isActive()) {
            throw new InvalidJobStateException(current.status(), "replace the active desktop job");
        }
        jobs.delete(currentJobId);
        currentJobId = null;
    }

    private UUID requireCurrentJob() {
        if (currentJobId == null) {
            throw new PdfBatchException("DESKTOP_JOB_REQUIRED", "Select and inspect a PDF and CSV first");
        }
        return currentJobId;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Desktop workflow is closed");
        }
    }
}
