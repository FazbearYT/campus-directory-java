package ru.fazbear.campusdirectory.trie;

import java.util.Locale;
import java.util.Objects;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.ContactValidator;

public final class ContactTrie {
    private final Node root = new Node('\0');
    private long visitId;
    private int size;

    public synchronized void add(Contact contact) {
        Objects.requireNonNull(contact, "contact must not be null");
        String key = normalizeKey(contact.getName());
        Node current = root;
        beginVisit();

        for (int index = 0; index < key.length(); index++) {
            char symbol = key.charAt(index);
            current = current.getOrCreateChild(symbol);
            current.lastVisitId = visitId;
        }

        current.addContact(contact);
        adjustSubtreeCounts(key, 1);
        size++;
    }

    public synchronized Contact findFirst(String name) {
        Node node = findNode(normalizeKey(name), true);
        return node == null || node.contactCount == 0 ? null : node.contacts[0];
    }

    public synchronized boolean remove(Contact contact) {
        Objects.requireNonNull(contact, "contact must not be null");
        String key = normalizeKey(contact.getName());
        Node current = root;
        beginVisit();

        Node[] nodes = new Node[key.length() + 1];
        nodes[0] = root;
        for (int index = 0; index < key.length(); index++) {
            current = current.findChild(key.charAt(index));
            if (current == null) {
                return false;
            }
            nodes[index + 1] = current;
            current.lastVisitId = visitId;
        }

        if (!current.removeContact(contact)) {
            return false;
        }

        for (Node node : nodes) {
            node.subtreeContactCount--;
        }
        for (int index = key.length(); index > 0; index--) {
            Node node = nodes[index];
            if (node.subtreeContactCount != 0) {
                break;
            }
            nodes[index - 1].removeChild(key.charAt(index - 1));
        }

        size--;
        return true;
    }

    public synchronized Contact[] autocomplete(String prefix) {
        String key = normalizePrefix(prefix);
        Node node = findNode(key, true);
        if (node == null) {
            return new Contact[0];
        }

        var contacts = new ContactBuffer(node.subtreeContactCount);
        collectContacts(node, contacts);
        return contacts.toArray();
    }

    public synchronized int countByPrefix(String prefix) {
        Node node = findNode(normalizePrefix(prefix), true);
        return node == null ? 0 : node.subtreeContactCount;
    }

    public synchronized Contact[] getAll() {
        var contacts = new ContactBuffer(size);
        collectContacts(root, contacts);
        return contacts.toArray();
    }

    public synchronized TrieNodeSnapshot snapshot() {
        return snapshot(Integer.MAX_VALUE);
    }

    public synchronized TrieNodeSnapshot snapshot(int maximumNodes) {
        return snapshot(maximumNodes, Integer.MAX_VALUE);
    }

    public synchronized TrieNodeSnapshot snapshot(int maximumNodes, int maximumDepth) {
        if (maximumNodes < 1) {
            throw new IllegalArgumentException("Количество отображаемых узлов должно быть положительным");
        }
        if (maximumDepth < 0) {
            throw new IllegalArgumentException("Глубина снимка не может быть отрицательной");
        }
        return breadthFirstSnapshot(maximumNodes, maximumDepth);
    }

    public synchronized int size() {
        return size;
    }

    private Node findNode(String key, boolean recordVisit) {
        Node current = root;
        if (recordVisit) {
            beginVisit();
        }

        for (int index = 0; index < key.length(); index++) {
            current = current.findChild(key.charAt(index));
            if (current == null) {
                return null;
            }
            if (recordVisit) {
                current.lastVisitId = visitId;
            }
        }

        return current;
    }

    private void beginVisit() {
        visitId++;
        root.lastVisitId = visitId;
    }

    private void adjustSubtreeCounts(String key, int change) {
        Node current = root;
        current.subtreeContactCount += change;
        for (int index = 0; index < key.length(); index++) {
            current = current.findChild(key.charAt(index));
            current.subtreeContactCount += change;
        }
    }

    private void collectContacts(Node node, ContactBuffer destination) {
        for (int index = 0; index < node.contactCount; index++) {
            destination.add(node.contacts[index]);
        }
        for (int index = 0; index < node.childCount; index++) {
            collectContacts(node.children[index], destination);
        }
    }

    private TrieNodeSnapshot breadthFirstSnapshot(int maximumNodes, int maximumDepth) {
        var queue = new SnapshotEntry[Math.min(maximumNodes, 64)];
        queue[0] = new SnapshotEntry(root, "");
        int tail = 1;
        for (int head = 0; head < tail; head++) {
            SnapshotEntry entry = queue[head];
            int visibleChildren = entry.path.length() >= maximumDepth ? 0
                    : Math.min(entry.node.childCount, maximumNodes - tail);
            entry.firstChild = tail;
            entry.visibleChildren = visibleChildren;
            if (tail + visibleChildren > queue.length) {
                var expanded = new SnapshotEntry[Math.max(tail + visibleChildren,
                        (int) Math.min(maximumNodes, queue.length * 2L))];
                System.arraycopy(queue, 0, expanded, 0, tail);
                queue = expanded;
            }
            for (int index = 0; index < visibleChildren; index++) {
                Node child = entry.node.children[index];
                queue[tail++] = new SnapshotEntry(child, entry.path + child.symbol);
            }
        }
        for (int index = tail - 1; index >= 0; index--) {
            SnapshotEntry entry = queue[index];
            var children = new TrieNodeSnapshot[entry.visibleChildren];
            for (int child = 0; child < children.length; child++) {
                children[child] = queue[entry.firstChild + child].snapshot;
            }
            Node node = entry.node;
            entry.snapshot = new TrieNodeSnapshot(node.symbol, entry.path, node.contactCount,
                    node.subtreeContactCount, visitId != 0 && node.lastVisitId == visitId,
                    node.childCount - children.length, children);
        }
        return queue[0].snapshot;
    }

    private static String normalizeKey(String value) {
        if (Objects.requireNonNull(value, "name must not be null").strip().length()
                > ContactValidator.MAXIMUM_NAME_LENGTH) {
            throw new IllegalArgumentException("Имя не должно превышать "
                    + ContactValidator.MAXIMUM_NAME_LENGTH + " символов");
        }
        String key = normalizePrefix(value);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Имя контакта не может быть пустым");
        }
        return key;
    }

    private static String normalizePrefix(String value) {
        return Objects.requireNonNull(value, "name must not be null").strip().toLowerCase(Locale.ROOT);
    }

    private static final class Node {
        private static final int INITIAL_CAPACITY = 2;

        private final char symbol;
        private Node[] children = new Node[INITIAL_CAPACITY];
        private Contact[] contacts = new Contact[INITIAL_CAPACITY];
        private int childCount;
        private int contactCount;
        private int subtreeContactCount;
        private long lastVisitId;

        private Node(char symbol) {
            this.symbol = symbol;
        }

        private Node findChild(char target) {
            int index = childIndex(target);
            return index >= 0 ? children[index] : null;
        }

        private Node getOrCreateChild(char target) {
            int index = childIndex(target);
            if (index >= 0) {
                return children[index];
            }

            int insertionIndex = -index - 1;
            ensureChildCapacity(childCount + 1);
            System.arraycopy(children, insertionIndex, children, insertionIndex + 1, childCount - insertionIndex);
            var child = new Node(target);
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

        private void addContact(Contact contact) {
            ensureContactCapacity(contactCount + 1);
            contacts[contactCount++] = contact;
        }

        private boolean removeContact(Contact contact) {
            for (int index = 0; index < contactCount; index++) {
                if (contacts[index] == contact) {
                    removeContactAt(index);
                    return true;
                }
            }
            for (int index = 0; index < contactCount; index++) {
                if (contacts[index].equals(contact)) {
                    removeContactAt(index);
                    return true;
                }
            }
            return false;
        }

        private void removeContactAt(int index) {
            int moved = contactCount - index - 1;
            if (moved > 0) {
                System.arraycopy(contacts, index + 1, contacts, index, moved);
            }
            contacts[--contactCount] = null;
        }

        private void ensureChildCapacity(int required) {
            if (required <= children.length) {
                return;
            }
            var expanded = new Node[children.length * 2];
            System.arraycopy(children, 0, expanded, 0, childCount);
            children = expanded;
        }

        private void ensureContactCapacity(int required) {
            if (required <= contacts.length) {
                return;
            }
            var expanded = new Contact[contacts.length * 2];
            System.arraycopy(contacts, 0, expanded, 0, contactCount);
            contacts = expanded;
        }
    }

    private static final class ContactBuffer {
        private Contact[] values;
        private int size;

        private ContactBuffer(int capacity) {
            values = new Contact[Math.max(capacity, 1)];
        }

        private void add(Contact contact) {
            if (size == values.length) {
                var expanded = new Contact[values.length * 2];
                System.arraycopy(values, 0, expanded, 0, size);
                values = expanded;
            }
            values[size++] = contact;
        }

        private Contact[] toArray() {
            var result = new Contact[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }

    private static final class SnapshotEntry {
        private final Node node;
        private final String path;
        private int firstChild;
        private int visibleChildren;
        private TrieNodeSnapshot snapshot;

        private SnapshotEntry(Node node, String path) {
            this.node = node;
            this.path = path;
        }
    }
}
