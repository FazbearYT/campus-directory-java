package ru.fazbear.campusdirectory.trie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenericTrieTest {
    @Test
    void readerCannotObserveAnInsertionWhileItsMonitorIsHeld() throws Exception {
        var writerInside = new java.util.concurrent.CountDownLatch(1);
        var releaseWriter = new java.util.concurrent.CountDownLatch(1);
        var readerStarted = new java.util.concurrent.CountDownLatch(1);
        var trie = new SynchronizedTrie<String>(value -> {
            writerInside.countDown();
            try {
                if (!releaseWriter.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Писатель не получил разрешение продолжить");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
            return value;
        }, String[]::new);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> trie.add("alpha"));
            try {
                assertTrue(writerInside.await(5, java.util.concurrent.TimeUnit.SECONDS));
                var reader = executor.submit(() -> {
                    readerStarted.countDown();
                    return trie.findFirst("alpha");
                });
                assertTrue(readerStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> reader.get(100, java.util.concurrent.TimeUnit.MILLISECONDS));
                releaseWriter.countDown();
                writer.get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals("alpha", reader.get(5, java.util.concurrent.TimeUnit.SECONDS));
            } finally {
                releaseWriter.countDown();
            }
        }
    }

    private record Book(String title, int edition) { }

    @Test
    void storesAnIndependentDomainTypeAndReturnsTypedArrays() {
        var trie = new SynchronizedTrie<Book>(Book::title, Book[]::new);
        var first = new Book("Algorithms", 1);
        var second = new Book("Algorithms", 2);
        var third = new Book("Algebra", 1);
        trie.add(first);
        trie.add(second);
        trie.add(third);
        assertSame(first, trie.findFirst("ALGORITHMS"));
        assertArrayEquals(new Book[]{third, first, second}, trie.autocomplete("al"));
        assertEquals(Book[].class, trie.getAll().getClass());
        assertEquals(3, trie.countByPrefix(""));
        assertTrue(trie.remove(second));
        assertArrayEquals(new Book[]{third, first}, trie.getAll());
    }

    @Test
    void returnedArrayCannotChangeStoredReferences() {
        var trie = new SynchronizedTrie<String>(value -> value, String[]::new);
        trie.add("alpha");
        trie.getAll()[0] = "changed";
        assertEquals("alpha", trie.findFirst("alpha"));
        assertEquals(0, trie.autocomplete("missing").length);
        assertEquals(String[].class, trie.autocomplete("missing").getClass());
    }

    @Test
    void rejectsInvalidKeyBeforeChangingStructure() {
        var trie = new SynchronizedTrie<Book>(Book::title, Book[]::new);
        assertThrows(NullPointerException.class, () -> trie.add(new Book(null, 1)));
        assertThrows(IllegalArgumentException.class, () -> trie.add(new Book(" ", 1)));
        assertEquals(0, trie.size());
        assertEquals(0, trie.snapshot().children().length);
    }
}
