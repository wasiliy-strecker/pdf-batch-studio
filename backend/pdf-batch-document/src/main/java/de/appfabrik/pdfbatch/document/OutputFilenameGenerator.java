package de.appfabrik.pdfbatch.document;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OutputFilenameGenerator {
    private static final Pattern TOKEN = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern UNSAFE = Pattern.compile("[^\\p{L}\\p{N}._ -]");
    private static final int MAX_BASENAME_LENGTH = 120;

    private final Map<String, Integer> occurrences = new HashMap<>();

    public String generate(String pattern, Map<String, String> row, long rowNumber) {
        String normalizedPattern = pattern.toLowerCase().endsWith(".pdf")
                ? pattern.substring(0, pattern.length() - 4)
                : pattern;
        Matcher matcher = TOKEN.matcher(normalizedPattern);
        StringBuilder replaced = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String value = token.equals("rowNumber")
                    ? Long.toString(rowNumber)
                    : row.getOrDefault(token, "");
            matcher.appendReplacement(replaced, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(replaced);

        String sanitized = Normalizer.normalize(replaced.toString(), Normalizer.Form.NFKC);
        sanitized = sanitized.replace('/', '-').replace('\\', '-');
        sanitized = UNSAFE.matcher(sanitized).replaceAll("-");
        sanitized = sanitized.replaceAll("[ .-]+", "-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            sanitized = "document-" + rowNumber;
        }
        if (sanitized.length() > MAX_BASENAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_BASENAME_LENGTH).replaceAll("[-. ]+$", "");
        }

        String key = sanitized.toLowerCase();
        int occurrence = occurrences.merge(key, 1, Integer::sum);
        String suffix = occurrence == 1 ? "" : "-" + occurrence;
        int allowedBaseLength = MAX_BASENAME_LENGTH - suffix.length();
        if (sanitized.length() > allowedBaseLength) {
            sanitized = sanitized.substring(0, allowedBaseLength);
        }
        return sanitized + suffix + ".pdf";
    }

    public static Set<String> tokens(String pattern) {
        Matcher matcher = TOKEN.matcher(pattern);
        java.util.HashSet<String> result = new java.util.HashSet<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return Set.copyOf(result);
    }
}
