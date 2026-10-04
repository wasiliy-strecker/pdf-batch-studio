package de.appfabrik.pdfbatch.desktop.storage;

import de.appfabrik.pdfbatch.core.FieldMapping;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Portable project format. Contains the original template and mapping, never recipient data. */
public final class ProjectArchive {
    private ProjectArchive() {}

    public static void exportProject(SavedProject project, Path destination) throws IOException {
        if (Files.exists(destination)) throw new IOException("Choose a new project filename");
        Path temporary =
                Files.createTempFile(
                        destination.toAbsolutePath().getParent(), ".pdfbatch-", ".tmp");
        try {
            Properties properties = new Properties();
            properties.setProperty("version", "1");
            properties.setProperty("name", project.name());
            properties.setProperty("pattern", project.filenamePattern());
            properties.setProperty("sheet", project.sheet());
            properties.setProperty("folder", Boolean.toString(project.folderExport()));
            properties.setProperty("count", Integer.toString(project.mappings().size()));
            for (int i = 0; i < project.mappings().size(); i++) {
                properties.setProperty("field." + i, project.mappings().get(i).pdfField());
                properties.setProperty("column." + i, project.mappings().get(i).csvColumn());
            }
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                zip.putNextEntry(new ZipEntry("project.properties"));
                properties.store(zip, "PDF Batch Studio project 1");
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("template.pdf"));
                Files.copy(project.template(), zip);
                zip.closeEntry();
            }
            Files.move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static SavedProject importProject(Path source, StudioRepository repository)
            throws IOException {
        var entries = new HashMap<String, byte[]>();
        try (var zip = new ZipInputStream(Files.newInputStream(source))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String name = entry.getName();
                if ((!name.equals("project.properties") && !name.equals("template.pdf"))
                        || entries.containsKey(name)) {
                    throw new IOException("Invalid project archive entry");
                }
                int limit = name.equals("template.pdf") ? 20_000_000 : 1_000_000;
                byte[] bytes = zip.readNBytes(limit + 1);
                if (bytes.length > limit)
                    throw new IOException("Project archive exceeds size limit");
                entries.put(name, bytes);
            }
        }
        if (entries.size() != 2) throw new IOException("Incomplete project archive");
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(entries.get("project.properties")));
        if (!"1".equals(properties.getProperty("version")))
            throw new IOException("Unsupported project version");
        try {
            int count = Integer.parseInt(properties.getProperty("count"));
            if (count < 1 || count > 500) throw new IOException("Invalid field mapping count");
            var mappings = new ArrayList<FieldMapping>();
            for (int i = 0; i < count; i++) {
                mappings.add(
                        new FieldMapping(
                                properties.getProperty("field." + i),
                                properties.getProperty("column." + i)));
            }
            String pattern = properties.getProperty("pattern");
            if (pattern == null || pattern.length() > 512)
                throw new IOException("Invalid filename pattern");
            Path temporary = Files.createTempFile(repository.workspace(), "project-", ".pdf");
            try {
                Files.write(temporary, entries.get("template.pdf"));
                return repository.save(
                        new SavedProject(
                                null,
                                properties.getProperty("name"),
                                temporary,
                                mappings,
                                pattern,
                                properties.getProperty("sheet", ""),
                                Boolean.parseBoolean(properties.getProperty("folder"))));
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid project configuration", exception);
        }
    }
}
