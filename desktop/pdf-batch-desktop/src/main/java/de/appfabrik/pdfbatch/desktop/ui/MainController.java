package de.appfabrik.pdfbatch.desktop.ui;

import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.PdfBatchException;
import de.appfabrik.pdfbatch.core.PdfFieldInfo;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopSnapshot;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopViewModel;
import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public final class MainController implements AutoCloseable {
    private final DesktopViewModel viewModel;
    private final ExecutorService background;
    private final ScheduledExecutorService pollingExecutor;
    private final List<MappingControl> mappingControls = new ArrayList<>();
    private Path selectedPdf;
    private Path selectedCsv;
    private Path selectedOutput;
    private ScheduledFuture<?> pollingTask;
    private boolean busy;
    private boolean configurationDirty = true;
    private boolean suppressConfigurationEvents;
    private boolean closed;
    private int previewHash;

    @FXML
    private VBox rootPane;

    @FXML
    private Label templatePathLabel;

    @FXML
    private Label csvPathLabel;

    @FXML
    private Label inspectionLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label operationLabel;

    @FXML
    private Label progressDetailLabel;

    @FXML
    private Label errorCodeLabel;

    @FXML
    private Label errorMessageLabel;

    @FXML
    private Label resultLabel;

    @FXML
    private VBox mappingRows;

    @FXML
    private VBox errorBox;

    @FXML
    private VBox resultBox;

    @FXML
    private TextField filenamePatternField;

    @FXML
    private TextField outputPathField;

    @FXML
    private ImageView previewImage;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Button templateButton;

    @FXML
    private Button csvButton;

    @FXML
    private Button inspectButton;

    @FXML
    private Button previewButton;

    @FXML
    private Button outputButton;

    @FXML
    private Button startButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button resetButton;

    @FXML
    private Button saveResultButton;

    @FXML
    private Button openZipButton;

    @FXML
    private Button openFolderButton;

    public MainController(DesktopViewModel viewModel) {
        this.viewModel = viewModel;
        background = Executors.newSingleThreadExecutor(runnable -> daemonThread(
                runnable, "pdf-batch-desktop-ui-worker"));
        pollingExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> daemonThread(
                runnable, "pdf-batch-desktop-status"));
    }

    @FXML
    private void initialize() {
        filenamePatternField.setText("{rowNumber}.pdf");
        filenamePatternField.textProperty().addListener((ignored, oldValue, newValue) ->
                configurationChanged());
        outputPathField.setEditable(false);
        render(viewModel.snapshot());
    }

    @FXML
    private void chooseTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select interactive PDF template");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF documents", "*.pdf"));
        File selected = chooser.showOpenDialog(owner());
        if (selected != null) {
            selectedPdf = selected.toPath();
            templatePathLabel.setText(selected.getName());
            templatePathLabel.setTooltip(new Tooltip(selected.getAbsolutePath()));
            render(viewModel.snapshot());
        }
    }

    @FXML
    private void chooseCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select CSV data");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV and TSV files", "*.csv", "*.tsv"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File selected = chooser.showOpenDialog(owner());
        if (selected != null) {
            selectedCsv = selected.toPath();
            csvPathLabel.setText(selected.getName());
            csvPathLabel.setTooltip(new Tooltip(selected.getAbsolutePath()));
            render(viewModel.snapshot());
        }
    }

    @FXML
    private void inspectFiles() {
        runAsync(
                "Inspecting local files…",
                () -> viewModel.inspect(selectedPdf, selectedCsv),
                snapshot -> {
                    buildMappingRows(snapshot);
                    filenamePatternField.setText("{rowNumber}.pdf");
                    configurationDirty = true;
                    render(snapshot);
                });
    }

    @FXML
    private void generatePreview() {
        List<FieldMapping> mappings = selectedMappings();
        String filenamePattern = filenamePatternField.getText();
        if (mappings.isEmpty()) {
            render(viewModel.recordError(
                    "MAPPING_REQUIRED", "Map at least one PDF field to a CSV column"));
            return;
        }
        runAsync(
                "Generating first-row preview…",
                () -> viewModel.preview(mappings, filenamePattern),
                snapshot -> {
                    configurationDirty = false;
                    render(snapshot);
                });
    }

    @FXML
    private void chooseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save result ZIP");
        chooser.setInitialFileName("pdf-batch-result.zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP archives", "*.zip"));
        File selected = chooser.showSaveDialog(owner());
        if (selected != null) {
            Path path = OutputPathNormalizer.ensureSingleZipExtension(selected.toPath());
            selectedOutput = path;
            outputPathField.setText(path.toAbsolutePath().toString());
            render(viewModel.snapshot());
        }
    }

    @FXML
    private void startBatch() {
        runAsync(
                "Starting local batch…",
                () -> viewModel.start(selectedOutput),
                snapshot -> {
                    render(snapshot);
                    schedulePoll();
                });
    }

    @FXML
    private void cancelBatch() {
        runAsync("Requesting cancellation…", viewModel::cancel, this::render);
    }

    @FXML
    private void saveResult() {
        if (selectedOutput == null) {
            chooseOutput();
        }
        if (selectedOutput != null) {
            runAsync("Saving result ZIP…", viewModel::exportResult, this::render);
        }
    }

    @FXML
    private void resetWorkflow() {
        stopPolling();
        runAsync(
                "Cleaning temporary files…",
                viewModel::reset,
                snapshot -> {
                    selectedPdf = null;
                    selectedCsv = null;
                    selectedOutput = null;
                    templatePathLabel.setText("No PDF selected");
                    csvPathLabel.setText("No CSV selected");
                    outputPathField.clear();
                    mappingRows.getChildren().clear();
                    mappingControls.clear();
                    configurationDirty = true;
                    previewHash = 0;
                    previewImage.setImage(null);
                    render(snapshot);
                });
    }

    @FXML
    private void openResultZip() {
        DesktopSnapshot snapshot = viewModel.snapshot();
        if (snapshot.exportedPath() != null) {
            open(snapshot.exportedPath());
        }
    }

    @FXML
    private void openResultFolder() {
        DesktopSnapshot snapshot = viewModel.snapshot();
        if (snapshot.exportedPath() != null && snapshot.exportedPath().getParent() != null) {
            open(snapshot.exportedPath().getParent());
        }
    }

    private void buildMappingRows(DesktopSnapshot snapshot) {
        suppressConfigurationEvents = true;
        mappingRows.getChildren().clear();
        mappingControls.clear();
        for (PdfFieldInfo field : snapshot.pdfFields()) {
            Label fieldLabel = new Label(field.name());
            fieldLabel.getStyleClass().add("mapping-field");
            Label typeLabel = new Label(field.supported() ? field.type() : field.type() + " · unsupported");
            typeLabel.getStyleClass().add("muted");
            VBox fieldDetails = new VBox(2, fieldLabel, typeLabel);

            ComboBox<String> columns = new ComboBox<>(FXCollections.observableArrayList(
                    snapshot.csvHeaders()));
            columns.setPromptText(field.supported() ? "Select CSV column" : "Unsupported field");
            columns.setDisable(!field.supported());
            columns.setMaxWidth(Double.MAX_VALUE);
            columns.valueProperty().addListener((ignored, oldValue, newValue) ->
                    configurationChanged());

            HBox row = new HBox(16, fieldDetails, columns);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(columns, javafx.scene.layout.Priority.ALWAYS);
            row.getStyleClass().add("mapping-row");
            mappingRows.getChildren().add(row);
            mappingControls.add(new MappingControl(field, columns));
        }
        suppressConfigurationEvents = false;
    }

    private List<FieldMapping> selectedMappings() {
        return mappingControls.stream()
                .filter(control -> control.field().supported())
                .filter(control -> control.columns().getValue() != null)
                .map(control -> new FieldMapping(control.field().name(), control.columns().getValue()))
                .toList();
    }

    private void configurationChanged() {
        if (suppressConfigurationEvents || !viewModel.snapshot().hasJob()) {
            return;
        }
        configurationDirty = true;
        render(viewModel.invalidatePreview());
    }

    private void schedulePoll() {
        stopPolling();
        pollingTask = pollingExecutor.schedule(this::pollStatus, 100, TimeUnit.MILLISECONDS);
    }

    private void pollStatus() {
        if (closed) {
            return;
        }
        try {
            DesktopSnapshot snapshot = viewModel.refresh();
            if (snapshot.isActive()) {
                DesktopSnapshot activeSnapshot = snapshot;
                Platform.runLater(() -> render(activeSnapshot));
                pollingTask = pollingExecutor.schedule(this::pollStatus, 120, TimeUnit.MILLISECONDS);
                return;
            }
            if (snapshot.isComplete()) {
                try {
                    snapshot = viewModel.exportResult();
                } catch (RuntimeException exception) {
                    snapshot = viewModel.recordError(exception);
                }
            }
            DesktopSnapshot terminal = snapshot;
            Platform.runLater(() -> render(terminal));
        } catch (RuntimeException exception) {
            DesktopSnapshot failed = viewModel.recordError(exception);
            Platform.runLater(() -> render(failed));
        }
    }

    private void stopPolling() {
        if (pollingTask != null) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
    }

    private void runAsync(
            String operation,
            Supplier<DesktopSnapshot> supplier,
            Consumer<DesktopSnapshot> onSuccess) {
        if (busy || closed) {
            return;
        }
        busy = true;
        operationLabel.setText(operation);
        render(viewModel.snapshot());
        CompletableFuture.supplyAsync(supplier, background).whenComplete((snapshot, throwable) ->
                Platform.runLater(() -> {
                    busy = false;
                    operationLabel.setText("All processing stays on this computer.");
                    if (throwable == null) {
                        onSuccess.accept(snapshot);
                    } else {
                        render(viewModel.recordError(throwable));
                    }
                }));
    }

    private void render(DesktopSnapshot snapshot) {
        JobStatus status = snapshot.status();
        statusLabel.setText(statusText(status));
        inspectionLabel.setText(snapshot.hasJob()
                ? snapshot.pageCount()
                        + " PDF page(s) · "
                        + snapshot.pdfFields().size()
                        + " form field(s) · "
                        + snapshot.totalRows()
                        + " CSV row(s) · "
                        + snapshot.delimiterName()
                : "Select an AcroForm PDF and a UTF-8 CSV to begin.");

        double progress = snapshot.totalRows() == 0
                ? 0
                : (double) snapshot.processedRows() / snapshot.totalRows();
        if (snapshot.isComplete()) {
            progress = 1;
        }
        progressBar.setProgress(progress);
        progressDetailLabel.setText(snapshot.hasJob()
                ? snapshot.processedRows()
                        + " / "
                        + snapshot.totalRows()
                        + " processed · "
                        + snapshot.successfulRows()
                        + " successful · "
                        + snapshot.failedRows()
                        + " failed"
                : "No active batch");

        byte[] preview = snapshot.previewPng();
        int currentHash = preview == null ? 0 : java.util.Arrays.hashCode(preview);
        if (currentHash != previewHash) {
            previewHash = currentHash;
            previewImage.setImage(preview == null
                    ? null
                    : new Image(new ByteArrayInputStream(preview)));
        }

        boolean hasError = snapshot.errorMessage() != null;
        errorBox.setVisible(hasError);
        errorBox.setManaged(hasError);
        errorCodeLabel.setText(hasError ? snapshot.errorCode() : "");
        errorMessageLabel.setText(hasError ? snapshot.errorMessage() : "");

        boolean showResult = snapshot.isComplete();
        resultBox.setVisible(showResult);
        resultBox.setManaged(showResult);
        resultLabel.setText(snapshot.exportedPath() == null
                ? "Processing completed, but the ZIP has not been saved yet."
                : "Saved to " + snapshot.exportedPath());
        saveResultButton.setVisible(showResult && snapshot.exportedPath() == null);
        saveResultButton.setManaged(showResult && snapshot.exportedPath() == null);
        openZipButton.setDisable(snapshot.exportedPath() == null);
        openFolderButton.setDisable(snapshot.exportedPath() == null);

        boolean active = snapshot.isActive();
        boolean hasJob = snapshot.hasJob();
        templateButton.setDisable(busy || hasJob);
        csvButton.setDisable(busy || hasJob);
        inspectButton.setDisable(busy || hasJob || selectedPdf == null || selectedCsv == null);
        previewButton.setDisable(
                busy
                        || status == null
                        || (status != JobStatus.DRAFT && status != JobStatus.READY)
                        || selectedMappings().isEmpty()
                        || filenamePatternField.getText().isBlank());
        outputButton.setDisable(
                busy || status == null || (status != JobStatus.DRAFT && status != JobStatus.READY));
        startButton.setDisable(
                busy
                        || status != JobStatus.READY
                        || configurationDirty
                        || selectedOutput == null
                        || snapshot.previewPng() == null);
        cancelButton.setDisable(busy || !active || status == JobStatus.CANCELLING);
        resetButton.setDisable(busy || active || !hasJob);
        filenamePatternField.setDisable(busy || active || status == null || status.isTerminal());
        outputPathField.setDisable(busy || active);
        mappingControls.forEach(control -> control.columns().setDisable(
                busy || active || status == null || status.isTerminal() || !control.field().supported()));
    }

    private void open(Path path) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("Desktop file opening is not supported on this system");
            }
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException | UnsupportedOperationException exception) {
            render(viewModel.recordError(
                    new PdfBatchException(
                            "DESKTOP_OPEN_FAILED", "Could not open " + path, exception)));
        }
    }

    private Window owner() {
        return rootPane.getScene() == null ? null : rootPane.getScene().getWindow();
    }

    private static String statusText(JobStatus status) {
        if (status == null) {
            return "Waiting for files";
        }
        return switch (status) {
            case DRAFT -> "Ready to map fields";
            case READY -> "Preview ready";
            case QUEUED -> "Queued locally";
            case PROCESSING -> "Generating PDFs";
            case PACKAGING -> "Packaging ZIP";
            case CANCELLING -> "Cancelling";
            case CANCELLED -> "Cancelled";
            case COMPLETED -> "Completed";
            case COMPLETED_WITH_ERRORS -> "Completed with row errors";
            case FAILED -> "Processing failed";
            default -> status.name().replace('_', ' ');
        };
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        stopPolling();
        pollingExecutor.shutdownNow();
        background.shutdownNow();
        viewModel.close();
    }

    private record MappingControl(PdfFieldInfo field, ComboBox<String> columns) {}
}
