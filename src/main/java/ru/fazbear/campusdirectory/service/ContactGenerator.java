package ru.fazbear.campusdirectory.service;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import ru.fazbear.campusdirectory.model.Contact;
import ru.fazbear.campusdirectory.model.CorporateContact;
import ru.fazbear.campusdirectory.model.PersonalContact;

public final class ContactGenerator {
    private static final long SEED = 20261031L;
    private static final int FREQUENT_NAME_COUNT = 6;
    private static final int FREQUENT_NAME_PERCENT = 45;
    private static final String[] MALE_NAMES = {
            "Александр", "Алексей", "Андрей", "Антон", "Артём", "Артур",
            "Анатолий", "Борис", "Вадим", "Валерий", "Василий", "Виктор", "Виталий",
            "Глеб", "Денис", "Дмитрий", "Евгений", "Егор", "Иван", "Игорь", "Илья",
            "Кирилл", "Лев", "Марк", "Матвей", "Михаил", "Никита", "Николай",
            "Олег", "Павел", "Пётр", "Роман", "Руслан", "Семён", "Сергей", "Степан",
            "Тимофей", "Тимур", "Фёдор", "Эдуард", "Ярослав"
    };
    private static final String[] FEMALE_NAMES = {
            "Анна", "Анастасия", "Александра", "Алёна", "Алина", "Алиса",
            "Антонина", "Ася", "Валентина", "Варвара", "Вера", "Вероника", "Виктория",
            "Галина", "Дарья", "Евгения", "Екатерина", "Елена", "Елизавета", "Зоя",
            "Ирина", "Ксения", "Лидия", "Маргарита", "Марина", "Мария", "Надежда",
            "Наталья", "Ольга", "Полина", "Светлана", "София", "Татьяна", "Эльвира", "Юлия", "Яна"
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
        return generate(count, cancelled, progress, false);
    }

    public Contact[] generateForBenchmark(int count, BooleanSupplier cancelled) {
        return generate(count, cancelled, completed -> { }, true);
    }

    private Contact[] generate(int count, BooleanSupplier cancelled, IntConsumer progress, boolean uniqueNames) {
        if (count < 0) {
            throw new IllegalArgumentException("Количество контактов не может быть отрицательным");
        }

        var random = new Random(SEED);
        var contacts = new Contact[count];
        for (int index = 0; index < count; index++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("Генерация отменена");
            }
            contacts[index] = createContact(index, random, uniqueNames);
            if ((index + 1) % 100 == 0 || index + 1 == count) {
                progress.accept(index + 1);
            }
        }
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Генерация отменена");
        }
        return contacts;
    }

    private Contact createContact(int index, Random random, boolean uniqueNames) {
        boolean female = random.nextBoolean();
        String[] firstNames = female ? FEMALE_NAMES : MALE_NAMES;
        int nameIndex = random.nextInt(100) < FREQUENT_NAME_PERCENT
                ? random.nextInt(FREQUENT_NAME_COUNT)
                : FREQUENT_NAME_COUNT + random.nextInt(firstNames.length - FREQUENT_NAME_COUNT);
        String firstName = firstNames[nameIndex];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)] + (female ? "а" : "");
        String name = firstName + " " + lastName;
        if (uniqueNames) {
            name += String.format(Locale.ROOT, " %06d", index + 1);
        }
        String phone = String.format(Locale.ROOT, "+7 900 %03d-%02d-%02d",
                index % 1_000,
                (index / 1_000) % 100,
                (index / 100_000) % 100
        );
        String email = String.format(Locale.ROOT, "contact%06d@example.com", index + 1);

        if (index % 3 == 0) {
            return new CorporateContact(
                    name,
                    phone,
                    email,
                    ORGANIZATIONS[random.nextInt(ORGANIZATIONS.length)],
                    POSITIONS[random.nextInt(POSITIONS.length)],
                    index % 999_999 + 1
            );
        }
        return new PersonalContact(name, phone, email, "");
    }
}
