package de.appfabrik.pdfbatch.desktop.workflow;

import de.appfabrik.pdfbatch.document.CsvDelimiterDetector;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TableImportService {
    public record Table(Path csv, List<String> headers, List<List<String>> sample) {}

    public List<String> sheets(Path source) throws Exception {
        checkSize(source);
        if (!isExcel(source)) return List.of();
        ZipSecureFile.setMaxEntrySize(20_000_000);
        try (var pkg = OPCPackage.open(source.toFile(), PackageAccess.READ)) {
            var iterator = (XSSFReader.SheetIterator) new XSSFReader(pkg).getSheetsData();
            List<String> names = new ArrayList<>();
            while (iterator.hasNext()) {
                try (var stream = iterator.next()) {
                    names.add(iterator.getSheetName());
                }
                if (names.size() > 100) throw new IOException("Too many worksheets");
            }
            return List.copyOf(names);
        }
    }

    public Table prepare(Path source, String sheet, Path workspace, int maxRows) throws Exception {
        checkSize(source);
        Files.createDirectories(workspace);
        Path target = Files.createTempFile(workspace, "table-", ".csv");
        try {
            if (isExcel(source)) {
                convertExcel(source, sheet, target, maxRows);
            } else {
                if (Files.size(source) > 5_000_000) throw new IOException("CSV exceeds 5 MB");
                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            byte[] prefix;
            try (var stream = Files.newInputStream(target)) {
                prefix = stream.readNBytes(64 * 1024);
            }
            char delimiter = new CsvDelimiterDetector().detect(prefix);
            try (var reader = Files.newBufferedReader(target, StandardCharsets.UTF_8);
                    var parser =
                            CSVFormat.DEFAULT
                                    .builder()
                                    .setDelimiter(delimiter)
                                    .setHeader()
                                    .setSkipHeaderRecord(true)
                                    .get()
                                    .parse(reader)) {
                if (parser.getHeaderNames().size() > 500)
                    throw new IOException("Table exceeds 500 columns");
                List<List<String>> sample = new ArrayList<>();
                for (var row : parser) {
                    if (row.toList().stream().anyMatch(value -> value.length() > 2000)) {
                        throw new IOException("A table cell exceeds 2000 characters");
                    }
                    sample.add(List.copyOf(row.toList()));
                    if (sample.size() == 50) break;
                }
                return new Table(target, parser.getHeaderNames(), List.copyOf(sample));
            }
        } catch (Exception exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    private void convertExcel(Path source, String selectedSheet, Path target, int maxRows)
            throws Exception {
        ZipSecureFile.setMaxEntrySize(20_000_000);
        try (var pkg = OPCPackage.open(source.toFile(), PackageAccess.READ);
                var output = new LimitedOutput(Files.newOutputStream(target));
                var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                var printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            var reader = new XSSFReader(pkg);
            reader.setUseReadOnlySharedStringsTable(true);
            var sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            boolean found = false;
            while (sheets.hasNext()) {
                try (var stream = sheets.next()) {
                    if (selectedSheet != null
                            && !selectedSheet.isEmpty()
                            && !selectedSheet.equals(sheets.getSheetName())) continue;
                    var handler =
                            new XSSFSheetXMLHandler.SheetContentsHandler() {
                                private List<String> row;
                                private int width;
                                private int count;

                                @Override
                                public void startRow(int number) {
                                    row = new ArrayList<>();
                                }

                                @Override
                                public void cell(
                                        String reference, String value, XSSFComment comment) {
                                    int column = new CellReference(reference).getCol();
                                    if (column >= 500 || value.length() > 2000) {
                                        throw new IllegalArgumentException(
                                                "Worksheet exceeds column or cell length limit");
                                    }
                                    while (row.size() <= column) row.add("");
                                    row.set(column, value);
                                }

                                @Override
                                public void endRow(int number) {
                                    if (row.stream().allMatch(String::isBlank)) return;
                                    if (++count > maxRows + 1)
                                        throw new IllegalArgumentException(
                                                "Too many worksheet rows");
                                    if (count == 1) width = row.size();
                                    if (row.size() > width)
                                        throw new IllegalArgumentException(
                                                "Data extends beyond the header columns");
                                    while (row.size() < width) row.add("");
                                    try {
                                        printer.printRecord(row);
                                    } catch (IOException exception) {
                                        throw new java.io.UncheckedIOException(exception);
                                    }
                                }
                            };
                    var xml = XMLHelper.newXMLReader();
                    xml.setContentHandler(
                            new XSSFSheetXMLHandler(
                                    reader.getStylesTable(),
                                    reader.getSharedStringsTable(),
                                    handler,
                                    new DataFormatter(Locale.GERMANY),
                                    false));
                    xml.parse(new InputSource(stream));
                    found = true;
                    break;
                }
            }
            if (!found) throw new IOException("Selected worksheet no longer exists");
        }
    }

    private static boolean isExcel(Path source) {
        return source.toString().toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    private static void checkSize(Path source) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > 20_000_000) {
            throw new IOException("Choose a readable table smaller than 20 MB");
        }
        String name = source.toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".csv") || name.endsWith(".tsv") || name.endsWith(".xlsx"))) {
            throw new IOException("Use CSV, TSV or XLSX files");
        }
    }

    private static final class LimitedOutput extends FilterOutputStream {
        private long bytes;

        LimitedOutput(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            if (++bytes > 5_000_000) throw new IOException("Normalized table exceeds 5 MB");
            out.write(value);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            bytes += length;
            if (bytes > 5_000_000) throw new IOException("Normalized table exceeds 5 MB");
            out.write(buffer, offset, length);
        }
    }
}
