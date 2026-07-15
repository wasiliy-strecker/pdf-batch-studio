package de.appfabrik.pdfbatch.core;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobConfigurationValidatorTest {
    @Test
    void acceptsSupportedMappingsAndKnownFilenameTokens() {
        JobConfiguration configuration = new JobConfiguration(
                List.of(new FieldMapping("fullName", "name")),
                "invoice-{customerId}-{rowNumber}.pdf");

        assertThatCode(() -> JobConfigurationValidator.validate(job(), configuration))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownColumnsFieldsAndDuplicateMappings() {
        assertCode(
                new JobConfiguration(List.of(new FieldMapping("missing", "name")), "{rowNumber}.pdf"),
                "UNKNOWN_PDF_FIELD");
        assertCode(
                new JobConfiguration(List.of(new FieldMapping("fullName", "missing")), "{rowNumber}.pdf"),
                "UNKNOWN_CSV_COLUMN");
        assertCode(
                new JobConfiguration(
                        List.of(
                                new FieldMapping("fullName", "name"),
                                new FieldMapping("fullName", "customerId")),
                        "{rowNumber}.pdf"),
                "DUPLICATE_PDF_MAPPING");
    }

    @Test
    void rejectsUnknownAndMalformedFilenameTokens() {
        assertCode(
                new JobConfiguration(List.of(new FieldMapping("fullName", "name")), "{missing}.pdf"),
                "UNKNOWN_FILENAME_TOKEN");
        assertCode(
                new JobConfiguration(List.of(new FieldMapping("fullName", "name")), "{name.pdf"),
                "INVALID_FILENAME_PATTERN");
    }

    private static void assertCode(JobConfiguration configuration, String code) {
        assertThatThrownBy(() -> JobConfigurationValidator.validate(job(), configuration))
                .isInstanceOfSatisfying(
                        DocumentValidationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo(code));
    }

    private static BatchJob job() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return BatchJob.draft(
                UUID.randomUUID(),
                new UploadInspection(
                        new PdfInspection(
                                1,
                                List.of(
                                        new PdfFieldInfo("fullName", "PDTextField", true),
                                        new PdfFieldInfo("accepted", "PDCheckBox", false))),
                        new CsvInspection(',', List.of("name", "customerId"), 1)),
                now,
                now.plusSeconds(3600));
    }
}
