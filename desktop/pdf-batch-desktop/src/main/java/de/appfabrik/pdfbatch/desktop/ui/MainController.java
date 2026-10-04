package de.appfabrik.pdfbatch.desktop.ui;

import de.appfabrik.pdfbatch.core.FieldMapping;
import de.appfabrik.pdfbatch.core.JobStatus;
import de.appfabrik.pdfbatch.core.PdfFieldInfo;
import de.appfabrik.pdfbatch.desktop.storage.ProjectArchive;
import de.appfabrik.pdfbatch.desktop.storage.SavedProject;
import de.appfabrik.pdfbatch.desktop.storage.StudioRepository;
import de.appfabrik.pdfbatch.desktop.workflow.DemoService;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopRuntimeSettings;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopSnapshot;
import de.appfabrik.pdfbatch.desktop.workflow.DesktopViewModel;
import de.appfabrik.pdfbatch.desktop.workflow.TableImportService;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MainController implements AutoCloseable {
    private final DesktopViewModel viewModel;
    private final StudioRepository repository;
    private final TableImportService tables = new TableImportService();
    private final ExecutorService background =
            Executors.newSingleThreadExecutor(r -> daemon(r, "studio-ui-worker"));
    private final ScheduledExecutorService polling =
            Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "studio-status"));
    private final List<MappingControl> mappings = new ArrayList<>();
    private ResourceBundle messages;
    private Path selectedPdf;
    private Path selectedData;
    private Path selectedOutput;
    private SavedProject project;
    private Future<?> pollTask;
    private volatile Thread operationThread;
    private volatile boolean operationCancelled;
    private boolean busy;
    private boolean suppress;
    private boolean dirty = true;
    private boolean checking;
    private volatile boolean closed;
    private int previewHash;

    @FXML private VBox rootPane, mappingRows, errorBox, resultBox;
    @FXML private TabPane tabs;
    @FXML private Tab homeTab, workTab, historyTab;
    @FXML private ComboBox<String> languagePicker, sheetPicker, zoomPicker;
    @FXML private ComboBox<SavedProject> projectPicker;
    @FXML private TextField projectNameField, filenamePatternField, outputPathField;
    @FXML private PasswordField templatePassword, outputPassword;
    @FXML private CheckBox folderExport;
    @FXML private TableView<List<String>> dataTable;
    @FXML private ListView<StudioRepository.Run> historyList;
    @FXML private ListView<String> issuesList;
    @FXML private Spinner<Integer> rowPicker, pagePicker;
    @FXML
    private Label templatePathLabel,
            csvPathLabel,
            inspectionLabel,
            statusLabel,
            operationLabel,
            progressDetailLabel,
            errorCodeLabel,
            errorMessageLabel,
            resultLabel,
            validationLabel,
            previewStateLabel;
    @FXML private ImageView previewImage;
    @FXML private ProgressBar progressBar;
    @FXML
    private Button templateButton,
            csvButton,
            inspectButton,
            previewButton,
            outputButton,
            startButton,
            cancelButton,
            resetButton,
            saveResultButton,
            openZipButton,
            openFolderButton,
            saveProjectButton,
            preflightButton;

    public MainController(DesktopViewModel viewModel) {
        this(viewModel, null);
    }

    public MainController(DesktopViewModel viewModel, StudioRepository repository) {
        this.viewModel = viewModel;
        this.repository = repository;
    }

    @FXML
    private void initialize() {
        languagePicker.getItems().setAll("Deutsch", "English");
        String language = repository == null ? "de" : repository.setting("language", "de");
        languagePicker.setValue(language.equals("de") ? "Deutsch" : "English");
        messages = bundle(language);
        rowPicker.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1));
        pagePicker.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1));
        zoomPicker.getItems().setAll("75%", "100%", "125%", "150%", "200%");
        zoomPicker.setValue("100%");
        zoomPicker
                .valueProperty()
                .addListener(
                        (o, old, value) ->
                                previewImage.setFitWidth(
                                        460 * Integer.parseInt(value.replace("%", "")) / 100.0));
        filenamePatternField.setText("{rowNumber}.pdf");
        filenamePatternField.textProperty().addListener((o, old, value) -> changed());
        rowPicker.valueProperty().addListener((o, old, value) -> changed());
        pagePicker.valueProperty().addListener((o, old, value) -> changed());
        templatePassword.textProperty().addListener((o, old, value) -> changed());
        outputPassword.textProperty().addListener((o, old, value) -> changed());
        folderExport
                .selectedProperty()
                .addListener(
                        (o, old, value) -> {
                            selectedOutput = null;
                            outputPathField.clear();
                            render(viewModel.snapshot());
                        });
        languagePicker
                .valueProperty()
                .addListener(
                        (o, old, value) -> {
                            String code = value.equals("Deutsch") ? "de" : "en";
                            messages = bundle(code);
                            if (repository != null) repository.saveSetting("language", code);
                            translate();
                            render(viewModel.snapshot());
                        });
        tabs.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, old, value) -> {
                            if (value == homeTab || value == historyTab) refreshLibrary();
                        });
        historyList.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(StudioRepository.Run run, boolean empty) {
                                super.updateItem(run, empty);
                                setText(
                                        empty || run == null
                                                ? null
                                                : run.started().substring(0, 19).replace('T', ' ')
                                                        + "  ·  "
                                                        + run.project()
                                                        + "  ·  "
                                                        + t("status." + run.status())
                                                        + "  ·  "
                                                        + run.successful()
                                                        + " "
                                                        + t("successful")
                                                        + " / "
                                                        + run.failed()
                                                        + " "
                                                        + t("failed"));
                            }
                        });
        rootPane.setOnDragOver(
                event -> {
                    if (!busy && !viewModel.snapshot().hasJob() && event.getDragboard().hasFiles())
                        event.acceptTransferModes(TransferMode.COPY);
                    event.consume();
                });
        rootPane.setOnDragDropped(
                event -> {
                    if (busy || viewModel.snapshot().hasJob()) {
                        event.setDropCompleted(false);
                        return;
                    }
                    boolean accepted = false;
                    for (File file : event.getDragboard().getFiles()) {
                        String name = file.getName().toLowerCase(Locale.ROOT);
                        if (name.endsWith(".pdf")) {
                            selectedPdf = file.toPath();
                            accepted = true;
                        } else if (name.endsWith(".csv")
                                || name.endsWith(".tsv")
                                || name.endsWith(".xlsx")) {
                            selectedData = file.toPath();
                            accepted = true;
                        }
                    }
                    event.setDropCompleted(accepted);
                    if (accepted) {
                        tabs.getSelectionModel().select(workTab);
                        loadSheets();
                    }
                    event.consume();
                });
        translate();
        refreshLibrary();
        render(viewModel.snapshot());
    }

    private static ResourceBundle bundle(String language) {
        return ResourceBundle.getBundle(
                "de.appfabrik.pdfbatch.desktop.messages", Locale.forLanguageTag(language));
    }

    private String t(String key) {
        return messages.containsKey(key) ? messages.getString(key) : key;
    }

    private void translate() {
        translateNode(rootPane);
        for (Tab tab : tabs.getTabs()) {
            tab.setText(t(tab.getUserData().toString()));
            translateNode(tab.getContent());
        }
        projectNameField.setPromptText(t("projectName"));
        templatePassword.setPromptText(t("templatePassword"));
        outputPassword.setPromptText(t("outputPassword"));
        projectPicker.setPromptText(t("selectProject"));
        sheetPicker.setPromptText(t("sheet"));
        dataTable.setPlaceholder(new Label(t("dataEmpty")));
        historyList.setPlaceholder(new Label(t("historyEmpty")));
        operationLabel.setText(t("offline"));
        historyList.refresh();
    }

    private void translateNode(Node node) {
        if (node instanceof Labeled labeled && node.getUserData() instanceof String key)
            labeled.setText(t(key));
        if (node instanceof ScrollPane scroll && scroll.getContent() != null) {
            translateNode(scroll.getContent());
        } else if (node instanceof SplitPane split) {
            split.getItems().forEach(this::translateNode);
        } else if (node instanceof TabPane pane) {
            pane.getTabs().forEach(tab -> translateNode(tab.getContent()));
        } else if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(this::translateNode);
        }
    }

    @FXML
    private void newProject() {
        resetWorkflow();
        tabs.getSelectionModel().select(workTab);
    }

    @FXML
    private void certificateDemo() {
        demo(true);
    }

    @FXML
    private void letterDemo() {
        demo(false);
    }

    private void demo(boolean certificate) {
        if (repository == null) return;
        tabs.getSelectionModel().select(workTab);
        boolean german = languagePicker.getValue().equals("Deutsch");
        runAsync(
                "loading",
                () -> {
                    viewModel.reset();
                    return new DemoService().create(repository.workspace(), certificate, german);
                },
                example -> {
                    clearControls();
                    selectedPdf = example.pdf();
                    selectedData = example.csv();
                    projectNameField.setText(example.name());
                    inspectFiles(true);
                });
    }

    @FXML
    private void chooseTemplate() {
        File file = chooser(t("choosePdf"), "PDF", "*.pdf").showOpenDialog(owner());
        if (file != null) {
            selectedPdf = file.toPath();
            render(viewModel.snapshot());
        }
    }

    @FXML
    private void chooseCsv() {
        File file =
                chooser(t("chooseData"), "CSV / TSV / Excel", "*.csv", "*.tsv", "*.xlsx")
                        .showOpenDialog(owner());
        if (file != null) {
            selectedData = file.toPath();
            loadSheets();
        }
    }

    private void loadSheets() {
        if (selectedData == null) {
            render(viewModel.snapshot());
            return;
        }
        Path data = selectedData;
        runAsync(
                "loading",
                () -> tables.sheets(data),
                names -> {
                    sheetPicker.getItems().setAll(names);
                    if (!names.isEmpty())
                        sheetPicker.setValue(
                                project != null && names.contains(project.sheet())
                                        ? project.sheet()
                                        : names.getFirst());
                    render(viewModel.snapshot());
                });
    }

    @FXML
    private void inspectFiles() {
        inspectFiles(false);
    }

    private void inspectFiles(boolean previewAfter) {
        Path pdf = selectedPdf;
        Path data = selectedData;
        String sheet = sheetPicker.getValue();
        String password = templatePassword.getText();
        if (pdf == null || data == null) return;
        runAsync(
                "loading",
                () -> {
                    viewModel.passwords(password, "");
                    Path workspace =
                            repository == null
                                    ? Files.createTempDirectory("studio-import-")
                                    : repository.workspace();
                    TableImportService.Table table =
                            tables.prepare(
                                    data,
                                    sheet,
                                    workspace,
                                    DesktopRuntimeSettings.fromEnvironment().maxRows());
                    try {
                        viewModel.inspect(pdf, table.csv());
                    } finally {
                        Files.deleteIfExists(table.csv());
                    }
                    return table;
                },
                table -> {
                    DesktopSnapshot snapshot = viewModel.snapshot();
                    buildMappings(snapshot);
                    showData(table);
                    suppress = true;
                    rowPicker.setValueFactory(
                            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                                    1, snapshot.totalRows()));
                    pagePicker.setValueFactory(
                            new SpinnerValueFactory.IntegerSpinnerValueFactory(
                                    1, snapshot.pageCount()));
                    filenamePatternField.setText(
                            project == null ? "{rowNumber}.pdf" : project.filenamePattern());
                    suppress = false;
                    dirty = true;
                    render(snapshot);
                    if (previewAfter) generatePreview();
                });
    }

    private void showData(TableImportService.Table table) {
        dataTable.getColumns().clear();
        for (int i = 0; i < table.headers().size(); i++) {
            final int index = i;
            TableColumn<List<String>, String> column =
                    new TableColumn<>(table.headers().get(i).replace("\ufeff", ""));
            column.setCellValueFactory(
                    cell ->
                            new SimpleStringProperty(
                                    index < cell.getValue().size()
                                            ? cell.getValue().get(index)
                                            : ""));
            column.setPrefWidth(150);
            dataTable.getColumns().add(column);
        }
        dataTable.setItems(FXCollections.observableArrayList(table.sample()));
    }

    private void buildMappings(DesktopSnapshot snapshot) {
        suppress = true;
        mappingRows.getChildren().clear();
        mappings.clear();
        for (PdfFieldInfo field : snapshot.pdfFields()) {
            Label name = new Label(field.name());
            name.setMinWidth(140);
            name.setMaxWidth(220);
            name.setWrapText(true);
            name.getStyleClass().add("mapping-field");
            List<String> items = new ArrayList<>();
            items.add("");
            items.addAll(snapshot.csvHeaders());
            ComboBox<String> columns = new ComboBox<>(FXCollections.observableArrayList(items));
            columns.setMaxWidth(Double.MAX_VALUE);
            columns.setTooltip(new Tooltip(field.type()));
            columns.setPromptText(field.supported() ? t("selectColumn") : t("unsupported"));
            if (field.supported()) {
                String selected =
                        project == null
                                ? snapshot.csvHeaders().stream()
                                        .filter(
                                                header ->
                                                        normalized(header)
                                                                .equals(normalized(field.name())))
                                        .findFirst()
                                        .orElse(null)
                                : project.mappings().stream()
                                        .filter(m -> m.pdfField().equals(field.name()))
                                        .map(FieldMapping::csvColumn)
                                        .filter(snapshot.csvHeaders()::contains)
                                        .findFirst()
                                        .orElse(null);
                columns.setValue(selected);
            }
            columns.valueProperty().addListener((o, old, value) -> changed());
            HBox row = new HBox(12, name, columns);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("mapping-row");
            HBox.setHgrow(columns, Priority.ALWAYS);
            mappingRows.getChildren().add(row);
            mappings.add(new MappingControl(field, columns));
        }
        suppress = false;
    }

    private static String normalized(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[ _-]", "");
    }

    private List<FieldMapping> selectedMappings() {
        return mappings.stream()
                .filter(
                        m ->
                                m.field().supported()
                                        && m.columns().getValue() != null
                                        && !m.columns().getValue().isEmpty())
                .map(m -> new FieldMapping(m.field().name(), m.columns().getValue()))
                .toList();
    }

    @FXML
    private void generatePreview() {
        List<FieldMapping> values = selectedMappings();
        String pattern = filenamePatternField.getText();
        int row = rowPicker.getValue() - 1;
        int page = pagePicker.getValue() - 1;
        String inputKey = templatePassword.getText();
        String outputKey = outputPassword.getText();
        runAsync(
                "rendering",
                () -> {
                    viewModel.passwords(inputKey, outputKey);
                    return viewModel.preview(values, pattern, row, page);
                },
                snapshot -> {
                    dirty = false;
                    render(snapshot);
                });
    }

    @FXML
    private void preflight() {
        List<FieldMapping> values = selectedMappings();
        String pattern = filenamePatternField.getText();
        String password = templatePassword.getText();
        checking = true;
        runAsync(
                "checking",
                () -> {
                    viewModel.passwords(password, "");
                    return viewModel.preflight(values, pattern);
                },
                issues -> {
                    checking = false;
                    validationLabel.setText(
                            issues.isEmpty()
                                    ? t("validationOk")
                                    : issues.size() + " " + t("validationErrors"));
                    issuesList
                            .getItems()
                            .setAll(
                                    issues.stream()
                                            .limit(200)
                                            .map(
                                                    issue ->
                                                            t("row")
                                                                    + " "
                                                                    + issue.row()
                                                                    + " · "
                                                                    + issue.code()
                                                                    + " · "
                                                                    + issue.message())
                                            .toList());
                    issuesList.setVisible(!issues.isEmpty());
                    issuesList.setManaged(!issues.isEmpty());
                    changed();
                });
    }

    @FXML
    private void chooseOutput() {
        if (folderExport.isSelected()) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(t("chooseParent"));
            File directory = chooser.showDialog(owner());
            if (directory == null) return;
            TextInputDialog name =
                    new TextInputDialog(
                            "pdf-batch-"
                                    + java.time.LocalDateTime.now()
                                            .format(
                                                    java.time.format.DateTimeFormatter.ofPattern(
                                                            "yyyyMMdd-HHmmss")));
            name.initOwner(owner());
            name.setHeaderText(t("newFolder"));
            var result = name.showAndWait();
            if (result.isEmpty()) return;
            String value = result.get().trim();
            if (value.isBlank()
                    || value.contains("/")
                    || value.contains("\\")
                    || value.equals(".")
                    || value.equals("..")) {
                showError(new IllegalArgumentException(t("invalidFolder")));
                return;
            }
            selectedOutput = directory.toPath().resolve(value);
        } else {
            FileChooser chooser = chooser(t("chooseOutput"), "ZIP", "*.zip");
            chooser.setInitialFileName("pdf-batch-result.zip");
            File file = chooser.showSaveDialog(owner());
            if (file == null) return;
            selectedOutput = OutputPathNormalizer.ensureSingleZipExtension(file.toPath());
        }
        if (Files.exists(selectedOutput)) {
            selectedOutput = null;
            showError(new IllegalArgumentException(t("noOverwrite")));
        }
        outputPathField.setText(selectedOutput == null ? "" : selectedOutput.toString());
        render(viewModel.snapshot());
    }

    @FXML
    private void startBatch() {
        if (selectedOutput == null) chooseOutput();
        if (selectedOutput == null) return;
        Path destination = selectedOutput;
        boolean folder = folderExport.isSelected();
        String projectId = project == null ? null : project.id();
        String inputKey = templatePassword.getText();
        String outputKey = outputPassword.getText();
        runAsync(
                "starting",
                () -> {
                    viewModel.project(projectId);
                    viewModel.passwords(inputKey, outputKey);
                    return viewModel.start(destination, folder);
                },
                snapshot -> {
                    render(snapshot);
                    schedulePoll();
                });
    }

    private void schedulePoll() {
        pollTask = polling.schedule(this::poll, 200, TimeUnit.MILLISECONDS);
    }

    private void poll() {
        if (closed) return;
        try {
            DesktopSnapshot snapshot = viewModel.refresh();
            if (snapshot.isActive()) {
                Platform.runLater(
                        () -> {
                            if (!closed) render(viewModel.snapshot());
                        });
                schedulePoll();
            } else {
                if (snapshot.isComplete()) {
                    try {
                        snapshot = viewModel.exportResult();
                    } catch (RuntimeException exception) {
                        snapshot = viewModel.recordError(exception);
                    }
                }
                DesktopSnapshot terminal = snapshot;
                Platform.runLater(
                        () -> {
                            if (!closed) {
                                render(terminal);
                                refreshLibrary();
                            }
                        });
            }
        } catch (RuntimeException exception) {
            Platform.runLater(
                    () -> {
                        if (!closed) showError(exception);
                    });
        }
    }

    @FXML
    private void cancelBatch() {
        if (checking) {
            operationCancelled = true;
            Thread running = operationThread;
            if (running != null) running.interrupt();
            return;
        }
        runAsync("cancelling", viewModel::cancel, this::render);
    }

    @FXML
    private void saveResult() {
        if (selectedOutput == null || Files.exists(selectedOutput)) chooseOutput();
        if (selectedOutput == null) return;
        Path destination = selectedOutput;
        boolean folder = folderExport.isSelected();
        runAsync(
                "saving",
                () -> viewModel.exportResult(destination, folder),
                snapshot -> {
                    render(snapshot);
                    refreshLibrary();
                });
    }

    @FXML
    private void resetWorkflow() {
        if (pollTask != null) pollTask.cancel(false);
        runAsync(
                "loading",
                viewModel::reset,
                snapshot -> {
                    clearControls();
                    render(snapshot);
                });
    }

    private void clearControls() {
        suppress = true;
        project = null;
        selectedPdf = null;
        selectedData = null;
        selectedOutput = null;
        projectNameField.clear();
        templatePassword.clear();
        outputPassword.clear();
        outputPathField.clear();
        filenamePatternField.setText("{rowNumber}.pdf");
        sheetPicker.getItems().clear();
        folderExport.setSelected(false);
        mappings.clear();
        mappingRows.getChildren().clear();
        dataTable.getColumns().clear();
        dataTable.getItems().clear();
        issuesList.getItems().clear();
        issuesList.setManaged(false);
        issuesList.setVisible(false);
        validationLabel.setText("");
        previewImage.setImage(null);
        previewHash = 0;
        dirty = true;
        suppress = false;
    }

    @FXML
    private void refreshLibrary() {
        if (repository == null || busy || viewModel.snapshot().isActive()) return;
        SavedProject selected = projectPicker.getValue();
        projectPicker.getItems().setAll(repository.projects());
        if (selected != null)
            projectPicker.getItems().stream()
                    .filter(p -> p.id().equals(selected.id()))
                    .findFirst()
                    .ifPresent(projectPicker::setValue);
        historyList.getItems().setAll(repository.history());
    }

    @FXML
    private void saveProject() {
        if (repository == null || selectedPdf == null) return;
        try {
            SavedProject draft =
                    new SavedProject(
                            project == null ? null : project.id(),
                            projectNameField.getText(),
                            selectedPdf,
                            selectedMappings(),
                            filenamePatternField.getText(),
                            sheetPicker.getValue(),
                            folderExport.isSelected());
            runAsync(
                    "saving",
                    () -> repository.save(draft),
                    saved -> {
                        project = saved;
                        refreshLibrary();
                        projectPicker.setValue(saved);
                        operationLabel.setText(t("projectSaved"));
                    });
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    @FXML
    private void openProject() {
        if (projectPicker.getValue() != null) loadProject(projectPicker.getValue());
    }

    private void loadProject(SavedProject saved) {
        runAsync(
                "loading",
                viewModel::reset,
                snapshot -> {
                    clearControls();
                    project = saved;
                    selectedPdf = saved.template();
                    projectNameField.setText(saved.name());
                    filenamePatternField.setText(saved.filenamePattern());
                    folderExport.setSelected(saved.folderExport());
                    tabs.getSelectionModel().select(workTab);
                    render(snapshot);
                });
    }

    @FXML
    private void duplicateProject() {
        SavedProject saved = projectPicker.getValue();
        if (saved == null || repository == null) return;
        TextInputDialog dialog = new TextInputDialog(saved.name() + " " + t("copy"));
        dialog.initOwner(owner());
        dialog.setHeaderText(t("projectName"));
        dialog.showAndWait()
                .filter(name -> !name.isBlank())
                .ifPresent(
                        name ->
                                runAsync(
                                        "saving",
                                        () ->
                                                repository.save(
                                                        new SavedProject(
                                                                null,
                                                                name,
                                                                saved.template(),
                                                                saved.mappings(),
                                                                saved.filenamePattern(),
                                                                saved.sheet(),
                                                                saved.folderExport())),
                                        copy -> {
                                            refreshLibrary();
                                            projectPicker.setValue(copy);
                                        }));
    }

    @FXML
    private void deleteProject() {
        SavedProject saved = projectPicker.getValue();
        if (saved == null || repository == null || !confirm(t("deleteProjectQuestion"))) return;
        runAsync(
                "saving",
                () -> {
                    repository.deleteProject(saved.id());
                    return true;
                },
                ignored -> {
                    if (project != null && project.id().equals(saved.id())) project = null;
                    refreshLibrary();
                });
    }

    @FXML
    private void exportProject() {
        SavedProject saved = projectPicker.getValue();
        if (saved == null) return;
        FileChooser chooser = chooser(t("exportProject"), "PDF Batch Studio", "*.pdfbatch");
        chooser.setInitialFileName("project.pdfbatch");
        File file = chooser.showSaveDialog(owner());
        if (file != null)
            runAsync(
                    "saving",
                    () -> {
                        ProjectArchive.exportProject(saved, file.toPath());
                        return true;
                    },
                    ignored -> operationLabel.setText(t("projectExported")));
    }

    @FXML
    private void importProject() {
        if (repository == null) return;
        File file =
                chooser(t("importProject"), "PDF Batch Studio", "*.pdfbatch")
                        .showOpenDialog(owner());
        if (file != null)
            runAsync(
                    "loading",
                    () -> ProjectArchive.importProject(file.toPath(), repository),
                    saved -> {
                        refreshLibrary();
                        projectPicker.setValue(saved);
                        loadProject(saved);
                    });
    }

    @FXML
    private void openHistoryResult() {
        var run = historyList.getSelectionModel().getSelectedItem();
        if (run != null && run.result() != null) open(Path.of(run.result()));
    }

    @FXML
    private void reuseProject() {
        var run = historyList.getSelectionModel().getSelectedItem();
        if (run != null && run.projectId() != null)
            repository.projects().stream()
                    .filter(p -> p.id().equals(run.projectId()))
                    .findFirst()
                    .ifPresent(this::loadProject);
    }

    @FXML
    private void historyErrors() {
        var run = historyList.getSelectionModel().getSelectedItem();
        if (run == null) return;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner());
        alert.setHeaderText(t("errors"));
        TextArea text = new TextArea(String.join("\n", repository.errors(run.id())));
        text.setEditable(false);
        text.setWrapText(true);
        alert.getDialogPane().setContent(text);
        alert.showAndWait();
    }

    @FXML
    private void clearHistory() {
        if (repository != null && confirm(t("clearHistoryQuestion"))) {
            repository.clearHistory();
            refreshLibrary();
        }
    }

    @FXML
    private void openResultZip() {
        if (viewModel.snapshot().exportedPath() != null) open(viewModel.snapshot().exportedPath());
    }

    @FXML
    private void openResultFolder() {
        if (viewModel.snapshot().exportedPath() != null)
            open(viewModel.snapshot().exportedPath().getParent());
    }

    private void changed() {
        if (suppress || !viewModel.snapshot().hasJob()) return;
        dirty = true;
        render(viewModel.invalidatePreview());
    }

    private <T> void runAsync(String description, Callable<T> work, Consumer<T> success) {
        if (busy || closed) return;
        busy = true;
        operationLabel.setText(t(description));
        render(viewModel.snapshot());
        operationCancelled = false;
        background.submit(
                () -> {
                    operationThread = Thread.currentThread();
                    try {
                        if (operationCancelled)
                            throw new de.appfabrik.pdfbatch.core.JobCancelledException();
                        T result = work.call();
                        Platform.runLater(
                                () -> {
                                    if (closed) return;
                                    busy = false;
                                    checking = false;
                                    operationLabel.setText(t("offline"));
                                    try {
                                        success.accept(result);
                                    } catch (Exception exception) {
                                        showError(exception);
                                    }
                                    render(viewModel.snapshot());
                                });
                    } catch (Exception exception) {
                        Platform.runLater(
                                () -> {
                                    if (closed) return;
                                    busy = false;
                                    checking = false;
                                    operationLabel.setText(t("offline"));
                                    showError(exception);
                                });
                    } finally {
                        operationThread = null;
                    }
                });
    }

    private void render(DesktopSnapshot snapshot) {
        JobStatus status = snapshot.status();
        boolean active = snapshot.isActive();
        boolean editable =
                !busy && !active && (status == JobStatus.DRAFT || status == JobStatus.READY);
        statusLabel.setText(t(status == null ? "status.EMPTY" : "status." + status));
        templatePathLabel.setText(
                selectedPdf == null ? t("noPdf") : selectedPdf.getFileName().toString());
        csvPathLabel.setText(
                selectedData == null ? t("noData") : selectedData.getFileName().toString());
        inspectionLabel.setText(
                snapshot.hasJob()
                        ? snapshot.pageCount()
                                + " "
                                + t("pages")
                                + " · "
                                + snapshot.pdfFields().size()
                                + " "
                                + t("fields")
                                + " · "
                                + snapshot.totalRows()
                                + " "
                                + t("rows")
                        : t("selectFiles"));
        progressBar.setProgress(
                snapshot.totalRows() == 0
                        ? 0
                        : (double) snapshot.processedRows() / snapshot.totalRows());
        progressDetailLabel.setText(
                snapshot.processedRows()
                        + " / "
                        + snapshot.totalRows()
                        + " · "
                        + snapshot.successfulRows()
                        + " "
                        + t("successful")
                        + " · "
                        + snapshot.failedRows()
                        + " "
                        + t("failed"));
        previewStateLabel.setText(t(dirty ? "previewStale" : "previewCurrent"));
        byte[] preview = snapshot.previewPng();
        int hash = java.util.Arrays.hashCode(preview);
        if (hash != previewHash) {
            previewHash = hash;
            previewImage.setImage(
                    preview == null ? null : new Image(new ByteArrayInputStream(preview)));
        }
        boolean error = snapshot.errorMessage() != null;
        errorBox.setVisible(error);
        errorBox.setManaged(error);
        errorCodeLabel.setText(error ? t("error") + " · " + snapshot.errorCode() : "");
        String key = "error." + snapshot.errorCode();
        errorMessageLabel.setText(
                error ? (messages.containsKey(key) ? t(key) : snapshot.errorMessage()) : "");
        resultBox.setVisible(snapshot.isComplete());
        resultBox.setManaged(snapshot.isComplete());
        resultLabel.setText(
                snapshot.exportedPath() == null
                        ? t("resultUnsaved")
                        : snapshot.exportedPath().toString());
        saveResultButton.setVisible(snapshot.exportedPath() == null);
        saveResultButton.setManaged(snapshot.exportedPath() == null);
        saveResultButton.setDisable(busy);
        openZipButton.setDisable(snapshot.exportedPath() == null);
        openFolderButton.setDisable(snapshot.exportedPath() == null);
        homeTab.setDisable(busy || active);
        historyTab.setDisable(busy || active);
        languagePicker.setDisable(busy || active);
        templateButton.setDisable(busy || snapshot.hasJob());
        csvButton.setDisable(busy || snapshot.hasJob());
        sheetPicker.setDisable(busy || snapshot.hasJob() || sheetPicker.getItems().isEmpty());
        templatePassword.setDisable(busy || active || (status != null && status.isTerminal()));
        outputPassword.setDisable(!editable);
        projectNameField.setDisable(busy || active);
        inspectButton.setDisable(
                busy || snapshot.hasJob() || selectedPdf == null || selectedData == null);
        boolean mapped = !selectedMappings().isEmpty();
        previewButton.setDisable(!editable || !mapped);
        preflightButton.setDisable(!editable || !mapped);
        saveProjectButton.setDisable(!editable || !mapped || repository == null);
        outputButton.setDisable(busy || !snapshot.hasJob() || active);
        startButton.setDisable(!editable || dirty || snapshot.previewPng() == null);
        cancelButton.setDisable((!active || status == JobStatus.CANCELLING || busy) && !checking);
        resetButton.setDisable(busy || active);
        filenamePatternField.setDisable(!editable);
        folderExport.setDisable(!editable);
        rowPicker.setDisable(!editable);
        pagePicker.setDisable(!editable);
        mappings.forEach(m -> m.columns().setDisable(!editable || !m.field().supported()));
    }

    private void showError(Throwable exception) {
        render(viewModel.recordError(exception));
    }

    private boolean confirm(String message) {
        Alert alert =
                new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(owner());
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void open(Path path) {
        try {
            if (!Files.exists(path)) throw new IllegalArgumentException(t("missingResult"));
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private FileChooser chooser(String title, String description, String... extensions) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extensions));
        return chooser;
    }

    private Window owner() {
        return rootPane.getScene() == null ? null : rootPane.getScene().getWindow();
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    public boolean confirmClose() {
        if (busy) return confirm(t("closeQuestion"));
        DesktopSnapshot snapshot = viewModel.snapshot();
        return !snapshot.hasJob() || snapshot.exportedPath() != null || confirm(t("closeQuestion"));
    }

    @Override
    public void close() {
        closed = true;
        if (pollTask != null) pollTask.cancel(false);
        background.shutdownNow();
        polling.shutdownNow();
        viewModel.close();
    }

    private record MappingControl(PdfFieldInfo field, ComboBox<String> columns) {}
}
