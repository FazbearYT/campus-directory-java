package ru.fazbear.campusdirectory.io;

public enum CsvErrorCode {
    IO_ERROR,
    MISSING_HEADER,
    WRONG_FIELD_COUNT,
    MALFORMED_QUOTES,
    UNKNOWN_TYPE,
    BAD_NUMBER,
    UNEXPECTED_DATA,
    INVALID_CONTACT
}
