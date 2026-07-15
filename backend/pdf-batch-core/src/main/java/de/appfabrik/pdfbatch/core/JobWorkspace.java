package de.appfabrik.pdfbatch.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public interface JobWorkspace {
    void storeTemplate(UUID jobId, InputStream input, long maxBytes);

    void storeCsv(UUID jobId, InputStream input, long maxBytes);

    InputStream openTemplate(UUID jobId);

    InputStream openCsv(UUID jobId);

    OutputStream createResult(UUID jobId);

    InputStream openResult(UUID jobId);

    boolean resultExists(UUID jobId);

    void deleteResult(UUID jobId);

    void deleteJob(UUID jobId);
}
