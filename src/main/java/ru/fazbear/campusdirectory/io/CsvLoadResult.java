package ru.fazbear.campusdirectory.io;

import java.util.List;

import ru.fazbear.campusdirectory.model.Contact;

public record CsvLoadResult(List<Contact> contacts, List<CsvRowError> errors) {
    public CsvLoadResult {
        contacts = List.copyOf(contacts);
        errors = List.copyOf(errors);
    }
}
