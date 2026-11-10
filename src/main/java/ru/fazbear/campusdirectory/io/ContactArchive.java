package ru.fazbear.campusdirectory.io;

import java.util.List;

import ru.fazbear.campusdirectory.model.Contact;

public record ContactArchive(int version, List<ContactData> contacts) {
    public static final int CURRENT_VERSION = 1;

    public ContactArchive {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Неподдерживаемая версия: " + version
                    + ". Ожидалась версия " + CURRENT_VERSION);
        }
        contacts = List.copyOf(contacts);
    }

    public static ContactArchive from(List<Contact> contacts) {
        return new ContactArchive(CURRENT_VERSION, contacts.stream().map(ContactData::from).toList());
    }
}
