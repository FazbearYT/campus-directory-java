package ru.fazbear.campusdirectory.service;

public record SerializationBenchmarkResult(String format, long fileBytes, double saveMillis, double loadMillis) {
}
