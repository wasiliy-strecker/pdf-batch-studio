package de.appfabrik.pdfbatch.core;

public record PdfFieldInfo(String name, String type, boolean supported) {
    public PdfFieldInfo {
        name = requireText(name, "PDF field name");
        type = requireText(type, "PDF field type");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
