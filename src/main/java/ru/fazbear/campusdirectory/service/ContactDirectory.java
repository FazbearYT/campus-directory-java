package ru.fazbear.campusdirectory.service;

import java.util.List;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.ContactValidator;
import ru.fazbear.campusdirectory.model.Editable;
import ru.fazbear.campusdirectory.trie.ContactTrie;
import ru.fazbear.campusdirectory.trie.TrieNodeSnapshot;

public final class ContactDirectory {
    private ContactTrie contacts = new ContactTrie();

    public List<Contact> getAll() {
        return List.of(contacts.getAll());
    }

    public void add(Contact contact) {
        requireEditable(contact);
        requireValid(contact);
        contacts.add(contact);
    }

    public void update(int index, Contact replacement) {
        Contact current = getAll().get(index);
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

    public void replaceAll(List<Contact> loadedContacts) {
        var replacement = new ContactTrie();
        for (Contact contact : loadedContacts) {
            requireValid(contact);
            replacement.add(contact);
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
