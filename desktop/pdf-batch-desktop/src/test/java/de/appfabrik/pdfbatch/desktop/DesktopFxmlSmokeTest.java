package de.appfabrik.pdfbatch.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import de.appfabrik.pdfbatch.desktop.ui.MainController;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopViewModel;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopWorkflowService;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class DesktopFxmlSmokeTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }

    @Test
    void loadsAndStylesTheCompleteDesktopView(@TempDir Path temporaryDirectory) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Parent> loadedRoot = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        Platform.runLater(() -> {
            var viewModel = new DesktopViewModel(
                    DesktopWorkflowService.create(temporaryDirectory.resolve("workspace")));
            var controller = new MainController(viewModel);
            try {
                FXMLLoader loader = new FXMLLoader(PdfBatchDesktopApplication.class.getResource(
                        "/de/appfabrik/pdfbatch/desktop/main-view.fxml"));
                loader.setController(controller);
                Parent root = loader.load();
                Scene scene = new Scene(root, 1180, 780);
                root.applyCss();
                root.layout();
                loadedRoot.set(root);
                assertThat(scene.getRoot().lookup("#inspectButton")).isNotNull();
                assertThat(scene.getRoot().lookup("#previewImage")).isNotNull();
                assertThat(scene.getRoot().lookup("#startButton")).isNotNull();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                controller.close();
                finished.countDown();
            }
        });

        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
        assertThat(loadedRoot.get()).isNotNull();
    }
}
