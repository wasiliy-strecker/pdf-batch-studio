package de.appfabrik.pdfbatch.desktop;

import java.util.Arrays;
import javafx.application.Application;

public final class DesktopLauncher {
    private DesktopLauncher() {}

    public static void main(String[] arguments) {
        if (Arrays.asList(arguments).contains("--version")) {
            System.out.println(DesktopApplicationInfo.NAME + " " + DesktopApplicationInfo.VERSION);
            return;
        }
        Application.launch(PdfBatchDesktopApplication.class, arguments);
    }
}
