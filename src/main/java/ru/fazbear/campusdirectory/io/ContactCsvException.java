package ru.fazbear.campusdirectory.io;

import java.io.Serial;

public final class ContactCsvException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    private final CsvErrorCode code;

    public ContactCsvException(CsvErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ContactCsvException(CsvErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public CsvErrorCode getCode() {
        return code;
    }
}
