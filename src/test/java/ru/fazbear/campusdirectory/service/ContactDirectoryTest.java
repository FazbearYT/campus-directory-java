package ru.fazbear.campusdirectory.service;

import java.util.List;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

import ru.fazbear.campusdirectory.model.EmergencyContact;
import ru.fazbear.campusdirectory.model.PersonalContact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContactDirectoryTest {
    @Test
    void keepsPreviousDataWhenCancelledByFinalProgressCallback() {
        var directory = new ContactDirectory();
        var original = new PersonalContact("Anna", "123", "", "");
        directory.add(original);
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        assertThrows(CancellationException.class, () -> directory.replaceAll(
                List.of(new PersonalContact("Boris", "123", "", "")),
                cancelled::get, completed -> cancelled.set(true)));
        assertEquals(List.of(original), directory.getAll());
    }

    @Test
    void invalidUpdateKeepsOriginalContact() {
        var directory = new ContactDirectory();
        var original = new PersonalContact("Anna", "123", "", "");
        directory.add(original);
        assertThrows(ContactValidationException.class, () -> directory.update(original,
                new PersonalContact("a".repeat(6000), "123", "", "")));
        assertEquals(List.of(original), directory.getAll());
    }

    @Test
    void addsEditableContact() {
        var directory = new ContactDirectory();
        var contact = new PersonalContact("Анна", "+7 900 100-20-30", "", "");

        directory.add(contact);

        assertEquals(List.of(contact), directory.getAll());
    }

    @Test
    void rejectsAddingReadOnlyContact() {
        var directory = new ContactDirectory();
        var contact = new EmergencyContact("Диспетчерская", "112", "", "Университет");

        assertThrows(IllegalArgumentException.class, () -> directory.add(contact));
    }

    @Test
    void allowsReadOnlyContactsFromImportedData() {
        var directory = new ContactDirectory();
        var contact = new EmergencyContact("Диспетчерская", "112", "", "Университет");

        directory.replaceAll(List.of(contact));

        assertEquals(1, directory.size());
    }

    @Test
    void preventsEditingReadOnlyContact() {
        var directory = new ContactDirectory();
        directory.replaceAll(List.of(
                new EmergencyContact("Диспетчерская", "112", "", "Университет")
        ));
        var replacement = new PersonalContact("Анна", "+7 900 100-20-30", "", "");

        assertThrows(IllegalArgumentException.class, () -> directory.update(0, replacement));
    }

    @Test
    void rejectsInvalidImportedData() {
        var directory = new ContactDirectory();
        var invalid = new PersonalContact("", "", "", "");

        assertThrows(ContactValidationException.class, () -> directory.replaceAll(List.of(invalid)));
    }

    @Test
    void supportsTrieSearchAutocompleteAndRemoval() {
        var directory = new ContactDirectory();
        var anna = new PersonalContact("Анна", "112", "", "");
        var anya = new PersonalContact("Аня", "113", "", "");
        directory.add(anya);
        directory.add(anna);

        assertEquals(anna, directory.findFirstByName("АННА"));
        assertEquals(List.of(anna, anya), directory.autocomplete("ан"));
        assertEquals(2, directory.countByPrefix("ан"));

        directory.remove(anna);

        assertNull(directory.findFirstByName("анна"));
        assertEquals(1, directory.size());
    }

    @Test
    void keepsPreviousDataWhenReplacementIsCancelled() {
        var directory = new ContactDirectory();
        var original = new PersonalContact("Анна", "112", "", "");
        directory.add(original);

        assertThrows(CancellationException.class, () -> directory.replaceAll(
                List.of(new PersonalContact("Борис", "113", "", "")),
                () -> true,
                completed -> { }
        ));

        assertEquals(List.of(original), directory.getAll());
    }
}
