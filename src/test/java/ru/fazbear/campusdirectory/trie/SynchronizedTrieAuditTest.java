package ru.fazbear.campusdirectory.trie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.PersonalContact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynchronizedTrieAuditTest {
    @Test
    void agreesWithReferenceModelDuringFiftyThousandMixedOperations() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        var reference = new ArrayList<Contact>();
        var random = new Random(20261023L);
        String[] names = {"Анна", " АННА ", "Аня", "Ан", "Мария", "Марк", "Ёлка", "Ян", "Alex"};
        String[] prefixes = {"", "а", "ан", "ма", "ё", "missing"};
        for (int step = 0; step < 50_000; step++) {
            if (reference.isEmpty() || random.nextInt(100) < 62) {
                Contact contact = new PersonalContact(names[random.nextInt(names.length)],
                        "123", "c" + step + "@example.com", "");
                trie.add(contact);
                reference.add(contact);
            } else {
                Contact contact = reference.remove(random.nextInt(reference.size()));
                assertTrue(trie.remove(contact));
                assertFalse(trie.remove(contact));
            }
            if (step % 251 == 0 || step == 49_999) {
                Contact[] expected = reference.stream().sorted(Comparator.comparing(this::key))
                        .toArray(Contact[]::new);
                assertArrayEquals(expected, trie.getAll(), "step " + step);
                assertEquals(reference.size(), trie.size());
                for (String prefix : prefixes) {
                    Contact[] matches = List.of(expected).stream()
                            .filter(contact -> key(contact).startsWith(prefix)).toArray(Contact[]::new);
                    assertArrayEquals(matches, trie.autocomplete(prefix));
                    assertEquals(matches.length, trie.countByPrefix(prefix));
                    if (matches.length > 0) {
                        Contact first = reference.stream().filter(contact -> key(contact).equals(key(matches[0])))
                                .findFirst().orElseThrow();
                        assertSame(first, trie.findFirst(matches[0].getName()));
                    }
                }
            }
        }
    }

    @Test
    void supportsConcurrentInsertionReadingAndRemoval() throws Exception {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        var contacts = new Contact[16_000];
        for (int index = 0; index < contacts.length; index++) {
            contacts[index] = new PersonalContact("Contact " + index, "123", "", "");
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            var jobs = new ArrayList<Callable<Void>>();
            for (int thread = 0; thread < 8; thread++) {
                int offset = thread * 2000;
                jobs.add(() -> {
                    for (int index = offset; index < offset + 2000; index++) {
                        trie.add(contacts[index]);
                        assertSame(contacts[index], trie.findFirst(contacts[index].getName()));
                        if (index % 100 == 0) {
                            assertEquals(0, trie.snapshot(100).path().length());
                        }
                    }
                    return null;
                });
            }
            for (var future : executor.invokeAll(jobs, 30, TimeUnit.SECONDS)) {
                future.get();
            }
            assertEquals(contacts.length, trie.size());
            assertEquals(contacts.length, trie.getAll().length);
            jobs.clear();
            for (int thread = 0; thread < 8; thread++) {
                int offset = thread * 2000;
                jobs.add(() -> {
                    for (int index = offset; index < offset + 2000; index++) {
                        assertTrue(trie.remove(contacts[index]));
                    }
                    return null;
                });
            }
            for (var future : executor.invokeAll(jobs, 30, TimeUnit.SECONDS)) {
                future.get();
            }
        }
        assertEquals(0, trie.size());
        assertEquals(0, trie.snapshot().children().length);
    }

    private String key(Contact contact) {
        return contact.getName().toLowerCase(Locale.ROOT);
    }
}
