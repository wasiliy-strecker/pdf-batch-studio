package de.appfabrik.pdfbatch.core;

public final class JobCapacityException extends PdfBatchException {
    public JobCapacityException() {
        super("COMMUNITY_CAPACITY_REACHED", "Community edition allows one active job at a time");
    }
}
