package ru.fazbear.campusdirectory.trie;

public record TrieNodeSnapshot(
        char symbol,
        String path,
        int contactCount,
        int subtreeContactCount,
        boolean visited,
        TrieNodeSnapshot[] children
) {
    public TrieNodeSnapshot {
        children = children.clone();
    }

    @Override
    public TrieNodeSnapshot[] children() {
        return children.clone();
    }

    public boolean isRoot() {
        return path.isEmpty();
    }
}
