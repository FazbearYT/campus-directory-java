package ru.fazbear.campusdirectory.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactValidatorTest {
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
}
