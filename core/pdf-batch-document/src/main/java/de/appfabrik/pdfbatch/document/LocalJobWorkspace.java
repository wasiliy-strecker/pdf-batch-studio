package de.appfabrik.pdfbatch.document;

import de.appfabrik.pdfbatch.core.DocumentValidationException;
import de.appfabrik.pdfbatch.core.JobWorkspace;
import de.appfabrik.pdfbatch.core.PdfBatchException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.UUID;

public final class LocalJobWorkspace implements JobWorkspace {
    private static final String TEMPLATE_FILE = "template.pdf";
    private static final String CSV_FILE = "data.csv";
    private static final String RESULT_FILE = "result.zip";

    private final Path root;

    public LocalJobWorkspace(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw new PdfBatchException(
                    "STORAGE_INIT_FAILED", "Could not initialize local job storage", exception);
        }
    }

    @Override
    public void storeTemplate(UUID jobId, InputStream input, long maxBytes) {
        store(jobId, TEMPLATE_FILE, input, maxBytes, "PDF_TOO_LARGE");
    }

    @Override
    public void storeCsv(UUID jobId, InputStream input, long maxBytes) {
        store(jobId, CSV_FILE, input, maxBytes, "CSV_TOO_LARGE");
    }

    private void store(
            UUID jobId, String filename, InputStream input, long maxBytes, String limitErrorCode) {
        Path directory = jobDirectory(jobId);
        Path destination = directory.resolve(filename);
        try {
            Files.createDirectories(directory);
            try (OutputStream output = Files.newOutputStream(
                    destination,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                copyWithLimit(input, output, maxBytes, limitErrorCode);
            }
        } catch (DocumentValidationException exception) {
            deleteJob(jobId);
            throw exception;
        } catch (IOException exception) {
            deleteJob(jobId);
            throw new PdfBatchException(
                    "STORAGE_WRITE_FAILED", "Could not save an uploaded document", exception);
        }
    }

    private static void copyWithLimit(
            InputStream input, OutputStream output, long maxBytes, String limitErrorCode)
            throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new DocumentValidationException(
                        limitErrorCode, "Uploaded document exceeds the configured size limit");
            }
            output.write(buffer, 0, read);
        }
        if (total == 0) {
            throw new DocumentValidationException("EMPTY_UPLOAD", "Uploaded document is empty");
        }
    }

    @Override
    public InputStream openTemplate(UUID jobId) {
        return open(jobId, TEMPLATE_FILE, "PDF_NOT_FOUND");
    }

    @Override
    public InputStream openCsv(UUID jobId) {
        return open(jobId, CSV_FILE, "CSV_NOT_FOUND");
    }

    @Override
    public InputStream openResult(UUID jobId) {
        return open(jobId, RESULT_FILE, "RESULT_NOT_FOUND");
    }

    private InputStream open(UUID jobId, String filename, String errorCode) {
        try {
            return Files.newInputStream(jobDirectory(jobId).resolve(filename));
        } catch (IOException exception) {
            throw new PdfBatchException(errorCode, "Requested local job file is not available", exception);
        }
    }

    @Override
    public OutputStream createResult(UUID jobId) {
        try {
            return Files.newOutputStream(
                    jobDirectory(jobId).resolve(RESULT_FILE),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new PdfBatchException(
                    "RESULT_CREATE_FAILED", "Could not create the result archive", exception);
        }
    }

    @Override
    public boolean resultExists(UUID jobId) {
        return Files.isRegularFile(jobDirectory(jobId).resolve(RESULT_FILE));
    }

    @Override
    public void deleteResult(UUID jobId) {
        try {
            Files.deleteIfExists(jobDirectory(jobId).resolve(RESULT_FILE));
        } catch (IOException exception) {
            throw new PdfBatchException(
                    "RESULT_DELETE_FAILED", "Could not delete an incomplete result", exception);
        }
    }

    @Override
    public void deleteJob(UUID jobId) {
        Path directory = jobDirectory(jobId);
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new StorageDeleteRuntimeException(exception);
                }
            });
        } catch (StorageDeleteRuntimeException exception) {
            throw new PdfBatchException(
                    "JOB_DELETE_FAILED", "Could not delete local job files", exception.getCause());
        } catch (IOException exception) {
            throw new PdfBatchException(
                    "JOB_DELETE_FAILED", "Could not delete local job files", exception);
        }
    }

    private Path jobDirectory(UUID jobId) {
        Path directory = root.resolve(jobId.toString()).normalize();
        if (!directory.startsWith(root)) {
            throw new PdfBatchException("INVALID_STORAGE_PATH", "Invalid job storage path");
        }
        return directory;
    }

    private static final class StorageDeleteRuntimeException extends RuntimeException {
        private StorageDeleteRuntimeException(IOException cause) {
            super(cause);
        }
    }
}
