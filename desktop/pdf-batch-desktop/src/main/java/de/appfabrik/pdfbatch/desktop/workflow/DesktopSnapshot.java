package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.PdfFieldInfo;
import java.nio.file.Path;
import java.util.List;

public record DesktopSnapshot(
        JobStatus status,
        Path pdfPath,
        Path csvPath,
        Path outputPath,
        Path exportedPath,
        int pageCount,
        int totalRows,
        int processedRows,
        int successfulRows,
        int failedRows,
        String delimiterName,
        List<PdfFieldInfo> pdfFields,
        List<String> csvHeaders,
        List<FieldMapping> mappings,
        String filenamePattern,
        byte[] previewPng,
        String errorCode,
        String errorMessage) {
    public DesktopSnapshot {
        pdfFields = List.copyOf(pdfFields);
        csvHeaders = List.copyOf(csvHeaders);
        mappings = List.copyOf(mappings);
        previewPng = previewPng == null ? null : previewPng.clone();
    }

    @Override
    public byte[] previewPng() {
        return previewPng == null ? null : previewPng.clone();
    }

    public boolean hasJob() {
        return status != null;
    }

    public boolean isActive() {
        return status != null && status.isActive();
    }

    public boolean isComplete() {
        return status == JobStatus.COMPLETED || status == JobStatus.COMPLETED_WITH_ERRORS;
    }
}
