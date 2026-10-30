package ru.fazbear.campusdirectory;

import java.io.File;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import ru.fazbear.campusdirectory.io.ContactCsvException;
import ru.fazbear.campusdirectory.io.ContactCsvService;
import ru.fazbear.campusdirectory.io.CsvLoadResult;
import ru.fazbear.campusdirectory.io.CsvErrorCode;
import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.ContactValidator;
import ru.fazbear.campusdirectory.model.CorporateContact;
import ru.fazbear.campusdirectory.model.Editable;
import ru.fazbear.campusdirectory.service.ContactDirectory;
import ru.fazbear.campusdirectory.service.ContactGenerator;
import ru.fazbear.campusdirectory.service.TrieBenchmarkResult;
import ru.fazbear.campusdirectory.service.TrieBenchmarkService;
import ru.fazbear.campusdirectory.service.TreeMapBenchmarkService;
import ru.fazbear.campusdirectory.trie.ConcurrencyExperiment;
import ru.fazbear.campusdirectory.ui.ContactDialog;
import ru.fazbear.campusdirectory.ui.TrieCanvasView;

public final class ContactDirectoryApp extends Application {
    private static final int VISUALIZATION_NODE_LIMIT = 500;
    private static final int VISUALIZATION_DEPTH_LIMIT = 128;
    private static final int MAXIMUM_GENERATED_CONTACTS = 100_000;

    private ContactDirectory directory = new ContactDirectory();
    private final ContactCsvService csvService = new ContactCsvService();
    private final ContactGenerator generator = new ContactGenerator();
    private final TrieBenchmarkService benchmarkService = new TrieBenchmarkService();
    private final TableView<Contact> table = new TableView<>();
    private final TrieCanvasView trieView = new TrieCanvasView();
    private final TextField prefixField = new TextField();
    private final Label statusLabel = new Label();
    private final Label operationLabel = new Label();
    private final ProgressBar operationProgress = new ProgressBar(0);
    private final Button cancelOperationButton = new Button("Отменить");
    private final FlowPane dataActions = new FlowPane(10, 8);
    private final HBox searchActions = new HBox(10);
    private Task<?> activeTask;
    private Stage stage;
    private File csvDirectory;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        configureTable();

        var title = new Label("Справочник контактов");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        var subtitle = new Label(
                "Lab 2: собственное префиксное дерево, визуализация обхода и фоновые операции"
        );
        subtitle.setStyle("-fx-text-fill: #5f6368;");
        var header = new VBox(4, title, subtitle);
        header.setPadding(new Insets(20, 20, 10, 20));

        configureDataActions();
        configureSearchActions();
        configureOperationBar();

        var tablePane = new VBox(8, searchActions, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        var visualizationPane = new VBox(8, new Label("Внутреннее устройство Trie"), trieView);
        VBox.setVgrow(trieView, Priority.ALWAYS);
        tablePane.setPadding(new Insets(0, 8, 0, 20));
        visualizationPane.setPadding(new Insets(0, 20, 0, 8));

        var splitPane = new SplitPane(tablePane, visualizationPane);
        splitPane.setDividerPositions(0.58);

        var operationBar = new HBox(10, operationLabel, operationProgress, cancelOperationButton);
        operationBar.setAlignment(Pos.CENTER_LEFT);
        var footer = new VBox(6, statusLabel, operationBar);
        footer.setPadding(new Insets(8, 20, 14, 20));

        var root = new BorderPane();
        root.setTop(new VBox(header, dataActions));
        root.setCenter(splitPane);
        root.setBottom(footer);

        refreshAll();
        stage.setTitle("Campus Directory — лабораторная работа №2");
        stage.setMinWidth(1120);
        stage.setMinHeight(680);
        stage.setScene(new Scene(root, 1500, 860));
        stage.setOnCloseRequest(event -> {
            if (activeTask != null) {
                event.consume();
                statusLabel.setText("Дождитесь завершения операции или отмените её перед закрытием окна.");
            }
        });
        stage.show();
    }

    private void configureDataActions() {
        Button addButton = new Button("Добавить");
        Button editButton = new Button("Изменить");
        Button deleteButton = new Button("Удалить");
        Button generateButton = new Button("Сгенерировать");
        Button benchmarkButton = new Button("Замеры");
        Button concurrencyButton = new Button("Гонка потоков");
        Button loadButton = new Button("Загрузить CSV");
        Button saveButton = new Button("Сохранить CSV");

        addButton.setDefaultButton(true);
        editButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !(table.getSelectionModel().getSelectedItem() instanceof Editable),
                table.getSelectionModel().selectedItemProperty()
        ));
        deleteButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !(table.getSelectionModel().getSelectedItem() instanceof Editable),
                table.getSelectionModel().selectedItemProperty()
        ));

        addButton.setOnAction(event -> addContact());
        editButton.setOnAction(event -> editContact());
        deleteButton.setOnAction(event -> deleteContact());
        generateButton.setOnAction(event -> generateContacts());
        benchmarkButton.setOnAction(event -> runBenchmarks());
        concurrencyButton.setOnAction(event -> runConcurrencyExperiment());
        loadButton.setOnAction(event -> loadContacts());
        saveButton.setOnAction(event -> saveContacts());

        dataActions.getChildren().addAll(
                addButton, editButton, deleteButton,
                generateButton, benchmarkButton, concurrencyButton, loadButton, saveButton
        );
        dataActions.setAlignment(Pos.CENTER_LEFT);
        dataActions.setPadding(new Insets(10, 20, 12, 20));
    }

    private void configureSearchActions() {
        prefixField.setPromptText("Имя или префикс");
        HBox.setHgrow(prefixField, Priority.ALWAYS);
        Button findButton = new Button("Найти точно");
        Button autocompleteButton = new Button("Автодополнение");
        Button showAllButton = new Button("Показать все");
        findButton.setOnAction(event -> findContact());
        autocompleteButton.setOnAction(event -> autocomplete());
        showAllButton.setOnAction(event -> refreshAll());
        prefixField.setOnAction(event -> autocomplete());
        searchActions.getChildren().addAll(prefixField, findButton, autocompleteButton, showAllButton);
        searchActions.setAlignment(Pos.CENTER_LEFT);
    }

    private void configureOperationBar() {
        operationProgress.setPrefWidth(240);
        operationProgress.setVisible(false);
        operationProgress.setManaged(false);
        cancelOperationButton.setVisible(false);
        cancelOperationButton.setManaged(false);
        cancelOperationButton.setOnAction(event -> {
            if (activeTask != null) {
                activeTask.cancel();
            }
        });
        statusLabel.setStyle("-fx-text-fill: #5f6368;");
        operationLabel.setStyle("-fx-text-fill: #5f6368;");
    }

    private void configureTable() {
        addColumn("Тип", 110, contact -> contact.getType().getDisplayName());
        addColumn("Имя", 180, Contact::getName);
        addColumn("Телефон", 145, Contact::getPhone);
        addColumn("E-mail", 180, Contact::getEmail);
        addColumn("Организация", 160, Contact::getOrganization);
        addColumn("Должность", 130,
                contact -> contact instanceof CorporateContact corporate ? corporate.getPosition() : "");
        addColumn("Внутренний №", 100,
                contact -> contact instanceof CorporateContact corporate
                        ? Integer.toString(corporate.getExtension()) : "");
        table.setPlaceholder(new Label("Контакты пока не добавлены"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void addColumn(String title, double minimumWidth, Function<Contact, String> extractor) {
        var column = new TableColumn<Contact, String>(title);
        column.setMinWidth(minimumWidth);
        column.setCellValueFactory(data -> new SimpleStringProperty(extractor.apply(data.getValue())));
        table.getColumns().add(column);
    }

    private void addContact() {
        ContactDialog.showCreate(stage).ifPresent(contact -> {
            directory.add(contact);
            refreshAll();
            selectContact(contact);
        });
    }

    private void editContact() {
        Contact selected = table.getSelectionModel().getSelectedItem();
        if (!(selected instanceof Editable)) {
            return;
        }
        ContactDialog.showEdit(stage, selected).ifPresent(replacement -> {
            directory.update(selected, replacement);
            refreshAll();
            selectContact(replacement);
        });
    }

    private void deleteContact() {
        Contact selected = table.getSelectionModel().getSelectedItem();
        if (!(selected instanceof Editable)) {
            return;
        }
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(stage);
        confirmation.setTitle("Удаление контакта");
        confirmation.setHeaderText("Удалить «" + selected.getName() + "»?");
        confirmation.setContentText("Контакт будет удалён из Trie.");
        ButtonType answer = confirmation.showAndWait().orElse(ButtonType.CANCEL);
        if (answer.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
            directory.remove(selected);
            refreshAll();
        }
    }

    private void findContact() {
        String name = prefixField.getText();
        if (name.isBlank()) {
            showError("Введите имя", "Для точного поиска требуется полное имя контакта.");
            return;
        }
        if (name.strip().length() > ContactValidator.MAXIMUM_NAME_LENGTH) {
            showError("Слишком длинное имя", "Допустимо до "
                    + ContactValidator.MAXIMUM_NAME_LENGTH + " символов.");
            return;
        }
        Contact found = directory.findFirstByName(name);
        refreshVisualization();
        if (found == null) {
            table.getItems().clear();
            table.getSelectionModel().clearSelection();
            table.setPlaceholder(new Label("Контакт с таким полным именем не найден"));
            statusLabel.setText("Контакт не найден; посещённый путь подсвечен на Trie");
        } else {
            table.getItems().setAll(found);
            table.sort();
            selectContact(found);
            statusLabel.setText("Найден первый контакт с точным именем: " + found.getName());
        }
    }

    private void autocomplete() {
        String prefix = prefixField.getText();
        List<Contact> matches = directory.autocomplete(prefix);
        int count = directory.countByPrefix(prefix);
        table.getItems().setAll(matches);
        table.sort();
        table.setPlaceholder(new Label("По этому префиксу контакты не найдены"));
        statusLabel.setText("Префикс «%s»: найдено контактов — %d".formatted(prefix.strip(), count));
        refreshVisualization();
    }

    private void generateContacts() {
        var dialog = new TextInputDialog("1000");
        dialog.initOwner(stage);
        dialog.setTitle("Генерация набора данных");
        dialog.setHeaderText("Количество генерируемых контактов");
        dialog.setContentText("От 1 до " + MAXIMUM_GENERATED_CONTACTS + ":");
        dialog.showAndWait().ifPresent(value -> parseGenerationCount(value));
    }

    private void parseGenerationCount(String value) {
        int count;
        try {
            count = Integer.parseInt(value.strip());
        } catch (NumberFormatException error) {
            showError("Некорректное количество", "Введите целое число.");
            return;
        }
        if (count < 1 || count > MAXIMUM_GENERATED_CONTACTS) {
            showError("Некорректное количество", "Допустимо от 1 до " + MAXIMUM_GENERATED_CONTACTS + ".");
            return;
        }
        if (confirmReplacement("Сгенерировать новый набор?")) {
            startGeneration(count);
        }
    }

    private boolean confirmReplacement(String header) {
        if (directory.size() == 0) {
            return true;
        }
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(stage);
        confirmation.setTitle("Замена набора контактов");
        confirmation.setHeaderText(header);
        confirmation.setContentText("Текущие контакты (" + directory.size()
                + ") будут заменены. Сохраните их в CSV, если хотите использовать их позже.");
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void startGeneration(int count) {
        var task = new Task<ContactDirectory>() {
            @Override
            protected ContactDirectory call() {
                updateMessage("Генерация контактов...");
                Contact[] generated = generator.generate(
                        count,
                        this::isCancelled,
                        completed -> updateProgress(completed, count * 2L)
                );
                var generatedDirectory = new ContactDirectory();
                updateMessage("Построение Trie...");
                for (int index = 0; index < generated.length; index++) {
                    if (isCancelled()) {
                        throw new CancellationException("Генерация отменена");
                    }
                    generatedDirectory.add(generated[index]);
                    if ((index + 1) % 100 == 0 || index + 1 == generated.length) {
                        updateProgress(count + index + 1L, count * 2L);
                    }
                }
                return generatedDirectory;
            }
        };
        task.setOnSucceeded(event -> {
            directory = task.getValue();
            finishOperation();
            refreshAll();
            showInformation("Генерация завершена", "Создано контактов: " + count);
        });
        configureTaskFailure(task, "Не удалось сгенерировать контакты");
        startTask(task, true);
    }

    private void runBenchmarks() {
        var task = new Task<TrieBenchmarkResult[]>() {
            @Override
            protected TrieBenchmarkResult[] call() {
                var results = new TrieBenchmarkResult[4];
                updateMessage("Замер на 10 000 контактов...");
                updateProgress(0, results.length);
                results[0] = benchmarkService.run(10_000, this::isCancelled);
                updateProgress(1, results.length);
                updateMessage("TreeMap: 10 000 контактов...");
                results[1] = new TreeMapBenchmarkService().run(10_000, this::isCancelled);
                updateProgress(2, results.length);
                updateMessage("Замер на 100 000 контактов...");
                results[2] = benchmarkService.run(100_000, this::isCancelled);
                updateProgress(3, results.length);
                updateMessage("TreeMap: 100 000 контактов...");
                results[3] = new TreeMapBenchmarkService().run(100_000, this::isCancelled);
                updateProgress(4, results.length);
                return results;
            }
        };
        task.setOnSucceeded(event -> {
            TrieBenchmarkResult[] results = task.getValue();
            finishOperation();
            showBenchmarkResults(results);
        });
        configureTaskFailure(task, "Не удалось выполнить замеры");
        startTask(task, true);
    }

    private void runConcurrencyExperiment() {
        var task = new Task<ConcurrencyExperiment.Result[]>() {
            @Override
            protected ConcurrencyExperiment.Result[] call() throws Exception {
                var experiment = new ConcurrencyExperiment();
                updateMessage("8 писателей: незащищённое дерево...");
                updateProgress(0, 2);
                var unsafe = experiment.run(false, 8, 2000, this::isCancelled);
                updateProgress(1, 2);
                updateMessage("8 писателей: защищённое дерево...");
                var safe = experiment.run(true, 8, 2000, this::isCancelled);
                if (!safe.consistent()) {
                    throw new IllegalStateException("Защищённая структура не прошла проверку: " + safe);
                }
                updateProgress(2, 2);
                return new ConcurrencyExperiment.Result[]{unsafe, safe};
            }
        };
        task.setOnSucceeded(event -> {
            var results = task.getValue();
            finishOperation();
            var text = new StringBuilder("8 потоков одновременно записывают по 2000 уникальных значений.\n"
                    + "В обоих случаях используются одинаковые алгоритмы узлов и массивов.\n\n");
            for (var result : results) {
                text.append(result.synchronizedAccess() ? "С synchronized" : "Без синхронизации")
                        .append(":\nОжидалось: ").append(result.expected())
                        .append("; size: ").append(result.size()).append("; в поддереве: ")
                        .append(result.subtreeCount()).append("; в обходе: ").append(result.stored())
                        .append("; не найдено: ").append(result.missing()).append("; ошибок: ")
                        .append(result.errors()).append("\n").append(result.details()).append("\n\n");
            }
            text.append(results[0].consistent()
                    ? "В этом запуске гонка не проявилась. Повторите эксперимент.\n"
                    : "Без защиты выявлено повреждение данных или счётчиков.\n");
            text.append("Успешный запуск сам по себе не доказывает отсутствие гонок.\n"
                    + "Значение -1 означает, что проверку прервало повреждение структуры.\n"
                    + "Синхронизация защищает каждую отдельную операцию целиком.\n"
                    + "Эксперимент использует отдельные деревья; контакты справочника не изменяются.");
            var area = new TextArea(text.toString());
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefColumnCount(90);
            area.setPrefRowCount(20);
            var alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(stage);
            alert.setTitle("Конкурентная запись");
            alert.setHeaderText("Незащищённая и защищённая структура");
            alert.getDialogPane().setContent(area);
            alert.setResizable(true);
            alert.showAndWait();
        });
        configureTaskFailure(task, "Не удалось выполнить эксперимент");
        startTask(task, true);
    }

    private void loadContacts() {
        FileChooser chooser = csvChooser("Загрузить контакты из CSV");
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        csvDirectory = file.getParentFile();
        if (!confirmReplacement("Загрузить новый набор из CSV?")) {
            return;
        }
        var task = new Task<LoadOutcome>() {
            @Override
            protected LoadOutcome call() throws ContactCsvException {
                updateMessage("Чтение CSV...");
                updateProgress(-1, 1);
                CsvLoadResult result = csvService.load(
                        file.toPath(),
                        this::isCancelled,
                        (completed, total) -> updateProgress(completed, Math.max(total, 1))
                );
                if (isCancelled()) {
                    throw new CancellationException("Загрузка отменена");
                }
                if (result.contacts().isEmpty() && !result.errors().isEmpty()) {
                    throw new ContactCsvException(CsvErrorCode.INVALID_CONTACT,
                            "В файле нет корректных контактов. Текущий набор сохранён. Ошибок: "
                                    + result.errors().size());
                }
                updateMessage("Построение Trie...");
                updateProgress(-1, 1);
                var loadedDirectory = new ContactDirectory();
                loadedDirectory.replaceAll(result.contacts(), this::isCancelled,
                        completed -> updateProgress(completed, Math.max(result.contacts().size(), 1)));
                return new LoadOutcome(loadedDirectory, result);
            }
        };
        task.setOnSucceeded(event -> {
            LoadOutcome outcome = task.getValue();
            directory = outcome.directory();
            finishOperation();
            refreshAll();
            if (!outcome.result().errors().isEmpty()) {
                showImportWarnings(outcome.result());
            }
        });
        configureTaskFailure(task, "Не удалось загрузить CSV");
        startTask(task, true);
    }

    private void saveContacts() {
        FileChooser chooser = csvChooser("Сохранить контакты в CSV");
        chooser.setInitialFileName("contacts.csv");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        csvDirectory = file.getParentFile();
        List<Contact> snapshot = directory.getAll();
        var task = new Task<Void>() {
            @Override
            protected Void call() throws ContactCsvException {
                updateMessage("Сохранение CSV...");
                updateProgress(-1, 1);
                csvService.save(file.toPath(), snapshot);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            finishOperation();
            showInformation("Файл сохранён", "Сохранено контактов: " + snapshot.size());
        });
        configureTaskFailure(task, "Не удалось сохранить CSV");
        startTask(task, false);
    }

    private FileChooser csvChooser(String title) {
        var chooser = new FileChooser();
        chooser.setTitle(title);
        if (csvDirectory == null || !csvDirectory.isDirectory()) {
            csvDirectory = new File(System.getProperty("user.dir"));
            for (File candidate : new File[]{new File("samples"), new File("campus-directory", "samples")}) {
                if (candidate.isDirectory()) {
                    csvDirectory = candidate.getAbsoluteFile();
                    break;
                }
            }
        }
        chooser.setInitialDirectory(csvDirectory);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV-файлы", "*.csv"));
        return chooser;
    }

    private void startTask(Task<?> task, boolean cancellable) {
        if (activeTask != null) {
            throw new IllegalStateException("Фоновая операция уже выполняется");
        }
        activeTask = task;
        dataActions.setDisable(true);
        searchActions.setDisable(true);
        operationProgress.setVisible(true);
        operationProgress.setManaged(true);
        cancelOperationButton.setVisible(cancellable);
        cancelOperationButton.setManaged(cancellable);
        operationProgress.progressProperty().bind(task.progressProperty());
        operationLabel.textProperty().bind(task.messageProperty());
        task.setOnCancelled(event -> finishOperation());
        var worker = new Thread(task, "campus-directory-background-operation");
        worker.setDaemon(true);
        worker.start();
    }

    private void configureTaskFailure(Task<?> task, String header) {
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            finishOperation();
            if (error instanceof ContactCsvException csvError) {
                showError(header, csvError.getCode() + ": " + csvError.getMessage());
            } else {
                showError(header, error == null ? "Неизвестная ошибка" : error.getMessage());
            }
        });
    }

    private void finishOperation() {
        operationProgress.progressProperty().unbind();
        operationLabel.textProperty().unbind();
        operationProgress.setProgress(0);
        operationLabel.setText("");
        operationProgress.setVisible(false);
        operationProgress.setManaged(false);
        cancelOperationButton.setVisible(false);
        cancelOperationButton.setManaged(false);
        dataActions.setDisable(false);
        searchActions.setDisable(false);
        activeTask = null;
    }

    private void refreshAll() {
        table.getItems().setAll(directory.getAll());
        table.sort();
        table.setPlaceholder(new Label("Контакты пока не добавлены"));
        statusLabel.setText("Контактов: " + directory.size());
        refreshVisualization();
    }

    private void selectContact(Contact contact) {
        for (int index = 0; index < table.getItems().size(); index++) {
            if (table.getItems().get(index) == contact) {
                table.getSelectionModel().select(index);
                table.scrollTo(index);
                return;
            }
        }
    }

    private void refreshVisualization() {
        trieView.setSnapshot(directory.getTrieSnapshot(VISUALIZATION_NODE_LIMIT, VISUALIZATION_DEPTH_LIMIT));
    }

    private void showImportWarnings(CsvLoadResult result) {
        var details = new TextArea(
                result.errors().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(System.lineSeparator()))
        );
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefColumnCount(70);
        details.setPrefRowCount(Math.min(12, result.errors().size() + 1));

        var alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(stage);
        alert.setTitle("CSV загружен с предупреждениями");
        alert.setHeaderText("Загружено: %d, пропущено: %d".formatted(
                result.contacts().size(), result.errors().size()
        ));
        alert.setContentText("Повреждённые строки не были добавлены.");
        alert.getDialogPane().setExpandableContent(details);
        alert.getDialogPane().setExpanded(true);
        alert.showAndWait();
    }

    private void showBenchmarkResults(TrieBenchmarkResult[] results) {
        var text = new StringBuilder();
        text.append(String.format(
                "%-9s %-10s %12s %12s %12s %14s %14s%n",
                "Хранилище", "Контакты", "Вставка", "Поиск", "Удаление", "Автодоп.", "Поддерево"
        ));
        for (int index = 0; index < results.length; index++) {
            TrieBenchmarkResult result = results[index];
            text.append(String.format(
                    "%-9s %,10d %12.3f %12.3f %12.3f %14.3f %14.3f%n",
                    index % 2 == 0 ? "Trie" : "TreeMap",
                    result.contactCount(),
                    result.insertionMicrosPerOperation(),
                    result.searchMicrosPerOperation(),
                    result.removalMicrosPerOperation(),
                    result.autocompleteMicrosPerOperation(),
                    result.subtreeCountMicrosPerOperation()
            ));
        }
        text.append("\nМедиана ").append(results[0].measurementRuns())
                .append(" прогонов после 2 прогревочных.\n")
                .append("Вставка/поиск/удаление: N вызовов; автодополнение/подсчёт: ")
                .append(results[0].additionalOperationCount()).append(" вызовов.\n")
                .append("Для автодополнения используются разные двухсимвольные префиксы.\n")
                .append("Время автодополнения включает сбор найденных контактов.\n")
                .append("Наборы одинаковые, имена уникальны; TreeMap используется только в эксперименте.\n")
                .append("Trie поддерживает счётчики поддеревьев; TreeMap считает совпадения обходом.\n")
                .append("Trie измеряется с синхронизацией, TreeMap — без неё.\n")
                .append("Замеры зависят от JIT, GC и нагрузки; это учебное сравнение.");

        var area = new TextArea(text.toString());
        area.setEditable(false);
        area.setStyle("-fx-font-family: monospace;");
        area.setPrefColumnCount(100);
        area.setPrefRowCount(16);
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("Замеры Trie");
        alert.setHeaderText("Среднее время одного вызова, микросекунды (медиана прогонов)");
        alert.setResizable(true);
        alert.getDialogPane().setContent(area);
        alert.showAndWait();
    }

    private void showError(String header, String message) {
        showAlert(Alert.AlertType.ERROR, "Ошибка", header, message);
    }

    private void showInformation(String header, String message) {
        showAlert(Alert.AlertType.INFORMATION, "Готово", header, message);
    }

    private void showAlert(Alert.AlertType type, String title, String header, String message) {
        var alert = new Alert(type);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private record LoadOutcome(ContactDirectory directory, CsvLoadResult result) {
    }
}
