package de.appfabrik.pdfbatch.core;

public interface ProcessingListener {
    void rowProcessed(int processedRows, int successfulRows, int failedRows);

    void packagingStarted();
}
