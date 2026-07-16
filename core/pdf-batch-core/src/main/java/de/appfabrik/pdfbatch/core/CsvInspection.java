package de.appfabrik.pdfbatch.core;

import java.util.List;

public record CsvInspection(char delimiter, List<String> headers, int rowCount) {
    public CsvInspection {
        headers = List.copyOf(headers);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("CSV headers must not be empty");
        }
        if (rowCount < 1) {
            throw new IllegalArgumentException("CSV must contain at least one data row");
        }
    }

    public String delimiterName() {
        return switch (delimiter) {
            case ',' -> "comma";
            case ';' -> "semicolon";
            case '\t' -> "tab";
            default -> Character.toString(delimiter);
        };
    }
}
