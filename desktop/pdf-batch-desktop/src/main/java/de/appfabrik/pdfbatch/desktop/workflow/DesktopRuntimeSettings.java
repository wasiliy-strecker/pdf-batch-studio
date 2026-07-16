package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.core.DocumentLimits;

public record DesktopRuntimeSettings(int maxRows) {
    public static final int DEFAULT_MAX_ROWS = 10_000;
    public static final String MAX_ROWS_ENVIRONMENT = "PDF_BATCH_MAX_ROWS";
    public static final String MAX_ROWS_PROPERTY = "pdf.batch.maxRows";

    public DesktopRuntimeSettings {
        if (maxRows < 1) {
            throw new IllegalArgumentException("Maximum CSV rows must be positive");
        }
    }

    public static DesktopRuntimeSettings fromEnvironment() {
        return fromValues(
                System.getProperty(MAX_ROWS_PROPERTY), System.getenv(MAX_ROWS_ENVIRONMENT));
    }

    static DesktopRuntimeSettings fromValues(String propertyValue, String environmentValue) {
        String configured = firstNonBlank(propertyValue, environmentValue);
        if (configured == null) {
            return new DesktopRuntimeSettings(DEFAULT_MAX_ROWS);
        }
        try {
            return new DesktopRuntimeSettings(Integer.parseInt(configured));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Maximum CSV rows must be a positive integer: " + configured, exception);
        }
    }

    public DocumentLimits documentLimits() {
        return new DocumentLimits(20_000_000, 5_000_000, maxRows, 100, 500, 2_000);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
