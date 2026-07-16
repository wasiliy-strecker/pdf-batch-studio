package de.appfabrik.pdfbatch.core;

public final class InvalidJobStateException extends PdfBatchException {
    public InvalidJobStateException(JobStatus actual, String action) {
        super("INVALID_JOB_STATE", "Cannot " + action + " while job is " + actual);
    }
}
