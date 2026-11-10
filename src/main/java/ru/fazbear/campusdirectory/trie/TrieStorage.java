package ru.fazbear.campusdirectory.trie;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

final class TrieStorage<T> {
    private static final int MAXIMUM_KEY_LENGTH = 256;
    private final Function<? super T, String> keyExtractor;
    private final IntFunction<T[]> arrayFactory;
    private final Node<T> root;
    private long visitId;
    private int size;

    TrieStorage(Function<? super T, String> keyExtractor, IntFunction<T[]> arrayFactory) {
        this.keyExtractor = Objects.requireNonNull(keyExtractor);
        this.arrayFactory = Objects.requireNonNull(arrayFactory);
        this.root = new Node<>('\0', arrayFactory);
    }

    @SuppressWarnings("unchecked")
    private static <E> Node<E>[] nodeArray(int length) {
        return (Node<E>[]) new Node<?>[length];
    }

    @SuppressWarnings("unchecked")
    private static <E> SnapshotEntry<E>[] entryArray(int length) {
        return (SnapshotEntry<E>[]) new SnapshotEntry<?>[length];
    }

    public void add(T value) {
        Objects.requireNonNull(value, "value must not be null");
        String key = normalizeKey(keyExtractor.apply(value));
        Node<T> current = root;
        beginVisit();

        for (int index = 0; index < key.length(); index++) {
            char symbol = key.charAt(index);
            current = current.getOrCreateChild(symbol);
            current.lastVisitId = visitId;
        }

        current.addValue(value);
        adjustSubtreeCounts(key, 1);
        size++;
    }

    public T findFirst(String name) {
        Node<T> node = findNode(normalizeKey(name));
        return node == null || node.valueCount == 0 ? null : node.values[0];
    }

    public T[] findExact(String name) {
        Node<T> node = findNode(normalizeKey(name));
        int count = node == null ? 0 : node.valueCount;
        T[] result = arrayFactory.apply(count);
        if (count > 0) {
            System.arraycopy(node.values, 0, result, 0, count);
        }
        return result;
    }

    public boolean remove(T value) {
        Objects.requireNonNull(value, "value must not be null");
        String key = normalizeKey(keyExtractor.apply(value));
        Node<T> current = root;
        beginVisit();

        Node<T>[] nodes = TrieStorage.<T>nodeArray(key.length() + 1);
        nodes[0] = root;
        for (int index = 0; index < key.length(); index++) {
            current = current.findChild(key.charAt(index));
            if (current == null) {
                return false;
            }
            nodes[index + 1] = current;
            current.lastVisitId = visitId;
        }

        if (!current.removeValue(value)) {
            return false;
        }

        for (Node<T> node : nodes) {
            node.subtreeValueCount--;
        }
        for (int index = key.length(); index > 0; index--) {
            Node<T> node = nodes[index];
            if (node.subtreeValueCount != 0) {
                break;
            }
            nodes[index - 1].removeChild(key.charAt(index - 1));
        }

        size--;
        return true;
    }

    public T[] autocomplete(String prefix) {
        String key = normalizePrefix(prefix);
        Node<T> node = findNode(key);
        if (node == null) {
            return arrayFactory.apply(0);
        }

        var values = new ValueBuffer(node.subtreeValueCount);
        collectValues(node, values);
        return values.toArray();
    }

    public int countByPrefix(String prefix) {
        Node<T> node = findNode(normalizePrefix(prefix));
        return node == null ? 0 : node.subtreeValueCount;
    }

    public T[] getAll() {
        var values = new ValueBuffer(size);
        collectValues(root, values);
        return values.toArray();
    }

    public TrieNodeSnapshot snapshot() {
        return snapshot(Integer.MAX_VALUE);
    }

    public TrieNodeSnapshot snapshot(int maximumNodes) {
        return snapshot(maximumNodes, Integer.MAX_VALUE);
    }

    public TrieNodeSnapshot snapshot(int maximumNodes, int maximumDepth) {
        if (maximumNodes < 1) {
            throw new IllegalArgumentException("Количество отображаемых узлов должно быть положительным");
        }
        if (maximumDepth < 0) {
            throw new IllegalArgumentException("Глубина снимка не может быть отрицательной");
        }
        return breadthFirstSnapshot(maximumNodes, maximumDepth);
    }

    public int size() {
        return size;
    }

    private Node<T> findNode(String key) {
        Node<T> current = root;
        beginVisit();

        for (int index = 0; index < key.length(); index++) {
            current = current.findChild(key.charAt(index));
            if (current == null) {
                return null;
            }
            current.lastVisitId = visitId;
        }

        return current;
    }

    private void beginVisit() {
        visitId++;
        root.lastVisitId = visitId;
    }

    private void adjustSubtreeCounts(String key, int change) {
        Node<T> current = root;
        current.subtreeValueCount += change;
        for (int index = 0; index < key.length(); index++) {
            current = current.findChild(key.charAt(index));
            current.subtreeValueCount += change;
        }
    }

    private void collectValues(Node<T> node, ValueBuffer destination) {
        for (int index = 0; index < node.valueCount; index++) {
            destination.add(node.values[index]);
        }
        for (int index = 0; index < node.childCount; index++) {
            collectValues(node.children[index], destination);
        }
    }

    private TrieNodeSnapshot breadthFirstSnapshot(int maximumNodes, int maximumDepth) {
        var queue = TrieStorage.<T>entryArray(Math.min(maximumNodes, 64));
        queue[0] = new SnapshotEntry<>(root, "");
        int tail = 1;
        for (int head = 0; head < tail; head++) {
            SnapshotEntry<T> entry = queue[head];
            int visibleChildren = entry.path.length() >= maximumDepth ? 0
                    : Math.min(entry.node.childCount, maximumNodes - tail);
            entry.firstChild = tail;
            entry.visibleChildren = visibleChildren;
            if (tail + visibleChildren > queue.length) {
                var expanded = TrieStorage.<T>entryArray(Math.max(tail + visibleChildren,
                        (int) Math.min(maximumNodes, queue.length * 2L)));
                System.arraycopy(queue, 0, expanded, 0, tail);
                queue = expanded;
            }
            for (int index = 0; index < visibleChildren; index++) {
                Node<T> child = entry.node.children[index];
                queue[tail++] = new SnapshotEntry<>(child, entry.path + child.symbol);
            }
        }
        for (int index = tail - 1; index >= 0; index--) {
            SnapshotEntry<T> entry = queue[index];
            var children = new TrieNodeSnapshot[entry.visibleChildren];
            for (int child = 0; child < children.length; child++) {
                children[child] = queue[entry.firstChild + child].snapshot;
            }
            Node<T> node = entry.node;
            entry.snapshot = new TrieNodeSnapshot(node.symbol, entry.path, node.valueCount,
                    node.subtreeValueCount, visitId != 0 && node.lastVisitId == visitId,
                    node.childCount - children.length, children);
        }
        return queue[0].snapshot;
    }

    private static String normalizeKey(String value) {
        String key = value.strip();
        if (key.length() > MAXIMUM_KEY_LENGTH) {
            throw new IllegalArgumentException("Имя не должно превышать "
                    + MAXIMUM_KEY_LENGTH + " символов");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Имя контакта не может быть пустым");
        }
        return key.toLowerCase(Locale.ROOT);
    }

    private static String normalizePrefix(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static final class Node<T> {
        private static final int INITIAL_CAPACITY = 2;

        private final char symbol;
        private Node<T>[] children = nodeArray(INITIAL_CAPACITY);
        private final IntFunction<T[]> arrayFactory;
        private T[] values;
        private int childCount;
        private int valueCount;
        private int subtreeValueCount;
        private long lastVisitId;

        private Node(char symbol, IntFunction<T[]> arrayFactory) {
            this.symbol = symbol;
            this.arrayFactory = arrayFactory;
            values = arrayFactory.apply(INITIAL_CAPACITY);
        }

        private Node<T> findChild(char target) {
            int index = childIndex(target);
            return index >= 0 ? children[index] : null;
        }

        private Node<T> getOrCreateChild(char target) {
            int index = childIndex(target);
            if (index >= 0) {
                return children[index];
            }

            int insertionIndex = -index - 1;
            ensureChildCapacity(childCount + 1);
            System.arraycopy(children, insertionIndex, children, insertionIndex + 1, childCount - insertionIndex);
            var child = new Node<>(target, arrayFactory);
            children[insertionIndex] = child;
            childCount++;
            return child;
        }

        private void removeChild(char target) {
            int index = childIndex(target);
            if (index < 0) {
                throw new IllegalStateException("Trie child is missing");
            }
            int moved = childCount - index - 1;
            if (moved > 0) {
                System.arraycopy(children, index + 1, children, index, moved);
            }
            children[--childCount] = null;
        }

        private int childIndex(char target) {
            int low = 0;
            int high = childCount - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                char current = children[middle].symbol;
                if (current < target) {
                    low = middle + 1;
                } else if (current > target) {
                    high = middle - 1;
                } else {
                    return middle;
                }
            }
            return -low - 1;
        }

        private void addValue(T value) {
            ensureValueCapacity(valueCount + 1);
            values[valueCount++] = value;
        }

        private boolean removeValue(T value) {
            for (int index = 0; index < valueCount; index++) {
                if (values[index] == value) {
                    removeValueAt(index);
                    return true;
                }
            }
            for (int index = 0; index < valueCount; index++) {
                if (values[index].equals(value)) {
                    removeValueAt(index);
                    return true;
                }
            }
            return false;
        }

        private void removeValueAt(int index) {
            int moved = valueCount - index - 1;
            if (moved > 0) {
                System.arraycopy(values, index + 1, values, index, moved);
            }
            values[--valueCount] = null;
        }

        private void ensureChildCapacity(int required) {
            if (required <= children.length) {
                return;
            }
            var expanded = TrieStorage.<T>nodeArray(children.length * 2);
            System.arraycopy(children, 0, expanded, 0, childCount);
            children = expanded;
        }

        private void ensureValueCapacity(int required) {
            if (required <= values.length) {
                return;
            }
            var expanded = arrayFactory.apply(values.length * 2);
            System.arraycopy(values, 0, expanded, 0, valueCount);
            values = expanded;
        }
    }

    private final class ValueBuffer {
        private T[] values;
        private int size;

        private ValueBuffer(int capacity) {
            values = arrayFactory.apply(Math.max(capacity, 1));
        }

        private void add(T value) {
            if (size == values.length) {
                var expanded = arrayFactory.apply(values.length * 2);
                System.arraycopy(values, 0, expanded, 0, size);
                values = expanded;
            }
            values[size++] = value;
        }

        private T[] toArray() {
            var result = arrayFactory.apply(size);
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }

    private static final class SnapshotEntry<T> {
        private final Node<T> node;
        private final String path;
        private int firstChild;
        private int visibleChildren;
        private TrieNodeSnapshot snapshot;

        private SnapshotEntry(Node<T> node, String path) {
            this.node = node;
            this.path = path;
        }
    }
}
