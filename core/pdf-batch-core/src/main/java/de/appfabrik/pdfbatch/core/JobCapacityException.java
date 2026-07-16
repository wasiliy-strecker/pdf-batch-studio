package de.appfabrik.pdfbatch.core;

public final class JobCapacityException extends PdfBatchException {
    public JobCapacityException(int maxActiveJobs) {
        super(
                "JOB_CAPACITY_REACHED",
                "The configured capacity of " + maxActiveJobs + " active job(s) has been reached");
    }
}
