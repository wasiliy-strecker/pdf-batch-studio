package de.appfabrik.pdfbatch.desktop.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.desktop.storage.ProjectArchive;
import de.appfabrik.pdfbatch.desktop.storage.SavedProject;
import de.appfabrik.pdfbatch.desktop.storage.StudioRepository;
import de.appfabrik.pdfbatch.document.SampleTemplateGenerator;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class DesktopStudioTest {
    @TempDir Path directory;

    @Test
    void importsExcelAndRoundTripsAProjectWithoutRecipientData() throws Exception {
        Path data = directory.resolve("recipients.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet("Ignore").createRow(0).createCell(0).setCellValue("unused");
            var sheet = workbook.createSheet("People");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("name");
            header.createCell(1).setCellValue("customerId");
            sheet.createRow(1).createCell(0).setCellValue("Anna Müller");
            sheet.getRow(1).createCell(1).setCellValue("C-001");
            try (var output = Files.newOutputStream(data)) {
                workbook.write(output);
            }
        }
        TableImportService importer = new TableImportService();
        assertThat(importer.sheets(data)).containsExactly("Ignore", "People");
        Path archive = directory.resolve("example.pdfbatch");
        String projectId;
        try (var repository = new StudioRepository(directory.resolve("app"))) {
            var table = importer.prepare(data, "People", repository.workspace(), 10_000);
            assertThat(table.headers()).containsExactly("name", "customerId");
            assertThat(table.sample().getFirst()).containsExactly("Anna Müller", "C-001");
            Path template =
                    Files.write(
                            directory.resolve("source.pdf"),
                            SampleTemplateGenerator.createTemplate());
            var saved =
                    repository.save(
                            new SavedProject(
                                    null,
                                    "Welcome",
                                    template,
                                    List.of(new FieldMapping("fullName", "name")),
                                    "{customerId}.pdf",
                                    "People",
                                    true));
            projectId = saved.id();
            ProjectArchive.exportProject(saved, archive);
            try (var zip = new java.util.zip.ZipFile(archive.toFile())) {
                assertThat(zip.stream().map(java.util.zip.ZipEntry::getName))
                        .containsExactly("project.properties", "template.pdf");
            }
            assertThat(ProjectArchive.importProject(archive, repository).mappings())
                    .isEqualTo(saved.mappings());
            assertThatThrownBy(() -> ProjectArchive.exportProject(saved, archive))
                    .hasMessageContaining("new project filename");
            try (var workflow =
                    DesktopWorkflowService.create(repository.workspace().resolve("run"))) {
                var job = workflow.inspect(template, table.csv());
                repository.begin(job, projectId);
            }
            repository.saveSetting("language", "en");
        }
        try (var reopened = new StudioRepository(directory.resolve("app"))) {
            assertThat(reopened.projects()).hasSize(2);
            assertThat(reopened.setting("language", "de")).isEqualTo("en");
            assertThat(reopened.history().getFirst().status()).isEqualTo("INTERRUPTED");
            assertThat(reopened.projects().getFirst().template()).isRegularFile();
        }
    }

    @Test
    void processesProtectedTemplatesChoicesAndSelectedPagesWithSafeExportRetry() throws Exception {
        Path template = directory.resolve("protected.pdf");
        try (var pdf = Loader.loadPDF(SampleTemplateGenerator.createTemplate())) {
            pdf.addPage(new PDPage());
            var form = pdf.getDocumentCatalog().getAcroForm();
            var choice = new PDComboBox(form);
            choice.setPartialName("department");
            choice.setOptions(List.of("Engineering", "Sales"));
            choice.setDefaultAppearance(form.getDefaultAppearance());
            choice.getWidgets().getFirst().setRectangle(new PDRectangle(70, 550, 250, 30));
            choice.getWidgets().getFirst().setPage(pdf.getPage(0));
            pdf.getPage(0).getAnnotations().add(choice.getWidgets().getFirst());
            form.getFields().add(choice);
            var policy =
                    new StandardProtectionPolicy(
                            "owner-test", "input-test", new AccessPermission());
            policy.setEncryptionKeyLength(256);
            pdf.protect(policy);
            pdf.save(template.toFile());
        }
        Path csv =
                Files.writeString(
                        directory.resolve("people.csv"),
                        "name,department\nAnna Müller,Engineering\nMax Weiß,Unknown\n");
        try (var repository = new StudioRepository(directory.resolve("app"));
                var workflow = DesktopWorkflowService.create(repository.workspace(), repository)) {
            workflow.passwords("wrong", "");
            assertThatThrownBy(() -> workflow.inspect(template, csv))
                    .hasMessageContaining("password");
            workflow.passwords("input-test", "output-test");
            workflow.inspect(template, csv);
            workflow.configure(
                    List.of(
                            new FieldMapping("fullName", "name"),
                            new FieldMapping("department", "department")),
                    "{name}.pdf");
            var issues = workflow.preflight();
            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().row()).isEqualTo(2);
            assertThat(issues.getFirst().message()).contains("department");
            byte[] preview = workflow.preview(0);
            assertThat(new PdfPreviewRenderer().render(preview, 1)).isNotEmpty();
            try (var pdf = Loader.loadPDF(preview)) {
                assertThat(pdf.isEncrypted()).isFalse();
                assertThat(
                                pdf.getDocumentCatalog()
                                        .getAcroForm()
                                        .getField("fullName")
                                        .getValueAsString())
                        .isEqualTo("Anna Müller");
            }
            workflow.start();
            long until = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
            while (!workflow.status().status().isTerminal() && System.nanoTime() < until)
                Thread.sleep(10);
            assertThat(workflow.status().status()).isEqualTo(JobStatus.COMPLETED_WITH_ERRORS);
            Path occupied = Files.writeString(directory.resolve("occupied.zip"), "keep me");
            assertThatThrownBy(() -> workflow.exportResult(occupied))
                    .hasMessageContaining("already exists");
            assertThat(occupied).hasContent("keep me");
            Path result = directory.resolve("result");
            workflow.exportResult(result, true);
            try (var paths = Files.list(result.resolve("documents"))) {
                Path generated = paths.findFirst().orElseThrow();
                assertThatThrownBy(() -> Loader.loadPDF(generated.toFile()))
                        .isInstanceOf(
                                org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
                                        .class);
                try (var pdf = Loader.loadPDF(generated.toFile(), "output-test")) {
                    assertThat(
                                    ((PDComboBox)
                                                    pdf.getDocumentCatalog()
                                                            .getAcroForm()
                                                            .getField("department"))
                                            .getValue())
                            .containsExactly("Engineering");
                }
            }
            assertThat(repository.history().getFirst().result()).isEqualTo(result.toString());
            assertThat(repository.errors(repository.history().getFirst().id())).hasSize(1);
        }
    }
}
