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

    default byte[] preview(
            InputStream pdf,
            InputStream csv,
            char delimiter,
            JobConfiguration configuration,
            DocumentLimits limits,
            int rowIndex) {
        if (rowIndex != 0) {
            throw new IllegalArgumentException("Only the first row is supported");
        }
        return preview(pdf, csv, delimiter, configuration, limits);
    }

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
