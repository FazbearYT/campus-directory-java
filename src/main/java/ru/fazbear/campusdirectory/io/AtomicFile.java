package ru.fazbear.campusdirectory.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class AtomicFile {
    private AtomicFile() {
    }

    static void write(Path target, Writer writer) throws IOException {
        Path destination = target.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".contacts-", ".tmp");
        try {
            writer.write(temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    interface Writer {
        void write(Path file) throws IOException;
    }
}
