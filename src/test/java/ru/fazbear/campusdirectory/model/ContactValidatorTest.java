package ru.fazbear.campusdirectory.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactValidatorTest {
    @Test
    void rejectsControlCharactersInPhone() {
        for (String phone : new String[]{"+7\n123", "+7\r123", "+7\t123"}) {
            assertFalse(new PersonalContact("Anna", phone, "", "").validate().isEmpty());
        }
    }

    @Test
    void rejectsNamesBeyondSupportedDepth() {
        assertTrue(new PersonalContact("a".repeat(ContactValidator.MAXIMUM_NAME_LENGTH),
                "123", "", "").validate().isEmpty());
        assertFalse(new PersonalContact("a".repeat(ContactValidator.MAXIMUM_NAME_LENGTH + 1),
                "123", "", "").validate().isEmpty());
    }

    @Test
    void acceptsValidPersonalContact() {
        var contact = new PersonalContact(
                "Анна Смирнова",
                "+7 (913) 555-12-34",
                "anna@example.com",
                ""
        );

        assertTrue(contact.validate().isEmpty());
    }

    @Test
    void reportsInvalidEmail() {
        var contact = new PersonalContact("Анна", "+7 913 555-12-34", "wrong@", "");

        assertTrue(contact.validate().contains("E-mail имеет неверный формат"));
    }

    @Test
    void requiresCorporateDetails() {
        var contact = new CorporateContact("Иван", "+7 913 555-12-34", "", "", "", 0);

        assertEquals(3, contact.validate().size());
    }

    @Test
    void emergencyContactIsReadOnly() {
        Contact contact = new EmergencyContact("Диспетчерская", "112", "", "Университет");

        assertFalse(contact instanceof Editable);
        assertTrue(ContactValidator.validate(contact).isEmpty());
    }

    @Test
    void corporateEqualityIncludesAdditionalFields() {
        var first = new CorporateContact("Иван", "+7 913 555-12-34", "", "Компания", "Инженер", 101);
        var second = new CorporateContact("Иван", "+7 913 555-12-34", "", "Компания", "Инженер", 102);

        assertFalse(first.equals(second));
    }

    @Test
    void normalizesWhitespaceWithoutHidingNullValues() {
        var contact = new PersonalContact("  Анна  ", "  112  ", "", "");

        assertEquals("Анна", contact.getName());
        assertEquals("112", contact.getPhone());
        assertThrows(NullPointerException.class, () -> new PersonalContact(null, "112", "", ""));
    }
}
