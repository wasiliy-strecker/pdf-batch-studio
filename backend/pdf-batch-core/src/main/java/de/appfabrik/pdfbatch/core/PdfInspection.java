package de.appfabrik.pdfbatch.core;

import java.util.List;

public record PdfInspection(int pageCount, List<PdfFieldInfo> fields) {
    public PdfInspection {
        if (pageCount < 1) {
            throw new IllegalArgumentException("PDF page count must be positive");
        }
        fields = List.copyOf(fields);
    }
}
