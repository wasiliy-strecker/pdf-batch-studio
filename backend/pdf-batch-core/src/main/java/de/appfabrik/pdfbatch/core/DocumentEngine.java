package de.appfabrik.pdfbatch.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;

public interface DocumentEngine {
    UploadInspection inspect(InputStream pdf, InputStream csv, DocumentLimits limits);

    byte[] preview(
            InputStream pdf,
            InputStream csv,
            char delimiter,
            JobConfiguration configuration,
            DocumentLimits limits);

    ProcessingSummary process(
            InputStream pdf,
            InputStream csv,
            char delimiter,
            JobConfiguration configuration,
            DocumentLimits limits,
            OutputStream zipOutput,
            BooleanSupplier cancellationRequested,
            ProcessingListener listener);
}
