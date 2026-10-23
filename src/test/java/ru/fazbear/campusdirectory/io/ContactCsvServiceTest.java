package ru.fazbear.campusdirectory.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.CorporateContact;
import ru.fazbear.campusdirectory.model.EmergencyContact;
import ru.fazbear.campusdirectory.model.PersonalContact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactCsvServiceTest {
    private final ContactCsvService service = new ContactCsvService();

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsTextAfterClosingQuoteAndPreservesValidRows() throws Exception {
        Path file = write("""
                type;name;phone;email;organization;position;extension
                PERSONAL;"Anna"garbage;123;;;;
                PERSONAL;""garbage;123;;;;
                PERSONAL;"Anna" ;123;;;;
                PERSONAL;"Anna";123;;;;
                """);
        CsvLoadResult result = service.load(file);
        assertEquals(1, result.contacts().size());
        assertEquals(List.of(CsvErrorCode.MALFORMED_QUOTES, CsvErrorCode.MALFORMED_QUOTES,
                CsvErrorCode.MALFORMED_QUOTES), result.errors().stream().map(CsvRowError::code).toList());
    }

    @Test
    void invalidPhoneCannotOverwriteExistingFile() throws Exception {
        Path file = write("existing data");
        assertThrows(ContactCsvException.class, () -> service.save(file,
                List.of(new PersonalContact("Anna", "+7\n123", "", ""))));
        assertEquals("existing data", Files.readString(file));
    }


    @Test
    void loadsAllSupportedContactTypes() throws Exception {
        Path file = write("""
                type;name;phone;email;organization;position;extension
                PERSONAL;Анна;+7 900 100-20-30;anna@example.com;;;
                CORPORATE;Иван;+7 900 200-30-40;ivan@example.com;Ромашка;Инженер;105
                EMERGENCY;Диспетчерская;112;;Университет;;
                """);

        CsvLoadResult result = service.load(file);

        assertEquals(3, result.contacts().size());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void skipsInvalidRowsAndReportsCodes() throws Exception {
        Path file = write("""
                type;name;phone;email;organization;position;extension
                PERSONAL;Анна;+7 900 100-20-30;anna@example.com;;;
                UNKNOWN;Ошибка;+7 900 000-00-00;;;;
                CORPORATE;Иван;+7 900 200-30-40;ivan@example.com;Ромашка;Инженер;abc
                PERSONAL;Пустой телефон;;;;;
                """);

        CsvLoadResult result = service.load(file);

        assertEquals(1, result.contacts().size());
        assertEquals(
                List.of(CsvErrorCode.UNKNOWN_TYPE, CsvErrorCode.BAD_NUMBER, CsvErrorCode.INVALID_CONTACT),
                result.errors().stream().map(CsvRowError::code).toList()
        );
    }

    @Test
    void roundTripPreservesQuotedFields() throws Exception {
        Path file = temporaryDirectory.resolve("contacts.csv");
        List<Contact> contacts = List.of(
                new PersonalContact("Анна; Мария", "+7 900 100-20-30", "anna@example.com", "Клуб \"Ритм\""),
                new CorporateContact(
                        "Иван",
                        "+7 900 200-30-40",
                        "ivan@example.com",
                        "Ромашка",
                        "Инженер",
                        105
                ),
                new EmergencyContact("Диспетчерская", "112", "", "Университет")
        );

        service.save(file, contacts);
        CsvLoadResult result = service.load(file);

        assertEquals(contacts, result.contacts());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void rejectsMalformedHeader() throws Exception {
        Path file = write("name;phone\nАнна;12345\n");

        ContactCsvException error = assertThrows(ContactCsvException.class, () -> service.load(file));

        assertEquals(CsvErrorCode.MISSING_HEADER, error.getCode());
    }

    @Test
    void reportsMalformedQuotesWithoutLosingValidRows() throws Exception {
        Path file = write("""
                type;name;phone;email;organization;position;extension
                PERSONAL;"Незакрытое имя;+7 900 100-20-30;;;;
                EMERGENCY;Диспетчерская;112;;Университет;;
                """);

        CsvLoadResult result = service.load(file);

        assertEquals(1, result.contacts().size());
        assertEquals(CsvErrorCode.MALFORMED_QUOTES, result.errors().getFirst().code());
    }

    @Test
    void reportsLoadProgress() throws Exception {
        Path file = write("""
                type;name;phone;email;organization;position;extension
                PERSONAL;Анна;112;;;;
                PERSONAL;Борис;113;;;;
                """);
        var completed = new AtomicLong();
        var total = new AtomicLong();

        CsvLoadResult result = service.load(file, () -> false, (done, maximum) -> {
            completed.set(done);
            total.set(maximum);
        });

        assertEquals(2, result.contacts().size());
        assertEquals(2, completed.get());
        assertEquals(2, total.get());
    }

    @Test
    void supportsLoadCancellation() throws Exception {
        Path file = write("""
                type;name;phone;email;organization;position;extension
                PERSONAL;Анна;112;;;;
                PERSONAL;Борис;113;;;;
                """);

        assertThrows(CancellationException.class, () -> service.load(
                file,
                () -> true,
                (completed, total) -> { }
        ));
    }

    private Path write(String content) throws Exception {
        Path file = temporaryDirectory.resolve("input.csv");
        Files.writeString(file, content);
        return file;
    }
}
