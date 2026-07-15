package de.appfabrik.pdfbatch.document;

import de.appfabrik.pdfbatch.core.DocumentValidationException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CsvDelimiterDetector {
    private static final char[] CANDIDATES = {',', ';', '\t'};

    public char detect(byte[] sample) {
        String text = new String(sample, StandardCharsets.UTF_8);
        List<String> lines = text.lines().filter(line -> !line.isBlank()).limit(8).toList();
        if (lines.isEmpty()) {
            throw new DocumentValidationException("EMPTY_CSV", "CSV file is empty");
        }

        Candidate best = null;
        for (char candidate : CANDIDATES) {
            List<Integer> columnCounts = new ArrayList<>();
            for (String line : lines) {
                columnCounts.add(countColumns(line, candidate));
            }
            int expected = columnCounts.getFirst();
            boolean consistent = expected > 1 && columnCounts.stream().allMatch(count -> count == expected);
            if (consistent && (best == null || expected > best.columns())) {
                best = new Candidate(candidate, expected);
            }
        }
        if (best == null) {
            throw new DocumentValidationException(
                    "CSV_DELIMITER_NOT_DETECTED",
                    "Could not detect a consistent comma, semicolon, or tab delimiter");
        }
        return best.delimiter();
    }

    private static int countColumns(String line, char delimiter) {
        boolean quoted = false;
        int columns = 1;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == delimiter && !quoted) {
                columns++;
            }
        }
        return columns;
    }

    private record Candidate(char delimiter, int columns) {}
}
