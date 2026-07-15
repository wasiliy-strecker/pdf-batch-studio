package de.appfabrik.pdfbatch.core;

public record DocumentLimits(
        long maxPdfBytes,
        long maxCsvBytes,
        int maxRows,
        int maxPdfPages,
        int maxPdfFields,
        int maxFieldValueLength) {
    public DocumentLimits {
        if (maxPdfBytes < 1
                || maxCsvBytes < 1
                || maxRows < 1
                || maxPdfPages < 1
                || maxPdfFields < 1
                || maxFieldValueLength < 1) {
            throw new IllegalArgumentException("All document limits must be positive");
        }
    }
}
