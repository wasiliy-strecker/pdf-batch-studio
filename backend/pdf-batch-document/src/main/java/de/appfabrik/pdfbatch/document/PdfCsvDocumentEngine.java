package de.appfabrik.pdfbatch.document;

import de.appfabrik.pdfbatch.core.CsvInspection;
import de.appfabrik.pdfbatch.core.DocumentEngine;
import de.appfabrik.pdfbatch.core.DocumentLimits;
import de.appfabrik.pdfbatch.core.DocumentValidationException;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobCancelledException;
import de.appfabrik.pdfbatch.core.JobConfiguration;
import de.appfabrik.pdfbatch.core.PdfBatchException;
import de.appfabrik.pdfbatch.core.PdfFieldInfo;
import de.appfabrik.pdfbatch.core.PdfInspection;
import de.appfabrik.pdfbatch.core.ProcessingListener;
import de.appfabrik.pdfbatch.core.ProcessingSummary;
import de.appfabrik.pdfbatch.core.UploadInspection;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

public final class PdfCsvDocumentEngine implements DocumentEngine {
    private static final int DELIMITER_SAMPLE_SIZE = 64 * 1024;
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final CsvDelimiterDetector delimiterDetector = new CsvDelimiterDetector();

    @Override
    public UploadInspection inspect(InputStream pdf, InputStream csv, DocumentLimits limits) {
        return new UploadInspection(inspectPdf(pdf, limits), inspectCsv(csv, limits));
    }

    @Override
    public byte[] preview(
            InputStream pdf,
            InputStream csv,
            char delimiter,
            JobConfiguration configuration,
            DocumentLimits limits) {
        try (CSVParser parser = parser(csv, delimiter)) {
            var iterator = parser.iterator();
            if (!iterator.hasNext()) {
                throw new DocumentValidationException("CSV_HAS_NO_ROWS", "CSV has no data rows");
            }
            CSVRecord record = iterator.next();
            validateRecord(record, configuration, limits);
            byte[] template = readLimited(pdf, limits.maxPdfBytes(), "PDF_TOO_LARGE");
            return fillPdf(template, values(record, configuration), limits);
        } catch (IOException exception) {
            throw new DocumentValidationException(
                    "PREVIEW_FAILED", "Could not generate the PDF preview", exception);
        }
    }

    @Override
    public ProcessingSummary process(
            InputStream pdf,
            InputStream csv,
            char delimiter,
            JobConfiguration configuration,
            DocumentLimits limits,
            OutputStream zipOutput,
            BooleanSupplier cancellationRequested,
            ProcessingListener listener) {
        byte[] template = readLimited(pdf, limits.maxPdfBytes(), "PDF_TOO_LARGE");
        List<RowResult> rowResults = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        OutputFilenameGenerator filenames = new OutputFilenameGenerator();
        int processed = 0;
        int successful = 0;
        int failed = 0;

        try (CSVParser parser = parser(csv, delimiter);
                ZipOutputStream zip = new ZipOutputStream(zipOutput, StandardCharsets.UTF_8)) {
            for (CSVRecord record : parser) {
                ensureNotCancelled(cancellationRequested);
                processed++;
                try {
                    validateRecord(record, configuration, limits);
                    Map<String, String> row = rowMap(record);
                    String filename = filenames.generate(
                            configuration.filenamePattern(), row, record.getRecordNumber());
                    byte[] outputPdf = fillPdf(template, values(record, configuration), limits);
                    putEntry(zip, "documents/" + filename, outputPdf);
                    successful++;
                    rowResults.add(new RowResult(record.getRecordNumber(), filename, "SUCCESS", null));
                } catch (DocumentValidationException exception) {
                    failed++;
                    errors.add(new RowError(
                            record.getRecordNumber(), exception.code(), exception.getMessage()));
                    rowResults.add(new RowResult(
                            record.getRecordNumber(), null, "FAILED", exception.code()));
                }
                listener.rowProcessed(processed, successful, failed);
            }

            ensureNotCancelled(cancellationRequested);
            listener.packagingStarted();
            putEntry(
                    zip,
                    "manifest.json",
                    manifest(processed, successful, failed, rowResults)
                            .getBytes(StandardCharsets.UTF_8));
            putEntry(zip, "errors.csv", errorCsv(errors).getBytes(StandardCharsets.UTF_8));
            zip.finish();
            return new ProcessingSummary(processed, successful, failed);
        } catch (JobCancelledException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PdfBatchException(
                    "BATCH_PROCESSING_FAILED", "Could not create the result archive", exception);
        }
    }

    private PdfInspection inspectPdf(InputStream input, DocumentLimits limits) {
        byte[] bytes = readLimited(input, limits.maxPdfBytes(), "PDF_TOO_LARGE");
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new DocumentValidationException(
                        "ENCRYPTED_PDF", "Encrypted or password-protected PDFs are not supported");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount > limits.maxPdfPages()) {
                throw new DocumentValidationException(
                        "PDF_PAGE_LIMIT_EXCEEDED",
                        "PDF contains more than " + limits.maxPdfPages() + " pages");
            }
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) {
                throw new DocumentValidationException(
                        "PDF_HAS_NO_ACROFORM", "PDF does not contain an AcroForm");
            }
            List<PdfFieldInfo> fields = new ArrayList<>();
            for (PDField field : form.getFieldTree()) {
                String name = field.getFullyQualifiedName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                fields.add(new PdfFieldInfo(
                        name, field.getClass().getSimpleName(), field instanceof PDTextField));
            }
            if (fields.size() > limits.maxPdfFields()) {
                throw new DocumentValidationException(
                        "PDF_FIELD_LIMIT_EXCEEDED",
                        "PDF contains more than " + limits.maxPdfFields() + " form fields");
            }
            if (fields.stream().noneMatch(PdfFieldInfo::supported)) {
                throw new DocumentValidationException(
                        "PDF_HAS_NO_TEXT_FIELDS", "PDF contains no supported AcroForm text fields");
            }
            return new PdfInspection(pageCount, fields);
        } catch (InvalidPasswordException exception) {
            throw new DocumentValidationException(
                    "ENCRYPTED_PDF", "Encrypted or password-protected PDFs are not supported");
        } catch (DocumentValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DocumentValidationException(
                    "INVALID_PDF", "Uploaded file is not a readable PDF", exception);
        }
    }

    private CsvInspection inspectCsv(InputStream input, DocumentLimits limits) {
        BufferedInputStream buffered = new BufferedInputStream(input);
        try {
            buffered.mark(DELIMITER_SAMPLE_SIZE + 1);
            byte[] sample = buffered.readNBytes(DELIMITER_SAMPLE_SIZE);
            buffered.reset();
            char delimiter = delimiterDetector.detect(sample);
            try (CSVParser parser = parser(buffered, delimiter)) {
                List<String> headers = normalizedHeaders(parser.getHeaderNames());
                validateHeaders(headers);
                int rowCount = 0;
                for (CSVRecord record : parser) {
                    if (!record.isConsistent()) {
                        throw new DocumentValidationException(
                                "INCONSISTENT_CSV_ROW",
                                "CSV row " + record.getRecordNumber() + " has an unexpected column count");
                    }
                    rowCount++;
                    if (rowCount > limits.maxRows()) {
                        throw new DocumentValidationException(
                                "COMMUNITY_ROW_LIMIT_EXCEEDED",
                                "Community edition accepts at most " + limits.maxRows() + " data rows");
                    }
                }
                if (rowCount == 0) {
                    throw new DocumentValidationException(
                            "CSV_HAS_NO_ROWS", "CSV must contain at least one data row");
                }
                return new CsvInspection(delimiter, headers, rowCount);
            }
        } catch (DocumentValidationException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new DocumentValidationException(
                    "INVALID_CSV", "Uploaded file is not a valid UTF-8 CSV", exception);
        }
    }

    private static CSVParser parser(InputStream input, char delimiter) throws IOException {
        PushbackInputStream withoutBom = new PushbackInputStream(input, UTF8_BOM.length);
        byte[] prefix = withoutBom.readNBytes(UTF8_BOM.length);
        if (!java.util.Arrays.equals(prefix, UTF8_BOM)) {
            withoutBom.unread(prefix);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                withoutBom,
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)));
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(true)
                .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
                .get();
        return format.parse(reader);
    }

    private static List<String> normalizedHeaders(List<String> rawHeaders) {
        return rawHeaders.stream().map(String::trim).toList();
    }

    private static void validateHeaders(List<String> headers) {
        if (headers.isEmpty()) {
            throw new DocumentValidationException("CSV_HAS_NO_HEADERS", "CSV has no header row");
        }
        Set<String> unique = new HashSet<>();
        for (String header : headers) {
            if (header.isBlank()) {
                throw new DocumentValidationException(
                        "EMPTY_CSV_HEADER", "CSV contains an empty header");
            }
            if (!unique.add(header)) {
                throw new DocumentValidationException(
                        "DUPLICATE_CSV_HEADER", "CSV contains duplicate header: " + header);
            }
        }
    }

    private static void validateRecord(
            CSVRecord record, JobConfiguration configuration, DocumentLimits limits) {
        if (!record.isConsistent()) {
            throw new DocumentValidationException(
                    "INCONSISTENT_CSV_ROW", "CSV row has an unexpected column count");
        }
        for (FieldMapping mapping : configuration.mappings()) {
            String value = record.get(mapping.csvColumn());
            if (value.length() > limits.maxFieldValueLength()) {
                throw new DocumentValidationException(
                        "FIELD_VALUE_TOO_LONG",
                        "Mapped value for " + mapping.csvColumn() + " exceeds the field length limit");
            }
        }
    }

    private static Map<String, String> values(
            CSVRecord record, JobConfiguration configuration) {
        Map<String, String> result = new LinkedHashMap<>();
        for (FieldMapping mapping : configuration.mappings()) {
            result.put(mapping.pdfField(), record.get(mapping.csvColumn()));
        }
        return result;
    }

    private static Map<String, String> rowMap(CSVRecord record) {
        return new HashMap<>(record.toMap());
    }

    private static byte[] fillPdf(
            byte[] template, Map<String, String> values, DocumentLimits limits) {
        try (PDDocument document = Loader.loadPDF(template);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null) {
                throw new DocumentValidationException(
                        "PDF_HAS_NO_ACROFORM", "PDF does not contain an AcroForm");
            }
            form.setNeedAppearances(true);
            for (Map.Entry<String, String> entry : values.entrySet()) {
                PDField field = form.getField(entry.getKey());
                if (!(field instanceof PDTextField textField)) {
                    throw new DocumentValidationException(
                            "UNSUPPORTED_PDF_FIELD", "PDF field is not a supported text field");
                }
                if (entry.getValue().length() > limits.maxFieldValueLength()) {
                    throw new DocumentValidationException(
                            "FIELD_VALUE_TOO_LONG", "Mapped field value exceeds the configured limit");
                }
                textField.setValue(entry.getValue());
                textField.setReadOnly(true);
            }
            document.getDocumentInformation().setCustomMetadataValue("GeneratedBy", "PDF Batch Community");
            document.save(output);
            return output.toByteArray();
        } catch (DocumentValidationException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new DocumentValidationException(
                    "PDF_FILL_FAILED", "Could not fill the PDF form", exception);
        }
    }

    private static byte[] readLimited(InputStream input, long maxBytes, String limitCode) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new DocumentValidationException(
                            limitCode, "Document exceeds the configured size limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new PdfBatchException("DOCUMENT_READ_FAILED", "Could not read document", exception);
        }
    }

    private static void ensureNotCancelled(BooleanSupplier cancellationRequested) {
        if (cancellationRequested.getAsBoolean()) {
            throw new JobCancelledException();
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        if (name.startsWith("/") || name.contains("..") || name.contains("\\")) {
            throw new PdfBatchException("UNSAFE_ZIP_ENTRY", "Unsafe ZIP entry name");
        }
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private static String errorCsv(List<RowError> errors) throws IOException {
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(
                writer,
                CSVFormat.DEFAULT.builder()
                        .setHeader("rowNumber", "code", "message")
                        .get())) {
            for (RowError error : errors) {
                printer.printRecord(error.rowNumber(), error.code(), error.message());
            }
        }
        return writer.toString();
    }

    private static String manifest(
            int processed, int successful, int failed, List<RowResult> results) {
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"formatVersion\": 1,\n")
                .append("  \"processedRows\": ").append(processed).append(",\n")
                .append("  \"successfulRows\": ").append(successful).append(",\n")
                .append("  \"failedRows\": ").append(failed).append(",\n")
                .append("  \"rows\": [\n");
        for (int index = 0; index < results.size(); index++) {
            RowResult result = results.get(index);
            json.append("    {\"rowNumber\": ").append(result.rowNumber())
                    .append(", \"status\": \"").append(escapeJson(result.status())).append("\"");
            if (result.filename() != null) {
                json.append(", \"filename\": \"")
                        .append(escapeJson(result.filename())).append("\"");
            }
            if (result.errorCode() != null) {
                json.append(", \"errorCode\": \"")
                        .append(escapeJson(result.errorCode())).append("\"");
            }
            json.append('}');
            if (index + 1 < results.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        return json.append("  ]\n}\n").toString();
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private record RowResult(long rowNumber, String filename, String status, String errorCode) {}

    private record RowError(long rowNumber, String code, String message) {}
}
