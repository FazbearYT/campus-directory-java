package ru.fazbear.campusdirectory.trie;

import org.junit.jupiter.api.Test;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.PersonalContact;
import ru.fazbear.campusdirectory.service.ContactGenerator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SynchronizedTrieTest {
    @Test
    void insertsAndFindsContactIgnoringCase() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        Contact contact = contact("Анна");

        trie.add(contact);

        assertSame(contact, trie.findFirst("  АННА "));
        assertEquals(1, trie.size());
    }

    @Test
    void storesContactsWithTheSameName() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        Contact first = contact("Анна");
        Contact second = new PersonalContact("Анна", "113", "", "");

        trie.add(first);
        trie.add(second);

        assertArrayEquals(new Contact[]{first, second}, trie.autocomplete("анна"));
        assertEquals(2, trie.countByPrefix("ан"));
    }

    @Test
    void autocompletesInLexicographicalOrder() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        Contact maria = contact("Мария");
        Contact mark = contact("Марк");
        Contact anna = contact("Анна");
        trie.add(maria);
        trie.add(mark);
        trie.add(anna);

        assertArrayEquals(new Contact[]{maria, mark}, trie.autocomplete("ма"));
        assertArrayEquals(new Contact[]{anna, maria, mark}, trie.getAll());
    }

    @Test
    void removesContactAndPrunesEmptyBranch() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        Contact anna = contact("Анна");
        Contact anya = contact("Аня");
        trie.add(anna);
        trie.add(anya);

        assertTrue(trie.remove(anna));

        assertNull(trie.findFirst("Анна"));
        assertSame(anya, trie.findFirst("Аня"));
        assertEquals(1, trie.countByPrefix("ан"));
        assertFalse(trie.remove(anna));
    }

    @Test
    void marksNodesVisitedByLastOperation() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        trie.add(contact("Анна"));

        trie.findFirst("Ан");
        TrieNodeSnapshot root = trie.snapshot();

        assertTrue(root.visited());
        assertTrue(root.children()[0].visited());
        assertTrue(root.children()[0].children()[0].visited());
        assertFalse(root.children()[0].children()[0].children()[0].visited());
    }

    @Test
    void limitsVisualizationSnapshotWithoutChangingCounts() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        trie.add(contact("Анна"));
        trie.add(contact("Борис"));

        TrieNodeSnapshot root = trie.snapshot(2);

        assertEquals(2, root.subtreeContactCount());
        assertEquals(1, root.children().length);
        assertEquals(1, root.omittedChildCount());
    }

    @Test
    void handlesLargeGeneratedDatasetAndBoundedSnapshot() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        Contact[] contacts = new ContactGenerator().generate(10_000);
        for (Contact contact : contacts) {
            trie.add(contact);
        }

        TrieNodeSnapshot root = trie.snapshot(500);

        assertEquals(10_000, trie.size());
        assertEquals(10_000, root.subtreeContactCount());
        assertEquals(500, countNodes(root));
        assertEquals(java.util.Arrays.stream(contacts)
                .map(contact -> contact.getName().toLowerCase(java.util.Locale.ROOT).charAt(0))
                .distinct().count(), root.children().length);
        assertEquals(0, root.omittedChildCount());
    }

    @Test
    void rejectsPathologicalKeysBeforeMutatingTree() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        assertThrows(IllegalArgumentException.class, () -> trie.add(contact("a".repeat(6000))));
        assertEquals(0, trie.size());
        assertEquals(0, trie.snapshot().children().length);
    }

    @Test
    void supportsMaximumNameLengthAndUnicodeLowercaseExpansion() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        Contact longest = contact("\u0130".repeat(256));
        trie.add(longest);
        assertSame(longest, trie.findFirst(longest.getName()));
        assertArrayEquals(new Contact[]{longest}, trie.getAll());
        assertEquals(513, countNodes(trie.snapshot()));
    }

    @Test
    void removesSelectedInstanceBeforeEqualDuplicate() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        Contact first = contact("Anna");
        Contact second = contact("Anna");
        trie.add(first);
        trie.add(second);
        assertTrue(trie.remove(second));
        assertSame(first, trie.findFirst("Anna"));
    }

    @Test
    void limitsSnapshotDepthWithoutLosingUnderlyingContact() {
        var trie = new SynchronizedTrie<Contact>(Contact::getName, Contact[]::new);
        Contact contact = contact("a".repeat(256));
        trie.add(contact);
        TrieNodeSnapshot snapshot = trie.snapshot(500, 128);
        assertEquals(129, countNodes(snapshot));
        while (snapshot.children().length > 0) {
            snapshot = snapshot.children()[0];
        }
        assertEquals(1, snapshot.omittedChildCount());
        assertSame(contact, trie.findFirst(contact.getName()));
        assertEquals(1, countNodes(trie.snapshot(500, 0)));
    }

    private PersonalContact contact(String name) {
        return new PersonalContact(name, "112", "", "");
    }

    private int countNodes(TrieNodeSnapshot node) {
        int count = 1;
        for (TrieNodeSnapshot child : node.children()) {
            count += countNodes(child);
        }
        return count;
    }
}
