package de.appfabrik.pdfbatch.desktop;

import de.appfabrik.pdfbatch.desktop.storage.StudioRepository;
import de.appfabrik.pdfbatch.desktop.ui.MainController;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopViewModel;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopWorkflowService;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class PdfBatchDesktopApplication extends Application {
    private MainController controller;
    private StudioRepository repository;

    @Override
    public void start(Stage stage) throws IOException {
        repository = new StudioRepository(StudioRepository.defaultDirectory());
        var viewModel =
                new DesktopViewModel(
                        DesktopWorkflowService.create(repository.workspace(), repository));
        controller = new MainController(viewModel, repository);

        FXMLLoader loader =
                new FXMLLoader(
                        PdfBatchDesktopApplication.class.getResource(
                                "/de/appfabrik/pdfbatch/desktop/main-view.fxml"));
        loader.setController(controller);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1240, 860);
        stage.setTitle(DesktopApplicationInfo.NAME);
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.setOnCloseRequest(
                event -> {
                    if (!controller.confirmClose()) event.consume();
                });
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
        if (repository != null) {
            try {
                StudioRepository.deleteTree(repository.workspace());
            } catch (IOException ignored) {
            }
            repository.close();
        }
    }
}
