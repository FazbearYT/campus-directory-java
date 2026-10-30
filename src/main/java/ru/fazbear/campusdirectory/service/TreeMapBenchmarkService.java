package ru.fazbear.campusdirectory.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import ru.fazbear.campusdirectory.model.Contact;

public final class TreeMapBenchmarkService {
    private static final int WARMUP_RUNS = 2;
    private static final int MEASUREMENT_RUNS = 5;
    private static final int PREFIX_OPERATIONS = 100;
    private volatile long consumedResults;

    public TrieBenchmarkResult run(int count, BooleanSupplier cancelled) {
        if (count < 1) {
            throw new IllegalArgumentException("Размер набора должен быть положительным");
        }
        Contact[] contacts = new ContactGenerator().generateForBenchmark(count, cancelled);
        var prefixes = new String[PREFIX_OPERATIONS];
        for (int index = 0; index < prefixes.length; index++) {
            String name = contacts[index % contacts.length].getName();
            prefixes[index] = name.substring(0, Math.min(2, name.length())).toLowerCase(Locale.ROOT);
        }
        long expectedMatches = 0;
        for (Contact contact : contacts) {
            check(cancelled);
            String key = key(contact);
            for (String prefix : prefixes) {
                if (key.startsWith(prefix)) { expectedMatches++; }
            }
        }
        long[][] samples = new long[5][MEASUREMENT_RUNS];
        for (int run = -WARMUP_RUNS; run < MEASUREMENT_RUNS; run++) {
            long[] sample = measure(contacts, prefixes, expectedMatches, cancelled);
            if (run >= 0) {
                for (int operation = 0; operation < samples.length; operation++) {
                    samples[operation][run] = sample[operation];
                }
            }
        }
        for (long[] sample : samples) {
            Arrays.sort(sample);
        }
        check(cancelled);
        int middle = MEASUREMENT_RUNS / 2;
        return new TrieBenchmarkResult(count, samples[0][middle], samples[1][middle], samples[2][middle],
                samples[3][middle], samples[4][middle], PREFIX_OPERATIONS, MEASUREMENT_RUNS);
    }

    private long[] measure(Contact[] contacts, String[] prefixes, long expectedMatches, BooleanSupplier cancelled) {
        var map = new TreeMap<String, Contact>();
        var times = new long[5];
        long started = System.nanoTime();
        for (int index = 0; index < contacts.length; index++) {
            if (index % 100 == 0) { check(cancelled); }
            map.put(key(contacts[index]), contacts[index]);
        }
        times[0] = System.nanoTime() - started;
        started = System.nanoTime();
        for (int index = 0; index < contacts.length; index++) {
            if (index % 100 == 0) { check(cancelled); }
            Contact[] found = {map.get(key(contacts[index]))};
            if (found[0] != contacts[index]) {
                throw new IllegalStateException("TreeMap потерял контакт");
            }
        }
        times[1] = System.nanoTime() - started;
        if (map.size() != contacts.length) {
            throw new IllegalStateException("Набор сравнения должен содержать уникальные имена");
        }
        long matches = 0;
        started = System.nanoTime();
        for (String prefix : prefixes) {
            check(cancelled);
            var found = new ArrayList<Contact>();
            for (var entry : map.tailMap(prefix).entrySet()) {
                if (!entry.getKey().startsWith(prefix)) { break; }
                found.add(entry.getValue());
            }
            matches += found.toArray(Contact[]::new).length;
        }
        times[3] = System.nanoTime() - started;
        long counted = 0;
        started = System.nanoTime();
        for (String prefix : prefixes) {
            check(cancelled);
            for (String key : map.tailMap(prefix).keySet()) {
                if (!key.startsWith(prefix)) { break; }
                counted++;
            }
        }
        times[4] = System.nanoTime() - started;
        if (matches != expectedMatches || counted != expectedMatches) {
            throw new IllegalStateException("Операции TreeMap дали неверный результат");
        }
        consumedResults = matches + counted;
        started = System.nanoTime();
        for (int index = 0; index < contacts.length; index++) {
            if (index % 100 == 0) { check(cancelled); }
            if (map.remove(key(contacts[index])) != contacts[index]) {
                throw new IllegalStateException("TreeMap не удалил контакт");
            }
        }
        times[2] = System.nanoTime() - started;
        return times;
    }

    private String key(Contact contact) {
        return contact.getName().strip().toLowerCase(Locale.ROOT);
    }

    private void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Сравнение отменено");
        }
    }
}
