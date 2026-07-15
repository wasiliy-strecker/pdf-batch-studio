package de.appfabrik.pdfbatch.core;

public record FieldMapping(String pdfField, String csvColumn) {
    public FieldMapping {
        if (pdfField == null || pdfField.isBlank()) {
            throw new IllegalArgumentException("PDF field must not be blank");
        }
        if (csvColumn == null || csvColumn.isBlank()) {
            throw new IllegalArgumentException("CSV column must not be blank");
        }
    }
}
