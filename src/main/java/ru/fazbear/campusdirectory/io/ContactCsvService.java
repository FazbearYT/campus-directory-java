package ru.fazbear.campusdirectory.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.ContactType;
import ru.fazbear.campusdirectory.model.ContactValidator;
import ru.fazbear.campusdirectory.model.CorporateContact;
import ru.fazbear.campusdirectory.model.EmergencyContact;
import ru.fazbear.campusdirectory.model.PersonalContact;

public final class ContactCsvService {
    private static final char DELIMITER = ';';
    private static final List<String> HEADER = List.of(
            "type",
            "name",
            "phone",
            "email",
            "organization",
            "position",
            "extension"
    );

    public CsvLoadResult load(Path path) throws ContactCsvException {
        return load(path, () -> false, (completed, total) -> { });
    }

    public CsvLoadResult load(
            Path path,
            BooleanSupplier cancelled,
            CsvProgressListener progress
    ) throws ContactCsvException {
        var contacts = new ArrayList<Contact>();
        var errors = new ArrayList<CsvRowError>();
        long totalRows = countDataRows(path, cancelled);
        progress.update(0, totalRows);

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            validateHeader(headerLine);

            String line;
            int lineNumber = 1;
            long completedRows = 0;
            while ((line = reader.readLine()) != null) {
                if (cancelled.getAsBoolean()) {
                    throw new CancellationException("Загрузка CSV отменена");
                }
                lineNumber++;
                completedRows++;
                if (line.isBlank()) {
                    progress.update(completedRows, totalRows);
                    continue;
                }

                try {
                    contacts.add(parseContact(line));
                } catch (ContactCsvException error) {
                    errors.add(new CsvRowError(lineNumber, error.getCode(), error.getMessage()));
                }
                progress.update(completedRows, totalRows);
            }
        } catch (IOException error) {
            throw new ContactCsvException(
                    CsvErrorCode.IO_ERROR,
                    "Не удалось прочитать файл: " + path,
                    error
            );
        }

        return new CsvLoadResult(contacts, errors);
    }

    private long countDataRows(Path path, BooleanSupplier cancelled) throws ContactCsvException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            if (reader.readLine() == null) {
                return 0;
            }
            long count = 0;
            while (reader.readLine() != null) {
                if (cancelled.getAsBoolean()) {
                    throw new CancellationException("Загрузка CSV отменена");
                }
                count++;
            }
            return count;
        } catch (IOException error) {
            throw new ContactCsvException(
                    CsvErrorCode.IO_ERROR,
                    "Не удалось прочитать файл: " + path,
                    error
            );
        }
    }

    public void save(Path path, List<Contact> contacts) throws ContactCsvException {
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent == null) {
            throw new ContactCsvException(CsvErrorCode.IO_ERROR, "Не удалось определить папку файла");
        }

        Path temporaryFile = null;
        try {
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, ".contacts-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                writer.write(String.join(Character.toString(DELIMITER), HEADER));
                writer.newLine();

                for (Contact contact : contacts) {
                    validateBeforeSave(contact);
                    writer.write(formatContact(contact));
                    writer.newLine();
                }
            }

            moveReplacing(temporaryFile, absolutePath);
            temporaryFile = null;
        } catch (IOException error) {
            throw new ContactCsvException(
                    CsvErrorCode.IO_ERROR,
                    "Не удалось сохранить файл: " + path,
                    error
            );
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    temporaryFile.toFile().deleteOnExit();
                }
            }
        }
    }

    private void validateHeader(String headerLine) throws ContactCsvException {
        if (headerLine == null) {
            throw new ContactCsvException(CsvErrorCode.MISSING_HEADER, "Файл пуст");
        }

        String normalized = headerLine.startsWith("\uFEFF") ? headerLine.substring(1) : headerLine;
        List<String> fields = parseLine(normalized);
        if (!fields.equals(HEADER)) {
            throw new ContactCsvException(
                    CsvErrorCode.MISSING_HEADER,
                    "Ожидается заголовок: " + String.join(Character.toString(DELIMITER), HEADER)
            );
        }
    }

    private Contact parseContact(String line) throws ContactCsvException {
        List<String> fields = parseLine(line);
        if (fields.size() != HEADER.size()) {
            throw new ContactCsvException(
                    CsvErrorCode.WRONG_FIELD_COUNT,
                    "Ожидалось полей: %d, получено: %d".formatted(HEADER.size(), fields.size())
            );
        }

        ContactType type;
        try {
            type = ContactType.valueOf(fields.get(0).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new ContactCsvException(CsvErrorCode.UNKNOWN_TYPE, "Неизвестный тип: " + fields.get(0));
        }

        Contact contact = switch (type) {
            case PERSONAL -> {
                requireEmptyCorporateFields(fields, type);
                yield new PersonalContact(fields.get(1), fields.get(2), fields.get(3), fields.get(4));
            }
            case EMERGENCY -> {
                requireEmptyCorporateFields(fields, type);
                yield new EmergencyContact(fields.get(1), fields.get(2), fields.get(3), fields.get(4));
            }
            case CORPORATE -> new CorporateContact(
                    fields.get(1),
                    fields.get(2),
                    fields.get(3),
                    fields.get(4),
                    fields.get(5),
                    parseExtension(fields.get(6))
            );
        };

        List<String> validationErrors = ContactValidator.validate(contact);
        if (!validationErrors.isEmpty()) {
            throw new ContactCsvException(
                    CsvErrorCode.INVALID_CONTACT,
                    String.join("; ", validationErrors)
            );
        }
        return contact;
    }

    private List<String> parseLine(String line) throws ContactCsvException {
        var fields = new ArrayList<String>();
        var current = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;

        for (int index = 0; index < line.length(); index++) {
            char symbol = line.charAt(index);
            if (closedQuote && symbol != DELIMITER) {
                throw new ContactCsvException(
                        CsvErrorCode.MALFORMED_QUOTES,
                        "После закрывающей кавычки ожидается разделитель или конец строки"
                );
            }
            if (symbol == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else if (!quoted && current.length() > 0) {
                    throw new ContactCsvException(
                            CsvErrorCode.MALFORMED_QUOTES,
                            "Кавычка должна находиться в начале поля"
                    );
                } else {
                    quoted = !quoted;
                    closedQuote = !quoted;
                }
            } else if (symbol == DELIMITER && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
                closedQuote = false;
            } else {
                current.append(symbol);
            }
        }

        if (quoted) {
            throw new ContactCsvException(CsvErrorCode.MALFORMED_QUOTES, "Незакрытая кавычка");
        }
        fields.add(current.toString());
        return fields;
    }

    private void requireEmptyCorporateFields(List<String> fields, ContactType type)
            throws ContactCsvException {
        if (!fields.get(5).isBlank() || !fields.get(6).isBlank()) {
            throw new ContactCsvException(
                    CsvErrorCode.UNEXPECTED_DATA,
                    "Поля position и extension недоступны для типа " + type
            );
        }
    }

    private int parseExtension(String value) throws ContactCsvException {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException error) {
            throw new ContactCsvException(
                    CsvErrorCode.BAD_NUMBER,
                    "Внутренний номер должен быть целым числом"
            );
        }
    }

    private void validateBeforeSave(Contact contact) throws ContactCsvException {
        List<String> errors = ContactValidator.validate(contact);
        if (!errors.isEmpty()) {
            throw new ContactCsvException(
                    CsvErrorCode.INVALID_CONTACT,
                    "Нельзя сохранить контакт «%s»: %s".formatted(
                            contact.getName(),
                            String.join("; ", errors)
                    )
            );
        }
    }

    private String formatContact(Contact contact) {
        List<String> fields = new ArrayList<>(HEADER.size());
        fields.add(contact.getType().name());
        fields.add(contact.getName());
        fields.add(contact.getPhone());
        fields.add(contact.getEmail());
        fields.add(contact.getOrganization());
        if (contact instanceof CorporateContact corporate) {
            fields.add(corporate.getPosition());
            fields.add(Integer.toString(corporate.getExtension()));
        } else {
            fields.add("");
            fields.add("");
        }
        return fields.stream().map(this::escape).reduce((left, right) -> left + DELIMITER + right).orElse("");
    }

    private String escape(String value) {
        if (value.indexOf(DELIMITER) >= 0 || value.indexOf('"') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
