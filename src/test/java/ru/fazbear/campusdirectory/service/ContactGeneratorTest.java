package ru.fazbear.campusdirectory.service;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import ru.fazbear.campusdirectory.model.ContactValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactGeneratorTest {
    private final ContactGenerator generator = new ContactGenerator();

    @Test
    void createsRequestedNumberOfValidContacts() {
        var progress = new AtomicInteger();

        var contacts = generator.generate(250, () -> false, progress::set);

        assertEquals(250, contacts.length);
        assertEquals(250, progress.get());
        for (var contact : contacts) {
            assertTrue(ContactValidator.validate(contact).isEmpty());
        }
    }

    @Test
    void rejectsNegativeCount() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(-1));
    }

    @Test
    void supportsCancellation() {
        var checks = new AtomicInteger();

        assertThrows(CancellationException.class, () -> generator.generate(
                1_000,
                () -> checks.incrementAndGet() > 10,
                completed -> { }
        ));
    }
}
