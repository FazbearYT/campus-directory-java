package ru.fazbear.campusdirectory.service;

public record TrieBenchmarkResult(
        int contactCount,
        long insertionNanos,
        long searchNanos,
        long removalNanos,
        long autocompleteNanos,
        long subtreeCountNanos
) {
    public double insertionMillis() {
        return toMillis(insertionNanos);
    }

    public double searchMillis() {
        return toMillis(searchNanos);
    }

    public double removalMillis() {
        return toMillis(removalNanos);
    }

    public double autocompleteMillis() {
        return toMillis(autocompleteNanos);
    }

    public double subtreeCountMillis() {
        return toMillis(subtreeCountNanos);
    }

    private double toMillis(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }
}
