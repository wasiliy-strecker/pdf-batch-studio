package de.appfabrik.pdfbatch.desktop.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.CsvInspection;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.PdfFieldInfo;
import de.appfabrik.pdfbatch.core.PdfInspection;
import de.appfabrik.pdfbatch.core.ProcessingSummary;
import de.appfabrik.pdfbatch.core.UploadInspection;
import de.appfabrik.pdfbatch.document.SampleTemplateGenerator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopViewModelTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesInspectionPreviewProgressAndExportState() throws Exception {
        Path pdf = Files.write(
                temporaryDirectory.resolve("template.pdf"),
                SampleTemplateGenerator.createTemplate());
        Path csv = Files.writeString(
                temporaryDirectory.resolve("data.csv"),
                "name,customerId\nAda,C-1\n",
                StandardCharsets.UTF_8);
        Path result = temporaryDirectory.resolve("result.zip");
        FakeWorkflow workflow = new FakeWorkflow();

        try (DesktopViewModel viewModel = new DesktopViewModel(workflow)) {
            DesktopSnapshot inspected = viewModel.inspect(pdf, csv);
            assertThat(inspected.status()).isEqualTo(JobStatus.DRAFT);
            assertThat(inspected.delimiterName()).isEqualTo("Comma");

            DesktopSnapshot previewed = viewModel.preview(
                    List.of(new FieldMapping("fullName", "name")), "{customerId}.pdf");
            assertThat(previewed.previewPng()).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

            assertThat(viewModel.start(result).status()).isEqualTo(JobStatus.QUEUED);
            DesktopSnapshot completed = viewModel.refresh();
            assertThat(completed.status()).isEqualTo(JobStatus.COMPLETED);
            assertThat(completed.processedRows()).isOne();

            DesktopSnapshot exported = viewModel.exportResult();
            assertThat(exported.exportedPath()).isEqualTo(result.toAbsolutePath());
            assertThat(result).hasContent("fake zip");
        }
        assertThat(workflow.closed).isTrue();
    }

    @Test
    void exposesCancellationAndStructuredErrors() throws Exception {
        Path pdf = Files.write(
                temporaryDirectory.resolve("template.pdf"),
                SampleTemplateGenerator.createTemplate());
        Path csv = Files.writeString(
                temporaryDirectory.resolve("data.csv"),
                "name,customerId\nAda,C-1\n",
                StandardCharsets.UTF_8);

        try (DesktopViewModel viewModel = new DesktopViewModel(new FakeWorkflow())) {
            viewModel.inspect(pdf, csv);
            viewModel.preview(List.of(new FieldMapping("fullName", "name")), "{rowNumber}.pdf");
            viewModel.start(temporaryDirectory.resolve("result.zip"));

            assertThat(viewModel.cancel().status()).isEqualTo(JobStatus.CANCELLING);
            assertThat(viewModel.recordError(
                                    new de.appfabrik.pdfbatch.core.PdfBatchException(
                                            "EXPECTED", "Expected desktop error"))
                            .errorCode())
                    .isEqualTo("EXPECTED");
        }
    }

    private static final class FakeWorkflow implements DesktopWorkflow {
        private BatchJob job;
        private boolean closed;

        @Override
        public BatchJob inspect(Path pdf, Path csv) {
            Instant now = Instant.parse("2026-01-01T00:00:00Z");
            job = BatchJob.draft(
                    UUID.randomUUID(),
                    new UploadInspection(
                            new PdfInspection(
                                    1,
                                    List.of(
                                            new PdfFieldInfo("fullName", "text", true),
                                            new PdfFieldInfo("customerNumber", "text", true))),
                            new CsvInspection(',', List.of("name", "customerId"), 1)),
                    now,
                    now.plusSeconds(3_600));
            return job;
        }

        @Override
        public BatchJob configure(List<FieldMapping> mappings, String filenamePattern) {
            job.configure(
                    new JobConfiguration(mappings, filenamePattern),
                    Instant.parse("2026-01-01T00:00:01Z"));
            return job;
        }

        @Override
        public byte[] preview() {
            try {
                return SampleTemplateGenerator.createTemplate();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        @Override
        public BatchJob start() {
            job.queue(Instant.parse("2026-01-01T00:00:02Z"));
            return job;
        }

        @Override
        public BatchJob status() {
            if (job.status() == JobStatus.QUEUED) {
                job.startProcessing(Instant.parse("2026-01-01T00:00:03Z"));
                job.updateProgress(1, 1, 0, Instant.parse("2026-01-01T00:00:04Z"));
                job.startPackaging(Instant.parse("2026-01-01T00:00:05Z"));
                job.complete(new ProcessingSummary(1, 1, 0), Instant.parse("2026-01-01T00:00:06Z"));
            }
            return job;
        }

        @Override
        public BatchJob cancel() {
            job.requestCancellation(Instant.parse("2026-01-01T00:00:03Z"));
            return job;
        }

        @Override
        public BatchJob exportResult(Path destination) {
            try {
                Files.writeString(destination, "fake zip", StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return job;
        }

        @Override
        public void reset() {
            job = null;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
