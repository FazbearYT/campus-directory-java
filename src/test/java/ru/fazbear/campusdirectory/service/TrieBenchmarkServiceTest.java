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
        assertTrue(result.insertionNanos() >= 0);
        assertTrue(result.searchNanos() >= 0);
        assertTrue(result.removalNanos() >= 0);
        assertTrue(result.autocompleteNanos() >= 0);
        assertTrue(result.subtreeCountNanos() >= 0);
    }

    @Test
    void supportsCancellation() {
        assertThrows(CancellationException.class, () -> service.run(100, () -> true));
    }
}
