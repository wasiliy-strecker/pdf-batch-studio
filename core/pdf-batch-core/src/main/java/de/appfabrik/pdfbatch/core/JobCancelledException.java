package de.appfabrik.pdfbatch.core;

public final class JobCancelledException extends PdfBatchException {
    public JobCancelledException() {
        super("JOB_CANCELLED", "Batch job was cancelled");
    }
}
