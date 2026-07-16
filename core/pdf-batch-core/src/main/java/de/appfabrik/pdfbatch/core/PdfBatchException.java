package de.appfabrik.pdfbatch.core;

public class PdfBatchException extends RuntimeException {
    private final String code;

    public PdfBatchException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PdfBatchException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
