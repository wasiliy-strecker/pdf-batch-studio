package de.appfabrik.pdfbatch.api;

import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobApplicationService;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Batch jobs")
@RestController
@RequestMapping("/api/v1/jobs")
public class JobRestController {
    private final JobApplicationService jobs;

    public JobRestController(JobApplicationService jobs) {
        this.jobs = jobs;
    }

    @Operation(summary = "Upload and inspect an AcroForm PDF and CSV")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobResponse> create(
            @RequestPart("template") MultipartFile template,
            @RequestPart("data") MultipartFile data) {
        try (InputStream pdfInput = template.getInputStream();
                InputStream csvInput = data.getInputStream()) {
            JobResponse response = JobResponse.from(jobs.create(pdfInput, csvInput));
            return ResponseEntity.created(URI.create("/api/v1/jobs/" + response.id()))
                    .body(response);
        } catch (IOException exception) {
            throw new de.appfabrik.pdfbatch.core.PdfBatchException(
                    "UPLOAD_READ_FAILED", "Could not read uploaded documents", exception);
        }
    }

    @Operation(summary = "Configure PDF field mappings and output filename")
    @PutMapping("/{id}/configuration")
    public JobResponse configure(
            @PathVariable UUID id, @Valid @RequestBody ConfigureJobRequest request) {
        JobConfiguration configuration = new JobConfiguration(
                request.mappings().stream()
                        .map(mapping -> new FieldMapping(
                                mapping.pdfField(), mapping.csvColumn()))
                        .toList(),
                request.filenamePattern());
        return JobResponse.from(jobs.configure(id, configuration));
    }

    @Operation(summary = "Generate a preview using the first CSV data row")
    @PostMapping(value = "/{id}/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("preview.pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(jobs.preview(id));
    }

    @Operation(summary = "Queue asynchronous PDF generation")
    @PostMapping("/{id}/process")
    public ResponseEntity<JobResponse> process(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobResponse.from(jobs.start(id)));
    }

    @Operation(summary = "Read job state and progress")
    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return JobResponse.from(jobs.get(id));
    }

    @Operation(summary = "Request cancellation")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<JobResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobResponse.from(jobs.cancel(id)));
    }

    @Operation(summary = "Download the completed ZIP result")
    @GetMapping(value = "/{id}/result", produces = "application/zip")
    public ResponseEntity<InputStreamResource> result(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("pdf-batch-result-" + id + ".zip")
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new InputStreamResource(jobs.result(id)));
    }

    @Operation(summary = "Delete job metadata and local files")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobs.delete(id);
        return ResponseEntity.noContent().build();
    }
}
