package de.appfabrik.pdfbatch.desktop.storage;

import de.appfabrik.pdfbatch.core.BatchJob;
import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.PdfBatchException;

import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Local JDBC repository. One application owns the database and managed workspace. */
public final class StudioRepository implements AutoCloseable {
    private final Path directory;
    private Connection connection;
    private FileChannel lockChannel;
    private FileLock lock;

    public record Run(
            String id,
            String projectId,
            String project,
            String status,
            String started,
            int successful,
            int failed,
            String result) {
        @Override
        public String toString() {
            return started.substring(0, 19).replace('T', ' ')
                    + "  ·  "
                    + project
                    + "  ·  "
                    + status
                    + "  ·  "
                    + successful
                    + " / "
                    + failed;
        }
    }

    public StudioRepository(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directory);
            lockChannel =
                    FileChannel.open(
                            this.directory.resolve("studio.lock"),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            if (lock == null) throw new IOException("PDF Batch Studio is already open");
            String url = "jdbc:sqlite:" + this.directory.resolve("studio.db");
            Flyway.configure()
                    .dataSource(url, null, null)
                    .locations("classpath:desktop-migrations")
                    .cleanDisabled(true)
                    .load()
                    .migrate();
            connection = DriverManager.getConnection(url);
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            execute(
                    "UPDATE batch_runs SET status='INTERRUPTED', finished_at=? WHERE finished_at IS"
                            + " NULL",
                    Instant.now().toString());
            deleteTree(this.directory.resolve("workspace"));
            Files.createDirectories(workspace());
            Files.createDirectories(this.directory.resolve("templates"));
        } catch (Exception exception) {
            close();
            throw failure(
                    "Could not open local storage. Another instance may already be running.",
                    exception);
        }
    }

    public static Path defaultDirectory() {
        String override = System.getProperty("pdf.batch.dataDir");
        if (override != null && !override.isBlank()) return Path.of(override);
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            return Path.of(System.getenv().getOrDefault("LOCALAPPDATA", home), "PDFBatchStudio");
        }
        if (os.contains("mac"))
            return Path.of(home, "Library", "Application Support", "PDFBatchStudio");
        return Path.of(
                System.getenv()
                        .getOrDefault("XDG_DATA_HOME", Path.of(home, ".local", "share").toString()),
                "pdf-batch-studio");
    }

    public Path workspace() {
        return directory.resolve("workspace");
    }

    public synchronized SavedProject save(SavedProject project) {
        try {
            if (Files.size(project.template()) > 20_000_000)
                throw new IOException("Template is too large");
            byte[] pdf = Files.readAllBytes(project.template());
            String hash =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
            Path template = directory.resolve("templates").resolve(hash + ".pdf");
            if (!Files.exists(template)) {
                Path temporary = Files.createTempFile(template.getParent(), "template-", ".tmp");
                try {
                    Files.write(temporary, pdf);
                    Files.move(temporary, template);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            String id = project.id() == null ? UUID.randomUUID().toString() : project.id();
            connection.setAutoCommit(false);
            try {
                execute(
                        "INSERT OR IGNORE INTO templates(id,path) VALUES(?,?)",
                        hash,
                        hash + ".pdf");
                execute(
                        """
                        INSERT INTO projects(id,name,template_id,filename_pattern,sheet,folder_export,updated_at)
                        VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,
                        template_id=excluded.template_id, filename_pattern=excluded.filename_pattern,
                        sheet=excluded.sheet,folder_export=excluded.folder_export,updated_at=excluded.updated_at
                        """,
                        id,
                        project.name().trim(),
                        hash,
                        project.filenamePattern(),
                        project.sheet(),
                        project.folderExport() ? 1 : 0,
                        Instant.now().toString());
                execute("DELETE FROM field_mappings WHERE project_id=?", id);
                int position = 0;
                for (FieldMapping mapping : project.mappings()) {
                    execute(
                            "INSERT INTO field_mappings VALUES(?,?,?,?)",
                            id,
                            position++,
                            mapping.pdfField(),
                            mapping.csvColumn());
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
            return new SavedProject(
                    id,
                    project.name().trim(),
                    template,
                    project.mappings(),
                    project.filenamePattern(),
                    project.sheet(),
                    project.folderExport());
        } catch (Exception exception) {
            throw failure("Could not save the project", exception);
        }
    }

    public synchronized List<SavedProject> projects() {
        List<SavedProject> projects = new ArrayList<>();
        try (var statement =
                        connection.prepareStatement(
                                """
                                SELECT p.*,t.path FROM projects p JOIN templates t ON p.template_id=t.id ORDER BY p.updated_at DESC
                                """);
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                List<FieldMapping> mappings = new ArrayList<>();
                try (var fields =
                                prepare(
                                        "SELECT * FROM field_mappings WHERE project_id=? ORDER BY"
                                                + " position",
                                        rows.getString("id"));
                        var entries = fields.executeQuery()) {
                    while (entries.next())
                        mappings.add(
                                new FieldMapping(
                                        entries.getString("pdf_field"),
                                        entries.getString("csv_column")));
                }
                projects.add(
                        new SavedProject(
                                rows.getString("id"),
                                rows.getString("name"),
                                directory.resolve("templates").resolve(rows.getString("path")),
                                mappings,
                                rows.getString("filename_pattern"),
                                rows.getString("sheet"),
                                rows.getBoolean("folder_export")));
            }
            return List.copyOf(projects);
        } catch (SQLException exception) {
            throw failure("Could not read projects", exception);
        }
    }

    public synchronized void deleteProject(String id) {
        try {
            execute("DELETE FROM projects WHERE id=?", id);
            List<String> unused = new ArrayList<>();
            try (var statement =
                            connection.prepareStatement(
                                    "SELECT id FROM templates WHERE id NOT IN (SELECT template_id"
                                            + " FROM projects)");
                    var rows = statement.executeQuery()) {
                while (rows.next()) unused.add(rows.getString(1));
            }
            for (String template : unused) {
                Files.deleteIfExists(directory.resolve("templates").resolve(template + ".pdf"));
                execute("DELETE FROM templates WHERE id=?", template);
            }
        } catch (SQLException | IOException exception) {
            throw failure("Could not delete project", exception);
        }
    }

    public synchronized void begin(BatchJob job, String projectId) {
        try {
            String name =
                    projects().stream()
                            .filter(p -> p.id().equals(projectId))
                            .map(SavedProject::name)
                            .findFirst()
                            .orElse("PDF Batch");
            execute(
                    "INSERT INTO batch_runs(id,project_id,project_name,status,started_at)"
                            + " VALUES(?,?,?,?,?)",
                    job.id().toString(),
                    projectId,
                    name,
                    "PROCESSING",
                    Instant.now().toString());
        } catch (SQLException exception) {
            throw failure("Could not save processing history", exception);
        }
    }

    public synchronized void finish(BatchJob job, Path result) {
        try {
            execute(
                    """
                    UPDATE batch_runs SET status=?,finished_at=?,processed=?,successful=?,failed=?,
                    result_path=COALESCE(?,result_path) WHERE id=?
                    """,
                    job.status().name(),
                    Instant.now().toString(),
                    job.processedRows(),
                    job.successfulRows(),
                    job.failedRows(),
                    result == null ? null : result.toString(),
                    job.id().toString());
        } catch (SQLException exception) {
            throw failure("Could not update processing history", exception);
        }
    }

    public synchronized void recordErrors(
            String runId,
            List<de.appfabrik.pdfbatch.document.PdfCsvDocumentEngine.ValidationIssue> errors) {
        try {
            connection.setAutoCommit(false);
            try (var statement =
                    connection.prepareStatement(
                            "INSERT OR REPLACE INTO batch_errors VALUES(?,?,?,?)")) {
                for (var error : errors) {
                    statement.setString(1, runId);
                    statement.setLong(2, error.row());
                    statement.setString(3, error.code());
                    statement.setString(4, error.message());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw failure("Could not save processing errors", exception);
        }
    }

    public synchronized List<String> errors(String runId) {
        List<String> errors = new ArrayList<>();
        try (var statement =
                        prepare(
                                "SELECT * FROM batch_errors WHERE run_id=? ORDER BY row_number"
                                        + " LIMIT 1000",
                                runId);
                var rows = statement.executeQuery()) {
            while (rows.next())
                errors.add(
                        rows.getLong("row_number")
                                + " · "
                                + rows.getString("code")
                                + " · "
                                + rows.getString("message"));
            return errors;
        } catch (SQLException exception) {
            throw failure("Could not read processing errors", exception);
        }
    }

    public synchronized List<Run> history() {
        List<Run> runs = new ArrayList<>();
        try (var statement =
                        connection.prepareStatement(
                                "SELECT * FROM batch_runs ORDER BY started_at DESC LIMIT 200");
                var rows = statement.executeQuery()) {
            while (rows.next())
                runs.add(
                        new Run(
                                rows.getString("id"),
                                rows.getString("project_id"),
                                rows.getString("project_name"),
                                rows.getString("status"),
                                rows.getString("started_at"),
                                rows.getInt("successful"),
                                rows.getInt("failed"),
                                rows.getString("result_path")));
            return List.copyOf(runs);
        } catch (SQLException exception) {
            throw failure("Could not read history", exception);
        }
    }

    public synchronized void clearHistory() {
        try {
            execute("DELETE FROM batch_runs WHERE finished_at IS NOT NULL");
        } catch (SQLException exception) {
            throw failure("Could not clear history", exception);
        }
    }

    public synchronized String setting(String name, String fallback) {
        try (var statement = prepare("SELECT value FROM app_settings WHERE name=?", name);
                var rows = statement.executeQuery()) {
            return rows.next() ? rows.getString(1) : fallback;
        } catch (SQLException exception) {
            throw failure("Could not read settings", exception);
        }
    }

    public synchronized void saveSetting(String name, String value) {
        try {
            execute(
                    "INSERT INTO app_settings VALUES(?,?) ON CONFLICT(name) DO UPDATE SET"
                            + " value=excluded.value",
                    name,
                    value);
        } catch (SQLException exception) {
            throw failure("Could not save settings", exception);
        }
    }

    private PreparedStatement prepare(String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
        return statement;
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (var statement = prepare(sql, parameters)) {
            statement.executeUpdate();
        }
    }

    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private static PdfBatchException failure(String message, Exception cause) {
        return new PdfBatchException("LOCAL_STORAGE", message, cause);
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
        }
        try {
            if (lock != null) lock.release();
        } catch (IOException ignored) {
        }
        try {
            if (lockChannel != null) lockChannel.close();
        } catch (IOException ignored) {
        }
    }
}
