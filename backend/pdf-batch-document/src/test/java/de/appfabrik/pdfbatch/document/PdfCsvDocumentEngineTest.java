package de.appfabrik.pdfbatch.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.appfabrik.pdfbatch.core.DocumentLimits;
import de.appfabrik.pdfbatch.core.DocumentValidationException;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import de.appfabrik.pdfbatch.core.ProcessingListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.junit.jupiter.api.Test;

class PdfCsvDocumentEngineTest {
    private static final DocumentLimits LIMITS =
            new DocumentLimits(2_000_000, 100_000, 25, 20, 100, 100);
    private final PdfCsvDocumentEngine engine = new PdfCsvDocumentEngine();

    @Test
    void inspectsAcroFormFieldsCsvHeadersAndDelimiter() throws IOException {
        var inspection = engine.inspect(
                input(templatePdf()), input("name;customerId\nAda;C-001\n"), LIMITS);

        assertThat(inspection.pdf().pageCount()).isOne();
        assertThat(inspection.pdf().fields())
                .extracting(field -> field.name())
                .containsExactly("fullName", "customerNumber");
        assertThat(inspection.csv().delimiter()).isEqualTo(';');
        assertThat(inspection.csv().headers()).containsExactly("name", "customerId");
        assertThat(inspection.csv().rowCount()).isOne();
    }

    @Test
    void rejectsEmptyDuplicateAndMissingHeaders() throws IOException {
        assertCsvError("name,,id\nAda,x,1\n", "EMPTY_CSV_HEADER");
        assertCsvError("name,name\nAda,Lovelace\n", "DUPLICATE_CSV_HEADER");
        assertCsvError("\n", "EMPTY_CSV");
    }

    @Test
    void enforcesTheCommunityRowLimitBeforeProcessing() throws IOException {
        StringBuilder csv = new StringBuilder("name,id\n");
        for (int row = 0; row < 26; row++) {
            csv.append("Person ").append(row).append(',').append(row).append('\n');
        }

        assertCsvError(csv.toString(), "COMMUNITY_ROW_LIMIT_EXCEEDED");
    }

    @Test
    void createsPreviewFromTheFirstRecord() throws IOException {
        byte[] preview = engine.preview(
                input(templatePdf()),
                input("name,id\nAda Lovelace,C-001\nGrace Hopper,C-002\n"),
                ',',
                configuration(),
                LIMITS);

        try (PDDocument document = Loader.loadPDF(preview)) {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            assertThat(form.getField("fullName").getValueAsString()).isEqualTo("Ada Lovelace");
            assertThat(form.getField("customerNumber").getValueAsString()).isEqualTo("C-001");
        }
    }

    @Test
    void generatesSemanticPdfsManifestAndSafeZipPathsEndToEnd() throws IOException {
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        List<Integer> progress = new ArrayList<>();

        var summary = engine.process(
                input(templatePdf()),
                input("name,id\nAda Lovelace,C-001\nGrace Hopper,C-002\n"),
                ',',
                configuration(),
                LIMITS,
                archive,
                () -> false,
                listener(progress));

        Map<String, byte[]> entries = unzip(archive.toByteArray());
        assertThat(summary.processedRows()).isEqualTo(2);
        assertThat(summary.successfulRows()).isEqualTo(2);
        assertThat(progress).containsExactly(1, 2);
        assertThat(entries.keySet())
                .containsExactlyInAnyOrder(
                        "documents/C-001-Ada-Lovelace.pdf",
                        "documents/C-002-Grace-Hopper.pdf",
                        "manifest.json",
                        "errors.csv");
        assertThat(entries.keySet()).allMatch(name -> !name.startsWith("/")
                && !name.contains("..")
                && !name.contains("\\"));
        assertThat(new String(entries.get("manifest.json"), StandardCharsets.UTF_8))
                .contains("\"successfulRows\": 2");

        try (PDDocument generated = Loader.loadPDF(
                entries.get("documents/C-001-Ada-Lovelace.pdf"))) {
            assertThat(generated.getDocumentCatalog()
                            .getAcroForm()
                            .getField("fullName")
                            .getValueAsString())
                    .isEqualTo("Ada Lovelace");
        }
    }

    @Test
    void reportsInvalidRowsButKeepsSuccessfulDocuments() throws IOException {
        DocumentLimits shortFields = new DocumentLimits(2_000_000, 100_000, 25, 20, 100, 5);
        ByteArrayOutputStream archive = new ByteArrayOutputStream();

        var summary = engine.process(
                input(templatePdf()),
                input("name,id\nAda,C-1\nThis value is too long,C-2\n"),
                ',',
                configuration(),
                shortFields,
                archive,
                () -> false,
                listener(new ArrayList<>()));

        Map<String, byte[]> entries = unzip(archive.toByteArray());
        assertThat(summary.successfulRows()).isOne();
        assertThat(summary.failedRows()).isOne();
        assertThat(new String(entries.get("errors.csv"), StandardCharsets.UTF_8))
                .contains("FIELD_VALUE_TOO_LONG");
    }

    @Test
    void rejectsInvalidAndEncryptedPdfs() throws IOException {
        assertThatThrownBy(() -> engine.inspect(input("not a PDF"), input("name,id\nAda,1\n"), LIMITS))
                .isInstanceOfSatisfying(
                        DocumentValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_PDF"));

        byte[] encrypted;
        try (PDDocument document = Loader.loadPDF(templatePdf());
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var policy = new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                    "owner", "user", new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);
            encrypted = output.toByteArray();
        }
        assertThatThrownBy(() -> engine.inspect(input(encrypted), input("name,id\nAda,1\n"), LIMITS))
                .isInstanceOfSatisfying(
                        DocumentValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ENCRYPTED_PDF"));
    }

    @Test
    void rejectsMalformedUtf8CsvInput() throws IOException {
        byte[] malformed = {
            'n', 'a', 'm', 'e', ',', 'i', 'd', '\n', (byte) 0xC3, (byte) 0x28, ',', '1', '\n'
        };

        assertThatThrownBy(() -> engine.inspect(input(templatePdf()), input(malformed), LIMITS))
                .isInstanceOfSatisfying(
                        DocumentValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_CSV"));
    }

    private void assertCsvError(String csv, String expectedCode) throws IOException {
        assertThatThrownBy(() -> engine.inspect(input(templatePdf()), input(csv), LIMITS))
                .isInstanceOfSatisfying(
                        DocumentValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expectedCode));
    }

    private static JobConfiguration configuration() {
        return new JobConfiguration(
                List.of(
                        new FieldMapping("fullName", "name"),
                        new FieldMapping("customerNumber", "id")),
                "{id}-{name}.pdf");
    }

    private static ProcessingListener listener(List<Integer> progress) {
        return new ProcessingListener() {
            @Override
            public void rowProcessed(int processed, int successful, int failed) {
                progress.add(processed);
            }

            @Override
            public void packagingStarted() {}
        };
    }

    private static ByteArrayInputStream input(String value) {
        return input(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ByteArrayInputStream input(byte[] value) {
        return new ByteArrayInputStream(value);
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(input(archive), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    static byte[] templatePdf() throws IOException {
        return SampleTemplateGenerator.createTemplate();
    }
}
