package ru.fazbear.campusdirectory;

import java.io.File;
import java.util.function.Function;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import ru.fazbear.campusdirectory.io.ContactCsvException;
import ru.fazbear.campusdirectory.io.ContactCsvService;
import ru.fazbear.campusdirectory.io.CsvLoadResult;
import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.CorporateContact;
import ru.fazbear.campusdirectory.model.Editable;
import ru.fazbear.campusdirectory.service.ContactDirectory;
import ru.fazbear.campusdirectory.ui.ContactDialog;

public final class ContactDirectoryApp extends Application {
    private final ContactDirectory directory = new ContactDirectory();
    private final ContactCsvService csvService = new ContactCsvService();
    private final TableView<Contact> table = new TableView<>();
    private final Label statusLabel = new Label();
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        configureTable();

        var title = new Label("Справочник контактов");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        var subtitle = new Label(
                "Личные и корпоративные контакты можно изменять. Аварийные загружаются только из CSV."
        );
        subtitle.setStyle("-fx-text-fill: #5f6368;");

        var header = new VBox(4, title, subtitle);
        header.setPadding(new Insets(20, 20, 12, 20));

        Button addButton = new Button("Добавить");
        Button editButton = new Button("Изменить");
        Button loadButton = new Button("Загрузить CSV");
        Button saveButton = new Button("Сохранить CSV");

        addButton.setDefaultButton(true);
        editButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !(table.getSelectionModel().getSelectedItem() instanceof Editable),
                table.getSelectionModel().selectedItemProperty()
        ));

        addButton.setOnAction(event -> addContact());
        editButton.setOnAction(event -> editContact());
        loadButton.setOnAction(event -> loadContacts());
        saveButton.setOnAction(event -> saveContacts());

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var actions = new HBox(10, addButton, editButton, spacer, loadButton, saveButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(12, 20, 12, 20));

        statusLabel.setPadding(new Insets(8, 20, 12, 20));
        statusLabel.setStyle("-fx-text-fill: #5f6368;");

        var root = new BorderPane();
        root.setTop(new VBox(header, actions));
        root.setCenter(table);
        root.setBottom(statusLabel);
        BorderPane.setMargin(table, new Insets(0, 20, 0, 20));

        refreshTable();
        stage.setTitle("Campus Directory — лабораторная работа №1");
        stage.setMinWidth(900);
        stage.setMinHeight(520);
        stage.setScene(new Scene(root, 1180, 680));
        stage.show();
    }

    private void configureTable() {
        addColumn("Тип", 120, contact -> contact.getType().getDisplayName());
        addColumn("Имя", 180, Contact::getName);
        addColumn("Телефон", 150, Contact::getPhone);
        addColumn("E-mail", 190, Contact::getEmail);
        addColumn("Организация", 180, Contact::getOrganization);
        addColumn(
                "Должность",
                150,
                contact -> contact instanceof CorporateContact corporate ? corporate.getPosition() : ""
        );
        addColumn(
                "Внутренний №",
                110,
                contact -> contact instanceof CorporateContact corporate
                        ? Integer.toString(corporate.getExtension())
                        : ""
        );
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
            refreshTable();
            table.getSelectionModel().select(contact);
        });
    }

    private void editContact() {
        Contact selected = table.getSelectionModel().getSelectedItem();
        if (!(selected instanceof Editable)) {
            return;
        }

        int index = table.getSelectionModel().getSelectedIndex();
        ContactDialog.showEdit(stage, selected).ifPresent(replacement -> {
            directory.update(index, replacement);
            refreshTable();
            table.getSelectionModel().select(index);
        });
    }

    private void loadContacts() {
        FileChooser chooser = csvChooser("Загрузить контакты из CSV");
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        try {
            CsvLoadResult result = csvService.load(file.toPath());
            directory.replaceAll(result.contacts());
            refreshTable();
            if (!result.errors().isEmpty()) {
                showImportWarnings(result);
            }
        } catch (ContactCsvException error) {
            showError("Не удалось загрузить CSV", error.getCode() + ": " + error.getMessage());
        }
    }

    private void saveContacts() {
        FileChooser chooser = csvChooser("Сохранить контакты в CSV");
        chooser.setInitialFileName("contacts.csv");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        try {
            csvService.save(file.toPath(), directory.getAll());
            showInformation("Файл сохранён", "Сохранено контактов: " + directory.size());
        } catch (ContactCsvException error) {
            showError("Не удалось сохранить CSV", error.getCode() + ": " + error.getMessage());
        }
    }

    private FileChooser csvChooser(String title) {
        var chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV-файлы", "*.csv"));
        return chooser;
    }

    private void refreshTable() {
        table.getItems().setAll(directory.getAll());
        statusLabel.setText("Контактов: " + directory.size());
    }

    private void showImportWarnings(CsvLoadResult result) {
        var details = new TextArea(
                result.errors().stream()
                        .map(Object::toString)
                        .reduce((left, right) -> left + System.lineSeparator() + right)
                        .orElse("")
        );
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefColumnCount(70);
        details.setPrefRowCount(Math.min(12, result.errors().size() + 1));

        var alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(stage);
        alert.setTitle("CSV загружен с предупреждениями");
        alert.setHeaderText("Загружено: %d, пропущено: %d".formatted(
                result.contacts().size(),
                result.errors().size()
        ));
        alert.setContentText("Повреждённые строки не были добавлены.");
        alert.getDialogPane().setExpandableContent(details);
        alert.getDialogPane().setExpanded(true);
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
}
