package ru.fazbear.campusdirectory.io;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import ru.fazbear.campusdirectory.model.ContactType;

public final class BinaryContactSerializer implements Serializer<ContactArchive> {
    private static final int MAGIC = 0x43444952;

    @Override
    public String format() {
        return "bin";
    }

    @Override
    public void save(ContactArchive data, Path file) throws IOException {
        AtomicFile.write(file, temporary -> {
            try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeInt(data.version());
                output.writeInt(data.contacts().size());
                for (ContactData contact : data.contacts()) {
                    writeString(output, contact.type().name());
                    writeString(output, contact.name());
                    writeString(output, contact.phone());
                    writeString(output, contact.email());
                    writeString(output, contact.organization());
                    writeString(output, contact.position());
                    output.writeInt(contact.extension());
                }
            }
        });
    }

    @Override
    public ContactArchive load(Path file) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Неверная сигнатура бинарного файла");
            }
            int version = input.readInt();
            if (version != ContactArchive.CURRENT_VERSION) {
                throw new IOException("Неподдерживаемая версия: " + version);
            }
            int count = input.readInt();
            if (count < 0 || count > input.available() / (7 * Integer.BYTES)) {
                throw new IOException("Некорректное число контактов: " + count);
            }
            var contacts = new ArrayList<ContactData>();
            for (int index = 0; index < count; index++) {
                contacts.add(new ContactData(ContactType.valueOf(readString(input)),
                        readString(input), readString(input), readString(input),
                        readString(input), readString(input), input.readInt()));
            }
            if (input.read() != -1) {
                throw new IOException("Лишние данные после списка контактов");
            }
            return new ContactArchive(version, contacts);
        } catch (EOFException error) {
            throw new IOException("Бинарный файл оборван", error);
        } catch (IllegalArgumentException error) {
            throw new IOException("Некорректные данные: " + error.getMessage(), error);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available()) {
            throw new IOException("Некорректная длина строки: " + length);
        }
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(input.readNBytes(length))).toString();
    }
}
