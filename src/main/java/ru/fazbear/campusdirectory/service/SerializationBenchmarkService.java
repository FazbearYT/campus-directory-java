package ru.fazbear.campusdirectory.service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import ru.fazbear.campusdirectory.io.BinaryContactSerializer;
import ru.fazbear.campusdirectory.io.ContactArchive;
import ru.fazbear.campusdirectory.io.JsonContactSerializer;
import ru.fazbear.campusdirectory.io.Serializer;

public final class SerializationBenchmarkService {
    public static final int CONTACT_COUNT = 10_000;
    public static final int WARMUP_RUNS = 2;
    public static final int MEASUREMENT_RUNS = 5;

    public List<SerializationBenchmarkResult> run(BooleanSupplier cancelled, IntConsumer progress) throws IOException {
        ContactArchive data = ContactArchive.from(List.of(new ContactGenerator().generate(
                CONTACT_COUNT, cancelled, completed -> { })));
        List<Serializer<ContactArchive>> serializers = List.of(new JsonContactSerializer(), new BinaryContactSerializer());
        var results = new ArrayList<SerializationBenchmarkResult>();
        int completed = 0;
        for (Serializer<ContactArchive> serializer : serializers) {
            var file = Files.createTempFile("contacts-benchmark-", "." + serializer.format());
            try {
                long saveNanos = 0;
                long loadNanos = 0;
                for (int run = -WARMUP_RUNS; run < MEASUREMENT_RUNS; run++) {
                    if (cancelled.getAsBoolean()) {
                        throw new CancellationException("Сравнение форматов отменено");
                    }
                    long start = System.nanoTime();
                    serializer.save(data, file);
                    long saved = System.nanoTime();
                    ContactArchive loaded = serializer.load(file);
                    long finished = System.nanoTime();
                    if (!data.equals(loaded)) {
                        throw new IOException("Данные изменились после сохранения в " + serializer.format());
                    }
                    if (run >= 0) {
                        saveNanos += saved - start;
                        loadNanos += finished - saved;
                    }
                    progress.accept(++completed);
                }
                results.add(new SerializationBenchmarkResult(serializer.format(), Files.size(file),
                        saveNanos / (MEASUREMENT_RUNS * 1_000_000.0), loadNanos / (MEASUREMENT_RUNS * 1_000_000.0)));
            } finally {
                Files.deleteIfExists(file);
            }
        }
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Сравнение форматов отменено");
        }
        return List.copyOf(results);
    }
}
