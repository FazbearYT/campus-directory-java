package ru.fazbear.campusdirectory.ui;

import java.util.List;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.ContactType;
import ru.fazbear.campusdirectory.model.CorporateContact;
import ru.fazbear.campusdirectory.model.Editable;
import ru.fazbear.campusdirectory.model.PersonalContact;

public final class ContactDialog {
    private final Dialog<Contact> dialog = new Dialog<>();
    private final ComboBox<ContactType> typeBox = new ComboBox<>();
    private final TextField nameField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField organizationField = new TextField();
    private final TextField positionField = new TextField();
    private final TextField extensionField = new TextField();
    private final Label positionLabel = new Label("Должность:");
    private final Label extensionLabel = new Label("Внутренний номер:");
    private final Label errorLabel = new Label();
    private final ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
    private final ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

    private ContactDialog(Window owner, Contact contact) {
        dialog.initOwner(owner);
        dialog.setTitle(contact == null ? "Добавление контакта" : "Изменение контакта");
        dialog.setHeaderText(contact == null ? "Новый редактируемый контакт" : contact.getName());
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

        typeBox.setItems(FXCollections.observableArrayList(ContactType.PERSONAL, ContactType.CORPORATE));
        typeBox.getSelectionModel().select(ContactType.PERSONAL);
        typeBox.setMaxWidth(Double.MAX_VALUE);

        nameField.setPromptText("Например, Анна Смирнова");
        phoneField.setPromptText("Например, +7 913 555-12-34");
        emailField.setPromptText("Необязательное поле");
        organizationField.setPromptText("Необязательное для личного контакта");
        positionField.setPromptText("Например, инженер");
        extensionField.setPromptText("Целое число от 1 до 999999");

        if (contact != null) {
            if (!(contact instanceof Editable)) {
                throw new IllegalArgumentException("Контакт доступен только для чтения");
            }
            fillFrom(contact);
            typeBox.setDisable(true);
        }

        var grid = createGrid();
        errorLabel.setStyle("-fx-text-fill: #b3261e;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(520);

        var content = new VBox(12, grid, errorLabel);
        content.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(580);

        typeBox.valueProperty().addListener((observable, previous, current) -> updateCorporateFields());
        updateCorporateFields();

        Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.addEventFilter(ActionEvent.ACTION, event -> {
            List<String> errors = validateForm();
            if (!errors.isEmpty()) {
                errorLabel.setText(String.join(System.lineSeparator(), errors));
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == saveButton ? createContact() : null);
    }

    public static Optional<Contact> showCreate(Window owner) {
        return new ContactDialog(owner, null).dialog.showAndWait();
    }

    public static Optional<Contact> showEdit(Window owner, Contact contact) {
        return new ContactDialog(owner, contact).dialog.showAndWait();
    }

    private GridPane createGrid() {
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        var labelColumn = new ColumnConstraints();
        var fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        fieldColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        grid.addRow(0, new Label("Тип:"), typeBox);
        grid.addRow(1, new Label("Имя:"), nameField);
        grid.addRow(2, new Label("Телефон:"), phoneField);
        grid.addRow(3, new Label("E-mail:"), emailField);
        grid.addRow(4, new Label("Организация:"), organizationField);
        grid.addRow(5, positionLabel, positionField);
        grid.addRow(6, extensionLabel, extensionField);

        for (Node node : grid.getChildren()) {
            if (node instanceof TextField || node instanceof ComboBox<?>) {
                GridPane.setFillWidth(node, true);
                ((Region) node).setMaxWidth(Double.MAX_VALUE);
            }
        }
        return grid;
    }

    private void fillFrom(Contact contact) {
        typeBox.getSelectionModel().select(contact.getType());
        nameField.setText(contact.getName());
        phoneField.setText(contact.getPhone());
        emailField.setText(contact.getEmail());
        organizationField.setText(contact.getOrganization());
        positionField.setText(contact.getPosition());
        extensionField.setText(contact.getExtensionText());
    }

    private void updateCorporateFields() {
        boolean corporate = typeBox.getValue() == ContactType.CORPORATE;
        positionLabel.setVisible(corporate);
        positionLabel.setManaged(corporate);
        positionField.setVisible(corporate);
        positionField.setManaged(corporate);
        extensionLabel.setVisible(corporate);
        extensionLabel.setManaged(corporate);
        extensionField.setVisible(corporate);
        extensionField.setManaged(corporate);
        errorLabel.setText("");
    }

    private List<String> validateForm() {
        try {
            Contact contact = createContact();
            return ((Editable) contact).validate();
        } catch (IllegalArgumentException error) {
            return List.of(error.getMessage());
        }
    }

    private Contact createContact() {
        if (typeBox.getValue() == ContactType.CORPORATE) {
            int extension;
            try {
                extension = Integer.parseInt(extensionField.getText().strip());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Внутренний номер должен быть целым числом");
            }
            return new CorporateContact(
                    nameField.getText(),
                    phoneField.getText(),
                    emailField.getText(),
                    organizationField.getText(),
                    positionField.getText(),
                    extension
            );
        }

        return new PersonalContact(
                nameField.getText(),
                phoneField.getText(),
                emailField.getText(),
                organizationField.getText()
        );
    }
}
