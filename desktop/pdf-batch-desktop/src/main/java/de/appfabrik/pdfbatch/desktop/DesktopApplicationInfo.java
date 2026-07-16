package de.appfabrik.pdfbatch.desktop;

import java.io.IOException;
import java.util.Properties;

public final class DesktopApplicationInfo {
    public static final String NAME = "PDF Batch Studio Desktop";
    public static final String VERSION = loadVersion();

    private DesktopApplicationInfo() {}

    private static String loadVersion() {
        try (var input = DesktopApplicationInfo.class.getResourceAsStream(
                "/de/appfabrik/pdfbatch/desktop/studio-version.properties")) {
            if (input == null) {
                throw new IllegalStateException("Desktop version resource is missing");
            }
            Properties properties = new Properties();
            properties.load(input);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("Desktop version is missing");
            }
            return version.trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Desktop version could not be read", exception);
        }
    }
}
