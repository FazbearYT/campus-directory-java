package ru.fazbear.campusdirectory.service;

import java.io.Serial;
import java.util.List;

public final class ContactValidationException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<String> errors;

    public ContactValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
