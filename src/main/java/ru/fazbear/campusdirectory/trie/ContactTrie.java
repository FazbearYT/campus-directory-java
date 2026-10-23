package ru.fazbear.campusdirectory.trie;

import java.util.Locale;
import java.util.Objects;

import ru.fazbear.campusdirectory.model.Contact;

public final class ContactTrie {
    private final Node root = new Node('\0');
    private String[] lastVisitedPaths = new String[0];
    private int size;

    public synchronized void add(Contact contact) {
        Objects.requireNonNull(contact, "contact must not be null");
        String key = normalizeKey(contact.getName());
        var visited = new PathBuffer();
        var path = new StringBuilder();
        Node current = root;
        visited.add("");

        for (int index = 0; index < key.length(); index++) {
            char symbol = key.charAt(index);
            current = current.getOrCreateChild(symbol);
            path.append(symbol);
            visited.add(path.toString());
        }

        current.addContact(contact);
        adjustSubtreeCounts(key, 1);
        size++;
        lastVisitedPaths = visited.toArray();
    }

    public synchronized Contact findFirst(String name) {
        Node node = findNode(normalizeKey(name), true);
        return node == null || node.contactCount == 0 ? null : node.contacts[0];
    }

    public synchronized boolean remove(Contact contact) {
        Objects.requireNonNull(contact, "contact must not be null");
        String key = normalizeKey(contact.getName());
        var visited = new PathBuffer();
        Node current = root;
        visited.add("");

        Node[] nodes = new Node[key.length() + 1];
        nodes[0] = root;
        var path = new StringBuilder();
        for (int index = 0; index < key.length(); index++) {
            current = current.findChild(key.charAt(index));
            if (current == null) {
                lastVisitedPaths = visited.toArray();
                return false;
            }
            nodes[index + 1] = current;
            path.append(key.charAt(index));
            visited.add(path.toString());
        }

        if (!current.removeContact(contact)) {
            lastVisitedPaths = visited.toArray();
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
        lastVisitedPaths = visited.toArray();
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
        return snapshot(root, "");
    }

    public synchronized int size() {
        return size;
    }

    private Node findNode(String key, boolean recordVisit) {
        var visited = new PathBuffer();
        var path = new StringBuilder();
        Node current = root;
        visited.add("");

        for (int index = 0; index < key.length(); index++) {
            current = current.findChild(key.charAt(index));
            if (current == null) {
                if (recordVisit) {
                    lastVisitedPaths = visited.toArray();
                }
                return null;
            }
            path.append(key.charAt(index));
            visited.add(path.toString());
        }

        if (recordVisit) {
            lastVisitedPaths = visited.toArray();
        }
        return current;
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

    private TrieNodeSnapshot snapshot(Node node, String path) {
        var children = new TrieNodeSnapshot[node.childCount];
        for (int index = 0; index < node.childCount; index++) {
            Node child = node.children[index];
            children[index] = snapshot(child, path + child.symbol);
        }
        return new TrieNodeSnapshot(
                node.symbol,
                path,
                node.contactCount,
                node.subtreeContactCount,
                wasVisited(path),
                children
        );
    }

    private boolean wasVisited(String path) {
        for (String visitedPath : lastVisitedPaths) {
            if (visitedPath.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeKey(String value) {
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
                if (contacts[index] == contact || contacts[index].equals(contact)) {
                    int moved = contactCount - index - 1;
                    if (moved > 0) {
                        System.arraycopy(contacts, index + 1, contacts, index, moved);
                    }
                    contacts[--contactCount] = null;
                    return true;
                }
            }
            return false;
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

    private static final class PathBuffer {
        private String[] values = new String[8];
        private int size;

        private void add(String path) {
            if (size == values.length) {
                var expanded = new String[values.length * 2];
                System.arraycopy(values, 0, expanded, 0, size);
                values = expanded;
            }
            values[size++] = path;
        }

        private String[] toArray() {
            var result = new String[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }
}
