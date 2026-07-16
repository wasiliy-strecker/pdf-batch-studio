package de.appfabrik.pdfbatch.core;

import java.util.List;

public record JobConfiguration(List<FieldMapping> mappings, String filenamePattern) {
    public static final String DEFAULT_FILENAME_PATTERN = "{rowNumber}.pdf";

    public JobConfiguration {
        mappings = List.copyOf(mappings);
        filenamePattern = filenamePattern == null || filenamePattern.isBlank()
                ? DEFAULT_FILENAME_PATTERN
                : filenamePattern.trim();
    }
}
