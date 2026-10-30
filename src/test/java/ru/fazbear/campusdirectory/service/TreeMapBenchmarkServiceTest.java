package ru.fazbear.campusdirectory.service;

import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeMapBenchmarkServiceTest {
    @Test
    void measuresSameOperationsWithSameRepetitionCounts() {
        var result = new TreeMapBenchmarkService().run(100, () -> false);
        assertEquals(100, result.contactCount());
        assertEquals(100, result.additionalOperationCount());
        assertEquals(5, result.measurementRuns());
        assertTrue(result.insertionNanos() >= 0);
        assertTrue(result.searchNanos() >= 0);
        assertTrue(result.removalNanos() >= 0);
        assertTrue(result.autocompleteNanos() >= 0);
        assertTrue(result.subtreeCountNanos() >= 0);
    }

    @Test
    void rejectsInvalidSizeAndSupportsCancellation() {
        var service = new TreeMapBenchmarkService();
        assertThrows(IllegalArgumentException.class, () -> service.run(0, () -> false));
        assertThrows(CancellationException.class, () -> service.run(100, () -> true));
    }
}
