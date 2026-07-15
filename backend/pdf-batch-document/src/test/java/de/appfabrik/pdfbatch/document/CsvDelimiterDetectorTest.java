package de.appfabrik.pdfbatch.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.appfabrik.pdfbatch.core.DocumentValidationException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class CsvDelimiterDetectorTest {
    private final CsvDelimiterDetector detector = new CsvDelimiterDetector();

    @ParameterizedTest
    @CsvSource({"'name,email\nAda,ada@example.test', ','", "'name;email\nAda;ada@example.test', ';'"})
    void detectsCommonDelimiters(String csv, char expected) {
        assertThat(detector.detect(csv.getBytes(StandardCharsets.UTF_8))).isEqualTo(expected);
    }

    @Test
    void detectsTabsAndIgnoresDelimitersInsideQuotes() {
        String tab = "name\temail\nAda\tada@example.test";
        String quotedComma = "name,note\nAda,\"Hello, world\"";

        assertThat(detector.detect(tab.getBytes(StandardCharsets.UTF_8))).isEqualTo('\t');
        assertThat(detector.detect(quotedComma.getBytes(StandardCharsets.UTF_8))).isEqualTo(',');
    }

    @Test
    void rejectsAnAmbiguousSingleColumnFile() {
        assertThatThrownBy(() -> detector.detect("name\nAda".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(
                        DocumentValidationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("CSV_DELIMITER_NOT_DETECTED"));
    }
}
