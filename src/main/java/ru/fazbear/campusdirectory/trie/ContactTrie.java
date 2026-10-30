package ru.fazbear.campusdirectory.trie;

import java.util.function.Function;
import java.util.function.IntFunction;

public final class ContactTrie<T> {
    private final TrieStorage<T> storage;

    public ContactTrie(Function<? super T, String> keyExtractor, IntFunction<T[]> arrayFactory) {
        storage = new TrieStorage<>(keyExtractor, arrayFactory);
    }

    public synchronized void add(T value) { storage.add(value); }
    public synchronized boolean remove(T value) { return storage.remove(value); }
    public synchronized T findFirst(String key) { return storage.findFirst(key); }
    public synchronized T[] autocomplete(String prefix) { return storage.autocomplete(prefix); }
    public synchronized int countByPrefix(String prefix) { return storage.countByPrefix(prefix); }
    public synchronized T[] getAll() { return storage.getAll(); }
    public synchronized int size() { return storage.size(); }
    public synchronized TrieNodeSnapshot snapshot() { return storage.snapshot(); }
    public synchronized TrieNodeSnapshot snapshot(int maximumNodes) { return storage.snapshot(maximumNodes); }
    public synchronized TrieNodeSnapshot snapshot(int maximumNodes, int maximumDepth) {
        return storage.snapshot(maximumNodes, maximumDepth);
    }
}
