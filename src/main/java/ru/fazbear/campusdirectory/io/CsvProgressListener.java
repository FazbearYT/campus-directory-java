package ru.fazbear.campusdirectory.io;

@FunctionalInterface
public interface CsvProgressListener {
    void update(long completedRows, long totalRows);
}
