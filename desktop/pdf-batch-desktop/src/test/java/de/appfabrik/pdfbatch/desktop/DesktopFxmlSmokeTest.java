package de.appfabrik.pdfbatch.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import de.appfabrik.pdfbatch.desktop.storage.StudioRepository;
import de.appfabrik.pdfbatch.desktop.ui.MainController;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopViewModel;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopWorkflowService;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class DesktopFxmlSmokeTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(
                () -> {
                    Platform.setImplicitExit(false);
                    started.countDown();
                });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }

    @Test
    void loadsTheUiRunsTheCertificateExampleAndSavesItsProject(@TempDir Path directory)
            throws Exception {
        Ui ui =
                fx(
                        () -> {
                            var repository = new StudioRepository(directory.resolve("app"));
                            var model =
                                    new DesktopViewModel(
                                            DesktopWorkflowService.create(
                                                    repository.workspace(), repository));
                            var controller = new MainController(model, repository);
                            FXMLLoader loader =
                                    new FXMLLoader(
                                            PdfBatchDesktopApplication.class.getResource(
                                                    "/de/appfabrik/pdfbatch/desktop/main-view.fxml"));
                            loader.setController(controller);
                            Parent root = loader.load();
                            new Scene(root, 1240, 860);
                            root.resize(1240, 860);
                            snapshot(root, "pdf-batch-home.png");
                            assertThat(((Button) root.lookup("#certificateDemoButton")).getText())
                                    .isEqualTo("Beispiel ausprobieren");
                            ((Button) root.lookup("#certificateDemoButton")).fire();
                            return new Ui(root, controller, model, repository);
                        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (fx(() -> ((ImageView) ui.root.lookup("#previewImage")).getImage() == null)
                    && System.nanoTime() < deadline) Thread.sleep(20);
            fx(
                    () -> {
                        assertThat(((ImageView) ui.root.lookup("#previewImage")).getImage())
                                .isNotNull();
                        assertThat(ui.model.snapshot().totalRows()).isEqualTo(3);
                        assertThat(ui.model.snapshot().errorMessage()).isNull();
                        assertThat(((Button) ui.root.lookup("#startButton")).isDisabled())
                                .isFalse();
                        snapshot(ui.root, "pdf-batch-desktop.png");
                        ((Button) ui.root.lookup("#saveProjectButton")).fire();
                        return null;
                    });
            while (ui.repository.projects().isEmpty() && System.nanoTime() < deadline)
                Thread.sleep(20);
            assertThat(ui.repository.projects()).hasSize(1);
        } finally {
            fx(
                    () -> {
                        ui.controller.close();
                        ui.repository.close();
                        return null;
                    });
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(action.call());
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
                    }
                });
        return result.get(15, TimeUnit.SECONDS);
    }

    private static void snapshot(Parent root, String name) throws Exception {
        root.applyCss();
        root.layout();
        String directory = System.getProperty("pdf.batch.screenshots");
        if (directory == null) return;
        var image = root.snapshot(null, null);
        var buffered =
                new java.awt.image.BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++)
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Files.createDirectories(Path.of(directory));
        javax.imageio.ImageIO.write(buffered, "png", Path.of(directory, name).toFile());
    }

    private record Ui(
            Parent root,
            MainController controller,
            DesktopViewModel model,
            StudioRepository repository) {}
}
