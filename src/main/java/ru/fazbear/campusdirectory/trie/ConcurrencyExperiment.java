package ru.fazbear.campusdirectory.trie;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class ConcurrencyExperiment {
    public Result run(boolean synchronizedAccess, int workers, int perWorker, BooleanSupplier cancelled)
            throws Exception {
        if (workers < 2 || workers > 32 || perWorker < 1 || perWorker > 100_000) {
            throw new IllegalArgumentException("Допустимо 2–32 потока, по 1–100000 записей");
        }
        check(cancelled);
        var unsafe = new TrieStorage<String>(value -> value, String[]::new);
        var safe = new ContactTrie<String>(value -> value, String[]::new);
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(workers);
        var errors = new AtomicInteger();
        String[] errorMessages = new String[workers];
        long started = System.nanoTime();
        try (var executor = Executors.newFixedThreadPool(workers)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int worker = 0; worker < workers; worker++) {
                int id = worker;
                tasks.add(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Потоки не получили сигнал старта");
                    }
                    for (int index = 0; index < perWorker; index++) {
                        check(cancelled);
                        String value = "writer-" + id + "-" + index;
                        try {
                            if (synchronizedAccess) { safe.add(value); }
                            else { unsafe.add(value); }
                        } catch (RuntimeException error) {
                            errors.incrementAndGet();
                            errorMessages[id] = describe(error);
                        }
                    }
                    return null;
                });
            }
            var futures = new ArrayList<java.util.concurrent.Future<Void>>();
            for (var task : tasks) { futures.add(executor.submit(task)); }
            try {
                if (!ready.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Потоки не готовы к запуску");
                }
                start.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                for (var future : futures) {
                    while (!future.isDone()) {
                        check(cancelled);
                        if (System.nanoTime() >= deadline) {
                            throw new IllegalStateException("Превышено время эксперимента");
                        }
                        TimeUnit.MILLISECONDS.sleep(10);
                    }
                    future.get();
                }
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
        check(cancelled);
        int size = synchronizedAccess ? safe.size() : unsafe.size();
        int subtreeCount = synchronizedAccess ? safe.countByPrefix("") : unsafe.countByPrefix("");
        int stored = 0;
        int missing = 0;
        try {
            stored = synchronizedAccess ? safe.getAll().length : unsafe.getAll().length;
            for (int worker = 0; worker < workers; worker++) {
                for (int index = 0; index < perWorker; index++) {
                    check(cancelled);
                    String key = "writer-" + worker + "-" + index;
                    String found = synchronizedAccess ? safe.findFirst(key) : unsafe.findFirst(key);
                    if (!key.equals(found)) { missing++; }
                }
            }
        } catch (CancellationException error) {
            throw error;
        } catch (RuntimeException error) {
            errors.incrementAndGet();
            stored = -1;
            missing = -1;
            errorMessages[0] = describe(error);
        }
        String details = String.join("\n", java.util.Arrays.stream(errorMessages)
                .filter(java.util.Objects::nonNull).toList());
        return new Result(synchronizedAccess, workers * perWorker, size, subtreeCount, stored, missing,
                errors.get(), System.nanoTime() - started, details);
    }

    private void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Эксперимент отменён");
        }
    }

    private String describe(RuntimeException error) {
        return error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : ": " + error.getMessage());
    }

    public record Result(boolean synchronizedAccess, int expected, int size, int subtreeCount, int stored,
                         int missing, int errors, long nanos, String details) {
        public boolean consistent() {
            return size == expected && subtreeCount == expected && stored == expected && missing == 0 && errors == 0;
        }
    }
}
