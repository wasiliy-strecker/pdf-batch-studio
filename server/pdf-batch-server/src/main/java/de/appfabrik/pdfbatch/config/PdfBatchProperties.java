package de.appfabrik.pdfbatch.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pdf-batch")
public record PdfBatchProperties(
        Path storageRoot,
        Duration retention,
        Duration cleanupInterval,
        Limits limits,
        Processing processing) {
    public PdfBatchProperties {
        if (storageRoot == null
                || retention == null
                || cleanupInterval == null
                || limits == null
                || processing == null) {
            throw new IllegalArgumentException("PDF Batch configuration must be complete");
        }
    }

    public record Limits(
            long maxPdfBytes,
            long maxCsvBytes,
            int maxRows,
            int maxPdfPages,
            int maxPdfFields,
            int maxFieldValueLength) {}

    public record Processing(int workers, int queueCapacity, int maxActiveJobs) {
        public Processing {
            if (workers < 1 || queueCapacity < 1 || maxActiveJobs < 1) {
                throw new IllegalArgumentException("Processing limits must be positive");
            }
            if (maxActiveJobs > workers + queueCapacity) {
                throw new IllegalArgumentException(
                        "Maximum active jobs cannot exceed worker and queue capacity");
            }
        }
    }
}
