package de.appfabrik.pdfbatch.api;

import de.appfabrik.pdfbatch.core.DocumentValidationException;
import de.appfabrik.pdfbatch.core.InvalidJobStateException;
import de.appfabrik.pdfbatch.core.JobCapacityException;
import de.appfabrik.pdfbatch.core.JobNotFoundException;
import de.appfabrik.pdfbatch.core.PdfBatchException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackageClasses = JobRestController.class)
public class ApiExceptionHandler {
    @ExceptionHandler(PdfBatchException.class)
    ResponseEntity<ProblemDetail> handlePdfBatch(PdfBatchException exception) {
        HttpStatus status = switch (exception) {
            case JobNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case InvalidJobStateException ignored -> HttpStatus.CONFLICT;
            case JobCapacityException ignored -> HttpStatus.CONFLICT;
            case DocumentValidationException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title(status));
        problem.setType(URI.create("urn:pdf-batch:error:" + exception.code().toLowerCase()));
        problem.setProperty("code", exception.code());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequest(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body validation failed");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("urn:pdf-batch:error:invalid-request"));
        problem.setProperty("code", "INVALID_REQUEST");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadLimit(MaxUploadSizeExceededException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONTENT_TOO_LARGE, "Multipart upload exceeds the configured request limit");
        problem.setTitle("Upload too large");
        problem.setType(URI.create("urn:pdf-batch:error:upload-too-large"));
        problem.setProperty("code", "UPLOAD_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(problem);
    }

    private static String title(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Job not found";
            case CONFLICT -> "Job conflict";
            case UNPROCESSABLE_CONTENT -> "Document validation failed";
            default -> "PDF Batch error";
        };
    }
}
