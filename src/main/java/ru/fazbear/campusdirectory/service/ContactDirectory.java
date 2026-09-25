package ru.fazbear.campusdirectory.service;

import java.util.ArrayList;
import java.util.List;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.ContactValidator;
import ru.fazbear.campusdirectory.model.Editable;

public final class ContactDirectory {
    private final List<Contact> contacts = new ArrayList<>();

    public List<Contact> getAll() {
        return List.copyOf(contacts);
    }

    public void add(Contact contact) {
        requireEditable(contact);
        requireValid(contact);
        contacts.add(contact);
    }

    public void update(int index, Contact replacement) {
        Contact current = contacts.get(index);
        requireEditable(current);
        requireEditable(replacement);
        requireValid(replacement);
        contacts.set(index, replacement);
    }

    public void replaceAll(List<Contact> loadedContacts) {
        for (Contact contact : loadedContacts) {
            requireValid(contact);
        }
        contacts.clear();
        contacts.addAll(loadedContacts);
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
