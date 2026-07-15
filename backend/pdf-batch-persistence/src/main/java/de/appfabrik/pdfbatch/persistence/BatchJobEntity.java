package de.appfabrik.pdfbatch.persistence;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.PdfFieldInfo;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "batch_jobs")
class BatchJobEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private JobStatus status;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @ElementCollection
    @CollectionTable(name = "job_pdf_fields", joinColumns = @JoinColumn(name = "job_id"))
    @OrderColumn(name = "position")
    private List<PdfFieldEmbeddable> pdfFields = new ArrayList<>();

    @Column(name = "csv_delimiter", nullable = false, length = 1)
    private String csvDelimiter;

    @ElementCollection
    @CollectionTable(name = "job_csv_headers", joinColumns = @JoinColumn(name = "job_id"))
    @OrderColumn(name = "position")
    @Column(name = "header_name", nullable = false, length = 512)
    private List<String> csvHeaders = new ArrayList<>();

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @ElementCollection
    @CollectionTable(name = "job_field_mappings", joinColumns = @JoinColumn(name = "job_id"))
    @OrderColumn(name = "position")
    private List<FieldMappingEmbeddable> mappings = new ArrayList<>();

    @Column(name = "filename_pattern", nullable = false, length = 512)
    private String filenamePattern;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "successful_rows", nullable = false)
    private int successfulRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected BatchJobEntity() {}

    static BatchJobEntity fromDomain(BatchJob job) {
        BatchJobEntity entity = new BatchJobEntity();
        entity.updateFrom(job);
        return entity;
    }

    void updateFrom(BatchJob job) {
        if (status == JobStatus.CANCELLING
                && job.status() != JobStatus.CANCELLING
                && job.status() != JobStatus.CANCELLED) {
            return;
        }
        id = job.id();
        status = job.status();
        pageCount = job.pageCount();
        pdfFields = job.pdfFields().stream()
                .map(field -> new PdfFieldEmbeddable(field.name(), field.type(), field.supported()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        csvDelimiter = Character.toString(job.csvDelimiter());
        csvHeaders = new ArrayList<>(job.csvHeaders());
        totalRows = job.totalRows();
        mappings = job.mappings().stream()
                .map(mapping -> new FieldMappingEmbeddable(
                        mapping.pdfField(), mapping.csvColumn()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        filenamePattern = job.filenamePattern();
        processedRows = job.processedRows();
        successfulRows = job.successfulRows();
        failedRows = job.failedRows();
        failureCode = job.failureCode();
        failureMessage = job.failureMessage();
        createdAt = job.createdAt();
        updatedAt = job.updatedAt();
        expiresAt = job.expiresAt();
    }

    BatchJob toDomain() {
        return BatchJob.restore(
                id,
                status,
                pageCount,
                pdfFields.stream()
                        .map(field -> new PdfFieldInfo(
                                field.name(), field.type(), field.supported()))
                        .toList(),
                csvDelimiter.charAt(0),
                csvHeaders,
                totalRows,
                mappings.stream()
                        .map(mapping -> new FieldMapping(
                                mapping.pdfField(), mapping.csvColumn()))
                        .toList(),
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

    UUID id() {
        return id;
    }
}
