package ru.fazbear.campusdirectory.model;

import java.util.Objects;

public abstract class Contact {
    private final String name;
    private final String phone;
    private final String email;
    private final String organization;

    protected Contact(String name, String phone, String email, String organization) {
        this.name = name.strip();
        this.phone = phone.strip();
        this.email = email.strip();
        this.organization = organization.strip();
    }

    public abstract ContactType getType();

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getOrganization() {
        return organization;
    }

    @Override
    public String toString() {
        return "%s: %s, %s".formatted(getType().getDisplayName(), name, phone);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Contact contact = (Contact) other;
        return name.equals(contact.name)
                && phone.equals(contact.phone)
                && email.equals(contact.email)
                && organization.equals(contact.organization);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, phone, email, organization);
    }
}
