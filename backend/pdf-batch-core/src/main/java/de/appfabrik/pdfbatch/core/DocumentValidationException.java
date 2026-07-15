package de.appfabrik.pdfbatch.core;

public final class DocumentValidationException extends PdfBatchException {
    public DocumentValidationException(String code, String message) {
        super(code, message);
    }

    public DocumentValidationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
