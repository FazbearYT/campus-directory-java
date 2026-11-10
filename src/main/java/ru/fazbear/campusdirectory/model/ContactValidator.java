package ru.fazbear.campusdirectory.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ContactValidator {
    public static final int MAXIMUM_NAME_LENGTH = 256;
    private static final Pattern PHONE_PATTERN = Pattern.compile("[+0-9()\\- ]{3,25}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+"
    );

    private ContactValidator() {
    }

    public static List<String> validate(Contact contact) {
        var errors = new ArrayList<String>();

        if (contact.getName().isEmpty()) {
            errors.add("Имя обязательно");
        } else if (containsLineBreak(contact.getName())) {
            errors.add("Имя должно занимать одну строку");
        } else if (contact.getName().length() > MAXIMUM_NAME_LENGTH) {
            errors.add("Имя не должно превышать " + MAXIMUM_NAME_LENGTH + " символов");
        }

        if (contact.getPhone().isEmpty()) {
            errors.add("Телефон обязателен");
        } else if (!PHONE_PATTERN.matcher(contact.getPhone()).matches()
                || contact.getPhone().chars().filter(symbol -> symbol >= '0' && symbol <= '9').count() < 3
                || contact.getPhone().indexOf('+', 1) >= 0) {
            errors.add("Телефон: от 3 до 25 символов, минимум 3 цифры; + допустим только в начале");
        }

        if (!contact.getEmail().isEmpty() && (!EMAIL_PATTERN.matcher(contact.getEmail()).matches()
                || contact.getEmail().startsWith(".") || contact.getEmail().contains("..")
                || contact.getEmail().contains(".@"))) {
            errors.add("E-mail имеет неверный формат");
        }

        if (containsLineBreak(contact.getOrganization())) {
            errors.add("Организация должна занимать одну строку");
        }

        if (contact instanceof CorporateContact corporate) {
            if (corporate.getOrganization().isEmpty()) {
                errors.add("Для корпоративного контакта нужна организация");
            }
            if (corporate.getPosition().isEmpty()) {
                errors.add("Для корпоративного контакта нужна должность");
            }
            if (containsLineBreak(corporate.getPosition())) {
                errors.add("Должность должна занимать одну строку");
            }
            if (corporate.getExtension() < 1 || corporate.getExtension() > 999_999) {
                errors.add("Внутренний номер должен быть от 1 до 999999");
            }
        }

        if (contact instanceof EmergencyContact && contact.getOrganization().isEmpty()) {
            errors.add("Для аварийного контакта нужна организация");
        }

        return List.copyOf(errors);
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}
