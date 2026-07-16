package de.appfabrik.pdfbatch.desktop;

import de.appfabrik.pdfbatch.desktop.ui.MainController;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopViewModel;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopWorkflowService;
import java.io.IOException;
import java.nio.file.Files;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class PdfBatchDesktopApplication extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        var workspace = Files.createTempDirectory("pdf-batch-desktop-");
        var viewModel = new DesktopViewModel(DesktopWorkflowService.create(workspace));
        controller = new MainController(viewModel);

        FXMLLoader loader = new FXMLLoader(PdfBatchDesktopApplication.class.getResource(
                "/de/appfabrik/pdfbatch/desktop/main-view.fxml"));
        loader.setController(controller);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1180, 780);
        stage.setTitle(DesktopApplicationInfo.NAME);
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
    }
}
