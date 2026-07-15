package de.appfabrik.pdfbatch.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pdf-batch")
public record PdfBatchProperties(
        Path storageRoot, Duration retention, Duration cleanupInterval, Limits limits) {
    public PdfBatchProperties {
        if (storageRoot == null || retention == null || cleanupInterval == null || limits == null) {
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
}
