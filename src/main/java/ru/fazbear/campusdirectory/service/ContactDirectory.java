package ru.fazbear.campusdirectory.service;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.ContactValidator;
import ru.fazbear.campusdirectory.model.Editable;
import ru.fazbear.campusdirectory.trie.ContactTrie;
import ru.fazbear.campusdirectory.trie.TrieNodeSnapshot;

public final class ContactDirectory {
    private ContactTrie<Contact> contacts = new ContactTrie<Contact>(Contact::getName, Contact[]::new);

    public List<Contact> getAll() {
        return List.of(contacts.getAll());
    }

    public void add(Contact contact) {
        requireEditable(contact);
        requireValid(contact);
        contacts.add(contact);
    }

    public void update(int index, Contact replacement) {
        update(getAll().get(index), replacement);
    }

    public void update(Contact current, Contact replacement) {
        requireEditable(current);
        requireEditable(replacement);
        requireValid(replacement);
        if (!contacts.remove(current)) {
            throw new IllegalStateException("Изменяемый контакт не найден");
        }
        contacts.add(replacement);
    }

    public boolean remove(Contact contact) {
        requireEditable(contact);
        return contacts.remove(contact);
    }

    public Contact findFirstByName(String name) {
        return contacts.findFirst(name);
    }

    public List<Contact> autocomplete(String prefix) {
        return List.of(contacts.autocomplete(prefix));
    }

    public int countByPrefix(String prefix) {
        return contacts.countByPrefix(prefix);
    }

    public TrieNodeSnapshot getTrieSnapshot() {
        return contacts.snapshot();
    }

    public TrieNodeSnapshot getTrieSnapshot(int maximumNodes) {
        return contacts.snapshot(maximumNodes);
    }

    public TrieNodeSnapshot getTrieSnapshot(int maximumNodes, int maximumDepth) {
        return contacts.snapshot(maximumNodes, maximumDepth);
    }

    public void replaceAll(List<Contact> loadedContacts) {
        replaceAll(loadedContacts, () -> false, completed -> { });
    }

    public void replaceAll(
            List<Contact> loadedContacts,
            BooleanSupplier cancelled,
            IntConsumer progress
    ) {
        var replacement = new ContactTrie<Contact>(Contact::getName, Contact[]::new);
        for (int index = 0; index < loadedContacts.size(); index++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("Построение Trie отменено");
            }
            Contact contact = loadedContacts.get(index);
            requireValid(contact);
            replacement.add(contact);
            if ((index + 1) % 100 == 0 || index + 1 == loadedContacts.size()) {
                progress.accept(index + 1);
            }
        }
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Построение Trie отменено");
        }
        contacts = replacement;
    }

    public int size() {
        return contacts.size();
    }

    private void requireEditable(Contact contact) {
        if (!(contact instanceof Editable)) {
            throw new IllegalArgumentException("Контакт доступен только для чтения");
        }
    }

    private void requireValid(Contact contact) {
        List<String> errors = ContactValidator.validate(contact);
        if (!errors.isEmpty()) {
            throw new ContactValidationException(errors);
        }
    }
}
