package ru.fazbear.campusdirectory.service;

import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrieBenchmarkServiceTest {
    private final TrieBenchmarkService service = new TrieBenchmarkService();

    @Test
    void measuresEveryRequiredOperation() {
        TrieBenchmarkResult result = service.run(100, () -> false);

        assertEquals(100, result.contactCount());
        assertEquals(5, result.measurementRuns());
        assertEquals(100, result.additionalOperationCount());
        assertTrue(result.insertionNanos() >= 0);
        assertTrue(result.searchNanos() >= 0);
        assertTrue(result.removalNanos() >= 0);
        assertTrue(result.autocompleteNanos() >= 0);
        assertTrue(result.subtreeCountNanos() >= 0);
    }

    @Test
    void normalizesDifferentBatchSizesToOneOperation() {
        var result = new TrieBenchmarkResult(10_000, 20_000_000, 10_000_000,
                30_000_000, 5_000_000, 100_000, 100, 5);
        assertEquals(2.0, result.insertionMicrosPerOperation());
        assertEquals(1.0, result.searchMicrosPerOperation());
        assertEquals(3.0, result.removalMicrosPerOperation());
        assertEquals(50.0, result.autocompleteMicrosPerOperation());
        assertEquals(1.0, result.subtreeCountMicrosPerOperation());
    }

    @Test
    void cancelsDuringWorkAfterGeneration() {
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(CancellationException.class, () -> service.run(100,
                () -> checks.incrementAndGet() > 105));
    }

    @Test
    void supportsCancellation() {
        assertThrows(CancellationException.class, () -> service.run(100, () -> true));
    }
}
