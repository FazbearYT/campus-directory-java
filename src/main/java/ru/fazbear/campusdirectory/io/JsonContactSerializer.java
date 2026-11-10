package ru.fazbear.campusdirectory.io;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class JsonContactSerializer implements Serializer<ContactArchive> {
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();

    @Override
    public String format() {
        return "json";
    }

    @Override
    public void save(ContactArchive data, Path file) throws IOException {
        AtomicFile.write(file, temporary -> mapper.writerWithDefaultPrettyPrinter()
                .writeValue(temporary.toFile(), data));
    }

    @Override
    public ContactArchive load(Path file) throws IOException {
        ContactArchive archive = mapper.readValue(file.toFile(), ContactArchive.class);
        if (archive == null) {
            throw new IOException("Вместо архива указан null");
        }
        return archive;
    }
}
