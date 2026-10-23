package ru.fazbear.campusdirectory.service;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.CorporateContact;
import ru.fazbear.campusdirectory.model.PersonalContact;

public final class ContactGenerator {
    private static final String[] FIRST_NAMES = {
            "Анна", "Борис", "Вера", "Глеб", "Дарья", "Егор", "Ирина", "Кирилл",
            "Мария", "Никита", "Ольга", "Павел", "София", "Тимур", "Юлия", "Ярослав"
    };
    private static final String[] LAST_NAMES = {
            "Белов", "Волков", "Громов", "Данилов", "Ершов", "Жуков", "Зайцев", "Ильин",
            "Ковалёв", "Лебедев", "Морозов", "Новиков", "Орлов", "Петров", "Смирнов", "Фролов"
    };
    private static final String[] ORGANIZATIONS = {
            "НГТУ", "Технопарк", "Городская служба", "Исследовательский центр"
    };
    private static final String[] POSITIONS = {
            "Инженер", "Аналитик", "Методист", "Разработчик"
    };

    public Contact[] generate(int count) {
        return generate(count, () -> false, completed -> { });
    }

    public Contact[] generate(int count, BooleanSupplier cancelled, IntConsumer progress) {
        if (count < 0) {
            throw new IllegalArgumentException("Количество контактов не может быть отрицательным");
        }

        var contacts = new Contact[count];
        for (int index = 0; index < count; index++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("Генерация отменена");
            }
            contacts[index] = createContact(index);
            if ((index + 1) % 100 == 0 || index + 1 == count) {
                progress.accept(index + 1);
            }
        }
        return contacts;
    }

    private Contact createContact(int index) {
        String firstName = FIRST_NAMES[index % FIRST_NAMES.length];
        String lastName = LAST_NAMES[(index / FIRST_NAMES.length) % LAST_NAMES.length];
        String name = "%s %s %06d".formatted(firstName, lastName, index + 1);
        String phone = "+7 900 %03d-%02d-%02d".formatted(
                index % 1_000,
                (index / 1_000) % 100,
                (index / 100_000) % 100
        );
        String email = "contact%06d@example.com".formatted(index + 1);

        if (index % 3 == 0) {
            return new CorporateContact(
                    name,
                    phone,
                    email,
                    ORGANIZATIONS[index % ORGANIZATIONS.length],
                    POSITIONS[index % POSITIONS.length],
                    index % 999_999 + 1
            );
        }
        return new PersonalContact(name, phone, email, "");
    }
}
