package ru.fazbear.campusdirectory.trie;

import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyExperimentTest {
    @Test
    void synchronizedWritersPreserveEveryValueAndBothCounts() throws Exception {
        var result = new ConcurrencyExperiment().run(true, 8, 2000, () -> false);
        assertTrue(result.consistent(), result.toString());
        assertEquals(16_000, result.stored());
        assertEquals(0, result.missing());
        assertEquals(0, result.errors());
    }

    @Test
    void unprotectedExperimentReportsOutcomeWithoutAssumingEveryScheduleFails() throws Exception {
        var result = new ConcurrencyExperiment().run(false, 8, 1000, () -> false);
        assertEquals(8000, result.expected());
        assertTrue(result.nanos() > 0);
        assertEquals(result.size() == 8000 && result.subtreeCount() == 8000 && result.stored() == 8000
                && result.missing() == 0 && result.errors() == 0, result.consistent());
    }

    @Test
    void cancellationTerminatesWorkerTasks() {
        assertThrows(CancellationException.class,
                () -> new ConcurrencyExperiment().run(true, 8, 2000, () -> true));
        assertThrows(IllegalArgumentException.class,
                () -> new ConcurrencyExperiment().run(true, 1, 10, () -> false));
    }

    @Test
    void cancellationAfterWritersStartIsPropagatedAndFinishes() throws Exception {
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var operation = executor.submit(() -> new ConcurrencyExperiment().run(true, 8, 10_000,
                    () -> checks.incrementAndGet() > 100));
            var error = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> operation.get(10, java.util.concurrent.TimeUnit.SECONDS));
            Throwable cause = error.getCause();
            while (cause instanceof java.util.concurrent.ExecutionException) {
                cause = cause.getCause();
            }
            assertTrue(cause instanceof CancellationException, cause.toString());
            assertTrue(checks.get() > 100);
        }
    }
}
