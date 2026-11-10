package ru.fazbear.campusdirectory.service;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SerializationBenchmarkServiceTest {
    @Test
    void honoursCancellationBeforeGeneratingContacts() {
        assertThrows(CancellationException.class,
                () -> new SerializationBenchmarkService().run(() -> true, completed -> { }));
    }

    @Test
    void honoursCancellationAtFinalProgressNotification() {
        var cancelled = new AtomicBoolean();
        int totalRuns = 2 * (SerializationBenchmarkService.WARMUP_RUNS
                + SerializationBenchmarkService.MEASUREMENT_RUNS);
        assertThrows(CancellationException.class, () -> new SerializationBenchmarkService().run(
                cancelled::get, completed -> cancelled.set(completed == totalRuns)));
    }
}
