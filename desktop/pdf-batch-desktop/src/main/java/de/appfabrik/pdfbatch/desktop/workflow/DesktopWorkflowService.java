package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.DocumentLimits;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.InvalidJobStateException;
import de.appfabrik.pdfbatch.core.JobApplicationService;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.JobWorker;
import de.appfabrik.pdfbatch.core.PdfBatchException;
import de.appfabrik.pdfbatch.desktop.storage.StudioRepository;
import de.appfabrik.pdfbatch.document.LocalJobWorkspace;
import de.appfabrik.pdfbatch.document.PdfCsvDocumentEngine;

import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

public final class DesktopWorkflowService implements DesktopWorkflow {
    private final Path workspaceRoot;
    private final JobApplicationService jobs;
    private final DesktopJobDispatcher dispatcher;
    private final PdfCsvDocumentEngine engine;
    private final LocalJobWorkspace workspace;
    private final DocumentLimits limits;
    private StudioRepository history;
    private String projectId;
    private boolean historyFinished;
    private UUID currentJobId;
    private boolean closed;

    public static DesktopWorkflowService create(Path workspaceRoot) {
        return create(workspaceRoot, DesktopRuntimeSettings.fromEnvironment().documentLimits());
    }

    public static DesktopWorkflowService create(Path workspaceRoot, StudioRepository history) {
        DesktopWorkflowService service = create(workspaceRoot);
        service.history = history;
        return service;
    }

    static DesktopWorkflowService create(Path workspaceRoot, DocumentLimits limits) {
        InMemoryJobRepository repository = new InMemoryJobRepository();
        LocalJobWorkspace workspace = new LocalJobWorkspace(workspaceRoot);
        PdfCsvDocumentEngine documentEngine = new PdfCsvDocumentEngine(true);
        Clock clock = Clock.systemUTC();
        JobWorker worker = new JobWorker(repository, workspace, documentEngine, limits, clock);
        DesktopJobDispatcher dispatcher = new DesktopJobDispatcher(worker);
        JobApplicationService jobs =
                new JobApplicationService(
                        repository,
                        workspace,
                        documentEngine,
                        dispatcher,
                        limits,
                        clock,
                        Duration.ofHours(1),
                        1);
        return new DesktopWorkflowService(
                workspaceRoot, jobs, dispatcher, documentEngine, workspace, limits);
    }

    DesktopWorkflowService(
            Path workspaceRoot,
            JobApplicationService jobs,
            DesktopJobDispatcher dispatcher,
            PdfCsvDocumentEngine engine,
            LocalJobWorkspace workspace,
            DocumentLimits limits) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.jobs = jobs;
        this.dispatcher = dispatcher;
        this.engine = engine;
        this.workspace = workspace;
        this.limits = limits;
    }

    @Override
    public synchronized BatchJob inspect(Path pdf, Path csv) {
        requireOpen();
        discardCurrentJob();
        try (InputStream pdfInput = Files.newInputStream(pdf);
                InputStream csvInput = Files.newInputStream(csv)) {
            BatchJob job = jobs.create(pdfInput, csvInput);
            currentJobId = job.id();
            historyFinished = false;
            return job;
        } catch (IOException exception) {
            throw new PdfBatchException(
                    "DESKTOP_FILE_READ_FAILED",
                    "Could not read the selected PDF or CSV",
                    exception);
        }
    }

    @Override
    public synchronized BatchJob configure(List<FieldMapping> mappings, String filenamePattern) {
        requireOpen();
        return jobs.configure(requireCurrentJob(), new JobConfiguration(mappings, filenamePattern));
    }

    @Override
    public synchronized byte[] preview() {
        requireOpen();
        return jobs.preview(requireCurrentJob());
    }

    @Override
    public synchronized byte[] preview(int rowIndex) {
        requireOpen();
        return jobs.preview(requireCurrentJob(), rowIndex);
    }

    @Override
    public synchronized void passwords(String templatePassword, String outputPassword) {
        requireOpen();
        if (currentJobId != null && jobs.get(currentJobId).status().isActive()) {
            throw new IllegalStateException("Cannot change passwords during processing");
        }
        engine.passwords(templatePassword, outputPassword);
    }

    @Override
    public synchronized void project(String projectId) {
        this.projectId = projectId;
    }

    @Override
    public synchronized List<PdfCsvDocumentEngine.ValidationIssue> preflight() {
        BatchJob job = jobs.get(requireCurrentJob());
        try (var pdf = workspace.openTemplate(job.id());
                var csv = workspace.openCsv(job.id())) {
            return engine.preflight(
                    pdf,
                    csv,
                    job.csvDelimiter(),
                    job.configuration(),
                    limits,
                    () -> Thread.currentThread().isInterrupted());
        } catch (IOException exception) {
            throw new PdfBatchException("PREFLIGHT_FAILED", "Could not validate input", exception);
        }
    }

    @Override
    public synchronized BatchJob start() {
        requireOpen();
        UUID id = requireCurrentJob();
        if (history != null) history.begin(jobs.get(id), projectId);
        try {
            return jobs.start(id);
        } catch (RuntimeException exception) {
            if (history != null) history.finish(jobs.get(id), null);
            throw exception;
        }
    }

    @Override
    public synchronized BatchJob status() {
        requireOpen();
        BatchJob job = jobs.get(requireCurrentJob());
        if (history != null && job.status().isTerminal() && !historyFinished) {
            history.finish(job, null);
            if (job.status() == JobStatus.COMPLETED_WITH_ERRORS) saveErrors(job.id());
            historyFinished = true;
        }
        return job;
    }

    @Override
    public synchronized BatchJob cancel() {
        requireOpen();
        return jobs.cancel(requireCurrentJob());
    }

    @Override
    public synchronized BatchJob exportResult(Path destination) {
        return exportResult(destination, false);
    }

    @Override
    public synchronized BatchJob exportResult(Path destination, boolean folder) {
        requireOpen();
        BatchJob completed = status();
        if (completed.status() != JobStatus.COMPLETED
                && completed.status() != JobStatus.COMPLETED_WITH_ERRORS) {
            throw new InvalidJobStateException(completed.status(), "export the result");
        }
        Path target = destination.toAbsolutePath().normalize();
        Path temporary = null;
        try {
            if (Files.exists(target))
                throw new IOException("Destination already exists. Choose a new name");
            Files.createDirectories(target.getParent());
            if (folder) {
                temporary = Files.createTempDirectory(target.getParent(), ".pdf-batch-");
                try (var zip = new ZipInputStream(jobs.result(completed.id()))) {
                    for (var entry = zip.getNextEntry();
                            entry != null;
                            entry = zip.getNextEntry()) {
                        Path output = temporary.resolve(entry.getName()).normalize();
                        if (!output.startsWith(temporary) || entry.isDirectory())
                            throw new IOException("Invalid result entry");
                        Files.createDirectories(output.getParent());
                        Files.copy(zip, output);
                    }
                }
            } else {
                temporary = Files.createTempFile(target.getParent(), ".pdf-batch-", ".zip");
                try (InputStream result = jobs.result(completed.id())) {
                    Files.copy(result, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.move(temporary, target);
            temporary = null;
            if (history != null) history.finish(completed, target);
            jobs.delete(completed.id());
            currentJobId = null;
            return completed;
        } catch (IOException exception) {
            throw new PdfBatchException(
                    "RESULT_EXPORT_FAILED",
                    "Could not save result: " + exception.getMessage(),
                    exception);
        } finally {
            if (temporary != null) {
                try {
                    StudioRepository.deleteTree(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void saveErrors(UUID id) {
        try (var zip = new ZipInputStream(jobs.result(id))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("errors.csv")) {
                    var reader = new InputStreamReader(zip, StandardCharsets.UTF_8);
                    var parser =
                            CSVFormat.DEFAULT
                                    .builder()
                                    .setHeader()
                                    .setSkipHeaderRecord(true)
                                    .get()
                                    .parse(reader);
                    var errors = new java.util.ArrayList<PdfCsvDocumentEngine.ValidationIssue>();
                    for (var row : parser) {
                        errors.add(
                                new PdfCsvDocumentEngine.ValidationIssue(
                                        Long.parseLong(row.get("rowNumber")),
                                        row.get("code"),
                                        row.get("message")));
                    }
                    history.recordErrors(id.toString(), errors);
                    break;
                }
            }
        } catch (IOException exception) {
            throw new PdfBatchException("HISTORY_FAILED", "Could not save error report", exception);
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
        engine.passwords("", "");
        if (history != null && jobId != null) {
            BatchJob finished = jobs.get(jobId);
            if (finished.status().isTerminal()) history.finish(finished, null);
        }

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
            throw new PdfBatchException(
                    "DESKTOP_JOB_REQUIRED", "Select and inspect a PDF and CSV first");
        }
        return currentJobId;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Desktop workflow is closed");
        }
    }
}
