package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import de.appfabrik.pdfbatch.core.PdfBatchException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;

public final class DesktopViewModel implements AutoCloseable {
    private final DesktopWorkflow workflow;
    private final PdfPreviewRenderer previewRenderer;
    private BatchJob job;
    private Path pdfPath;
    private Path csvPath;
    private Path outputPath;
    private Path exportedPath;
    private byte[] previewPng;
    private boolean folderExport;
    private String errorCode;
    private String errorMessage;

    public DesktopViewModel(DesktopWorkflow workflow) {
        this(workflow, new PdfPreviewRenderer());
    }

    DesktopViewModel(DesktopWorkflow workflow, PdfPreviewRenderer previewRenderer) {
        this.workflow = workflow;
        this.previewRenderer = previewRenderer;
    }

    public synchronized DesktopSnapshot inspect(Path pdf, Path csv) {
        clearError();
        if (pdf == null || !Files.isRegularFile(pdf)) {
            throw new PdfBatchException("DESKTOP_PDF_REQUIRED", "Select a readable PDF template");
        }
        if (csv == null || !Files.isRegularFile(csv)) {
            throw new PdfBatchException("DESKTOP_CSV_REQUIRED", "Select a readable CSV file");
        }
        job = workflow.inspect(pdf, csv);
        pdfPath = pdf.toAbsolutePath().normalize();
        csvPath = csv.toAbsolutePath().normalize();
        outputPath = null;
        exportedPath = null;
        previewPng = null;
        return snapshot();
    }

    public synchronized DesktopSnapshot preview(
            List<FieldMapping> mappings, String filenamePattern) {
        return preview(mappings, filenamePattern, 0, 0);
    }

    public synchronized DesktopSnapshot preview(
            List<FieldMapping> mappings, String filenamePattern, int row, int page) {
        clearError();
        job = workflow.configure(mappings, filenamePattern);
        previewPng = previewRenderer.render(workflow.preview(row), page);
        return snapshot();
    }

    public synchronized DesktopSnapshot start(Path destination, boolean folder) {
        folderExport = folder;
        return start(destination);
    }

    public synchronized void passwords(String templatePassword, String outputPassword) {
        workflow.passwords(templatePassword, outputPassword);
    }

    public synchronized void project(String id) {
        workflow.project(id);
    }

    public synchronized List<de.appfabrik.pdfbatch.document.PdfCsvDocumentEngine.ValidationIssue>
            preflight(List<FieldMapping> mappings, String pattern) {
        job = workflow.configure(mappings, pattern);
        return workflow.preflight();
    }

    public synchronized DesktopSnapshot start(Path destination) {
        clearError();
        if (previewPng == null) {
            throw new PdfBatchException(
                    "DESKTOP_PREVIEW_REQUIRED", "Generate a preview before starting the batch");
        }
        if (destination == null) {
            throw new PdfBatchException(
                    "DESKTOP_OUTPUT_REQUIRED", "Choose where to save the result ZIP");
        }
        outputPath = destination.toAbsolutePath().normalize();
        exportedPath = null;
        job = workflow.start();
        return snapshot();
    }

    public synchronized DesktopSnapshot refresh() {
        clearError();
        job = workflow.status();
        return snapshot();
    }

    public synchronized DesktopSnapshot cancel() {
        clearError();
        job = workflow.cancel();
        return snapshot();
    }

    public synchronized DesktopSnapshot exportResult(Path destination, boolean folder) {
        outputPath = destination.toAbsolutePath().normalize();
        folderExport = folder;
        return exportResult();
    }

    public synchronized DesktopSnapshot exportResult() {
        clearError();
        if (outputPath == null) {
            throw new PdfBatchException(
                    "DESKTOP_OUTPUT_REQUIRED", "Choose where to save the result ZIP");
        }
        job = workflow.exportResult(outputPath, folderExport);
        exportedPath = outputPath;
        return snapshot();
    }

    public synchronized DesktopSnapshot invalidatePreview() {
        previewPng = null;
        exportedPath = null;
        return snapshot();
    }

    public synchronized DesktopSnapshot reset() {
        workflow.reset();
        job = null;
        pdfPath = null;
        csvPath = null;
        outputPath = null;
        exportedPath = null;
        previewPng = null;
        clearError();
        return snapshot();
    }

    public synchronized DesktopSnapshot recordError(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof PdfBatchException pdfBatchException) {
            errorCode = pdfBatchException.code();
            errorMessage = pdfBatchException.getMessage();
        } else if (cause instanceof IllegalArgumentException illegalArgumentException) {
            errorCode = "DESKTOP_INPUT_INVALID";
            errorMessage = illegalArgumentException.getMessage();
        } else {
            errorCode = "DESKTOP_OPERATION_FAILED";
            errorMessage =
                    cause.getMessage() == null
                            ? "The desktop operation failed"
                            : cause.getMessage();
        }
        return snapshot();
    }

    public synchronized DesktopSnapshot recordError(String code, String message) {
        errorCode = code;
        errorMessage = message;
        return snapshot();
    }

    public synchronized DesktopSnapshot snapshot() {
        return new DesktopSnapshot(
                job == null ? null : job.status(),
                pdfPath,
                csvPath,
                outputPath,
                exportedPath,
                job == null ? 0 : job.pageCount(),
                job == null ? 0 : job.totalRows(),
                job == null ? 0 : job.processedRows(),
                job == null ? 0 : job.successfulRows(),
                job == null ? 0 : job.failedRows(),
                job == null ? "—" : delimiterName(job.csvDelimiter()),
                job == null ? List.of() : job.pdfFields(),
                job == null ? List.of() : job.csvHeaders(),
                job == null ? List.of() : job.mappings(),
                job == null ? JobConfiguration.DEFAULT_FILENAME_PATTERN : job.filenamePattern(),
                previewPng,
                errorCode,
                errorMessage);
    }

    @Override
    public synchronized void close() {
        workflow.close();
    }

    private void clearError() {
        errorCode = null;
        errorMessage = null;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String delimiterName(char delimiter) {
        return switch (delimiter) {
            case ',' -> "Comma";
            case ';' -> "Semicolon";
            case '\t' -> "Tab";
            default -> Character.toString(delimiter);
        };
    }
}
