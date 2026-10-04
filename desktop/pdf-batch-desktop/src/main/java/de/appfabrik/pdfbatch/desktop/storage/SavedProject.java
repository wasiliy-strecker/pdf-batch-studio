package de.appfabrik.pdfbatch.desktop.storage;

import de.appfabrik.pdfbatch.core.FieldMapping;

import java.nio.file.Path;
import java.util.List;

public record SavedProject(
        String id,
        String name,
        Path template,
        List<FieldMapping> mappings,
        String filenamePattern,
        String sheet,
        boolean folderExport) {
    public SavedProject {
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("Project name must contain 1–120 characters");
        }
        mappings = List.copyOf(mappings);
        sheet = sheet == null ? "" : sheet;
    }

    @Override
    public String toString() {
        return name;
    }
}
