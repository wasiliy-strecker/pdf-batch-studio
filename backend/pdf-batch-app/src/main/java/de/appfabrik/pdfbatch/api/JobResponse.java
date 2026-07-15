package de.appfabrik.pdfbatch.api;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.PdfFieldInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobResponse(
        UUID id,
        JobStatus status,
        int pageCount,
        List<PdfFieldInfo> pdfFields,
        String csvDelimiter,
        List<String> csvHeaders,
        int totalRows,
        List<FieldMapping> mappings,
        String filenamePattern,
        int processedRows,
        int successfulRows,
        int failedRows,
        int progressPercent,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        boolean previewAvailable,
        boolean resultAvailable) {
    public static JobResponse from(BatchJob job) {
        int progress = job.totalRows() == 0
                ? 0
                : Math.min(100, (job.processedRows() * 100) / job.totalRows());
        boolean readyForPreview = job.status() == JobStatus.READY;
        boolean resultAvailable = job.status() == JobStatus.COMPLETED
                || job.status() == JobStatus.COMPLETED_WITH_ERRORS;
        return new JobResponse(
                job.id(),
                job.status(),
                job.pageCount(),
                job.pdfFields(),
                switch (job.csvDelimiter()) {
                    case ',' -> "comma";
                    case ';' -> "semicolon";
                    case '\t' -> "tab";
                    default -> Character.toString(job.csvDelimiter());
                },
                job.csvHeaders(),
                job.totalRows(),
                job.mappings(),
                job.filenamePattern(),
                job.processedRows(),
                job.successfulRows(),
                job.failedRows(),
                progress,
                job.failureCode(),
                job.failureMessage(),
                job.createdAt(),
                job.updatedAt(),
                job.expiresAt(),
                readyForPreview,
                resultAvailable);
    }
}
