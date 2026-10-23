package ru.fazbear.campusdirectory.service;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.trie.ContactTrie;

public final class TrieBenchmarkService {
    private static final int ADDITIONAL_OPERATION_REPETITIONS = 100;

    private final ContactGenerator generator = new ContactGenerator();

    public TrieBenchmarkResult run(int contactCount, BooleanSupplier cancelled) {
        if (contactCount < 1) {
            throw new IllegalArgumentException("Размер набора должен быть положительным");
        }

        Contact[] contacts = generator.generate(contactCount, cancelled, completed -> { });
        var trie = new ContactTrie();

        long insertionStarted = System.nanoTime();
        for (int index = 0; index < contacts.length; index++) {
            checkCancellation(cancelled, index);
            trie.add(contacts[index]);
        }
        long insertionNanos = System.nanoTime() - insertionStarted;

        long searchStarted = System.nanoTime();
        for (int index = 0; index < contacts.length; index++) {
            checkCancellation(cancelled, index);
            Contact found = trie.findFirst(contacts[index].getName());
            if (found == null) {
                throw new IllegalStateException("Контакт потерян во время замера поиска");
            }
        }
        long searchNanos = System.nanoTime() - searchStarted;

        String prefix = contacts[0].getName().substring(0, 2);
        long autocompleteStarted = System.nanoTime();
        for (int index = 0; index < ADDITIONAL_OPERATION_REPETITIONS; index++) {
            checkCancellation(cancelled, index);
            trie.autocomplete(prefix);
        }
        long autocompleteNanos = System.nanoTime() - autocompleteStarted;

        long countStarted = System.nanoTime();
        for (int index = 0; index < ADDITIONAL_OPERATION_REPETITIONS; index++) {
            checkCancellation(cancelled, index);
            trie.countByPrefix(prefix);
        }
        long subtreeCountNanos = System.nanoTime() - countStarted;

        long removalStarted = System.nanoTime();
        for (int index = 0; index < contacts.length; index++) {
            checkCancellation(cancelled, index);
            if (!trie.remove(contacts[index])) {
                throw new IllegalStateException("Контакт потерян во время замера удаления");
            }
        }
        long removalNanos = System.nanoTime() - removalStarted;

        return new TrieBenchmarkResult(
                contactCount,
                insertionNanos,
                searchNanos,
                removalNanos,
                autocompleteNanos,
                subtreeCountNanos
        );
    }

    private void checkCancellation(BooleanSupplier cancelled, int iteration) {
        if (iteration % 100 == 0 && cancelled.getAsBoolean()) {
            throw new CancellationException("Замер отменён");
        }
    }
}
