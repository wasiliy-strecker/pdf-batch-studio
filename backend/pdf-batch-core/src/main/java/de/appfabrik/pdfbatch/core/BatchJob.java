package de.appfabrik.pdfbatch.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class BatchJob {
    private final UUID id;
    private JobStatus status;
    private final int pageCount;
    private final List<PdfFieldInfo> pdfFields;
    private final char csvDelimiter;
    private final List<String> csvHeaders;
    private final int totalRows;
    private List<FieldMapping> mappings;
    private String filenamePattern;
    private int processedRows;
    private int successfulRows;
    private int failedRows;
    private String failureCode;
    private String failureMessage;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Instant expiresAt;

    private BatchJob(
            UUID id,
            JobStatus status,
            int pageCount,
            List<PdfFieldInfo> pdfFields,
            char csvDelimiter,
            List<String> csvHeaders,
            int totalRows,
            List<FieldMapping> mappings,
            String filenamePattern,
            int processedRows,
            int successfulRows,
            int failedRows,
            String failureCode,
            String failureMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id);
        this.status = Objects.requireNonNull(status);
        this.pageCount = pageCount;
        this.pdfFields = List.copyOf(pdfFields);
        this.csvDelimiter = csvDelimiter;
        this.csvHeaders = List.copyOf(csvHeaders);
        this.totalRows = totalRows;
        this.mappings = List.copyOf(mappings);
        this.filenamePattern = filenamePattern;
        this.processedRows = processedRows;
        this.successfulRows = successfulRows;
        this.failedRows = failedRows;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public static BatchJob draft(UUID id, UploadInspection inspection, Instant now, Instant expiresAt) {
        return new BatchJob(
                id,
                JobStatus.DRAFT,
                inspection.pdf().pageCount(),
                inspection.pdf().fields(),
                inspection.csv().delimiter(),
                inspection.csv().headers(),
                inspection.csv().rowCount(),
                List.of(),
                JobConfiguration.DEFAULT_FILENAME_PATTERN,
                0,
                0,
                0,
                null,
                null,
                now,
                now,
                expiresAt);
    }

    public static BatchJob restore(
            UUID id,
            JobStatus status,
            int pageCount,
            List<PdfFieldInfo> pdfFields,
            char csvDelimiter,
            List<String> csvHeaders,
            int totalRows,
            List<FieldMapping> mappings,
            String filenamePattern,
            int processedRows,
            int successfulRows,
            int failedRows,
            String failureCode,
            String failureMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt) {
        return new BatchJob(
                id,
                status,
                pageCount,
                pdfFields,
                csvDelimiter,
                csvHeaders,
                totalRows,
                mappings,
                filenamePattern,
                processedRows,
                successfulRows,
                failedRows,
                failureCode,
                failureMessage,
                createdAt,
                updatedAt,
                expiresAt);
    }

    public void configure(JobConfiguration configuration, Instant now) {
        if (status != JobStatus.DRAFT && status != JobStatus.READY) {
            throw new InvalidJobStateException(status, "configure");
        }
        mappings = configuration.mappings();
        filenamePattern = configuration.filenamePattern();
        transitionTo(JobStatus.READY, now);
    }

    public void queue(Instant now) {
        requireStatus(JobStatus.READY, "start processing");
        transitionTo(JobStatus.QUEUED, now);
    }

    public void startProcessing(Instant now) {
        requireStatus(JobStatus.QUEUED, "begin processing");
        transitionTo(JobStatus.PROCESSING, now);
    }

    public void updateProgress(int processed, int successful, int failed, Instant now) {
        if (status != JobStatus.PROCESSING) {
            throw new InvalidJobStateException(status, "update progress");
        }
        if (processed < processedRows || successful < successfulRows || failed < failedRows) {
            throw new IllegalArgumentException("Job progress must be monotonic");
        }
        if (processed != successful + failed || processed > totalRows) {
            throw new IllegalArgumentException("Invalid job progress counters");
        }
        processedRows = processed;
        successfulRows = successful;
        failedRows = failed;
        updatedAt = now;
    }

    public void startPackaging(Instant now) {
        requireStatus(JobStatus.PROCESSING, "package results");
        transitionTo(JobStatus.PACKAGING, now);
    }

    public void complete(ProcessingSummary summary, Instant now) {
        requireStatus(JobStatus.PACKAGING, "complete");
        processedRows = summary.processedRows();
        successfulRows = summary.successfulRows();
        failedRows = summary.failedRows();
        transitionTo(
                failedRows == 0 ? JobStatus.COMPLETED : JobStatus.COMPLETED_WITH_ERRORS, now);
    }

    public void requestCancellation(Instant now) {
        if (!status.isActive()) {
            throw new InvalidJobStateException(status, "cancel");
        }
        if (status != JobStatus.CANCELLING) {
            transitionTo(JobStatus.CANCELLING, now);
        }
    }

    public void cancel(Instant now) {
        if (status != JobStatus.CANCELLING && status != JobStatus.QUEUED) {
            throw new InvalidJobStateException(status, "mark cancelled");
        }
        transitionTo(JobStatus.CANCELLED, now);
    }

    public void fail(String code, String message, Instant now) {
        if (status.isTerminal()) {
            throw new InvalidJobStateException(status, "mark failed");
        }
        failureCode = code;
        failureMessage = message;
        transitionTo(JobStatus.FAILED, now);
    }

    public void expire(Instant now) {
        if (status.isActive()) {
            throw new InvalidJobStateException(status, "expire");
        }
        transitionTo(JobStatus.EXPIRED, now);
    }

    private void requireStatus(JobStatus expected, String action) {
        if (status != expected) {
            throw new InvalidJobStateException(status, action);
        }
    }

    private void transitionTo(JobStatus next, Instant now) {
        status = next;
        updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public JobStatus status() {
        return status;
    }

    public int pageCount() {
        return pageCount;
    }

    public List<PdfFieldInfo> pdfFields() {
        return pdfFields;
    }

    public char csvDelimiter() {
        return csvDelimiter;
    }

    public List<String> csvHeaders() {
        return csvHeaders;
    }

    public int totalRows() {
        return totalRows;
    }

    public List<FieldMapping> mappings() {
        return mappings;
    }

    public String filenamePattern() {
        return filenamePattern;
    }

    public int processedRows() {
        return processedRows;
    }

    public int successfulRows() {
        return successfulRows;
    }

    public int failedRows() {
        return failedRows;
    }

    public String failureCode() {
        return failureCode;
    }

    public String failureMessage() {
        return failureMessage;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public JobConfiguration configuration() {
        return new JobConfiguration(mappings, filenamePattern);
    }
}
