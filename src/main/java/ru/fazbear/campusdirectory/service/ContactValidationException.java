package ru.fazbear.campusdirectory.service;

import java.util.List;

public final class ContactValidationException extends IllegalArgumentException {
    private final List<String> errors;

    public ContactValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
