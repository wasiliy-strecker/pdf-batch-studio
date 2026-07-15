package de.appfabrik.pdfbatch.core;

public record ProcessingSummary(int processedRows, int successfulRows, int failedRows) {
    public ProcessingSummary {
        if (processedRows < 0 || successfulRows < 0 || failedRows < 0) {
            throw new IllegalArgumentException("Processing counters must not be negative");
        }
        if (processedRows != successfulRows + failedRows) {
            throw new IllegalArgumentException("Processed rows must equal successful plus failed rows");
        }
    }
}
