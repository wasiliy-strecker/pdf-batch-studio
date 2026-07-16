package de.appfabrik.pdfbatch.desktop.ui;

import java.nio.file.Path;
import java.util.Locale;

public final class OutputPathNormalizer {
    private OutputPathNormalizer() {}

    public static Path ensureSingleZipExtension(Path selected) {
        String filename = selected.getFileName().toString();
        while (filename.toLowerCase(Locale.ROOT).endsWith(".zip.zip")) {
            filename = filename.substring(0, filename.length() - 4);
        }
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            filename += ".zip";
        }
        return selected.resolveSibling(filename);
    }
}
