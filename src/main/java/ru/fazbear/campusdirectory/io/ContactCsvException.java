package ru.fazbear.campusdirectory.io;

public final class ContactCsvException extends Exception {
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
