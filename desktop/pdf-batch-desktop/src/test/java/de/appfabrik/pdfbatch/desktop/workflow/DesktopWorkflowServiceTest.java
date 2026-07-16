package de.appfabrik.pdfbatch.desktop.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.appfabrik.pdfbatch.core.DocumentLimits;
import de.appfabrik.pdfbatch.core.DocumentValidationException;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.document.SampleTemplateGenerator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopWorkflowServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void runsTheRealOfflineWorkflowAndExportsASemanticZip() throws Exception {
        Path pdf = temporaryDirectory.resolve("template.pdf");
        Path csv = temporaryDirectory.resolve("customers.csv");
        Path workspace = temporaryDirectory.resolve("workspace");
        Path result = temporaryDirectory.resolve("result.zip");
        Files.write(pdf, SampleTemplateGenerator.createTemplate());
        Files.writeString(
                csv,
                "name,customerId\nAda Lovelace,C-001\nGrace Hopper,C-002\n",
                StandardCharsets.UTF_8);

        try (DesktopWorkflowService service = DesktopWorkflowService.create(workspace)) {
            var draft = service.inspect(pdf, csv);
            assertThat(draft.status()).isEqualTo(JobStatus.DRAFT);
            assertThat(draft.totalRows()).isEqualTo(2);

            service.configure(
                    List.of(
                            new FieldMapping("fullName", "name"),
                            new FieldMapping("customerNumber", "customerId")),
                    "{customerId}-{name}.pdf");

            byte[] preview = service.preview();
            try (PDDocument document = Loader.loadPDF(preview)) {
                assertThat(document.getDocumentCatalog()
                                .getAcroForm()
                                .getField("fullName")
                                .getValueAsString())
                        .isEqualTo("Ada Lovelace");
            }

            service.start();
            var terminal = awaitTerminal(service);
            assertThat(terminal.status()).isEqualTo(JobStatus.COMPLETED);
            assertThat(terminal.successfulRows()).isEqualTo(2);

            service.exportResult(result);
        }

        assertThat(result).isRegularFile();
        Map<String, byte[]> entries = unzip(result);
        assertThat(entries.keySet())
                .containsExactlyInAnyOrder(
                        "documents/C-001-Ada-Lovelace.pdf",
                        "documents/C-002-Grace-Hopper.pdf",
                        "manifest.json",
                        "errors.csv");
        try (PDDocument generated = Loader.loadPDF(entries.get(
                "documents/C-002-Grace-Hopper.pdf"))) {
            assertThat(generated.getDocumentCatalog()
                            .getAcroForm()
                            .getField("customerNumber")
                            .getValueAsString())
                    .isEqualTo("C-002");
        }
        assertThat(workspace).doesNotExist();
    }

    @Test
    void acceptsMoreThanTheFormerEditionLimitByDefault() throws Exception {
        Path pdf = temporaryDirectory.resolve("template.pdf");
        Path csv = temporaryDirectory.resolve("too-many.csv");
        Files.write(pdf, SampleTemplateGenerator.createTemplate());
        StringBuilder contents = new StringBuilder("name,customerId\n");
        for (int row = 1; row <= 26; row++) {
            contents.append("Person ").append(row).append(',').append(row).append('\n');
        }
        Files.writeString(csv, contents, StandardCharsets.UTF_8);

        try (DesktopWorkflowService service = DesktopWorkflowService.create(
                temporaryDirectory.resolve("default-workspace"))) {
            assertThat(service.inspect(pdf, csv).totalRows()).isEqualTo(26);
        }
    }

    @Test
    void enforcesAConfiguredRowSafetyLimit() throws Exception {
        Path pdf = temporaryDirectory.resolve("limited-template.pdf");
        Path csv = temporaryDirectory.resolve("limited.csv");
        Files.write(pdf, SampleTemplateGenerator.createTemplate());
        Files.writeString(
                csv,
                "name,customerId\nAda,1\nGrace,2\nKatherine,3\n",
                StandardCharsets.UTF_8);
        DocumentLimits limits = new DocumentLimits(20_000_000, 5_000_000, 2, 100, 500, 2_000);

        try (DesktopWorkflowService service = DesktopWorkflowService.create(
                temporaryDirectory.resolve("limited-workspace"), limits)) {
            assertThatThrownBy(() -> service.inspect(pdf, csv))
                    .isInstanceOfSatisfying(
                            DocumentValidationException.class,
                            exception -> assertThat(exception.code())
                                    .isEqualTo("ROW_LIMIT_EXCEEDED"));
        }
    }

    private static de.appfabrik.pdfbatch.core.BatchJob awaitTerminal(
            DesktopWorkflowService service) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        de.appfabrik.pdfbatch.core.BatchJob job = service.status();
        while (!job.status().isTerminal() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
            job = service.status();
        }
        return job;
    }

    private static Map<String, byte[]> unzip(Path archive) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
            return zipEntries(zip).stream().collect(Collectors.toMap(
                    ZipEntryContent::name,
                    ZipEntryContent::contents,
                    (left, right) -> left,
                    java.util.LinkedHashMap::new));
        }
    }

    private static List<ZipEntryContent> zipEntries(ZipInputStream zip) throws Exception {
        java.util.ArrayList<ZipEntryContent> entries = new java.util.ArrayList<>();
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            entries.add(new ZipEntryContent(entry.getName(), zip.readAllBytes()));
        }
        return entries;
    }

    private record ZipEntryContent(String name, byte[] contents) {}
}
