package ru.fazbear.campusdirectory.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.CorporateContact;
import ru.fazbear.campusdirectory.model.EmergencyContact;
import ru.fazbear.campusdirectory.model.PersonalContact;
import ru.fazbear.campusdirectory.service.ContactFileService;
import ru.fazbear.campusdirectory.service.ContactValidationException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactSerializerTest {
    @TempDir
    Path temporary;

    static Stream<Serializer<ContactArchive>> serializers() {
        return Stream.of(new JsonContactSerializer(), new BinaryContactSerializer());
    }

    private List<Contact> referenceContacts() {
        return List.of(
                new PersonalContact("Анна Ёлкина", "+7 900 100-20-30", "anna@example.org", ""),
                new CorporateContact("Борис Иванов", "+7 383 100-20-30", "boris@example.org",
                        "НГТУ \"НЭТИ\"; корпус 1", "Инженер", 123),
                new EmergencyContact("Диспетчерская", "112", "", "Университет"),
                new PersonalContact("Анна Ёлкина", "456", "", ""));
    }

    private Path fixture(String name) throws Exception {
        Path copy = temporary.resolve(name);
        try (var input = getClass().getResourceAsStream("/serialization/" + name)) {
            Files.copy(input, copy);
        }
        return copy;
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void loadsIndependentReferenceFileWithEverySubtype(Serializer<ContactArchive> serializer) throws Exception {
        assertEquals(ContactArchive.from(referenceContacts()), serializer.load(fixture("contacts." + serializer.format())));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void roundtripPreservesEveryFieldOrderAndDuplicate(Serializer<ContactArchive> serializer) throws Exception {
        var contacts = new java.util.ArrayList<>(referenceContacts());
        contacts.add(contacts.getFirst());
        ContactArchive archive = ContactArchive.from(contacts);
        Path file = temporary.resolve("roundtrip." + serializer.format());
        serializer.save(archive, file);
        assertEquals(archive, serializer.load(file));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void emptyArchiveReplacesAnExistingFile(Serializer<ContactArchive> serializer) throws Exception {
        Path file = fixture("contacts." + serializer.format());
        var empty = ContactArchive.from(List.of());
        serializer.save(empty, file);
        assertEquals(empty, serializer.load(file));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void preservesStringsLongerThanWriteUtfLimit(Serializer<ContactArchive> serializer) throws Exception {
        var archive = ContactArchive.from(List.of(new PersonalContact("Анна", "123", "", "я".repeat(40_000))));
        Path file = temporary.resolve("large." + serializer.format());
        serializer.save(archive, file);
        assertEquals(archive, serializer.load(file));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void rebuildsSearchableTrieAndKeepsEmergencyReadOnly(Serializer<ContactArchive> serializer) throws Exception {
        var directory = new ContactFileService().load(serializer, fixture("contacts." + serializer.format()),
                () -> false, completed -> { });
        assertEquals(4, directory.size());
        assertEquals(2, directory.countByPrefix("АННА"));
        assertEquals(2, directory.findByName("анна ёлкина").size());
        assertThrows(IllegalArgumentException.class, () -> directory.remove(directory.findFirstByName("Диспетчерская")));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void reportsMissingFile(Serializer<ContactArchive> serializer) {
        assertThrows(IOException.class, () -> serializer.load(temporary.resolve("missing")));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void rejectsEmptyFile(Serializer<ContactArchive> serializer) throws Exception {
        Path file = Files.createFile(temporary.resolve("empty"));
        assertThrows(IOException.class, () -> serializer.load(file));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void rejectsTruncatedReferenceFile(Serializer<ContactArchive> serializer) throws Exception {
        Path file = fixture("contacts." + serializer.format());
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length / 2));
        assertThrows(IOException.class, () -> serializer.load(file));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void rejectsFutureVersion(Serializer<ContactArchive> serializer) throws Exception {
        assertThrows(IOException.class, () -> serializer.load(fixture("future." + serializer.format())));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void invalidContactFailsBeforePublishingNewDirectory(Serializer<ContactArchive> serializer) throws Exception {
        assertThrows(ContactValidationException.class, () -> new ContactFileService().load(serializer,
                fixture("invalid-contact." + serializer.format()), () -> false, completed -> { }));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void cancellationAtFinalProgressDoesNotReturnReplacement(Serializer<ContactArchive> serializer) throws Exception {
        var cancelled = new AtomicBoolean();
        assertThrows(CancellationException.class, () -> new ContactFileService().load(serializer,
                fixture("contacts." + serializer.format()), cancelled::get, completed -> cancelled.set(true)));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void alreadyCancelledImportDoesNotReadTheFile(Serializer<ContactArchive> serializer) {
        assertThrows(CancellationException.class, () -> new ContactFileService().load(serializer,
                temporary.resolve("missing"), () -> true, completed -> { }));
    }

    @Test
    void reportsBrokenJsonFixture() throws Exception {
        assertThrows(IOException.class, () -> new JsonContactSerializer().load(fixture("broken.json")));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void appendingDataIsRejected(Serializer<ContactArchive> serializer) throws Exception {
        Path file = fixture("contacts." + serializer.format());
        Files.writeString(file, "{}", java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> serializer.load(file));
    }

    @ParameterizedTest
    @MethodSource("serializers")
    void failedSaveDoesNotLeaveTemporaryFiles(Serializer<ContactArchive> serializer) throws Exception {
        Path target = Files.createDirectory(temporary.resolve("occupied"));
        Files.writeString(target.resolve("keep"), "keep");
        assertThrows(IOException.class, () -> serializer.save(ContactArchive.from(referenceContacts()), target));
        try (var paths = Files.list(temporary)) {
            assertEquals(List.of(target), paths.toList());
        }
        assertEquals("keep", Files.readString(target.resolve("keep")));
    }

    @Test
    void failedAtomicWritePreservesPreviousFile() throws Exception {
        Path file = fixture("contacts.json");
        byte[] previous = Files.readAllBytes(file);
        assertThrows(IOException.class, () -> AtomicFile.write(file, temporaryFile -> {
            Files.writeString(temporaryFile, "partial");
            throw new IOException("Запись прервана");
        }));
        assertArrayEquals(previous, Files.readAllBytes(file));
        try (var paths = Files.list(temporary)) {
            assertEquals(List.of(file), paths.toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "{\"version\":1}", "{\"version\":1,\"contacts\":null}",
            "{\"version\":1,\"contacts\":[null]}", "{\"version\":1,\"contacts\":[],\"extra\":0}",
            "{\"version\":1,\"version\":1,\"contacts\":[]}", "{\"version\":\"1\",\"contacts\":[]}",
            "{\"version\":1.5,\"contacts\":[]}", "{\"version\":null,\"contacts\":[]}"
    })
    void rejectsInvalidJsonEnvelope(String contents) throws Exception {
        Path file = temporary.resolve("invalid.json");
        Files.writeString(file, contents);
        assertThrows(IOException.class, () -> new JsonContactSerializer().load(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-name", "null-name", "unknown-type", "fractional-extension"})
    void rejectsMalformedJsonContact(String mutation) throws Exception {
        String json = Files.readString(fixture("contacts.json"));
        json = switch (mutation) {
            case "missing-name" -> json.replace("\"name\":\"Анна Ёлкина\",", "");
            case "null-name" -> json.replace("\"name\":\"Анна Ёлкина\"", "\"name\":null");
            case "unknown-type" -> json.replace("PERSONAL", "UNKNOWN");
            case "fractional-extension" -> json.replace("\"extension\":123", "\"extension\":1.5");
            default -> throw new AssertionError(mutation);
        };
        Path file = temporary.resolve("invalid.json");
        Files.writeString(file, json);
        assertThrows(IOException.class, () -> new JsonContactSerializer().load(file));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 8, 12})
    void rejectsInvalidBinaryHeaderOrLength(int offset) throws Exception {
        Path file = fixture("contacts.bin");
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer.wrap(bytes).putInt(offset, -1);
        Files.write(file, bytes);
        assertThrows(IOException.class, () -> new BinaryContactSerializer().load(file));
    }

    @Test
    void rejectsMalformedUtf8InBinaryFile() throws Exception {
        Path file = fixture("contacts.bin");
        byte[] bytes = Files.readAllBytes(file);
        bytes[16] = (byte) 0xff;
        Files.write(file, bytes);
        assertThrows(IOException.class, () -> new BinaryContactSerializer().load(file));
    }

    @Test
    void archiveIsAnImmutableSnapshot() {
        var source = new java.util.ArrayList<>(ContactArchive.from(referenceContacts()).contacts());
        var archive = new ContactArchive(1, source);
        source.clear();
        assertEquals(4, archive.contacts().size());
        assertThrows(UnsupportedOperationException.class, () -> archive.contacts().clear());
        assertTrue(archive.contacts().stream().anyMatch(data -> data.type().name().equals("EMERGENCY")));
    }
}
