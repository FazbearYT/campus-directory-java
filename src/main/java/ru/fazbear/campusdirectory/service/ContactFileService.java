package ru.fazbear.campusdirectory.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import ru.fazbear.campusdirectory.io.ContactArchive;
import ru.fazbear.campusdirectory.io.Serializer;

public final class ContactFileService {
    public ContactDirectory load(
            Serializer<ContactArchive> serializer, Path file,
            BooleanSupplier cancelled, IntConsumer progress
    ) throws IOException {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Импорт отменён");
        }
        ContactArchive archive = serializer.load(file);
        var directory = new ContactDirectory();
        directory.replaceAll(archive.contacts().stream().map(data -> data.toContact()).toList(),
                cancelled, progress);
        return directory;
    }
}
