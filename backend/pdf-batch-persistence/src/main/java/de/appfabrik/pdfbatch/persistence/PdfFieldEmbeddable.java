package de.appfabrik.pdfbatch.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class PdfFieldEmbeddable {
    @Column(name = "field_name", nullable = false, length = 512)
    private String name;

    @Column(name = "field_type", nullable = false, length = 100)
    private String type;

    @Column(name = "supported", nullable = false)
    private boolean supported;

    protected PdfFieldEmbeddable() {}

    PdfFieldEmbeddable(String name, String type, boolean supported) {
        this.name = name;
        this.type = type;
        this.supported = supported;
    }

    String name() {
        return name;
    }

    String type() {
        return type;
    }

    boolean supported() {
        return supported;
    }
}
