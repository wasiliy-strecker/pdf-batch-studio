package de.appfabrik.pdfbatch.core;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JobConfigurationValidator {
    private static final Pattern TOKEN = Pattern.compile("\\{([^{}]+)}");

    private JobConfigurationValidator() {}

    public static void validate(BatchJob job, JobConfiguration configuration) {
        if (configuration.mappings().isEmpty()) {
            throw new DocumentValidationException(
                    "MAPPING_REQUIRED", "At least one PDF field must be mapped");
        }

        Set<String> supportedFields = new HashSet<>();
        job.pdfFields().stream()
                .filter(PdfFieldInfo::supported)
                .map(PdfFieldInfo::name)
                .forEach(supportedFields::add);
        Set<String> headers = new HashSet<>(job.csvHeaders());
        Set<String> mappedFields = new HashSet<>();

        for (FieldMapping mapping : configuration.mappings()) {
            if (!supportedFields.contains(mapping.pdfField())) {
                throw new DocumentValidationException(
                        "UNKNOWN_PDF_FIELD", "Unknown or unsupported PDF field: " + mapping.pdfField());
            }
            if (!headers.contains(mapping.csvColumn())) {
                throw new DocumentValidationException(
                        "UNKNOWN_CSV_COLUMN", "Unknown CSV column: " + mapping.csvColumn());
            }
            if (!mappedFields.add(mapping.pdfField())) {
                throw new DocumentValidationException(
                        "DUPLICATE_PDF_MAPPING", "PDF field is mapped more than once: " + mapping.pdfField());
            }
        }

        Matcher matcher = TOKEN.matcher(configuration.filenamePattern());
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!token.equals("rowNumber") && !headers.contains(token)) {
                throw new DocumentValidationException(
                        "UNKNOWN_FILENAME_TOKEN", "Unknown filename token: " + token);
            }
        }
        String unmatched = TOKEN.matcher(configuration.filenamePattern()).replaceAll("");
        if (unmatched.contains("{") || unmatched.contains("}")) {
            throw new DocumentValidationException(
                    "INVALID_FILENAME_PATTERN", "Filename pattern contains unmatched braces");
        }
    }
}
