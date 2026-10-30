package ru.fazbear.campusdirectory.service;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.trie.ContactTrie;

public final class TrieBenchmarkService {
    private static final int ADDITIONAL_OPERATION_REPETITIONS = 100;
    private static final int WARMUP_RUNS = 2;
    private static final int MEASUREMENT_RUNS = 5;

    private volatile long consumedResults;

    private final ContactGenerator generator = new ContactGenerator();

    public TrieBenchmarkResult run(int contactCount, BooleanSupplier cancelled) {
        if (contactCount < 1) {
            throw new IllegalArgumentException("Размер набора должен быть положительным");
        }

        Objects.requireNonNull(cancelled, "cancelled must not be null");
        Contact[] contacts = generator.generate(contactCount, cancelled, completed -> { });
        for (int run = 0; run < WARMUP_RUNS; run++) {
            measure(contacts, cancelled);
        }
        var insertion = new long[MEASUREMENT_RUNS];
        var search = new long[MEASUREMENT_RUNS];
        var removal = new long[MEASUREMENT_RUNS];
        var autocomplete = new long[MEASUREMENT_RUNS];
        var subtreeCount = new long[MEASUREMENT_RUNS];
        for (int run = 0; run < MEASUREMENT_RUNS; run++) {
            TrieBenchmarkResult sample = measure(contacts, cancelled);
            insertion[run] = sample.insertionNanos();
            search[run] = sample.searchNanos();
            removal[run] = sample.removalNanos();
            autocomplete[run] = sample.autocompleteNanos();
            subtreeCount[run] = sample.subtreeCountNanos();
        }
        checkCancellation(cancelled, 0);
        return new TrieBenchmarkResult(contactCount, median(insertion), median(search), median(removal),
                median(autocomplete), median(subtreeCount), ADDITIONAL_OPERATION_REPETITIONS,
                MEASUREMENT_RUNS);
    }

    private TrieBenchmarkResult measure(Contact[] contacts, BooleanSupplier cancelled) {
        var trie = new ContactTrie<Contact>(Contact::getName, Contact[]::new);

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
            if (found != contacts[index]) {
                throw new IllegalStateException("Контакт потерян во время замера поиска");
            }
        }
        long searchNanos = System.nanoTime() - searchStarted;

        var prefixes = new String[ADDITIONAL_OPERATION_REPETITIONS];
        long expectedMatches = 0;
        for (int index = 0; index < prefixes.length; index++) {
            String name = contacts[index % contacts.length].getName();
            prefixes[index] = name.substring(0, Math.min(2, name.length()));
            expectedMatches += trie.countByPrefix(prefixes[index]);
        }
        long autocompleteMatches = 0;
        long autocompleteStarted = System.nanoTime();
        for (int index = 0; index < ADDITIONAL_OPERATION_REPETITIONS; index++) {
            checkCancellation(cancelled, index);
            autocompleteMatches += trie.autocomplete(prefixes[index]).length;
        }
        long autocompleteNanos = System.nanoTime() - autocompleteStarted;

        long countedMatches = 0;
        long countStarted = System.nanoTime();
        for (int index = 0; index < ADDITIONAL_OPERATION_REPETITIONS; index++) {
            checkCancellation(cancelled, index);
            countedMatches += trie.countByPrefix(prefixes[index]);
        }
        long subtreeCountNanos = System.nanoTime() - countStarted;
        if (autocompleteMatches != expectedMatches || countedMatches != expectedMatches) {
            throw new IllegalStateException("Дополнительные операции дали неверный результат");
        }
        consumedResults = autocompleteMatches + countedMatches;

        long removalStarted = System.nanoTime();
        for (int index = 0; index < contacts.length; index++) {
            checkCancellation(cancelled, index);
            if (!trie.remove(contacts[index])) {
                throw new IllegalStateException("Контакт потерян во время замера удаления");
            }
        }
        long removalNanos = System.nanoTime() - removalStarted;

        return new TrieBenchmarkResult(
                contacts.length,
                insertionNanos,
                searchNanos,
                removalNanos,
                autocompleteNanos,
                subtreeCountNanos,
                ADDITIONAL_OPERATION_REPETITIONS,
                1
        );
    }

    private long median(long[] samples) {
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private void checkCancellation(BooleanSupplier cancelled, int iteration) {
        if (iteration % 100 == 0 && cancelled.getAsBoolean()) {
            throw new CancellationException("Замер отменён");
        }
    }
}
