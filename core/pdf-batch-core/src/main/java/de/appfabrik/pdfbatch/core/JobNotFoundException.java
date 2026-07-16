package de.appfabrik.pdfbatch.core;

import java.util.UUID;

public final class JobNotFoundException extends PdfBatchException {
    public JobNotFoundException(UUID id) {
        super("JOB_NOT_FOUND", "Batch job " + id + " was not found");
    }
}
