package de.appfabrik.pdfbatch.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class FieldMappingEmbeddable {
    @Column(name = "pdf_field", nullable = false, length = 512)
    private String pdfField;

    @Column(name = "csv_column", nullable = false, length = 512)
    private String csvColumn;

    protected FieldMappingEmbeddable() {}

    FieldMappingEmbeddable(String pdfField, String csvColumn) {
        this.pdfField = pdfField;
        this.csvColumn = csvColumn;
    }

    String pdfField() {
        return pdfField;
    }

    String csvColumn() {
        return csvColumn;
    }
}
